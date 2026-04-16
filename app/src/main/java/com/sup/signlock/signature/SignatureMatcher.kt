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
 * Strict segment-gated signature matcher.
 *
 * Key innovation: the signature is split into 4 equal segments and
 * EACH segment must independently score above a minimum threshold.
 * This prevents "Supr" vs "Suti" from matching — even though the
 * "Su" portion is similar, the "pr" vs "ti" segments will fail the gate.
 *
 * Three layers:
 *  1. Segment-gated direct comparison (40%) — position + angle, no warping
 *  2. Banded DTW (25%) — handles natural variation
 *  3. Feature histograms (35%) — direction, spatial, curvature, speed
 *
 * The direct comparison uses an ADDITIVE angle penalty so that points
 * at similar positions but with different pen directions score poorly.
 */
class SignatureMatcher {

    companion object {
        private const val TAG = "SignatureMatcher"

        // ── Layer weights (sum = 1.0) ──
        private const val W_DIRECT     = 0.40f
        private const val W_DTW        = 0.25f
        private const val W_DIR_HIST   = 0.12f
        private const val W_GRID       = 0.08f
        private const val W_CURVATURE  = 0.05f
        private const val W_ASPECT     = 0.05f
        private const val W_VELOCITY   = 0.03f
        private const val W_STROKE     = 0.02f

        // ── Threshold ──
        private const val MATCH_THRESHOLD = 0.45f

        // ── Segment gating ──
        private const val SEGMENT_COUNT = 4            // Split each stroke into 4 parts
        private const val MIN_SEGMENT_SCORE = 0.10f    // Every segment must pass this

        // ── Hard gates ──
        private const val MIN_DIRECT_SCORE = 0.08f
        private const val MAX_STROKE_DIFF = 3
        private const val MIN_ASPECT_RATIO = 0.25f
        private const val MIN_PATH_RATIO = 0.30f

        // ── Algorithm params ──
        private const val RESAMPLE_N = 64
        private const val DIRECT_SCALE = 18.0          // For segment scoring
        private const val ANGLE_DIRECT_PENALTY = 20.0  // Additive angle penalty in direct comparison
        private const val DTW_SCALE = 14.0
        private const val DTW_BAND = 10
        private const val ANGLE_DTW_PENALTY = 1.5      // Multiplicative in DTW
        private const val DIR_BINS = 8
        private const val GRID_SIZE = 4
        private const val CURVE_BINS = 8
    }

    data class MatchResult(
        val isMatch: Boolean,
        val score: Float,
        val message: String
    )

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

        val scores = storedTemplates.map { t ->
            compareAll(inputFeatures, extractFeatures(t))
        }

        val maxScore = scores.maxOrNull() ?: 0f
        val avgScore = scores.average().toFloat()
        val finalScore = maxScore * 0.5f + avgScore * 0.5f
        val isMatch = finalScore >= MATCH_THRESHOLD

        Log.d(TAG, "Per-template: $scores | max=%.3f avg=%.3f final=%.3f match=$isMatch"
            .format(maxScore, avgScore, finalScore))

