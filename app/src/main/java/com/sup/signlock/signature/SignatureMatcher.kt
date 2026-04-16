package com.sup.signlock.signature

import android.util.Log
import com.sup.signlock.data.SignaturePoint
import com.sup.signlock.data.SignatureStroke
import com.sup.signlock.data.SignatureTemplate
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Smart multi-feature signature matching engine.
 *
 * Uses 7 independent analysis layers so that different letters/words
 * are reliably rejected even if they share a similar overall size or speed:
 *
 *  1. Direction Histogram — distribution of stroke directions (8 bins)
 *  2. Grid Density — WHERE ink is placed on a 4×4 spatial grid
 *  3. Direction-Augmented DTW — fine-grained shape + direction comparison
 *  4. Curvature Histogram — distribution of curve tightness/direction
 *  5. Velocity Rhythm — coefficient of variation of drawing speed
 *  6. Aspect Ratio — bounding box proportions
 *  7. Stroke Count — number of separate strokes
 *
 * Quick-reject filters discard obviously different inputs before
 * expensive DTW runs.
 */
class SignatureMatcher {

    companion object {
        private const val TAG = "SignatureMatcher"

        // ── Weights (sum = 1.0) ──
        private const val W_DIR_HIST   = 0.22f   // Direction distribution (best letter discriminator)
        private const val W_GRID       = 0.18f   // Spatial layout
        private const val W_DTW        = 0.20f   // Fine-grained shape + direction
        private const val W_CURVATURE  = 0.15f   // Curve structure
        private const val W_VELOCITY   = 0.10f   // Drawing rhythm / style
        private const val W_ASPECT     = 0.10f   // Proportions
        private const val W_STROKE     = 0.05f   // Stroke count

        // ── Threshold ──
        private const val MATCH_THRESHOLD = 0.48f

        // ── Quick-reject limits ──
        private const val MAX_STROKE_DIFF = 3
        private const val MIN_ASPECT_RATIO = 0.25f
        private const val MIN_PATH_RATIO = 0.30f

        // ── Algorithm params ──
        private const val RESAMPLE_N = 64
        private const val DIR_BINS = 8
        private const val GRID_SIZE = 4           // 4×4 = 16 cells
        private const val CURVE_BINS = 8
        private const val DTW_SCALE = 12.0
        private const val ANGLE_PENALTY = 1.5
    }

    data class MatchResult(
        val isMatch: Boolean,
        val score: Float,
        val message: String
    )

    // ─── Internal feature bundle ────────────────────────────────────
    private class Features(
        val dirHistogram: FloatArray,        // 8 direction bins
        val gridDensity: FloatArray,         // 16 spatial cells
        val curvHistogram: FloatArray,       // 8 curvature bins
        val velocityCV: Float,               // speed coefficient of variation
        val aspectRatio: Float,              // width / height
        val strokeCount: Int,
        val normPathLength: Float,           // total path / bbox diagonal
        val resampledStrokes: List<List<SignaturePoint>>,
        val strokeAngles: List<FloatArray>
    )

    // =================================================================
    //  PUBLIC API
    // =================================================================

    fun matchSignature(
        inputSignature: SignatureTemplate,
        storedTemplates: List<SignatureTemplate>
    ): MatchResult {
        if (storedTemplates.isEmpty()) {
            return MatchResult(false, 0f, "No templates stored")
        }

        val inputFeatures = extractFeatures(inputSignature)

        val scores = storedTemplates.map { template ->
            val templateFeatures = extractFeatures(template)
            compareFeatures(inputFeatures, templateFeatures)
        }

        val maxScore = scores.maxOrNull() ?: 0f
        val avgScore = scores.average().toFloat()
        val finalScore = maxScore * 0.6f + avgScore * 0.4f
        val isMatch = finalScore >= MATCH_THRESHOLD

        Log.d(TAG, "Per-template: $scores | max=$maxScore avg=$avgScore final=$finalScore match=$isMatch")

        return MatchResult(
            isMatch = isMatch,
            score = finalScore,
            message = if (isMatch) "Signature matched!" else "Signature does not match"
        )
    }

    // =================================================================
    //  FEATURE EXTRACTION
    // =================================================================

