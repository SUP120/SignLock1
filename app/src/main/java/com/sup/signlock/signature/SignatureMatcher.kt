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
 * Smart signature matcher with strict verification.
 *
 * Three-layer architecture:
 *
 *  LAYER 1 — Direct Point Comparison (weight 40%)
 *   After normalising and resampling to equidistant points, compares
 *   position-by-position. This is the STRICTEST check: point 30 of
 *   an "L" is in a completely different place to point 30 of an "S".
 *   No elastic warping → different shapes CANNOT cheat.
 *
 *  LAYER 2 — Banded DTW (weight 25%)
 *   Direction-augmented DTW with Sakoe-Chiba band (±10 points).
 *   Allows slight timing/speed variation while rejecting large
 *   structural differences.
 *
 *  LAYER 3 — Feature Checks (weight 35%)
 *   Direction histogram, spatial grid density, curvature histogram,
 *   aspect ratio, velocity rhythm, stroke count.
 *
 *  GATES — Instant rejection if:
 *   • Direct similarity < 0.08  (completely different shape)
 *   • Stroke count differs by > 3
 *   • Aspect ratio ratio < 0.25
 *   • Path length ratio < 0.30
 */
class SignatureMatcher {

    companion object {
        private const val TAG = "SignatureMatcher"

        // ── Layer weights (sum = 1.0) ──
        private const val W_DIRECT     = 0.40f   // Direct point-to-point (strictest)
        private const val W_DTW        = 0.25f   // Banded DTW (handles natural variation)
        private const val W_DIR_HIST   = 0.12f   // Direction distribution
        private const val W_GRID       = 0.08f   // Spatial layout
        private const val W_CURVATURE  = 0.05f   // Curve pattern
        private const val W_ASPECT     = 0.05f   // Proportions
        private const val W_VELOCITY   = 0.03f   // Drawing rhythm
        private const val W_STROKE     = 0.02f   // Stroke count

        // ── Threshold ──
        private const val MATCH_THRESHOLD = 0.45f

        // ── Hard gates ──
        private const val MIN_DIRECT_SCORE = 0.08f  // Below this = instant reject
        private const val MAX_STROKE_DIFF = 3
        private const val MIN_ASPECT_RATIO = 0.25f
        private const val MIN_PATH_RATIO = 0.30f

        // ── Algorithm params ──
        private const val RESAMPLE_N = 64
        private const val DIRECT_SCALE = 14.0     // For point-to-point scoring
        private const val DTW_SCALE = 14.0        // For DTW scoring
        private const val DTW_BAND = 10           // Sakoe-Chiba bandwidth
        private const val ANGLE_PENALTY = 1.5     // Direction mismatch multiplier
        private const val DIR_BINS = 8
        private const val GRID_SIZE = 4           // 4×4 = 16 cells
        private const val CURVE_BINS = 8
    }

    data class MatchResult(
        val isMatch: Boolean,
        val score: Float,
        val message: String
    )

    // ── Feature bundle ───────────────────────────────────────────────
    private class Features(
        val dirHistogram: FloatArray,
        val gridDensity: FloatArray,
        val curvHistogram: FloatArray,
        val velocityCV: Float,
        val aspectRatio: Float,
        val strokeCount: Int,
        val normPathLength: Float,
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
            val tf = extractFeatures(template)
            compareAll(inputFeatures, tf)
        }

        val maxScore = scores.maxOrNull() ?: 0f
        val avgScore = scores.average().toFloat()

        // Equal weight on best match and consistency
        val finalScore = maxScore * 0.5f + avgScore * 0.5f
        val isMatch = finalScore >= MATCH_THRESHOLD

        Log.d(TAG, "Per-template: $scores")
        Log.d(TAG, "max=%.3f avg=%.3f final=%.3f match=$isMatch"
            .format(maxScore, avgScore, finalScore))

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

        val normStrokes = sig.strokes.map { s ->
            s.points.map { p ->
                SignaturePoint(
                    (p.x - bbox.minX) * scale,
                    (p.y - bbox.minY) * scale,
                    p.timestamp
                )
            }
        }

        val resampled = normStrokes.map { resamplePoints(it, RESAMPLE_N) }
        val angles = resampled.map { calculateAngles(it) }

        val allResampled = resampled.flatten()
        val allNorm = normStrokes.flatten()