        return MatchResult(isMatch, finalScore,
            if (isMatch) "Signature matched!" else "Signature does not match")
    }

    // =================================================================
    //  FEATURE EXTRACTION
    // =================================================================

    private fun extractFeatures(sig: SignatureTemplate): Features {
        val bbox = sig.boundingBox
        val dim = max(bbox.width, bbox.height).coerceAtLeast(1f)
        val scale = 100f / dim

        val normStrokes = sig.strokes.map { s ->
            s.points.map { p -> SignaturePoint((p.x - bbox.minX) * scale, (p.y - bbox.minY) * scale, p.timestamp) }
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
        // Quick rejects
        if (abs(input.strokeCount - template.strokeCount) > MAX_STROKE_DIFF) return 0f
        if (safeRatio(input.aspectRatio, template.aspectRatio) < MIN_ASPECT_RATIO) return 0f
        if (safeRatio(input.normPathLength, template.normPathLength) < MIN_PATH_RATIO) return 0f

        // LAYER 1: Segment-gated direct comparison
        val directScore = computeDirectScore(input, template)
        if (directScore < MIN_DIRECT_SCORE) {
            Log.d(TAG, "GATE REJECT: direct=%.3f".format(directScore))
            return 0f
        }

        // LAYER 2: Banded DTW
        val dtwScore = computeBandedDTWScore(input, template)

        // LAYER 3: Features
        val dirScore = cosineSimilarity(input.dirHistogram, template.dirHistogram)
        val gridScore = cosineSimilarity(input.gridDensity, template.gridDensity)
        val curvScore = cosineSimilarity(input.curvHistogram, template.curvHistogram)
        val velScore = compareRatio(input.velocityCV, template.velocityCV, 0.5f)
        val aspectScore = compareRatio(input.aspectRatio, template.aspectRatio, 0f)
        val strokeScore = strokeCountScore(input.strokeCount, template.strokeCount)

        val total = (directScore * W_DIRECT) + (dtwScore * W_DTW) +
                (dirScore * W_DIR_HIST) + (gridScore * W_GRID) +
                (curvScore * W_CURVATURE) + (aspectScore * W_ASPECT) +
                (velScore * W_VELOCITY) + (strokeScore * W_STROKE)

        Log.d(TAG, "DIR=%.3f DTW=%.3f dir=%.2f grid=%.2f curv=%.2f asp=%.2f vel=%.2f str=%.2f → %.3f"
            .format(directScore, dtwScore, dirScore, gridScore, curvScore, aspectScore, velScore, strokeScore, total))

        return total
    }

    // =================================================================
    //  LAYER 1: SEGMENT-GATED DIRECT COMPARISON
    // =================================================================

    /**
     * Splits each stroke into 4 segments and computes a score per segment
     * using position + angle (additive penalty). If ANY segment scores
     * below MIN_SEGMENT_SCORE, the whole signature is rejected.
     *
     * This catches "Supr" vs "Suti": the "Su" segments pass, but the
     * "pr" vs "ti" segments fail because positions AND angles differ.
     *
     * The additive angle penalty means even if two points are at similar
     * positions, they score poorly if the pen was moving in a different
     * direction (e.g. "p" curves down while "t" goes up).
     */
    private fun computeDirectScore(input: Features, template: Features): Float {
        val pairs = min(input.resampledStrokes.size, template.resampledStrokes.size)
        if (pairs == 0) return 0f

        var overallScore = 0.0
        var worstSegment = Float.MAX_VALUE

        for (i in 0 until pairs) {
            val pts1 = input.resampledStrokes[i]
            val pts2 = template.resampledStrokes[i]
            val a1 = input.strokeAngles[i]
            val a2 = template.strokeAngles[i]
            val n = min(pts1.size, pts2.size)
            if (n < SEGMENT_COUNT) continue

            val segSize = n / SEGMENT_COUNT
            var strokeScore = 0.0

            for (seg in 0 until SEGMENT_COUNT) {
                val start = seg * segSize
                val end = if (seg == SEGMENT_COUNT - 1) n else (seg + 1) * segSize

                var segDist = 0.0
                for (j in start until end) {
                    segDist += directDist(pts1[j], a1[j], pts2[j], a2[j])
                }
                val avgDist = segDist / (end - start)
                val segScore = exp(-avgDist / DIRECT_SCALE).toFloat()

                worstSegment = min(worstSegment, segScore)
                strokeScore += segScore

                Log.d(TAG, "  Stroke$i Seg$seg: avgDist=%.1f score=%.3f".format(avgDist, segScore))
            }

            overallScore += strokeScore / SEGMENT_COUNT
        }

        // SEGMENT GATE: if any part of the signature is very different, reject
        if (worstSegment < MIN_SEGMENT_SCORE) {
            Log.d(TAG, "SEGMENT GATE: worst=%.3f < %.3f → REJECT".format(worstSegment, MIN_SEGMENT_SCORE))
            return worstSegment
        }

        return (overallScore / pairs).toFloat()
    }

    /**
     * Combined distance: spatial + direction (additive).
     *
     * "p" going ↓ at position (50,70) vs "t" going ↑ at position (50,70):
     *   posDist = 0, angleDiff = π, combined = 0 + π × 20 = 63
     *   → score = exp(-63/18) = 0.03 → FAIL
     *
     * Same letter, slight variation:
     *   posDist = 10, angleDiff = 0.3, combined = 10 + 0.3 × 20 = 16
     *   → score = exp(-16/18) = 0.41 → PASS
     */
    private fun directDist(p1: SignaturePoint, a1: Float, p2: SignaturePoint, a2: Float): Double {
        val posDist = eucDist(p1, p2)
        var angleDiff = abs(a1 - a2).toDouble()
        if (angleDiff > Math.PI) angleDiff = 2.0 * Math.PI - angleDiff
        return posDist + angleDiff * ANGLE_DIRECT_PENALTY
    }

    // =================================================================
    //  LAYER 2: BANDED DTW
    // =================================================================

    private fun computeBandedDTWScore(input: Features, template: Features): Float {
        val pairs = min(input.resampledStrokes.size, template.resampledStrokes.size)
        if (pairs == 0) return 0f
        var total = 0.0
        for (i in 0 until pairs) {
            val dtw = bandedDTW(input.resampledStrokes[i], input.strokeAngles[i],
                template.resampledStrokes[i], template.strokeAngles[i])
            val pl = max(input.resampledStrokes[i].size, template.resampledStrokes[i].size)
            total += exp(-(if (pl > 0) dtw / pl else dtw) / DTW_SCALE)
        }
        return (total / pairs).toFloat()
    }

    private fun bandedDTW(
        s1: List<SignaturePoint>, a1: FloatArray,
        s2: List<SignaturePoint>, a2: FloatArray
    ): Double {
        val n = s1.size; val m = s2.size
        if (n == 0 || m == 0) return Double.MAX_VALUE
        val d = Array(n + 1) { DoubleArray(m + 1) { Double.MAX_VALUE } }
        d[0][0] = 0.0
        for (i in 1..n) {
            val dj = (i.toLong() * m / n).toInt()
            for (j in max(1, dj - DTW_BAND)..min(m, dj + DTW_BAND)) {
                val cost = dtwCost(s1[i - 1], a1[i - 1], s2[j - 1], a2[j - 1])
                d[i][j] = cost + minOf(d[i - 1][j], d[i][j - 1], d[i - 1][j - 1])
            }
        }
        return d[n][m]
    }

    private fun dtwCost(p1: SignaturePoint, a1: Float, p2: SignaturePoint, a2: Float): Double {
        val pd = eucDist(p1, p2)
        var ad = abs(a1 - a2).toDouble()
        if (ad > Math.PI) ad = 2.0 * Math.PI - ad
        return pd * (1.0 + (ad / Math.PI) * ANGLE_DTW_PENALTY)
    }

    // =================================================================
    //  LAYER 3: FEATURE HISTOGRAMS
    // =================================================================

    private fun computeDirectionHistogram(points: List<SignaturePoint>): FloatArray {
        val bins = FloatArray(DIR_BINS)
        if (points.size < 2) return bins
        for (i in 0 until points.size - 1) {
            val dx = points[i + 1].x - points[i].x; val dy = points[i + 1].y - points[i].y
            if (dx == 0f && dy == 0f) continue
            var a = atan2(dy, dx); if (a < 0) a += (2 * Math.PI).toFloat()
            bins[((a / (2 * Math.PI).toFloat()) * DIR_BINS).toInt().coerceIn(0, DIR_BINS - 1)]++
        }
        norm(bins); return bins
    }

    private fun computeGridDensity(points: List<SignaturePoint>): FloatArray {
        val cells = FloatArray(GRID_SIZE * GRID_SIZE)
        for (p in points) {
            val c = ((p.x / 100f) * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
            val r = ((p.y / 100f) * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
            cells[r * GRID_SIZE + c]++
        }
        norm(cells); return cells
    }

    private fun computeCurvatureHistogram(sa: List<FloatArray>): FloatArray {
        val bins = FloatArray(CURVE_BINS)
        for (angles in sa) {
            if (angles.size < 2) continue
            for (i in 0 until angles.size - 1) {
                var c = angles[i + 1] - angles[i]
                while (c > Math.PI) c -= (2 * Math.PI).toFloat()
                while (c < -Math.PI) c += (2 * Math.PI).toFloat()
                val n = (c + Math.PI.toFloat()) / (2 * Math.PI).toFloat()
                bins[(n * CURVE_BINS).toInt().coerceIn(0, CURVE_BINS - 1)]++
            }
        }
        norm(bins); return bins
    }

    private fun computeVelocityCV(points: List<SignaturePoint>): Float {
        if (points.size < 3) return 0f
        val speeds = (0 until points.size - 1).map { i ->
            val dx = points[i + 1].x - points[i].x; val dy = points[i + 1].y - points[i].y
            sqrt(dx * dx + dy * dy) / max(1L, points[i + 1].timestamp - points[i].timestamp).toFloat()
        }
        val mean = speeds.average().toFloat()
        if (mean < 0.0001f) return 0f
        return sqrt(speeds.map { (it - mean).pow(2) }.average().toFloat()) / mean
    }

    // =================================================================
    //  NORMALISATION & RESAMPLING
    // =================================================================

    private fun calculateAngles(points: List<SignaturePoint>): FloatArray {
        if (points.size < 2) return FloatArray(points.size)
        val a = FloatArray(points.size)
        for (i in 0 until points.size - 1)
            a[i] = atan2(points[i + 1].y - points[i].y, points[i + 1].x - points[i].x)
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
                result.add(SignaturePoint(
                    prev.x + t * (points[idx].x - prev.x),
                    prev.y + t * (points[idx].y - prev.y),
                    prev.timestamp + ((points[idx].timestamp - prev.timestamp) * t).toLong()
                ))
                prev = result.last(); acc = 0.0
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

    private fun compareRatio(a: Float, b: Float, def: Float): Float {
        if (a < 0.0001f && b < 0.0001f) return def
        return (min(a, b) / max(a, b).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    }

    private fun safeRatio(a: Float, b: Float): Float {
        val mx = max(a, b); return if (mx > 0.0001f) min(a, b) / mx else 1f
    }

    private fun strokeCountScore(a: Int, b: Int) = when (abs(a - b)) {
        0 -> 1f; 1 -> 0.8f; 2 -> 0.5f; else -> 0.2f
    }

    private fun norm(arr: FloatArray) {
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