    private fun extractFeatures(sig: SignatureTemplate): Features {
        val bbox = sig.boundingBox
        val dim = max(bbox.width, bbox.height).coerceAtLeast(1f)
        val scale = 100f / dim

        // 1. Normalise points to 0-100 (preserving aspect ratio)
        val normStrokes = sig.strokes.map { stroke ->
            stroke.points.map { pt ->
                SignaturePoint(
                    x = (pt.x - bbox.minX) * scale,
                    y = (pt.y - bbox.minY) * scale,
                    timestamp = pt.timestamp
                )
            }
        }

        // 2. Resample each stroke to fixed point count
        val resampled = normStrokes.map { resamplePoints(it, RESAMPLE_N) }

        // 3. Angles per resampled stroke
        val angles = resampled.map { calculateAngles(it) }

        // ── Direction histogram (from resampled → equidistant sampling) ──
        val allResampled = resampled.flatten()
        val dirHist = computeDirectionHistogram(allResampled)

        // ── Grid density (from raw normalised → more spatial detail) ──
        val allNorm = normStrokes.flatten()
        val grid = computeGridDensity(allNorm)

        // ── Curvature histogram (from per-stroke angles, merged) ──
        val curvHist = computeCurvatureHistogram(angles)

        // ── Velocity CV ──
        val velCV = computeVelocityCV(allResampled)

        // ── Aspect ratio ──
        val ar = max(bbox.width, 1f) / max(bbox.height, 1f)

        // ── Normalised path length ──
        val totalPath = normStrokes.sumOf { pathLength(it) }
        val diag = sqrt((bbox.width * bbox.width + bbox.height * bbox.height).toDouble()).coerceAtLeast(1.0)
        val npl = (totalPath / diag).toFloat()

        return Features(
            dirHistogram = dirHist,
            gridDensity = grid,
            curvHistogram = curvHist,
            velocityCV = velCV,
            aspectRatio = ar,
            strokeCount = sig.strokes.size,
            normPathLength = npl,
            resampledStrokes = resampled,
            strokeAngles = angles
        )
    }

    // =================================================================
    //  FEATURE COMPARISON
    // =================================================================

    private fun compareFeatures(input: Features, template: Features): Float {
        // ── Quick reject (cheap checks first) ──
        if (quickReject(input, template)) return 0f

        // ── Individual scores ──
        val dirScore    = cosineSimilarity(input.dirHistogram, template.dirHistogram)
        val gridScore   = cosineSimilarity(input.gridDensity, template.gridDensity)
        val curvScore   = cosineSimilarity(input.curvHistogram, template.curvHistogram)
        val dtwScore    = computeDTWScore(input, template)
        val velScore    = compareRatio(input.velocityCV, template.velocityCV, default = 0.5f)
        val aspectScore = compareRatio(input.aspectRatio, template.aspectRatio, default = 0f)
        val strokeScore = strokeCountScore(input.strokeCount, template.strokeCount)

        val total = (dirScore * W_DIR_HIST) +
                (gridScore * W_GRID) +
                (dtwScore * W_DTW) +
                (curvScore * W_CURVATURE) +
                (velScore * W_VELOCITY) +
                (aspectScore * W_ASPECT) +
                (strokeScore * W_STROKE)

        Log.d(TAG, "dir=%.2f grid=%.2f dtw=%.2f curv=%.2f vel=%.2f asp=%.2f str=%.2f → %.3f"
            .format(dirScore, gridScore, dtwScore, curvScore, velScore, aspectScore, strokeScore, total))

        return total
    }

    private fun quickReject(a: Features, b: Features): Boolean {
        if (abs(a.strokeCount - b.strokeCount) > MAX_STROKE_DIFF) {
            Log.d(TAG, "Quick reject: stroke count ${a.strokeCount} vs ${b.strokeCount}")
            return true
        }
        val arRatio = safeRatio(a.aspectRatio, b.aspectRatio)
        if (arRatio < MIN_ASPECT_RATIO) {
            Log.d(TAG, "Quick reject: aspect ratio %.2f vs %.2f".format(a.aspectRatio, b.aspectRatio))
            return true
        }
        val plRatio = safeRatio(a.normPathLength, b.normPathLength)
        if (plRatio < MIN_PATH_RATIO) {
            Log.d(TAG, "Quick reject: path length %.2f vs %.2f".format(a.normPathLength, b.normPathLength))
            return true
        }
        return false
    }

    // =================================================================
    //  HISTOGRAM COMPUTATION
    // =================================================================