        return Features(
            dirHistogram = computeDirectionHistogram(allResampled),
            gridDensity = computeGridDensity(allNorm),
            curvHistogram = computeCurvatureHistogram(angles),
            velocityCV = computeVelocityCV(allResampled),
            aspectRatio = max(bbox.width, 1f) / max(bbox.height, 1f),
            strokeCount = sig.strokes.size,
            normPathLength = run {
                val total = normStrokes.sumOf { pathLength(it) }
                val diag = sqrt((bbox.width.pow(2) + bbox.height.pow(2)).toDouble()).coerceAtLeast(1.0)
                (total / diag).toFloat()
            },
            resampledStrokes = resampled,
            strokeAngles = angles
        )
    }

    // =================================================================
    //  COMPARISON ENGINE
    // =================================================================

    private fun compareAll(input: Features, template: Features): Float {
        // ── Quick rejects ──
        if (abs(input.strokeCount - template.strokeCount) > MAX_STROKE_DIFF) {
            Log.d(TAG, "REJECT: stroke count ${input.strokeCount} vs ${template.strokeCount}")
            return 0f
        }
        if (safeRatio(input.aspectRatio, template.aspectRatio) < MIN_ASPECT_RATIO) {
            Log.d(TAG, "REJECT: aspect ratio %.2f vs %.2f".format(input.aspectRatio, template.aspectRatio))
            return 0f
        }
        if (safeRatio(input.normPathLength, template.normPathLength) < MIN_PATH_RATIO) {
            Log.d(TAG, "REJECT: path length %.2f vs %.2f".format(input.normPathLength, template.normPathLength))
            return 0f
        }

        // ── LAYER 1: Direct point-to-point comparison ──
        val directScore = computeDirectScore(input, template)

        // Hard gate: if shapes are completely different, stop here
        if (directScore < MIN_DIRECT_SCORE) {
            Log.d(TAG, "GATE REJECT: direct score %.3f < %.3f".format(directScore, MIN_DIRECT_SCORE))
            return 0f
        }

        // ── LAYER 2: Banded DTW ──
        val dtwScore = computeBandedDTWScore(input, template)

        // ── LAYER 3: Feature scores ──
        val dirScore = cosineSimilarity(input.dirHistogram, template.dirHistogram)
        val gridScore = cosineSimilarity(input.gridDensity, template.gridDensity)
        val curvScore = cosineSimilarity(input.curvHistogram, template.curvHistogram)
        val velScore = compareRatio(input.velocityCV, template.velocityCV, 0.5f)
        val aspectScore = compareRatio(input.aspectRatio, template.aspectRatio, 0f)
        val strokeScore = strokeCountScore(input.strokeCount, template.strokeCount)

        val total = (directScore * W_DIRECT) +
                (dtwScore * W_DTW) +
                (dirScore * W_DIR_HIST) +
                (gridScore * W_GRID) +
                (curvScore * W_CURVATURE) +
                (aspectScore * W_ASPECT) +
                (velScore * W_VELOCITY) +
                (strokeScore * W_STROKE)

        Log.d(TAG, "DIRECT=%.3f DTW=%.3f dir=%.2f grid=%.2f curv=%.2f asp=%.2f vel=%.2f str=%.2f → %.3f"
            .format(directScore, dtwScore, dirScore, gridScore, curvScore, aspectScore, velScore, strokeScore, total))

        return total
    }

    // =================================================================
    //  LAYER 1: DIRECT POINT-TO-POINT COMPARISON
    // =================================================================

    /**
     * Compares signatures point-by-point WITHOUT any elastic warping.
     * After spatial resampling, point i = position (i/N) along the path.
     * For the SAME word, these positions correspond to the same part of the writing.
     * For DIFFERENT words, they correspond to completely different positions.
     *
     * This is the primary discriminator — the one that actually rejects
     * different letters/words reliably.
     */
    private fun computeDirectScore(input: Features, template: Features): Float {
        val pairs = min(input.resampledStrokes.size, template.resampledStrokes.size)
        if (pairs == 0) return 0f

        var totalScore = 0.0
        for (i in 0 until pairs) {
            val pts1 = input.resampledStrokes[i]
            val pts2 = template.resampledStrokes[i]
            val n = min(pts1.size, pts2.size)
            if (n == 0) continue

            var totalDist = 0.0
            for (j in 0 until n) {
                totalDist += eucDist(pts1[j], pts2[j])
            }
            val avgDist = totalDist / n
            totalScore += exp(-avgDist / DIRECT_SCALE)
        }

        return (totalScore / pairs).toFloat()
    }

    // =================================================================
    //  LAYER 2: BANDED DTW
    // =================================================================

    /**
     * Direction-augmented DTW with Sakoe-Chiba band constraint.
     * The band prevents excessive warping — the alignment must stay
     * within ±10 positions of the diagonal. This allows slight natural
     * variation while rejecting fundamentally different shapes.
     */
    private fun computeBandedDTWScore(input: Features, template: Features): Float {
        val pairs = min(input.resampledStrokes.size, template.resampledStrokes.size)
        if (pairs == 0) return 0f

        var totalScore = 0.0
        for (i in 0 until pairs) {
            val pts1 = input.resampledStrokes[i]
            val pts2 = template.resampledStrokes[i]
            val a1 = input.strokeAngles[i]
            val a2 = template.strokeAngles[i]

            val dtw = bandedDirectionalDTW(pts1, a1, pts2, a2)
            val pathLen = max(pts1.size, pts2.size)
            val avg = if (pathLen > 0) dtw / pathLen else dtw

            totalScore += exp(-avg / DTW_SCALE)
        }

        return (totalScore / pairs).toFloat()
    }

    private fun bandedDirectionalDTW(
        seq1: List<SignaturePoint>, angles1: FloatArray,
        seq2: List<SignaturePoint>, angles2: FloatArray
    ): Double {
        val n = seq1.size; val m = seq2.size
        if (n == 0 || m == 0) return Double.MAX_VALUE

        val dtw = Array(n + 1) { DoubleArray(m + 1) { Double.MAX_VALUE } }
        dtw[0][0] = 0.0

        for (i in 1..n) {
            // Sakoe-Chiba band: only compute cells near the diagonal
            val diagJ = (i.toLong() * m / n).toInt()  // position on diagonal
            val jStart = max(1, diagJ - DTW_BAND)
            val jEnd = min(m, diagJ + DTW_BAND)

            for (j in jStart..jEnd) {
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
    //  LAYER 3: FEATURE HISTOGRAMS
    // =================================================================

    private fun computeDirectionHistogram(points: List<SignaturePoint>): FloatArray {
        val bins = FloatArray(DIR_BINS)
        if (points.size < 2) return bins
        for (i in 0 until points.size - 1) {
            val dx = points[i + 1].x - points[i].x
            val dy = points[i + 1].y - points[i].y
            if (dx == 0f && dy == 0f) continue
            var angle = atan2(dy, dx)
            if (angle < 0) angle += (2 * Math.PI).toFloat()
            val bin = ((angle / (2 * Math.PI).toFloat()) * DIR_BINS).toInt().coerceIn(0, DIR_BINS - 1)
            bins[bin]++
        }
        normalize(bins)
        return bins
    }

    private fun computeGridDensity(points: List<SignaturePoint>): FloatArray {
        val cells = FloatArray(GRID_SIZE * GRID_SIZE)
        for (pt in points) {
            val c = ((pt.x / 100f) * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
            val r = ((pt.y / 100f) * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
            cells[r * GRID_SIZE + c]++
        }
        normalize(cells)
        return cells
    }

    private fun computeCurvatureHistogram(strokeAngles: List<FloatArray>): FloatArray {
        val bins = FloatArray(CURVE_BINS)
        for (angles in strokeAngles) {
            if (angles.size < 2) continue
            for (i in 0 until angles.size - 1) {
                var c = angles[i + 1] - angles[i]
                while (c > Math.PI) c -= (2 * Math.PI).toFloat()
                while (c < -Math.PI) c += (2 * Math.PI).toFloat()
                val norm = (c + Math.PI.toFloat()) / (2 * Math.PI).toFloat()
                bins[(norm * CURVE_BINS).toInt().coerceIn(0, CURVE_BINS - 1)]++
            }
        }
        normalize(bins)
        return bins
    }

    private fun computeVelocityCV(points: List<SignaturePoint>): Float {
        if (points.size < 3) return 0f
        val speeds = (0 until points.size - 1).map { i ->
            val dx = points[i + 1].x - points[i].x; val dy = points[i + 1].y - points[i].y
            val dist = sqrt(dx * dx + dy * dy)
            dist / max(1L, points[i + 1].timestamp - points[i].timestamp).toFloat()
        }
        val mean = speeds.average().toFloat()
        if (mean < 0.0001f) return 0f
        val std = sqrt(speeds.map { (it - mean).pow(2) }.average().toFloat())
        return std / mean
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
        a[points.size - 1] = a.getOrElse(points.size - 2) { 0f }
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
                    prev.x + t * (points[idx].x - prev.x),
                    prev.y + t * (points[idx].y - prev.y),
                    prev.timestamp + ((points[idx].timestamp - prev.timestamp) * t).toLong()
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
        for (i in a.indices) { dot += a[i] * b[i]; nA += a[i].pow(2); nB += b[i].pow(2) }
        val d = sqrt(nA) * sqrt(nB)
        return if (d > 0f) (dot / d).coerceIn(0f, 1f) else 0f
    }

    private fun compareRatio(a: Float, b: Float, default: Float): Float {
        if (a < 0.0001f && b < 0.0001f) return default
        return (min(a, b) / max(a, b).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    }

    private fun safeRatio(a: Float, b: Float): Float {
        val mx = max(a, b)
        return if (mx > 0.0001f) min(a, b) / mx else 1f
    }

    private fun strokeCountScore(a: Int, b: Int) = when (abs(a - b)) {
        0 -> 1f; 1 -> 0.8f; 2 -> 0.5f; else -> 0.2f
    }

    private fun normalize(arr: FloatArray) {
        val s = arr.sum(); if (s > 0) for (i in arr.indices) arr[i] /= s
    }

    private fun pathLength(pts: List<SignaturePoint>): Double {
        var l = 0.0; for (i in 1 until pts.size) l += eucDist(pts[i - 1], pts[i]); return l
    }

    private fun eucDist(a: SignaturePoint, b: SignaturePoint): Double {
        val dx = (a.x - b.x).toDouble(); val dy = (a.y - b.y).toDouble()
        return sqrt(dx.pow(2) + dy.pow(2))
    }
}