    /**
     * Direction histogram: bins the angle of movement between consecutive
     * points into 8 compass sectors (N, NE, E, SE, S, SW, W, NW).
     * Different letters produce dramatically different distributions.
     */
    private fun computeDirectionHistogram(points: List<SignaturePoint>): FloatArray {
        val bins = FloatArray(DIR_BINS)
        if (points.size < 2) return bins

        for (i in 0 until points.size - 1) {
            val dx = points[i + 1].x - points[i].x
            val dy = points[i + 1].y - points[i].y
            if (dx == 0f && dy == 0f) continue

            var angle = atan2(dy, dx)                       // [-π, π]
            if (angle < 0) angle += (2 * Math.PI).toFloat() // [0, 2π]

            val bin = ((angle / (2 * Math.PI).toFloat()) * DIR_BINS)
                .toInt().coerceIn(0, DIR_BINS - 1)
            bins[bin]++
        }

        normalizeArray(bins)
        return bins
    }

    /**
     * Grid density: divides the 100×100 normalised canvas into a 4×4 grid
     * and counts ink concentration per cell. Captures spatial layout.
     */
    private fun computeGridDensity(points: List<SignaturePoint>): FloatArray {
        val cells = FloatArray(GRID_SIZE * GRID_SIZE)
        if (points.isEmpty()) return cells

        for (pt in points) {
            val col = ((pt.x / 100f) * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
            val row = ((pt.y / 100f) * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
            cells[row * GRID_SIZE + col]++
        }

        normalizeArray(cells)
        return cells
    }

    /**
     * Curvature histogram: how the stroke curves at each point.
     * Bins the angular change (curvature) into 8 sectors.
     * Straight lines, gentle curves, and sharp turns have different profiles.
     */
    private fun computeCurvatureHistogram(strokeAngles: List<FloatArray>): FloatArray {
        val bins = FloatArray(CURVE_BINS)

        for (angles in strokeAngles) {
            if (angles.size < 2) continue
            for (i in 0 until angles.size - 1) {
                var curv = angles[i + 1] - angles[i]
                // Wrap to [-π, π]
                while (curv > Math.PI)  curv -= (2 * Math.PI).toFloat()
                while (curv < -Math.PI) curv += (2 * Math.PI).toFloat()

                // Map [-π, π] → [0, CURVE_BINS-1]
                val norm = (curv + Math.PI.toFloat()) / (2 * Math.PI).toFloat()
                val bin = (norm * CURVE_BINS).toInt().coerceIn(0, CURVE_BINS - 1)
                bins[bin]++
            }
        }

        normalizeArray(bins)
        return bins
    }

    /**
     * Velocity coefficient of variation — measures the RHYTHM of drawing.
     * Same signer has consistent rhythm; a forger tracing slowly has CV ≈ 0.
     */
    private fun computeVelocityCV(points: List<SignaturePoint>): Float {
        if (points.size < 3) return 0f

        val speeds = mutableListOf<Float>()
        for (i in 0 until points.size - 1) {
            val dx = points[i + 1].x - points[i].x
            val dy = points[i + 1].y - points[i].y
            val dist = sqrt(dx * dx + dy * dy)
            val dt = max(1L, points[i + 1].timestamp - points[i].timestamp)
            speeds.add(dist / dt.toFloat())
        }

        val mean = speeds.average().toFloat()
        if (mean < 0.0001f) return 0f

        val variance = speeds.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance) / mean
    }

    // =================================================================
    //  DTW (Direction-Augmented)
    // =================================================================

    private fun computeDTWScore(input: Features, template: Features): Float {
        val pairs = min(input.resampledStrokes.size, template.resampledStrokes.size)
        if (pairs == 0) return 0f

        var totalScore = 0.0
        for (i in 0 until pairs) {
            val pts1 = input.resampledStrokes[i]
            val pts2 = template.resampledStrokes[i]
            val a1 = input.strokeAngles[i]
            val a2 = template.strokeAngles[i]

            val dtw = directionalDTW(pts1, a1, pts2, a2)
            val pathLen = max(pts1.size, pts2.size)
            val avg = if (pathLen > 0) dtw / pathLen else dtw

            totalScore += exp(-avg / DTW_SCALE)
        }

        return (totalScore / pairs).toFloat()
    }

    private fun directionalDTW(
        seq1: List<SignaturePoint>, angles1: FloatArray,
        seq2: List<SignaturePoint>, angles2: FloatArray
    ): Double {
        val n = seq1.size; val m = seq2.size
        if (n == 0 || m == 0) return Double.MAX_VALUE

        val dtw = Array(n + 1) { DoubleArray(m + 1) { Double.MAX_VALUE } }
        dtw[0][0] = 0.0

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = augmentedCost(seq1[i - 1], angles1[i - 1], seq2[j - 1], angles2[j - 1])
                dtw[i][j] = cost + minOf(dtw[i - 1][j], dtw[i][j - 1], dtw[i - 1][j - 1])
            }
        }
        return dtw[n][m]
    }

    private fun augmentedCost(p1: SignaturePoint, a1: Float, p2: SignaturePoint, a2: Float): Double {
        val posDist = eucDist(p1, p2)
        var aDiff = abs(a1 - a2).toDouble()
        if (aDiff > Math.PI) aDiff = 2.0 * Math.PI - aDiff
        return posDist * (1.0 + (aDiff / Math.PI) * ANGLE_PENALTY)
    }

    // =================================================================
    //  NORMALISATION & RESAMPLING
    // =================================================================

    private fun calculateAngles(points: List<SignaturePoint>): FloatArray {
        if (points.size < 2) return FloatArray(points.size)
        val a = FloatArray(points.size)
        for (i in 0 until points.size - 1) {
            a[i] = atan2(points[i + 1].y - points[i].y, points[i + 1].x - points[i].x)
        }
        a[points.size - 1] = a[points.size - 2]
        return a
    }

    private fun resamplePoints(points: List<SignaturePoint>, n: Int): List<SignaturePoint> {
        if (points.size < 2) return points
        val totalLen = pathLength(points)
        if (totalLen < 0.001) return points

        val interval = totalLen / (n - 1)
        val result = mutableListOf(points[0])
        var acc = 0.0; var prev = points[0]; var idx = 1

        while (result.size < n - 1 && idx < points.size) {
            val d = eucDist(prev, points[idx])
            if (acc + d >= interval) {
                val t = ((interval - acc) / d).toFloat().coerceIn(0f, 1f)
                val np = SignaturePoint(
                    x = prev.x + t * (points[idx].x - prev.x),
                    y = prev.y + t * (points[idx].y - prev.y),
                    timestamp = prev.timestamp + ((points[idx].timestamp - prev.timestamp) * t).toLong()
                )
                result.add(np); prev = np; acc = 0.0
            } else { acc += d; prev = points[idx]; idx++ }
        }

        while (result.size < n) result.add(points.last())
        return result.take(n)
    }

    // =================================================================
    //  UTILITIES
    // =================================================================

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var nA = 0f; var nB = 0f
        for (i in a.indices) { dot += a[i] * b[i]; nA += a[i] * a[i]; nB += b[i] * b[i] }
        val denom = sqrt(nA) * sqrt(nB)
        return if (denom > 0f) (dot / denom).coerceIn(0f, 1f) else 0f
    }

    private fun compareRatio(a: Float, b: Float, default: Float): Float {
        if (a < 0.0001f && b < 0.0001f) return default
        val mn = min(a, b); val mx = max(a, b)
        return if (mx > 0.0001f) (mn / mx).coerceIn(0f, 1f) else default
    }

    private fun safeRatio(a: Float, b: Float): Float {
        val mn = min(a, b); val mx = max(a, b)
        return if (mx > 0.0001f) mn / mx else 1f
    }

    private fun strokeCountScore(a: Int, b: Int): Float = when (abs(a - b)) {
        0 -> 1f; 1 -> 0.8f; 2 -> 0.5f; else -> 0.2f
    }

    private fun normalizeArray(arr: FloatArray) {
        val total = arr.sum()
        if (total > 0) for (i in arr.indices) arr[i] /= total
    }

    private fun pathLength(pts: List<SignaturePoint>): Double {
        var l = 0.0; for (i in 1 until pts.size) l += eucDist(pts[i - 1], pts[i]); return l
    }

    private fun eucDist(a: SignaturePoint, b: SignaturePoint): Double {
        val dx = (a.x - b.x).toDouble(); val dy = (a.y - b.y).toDouble()
        return sqrt(dx.pow(2) + dy.pow(2))
    }
}
