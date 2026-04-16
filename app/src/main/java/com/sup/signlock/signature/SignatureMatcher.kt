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
 * Signature matching engine using Direction-Augmented Dynamic Time Warping.
 *
 * Key security features:
 *  1. Shape matching via DTW on normalised+resampled points
 *  2. Stroke DIRECTION analysis — two letters that pass through similar areas
 *     but curve differently will score very low
 *  3. Aspect ratio comparison — catches obviously different proportions
 *  4. Speed profiling — slow/deliberate drawing is penalised (likely forgery)
 */
class SignatureMatcher {

    companion object {
        private const val TAG = "SignatureMatcher"

        // --- Weights (must sum to 1.0) ---
        private const val SHAPE_WEIGHT = 0.55f    // Position + direction DTW
        private const val SPEED_WEIGHT = 0.20f    // Speed profiling
        private const val ASPECT_WEIGHT = 0.15f   // Bounding box proportions
        private const val STROKE_WEIGHT = 0.10f   // Stroke count similarity

        // --- Threshold ---
        private const val MATCH_THRESHOLD = 0.50f

        // --- Algorithm tuning ---
        private const val RESAMPLE_COUNT = 64
        private const val DTW_SCALE = 12.0        // Stricter than before
        private const val ANGLE_PENALTY = 1.5     // How much direction mismatch amplifies cost
    }

    data class MatchResult(
        val isMatch: Boolean,
        val score: Float,
        val message: String
    )

    // -----------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------

    fun matchSignature(
        inputSignature: SignatureTemplate,
        storedTemplates: List<SignatureTemplate>
    ): MatchResult {
        if (storedTemplates.isEmpty()) {
            return MatchResult(false, 0f, "No templates stored")
        }

        val scores = storedTemplates.map { template ->
            calculateSimilarityScore(inputSignature, template)
        }

        val maxScore = scores.maxOrNull() ?: 0f
        val avgScore = scores.average().toFloat()

        // Blend: best match (60%) + consistency across all templates (40%)
        val finalScore = maxScore * 0.6f + avgScore * 0.4f
        val isMatch = finalScore >= MATCH_THRESHOLD

        Log.d(TAG, "Per-template scores: $scores")
        Log.d(TAG, "max=$maxScore  avg=$avgScore  final=$finalScore  match=$isMatch")

        return MatchResult(
            isMatch = isMatch,
            score = finalScore,
            message = if (isMatch) "Signature matched!" else "Signature does not match"
        )
    }

    // -----------------------------------------------------------------
    //  Composite score
    // -----------------------------------------------------------------

    private fun calculateSimilarityScore(
        input: SignatureTemplate,
        template: SignatureTemplate
    ): Float {
        val shapeScore = calculateShapeScore(input, template)
        val speedScore = calculateSpeedScore(input.totalTime, template.totalTime)
        val aspectScore = calculateAspectRatioScore(input, template)
        val strokeScore = calculateStrokeScore(input.strokes.size, template.strokes.size)

        val total = (shapeScore * SHAPE_WEIGHT) +
                (speedScore * SPEED_WEIGHT) +
                (aspectScore * ASPECT_WEIGHT) +
                (strokeScore * STROKE_WEIGHT)

        Log.d(TAG, "shape=$shapeScore  speed=$speedScore  aspect=$aspectScore  stroke=$strokeScore  → $total")
        return total
    }

    // -----------------------------------------------------------------
    //  Shape score (direction-augmented DTW)
    // -----------------------------------------------------------------

    /**
     * Compares two signatures using DTW with both position AND direction.
     * This is the core discriminator — two different letters drawn in similar
     * areas will score low because their stroke directions differ.
     */
    private fun calculateShapeScore(
        input: SignatureTemplate,
        template: SignatureTemplate
    ): Float {
        val normInput = normalizeAndResample(input)
        val normTemplate = normalizeAndResample(template)

        val pairCount = min(normInput.size, normTemplate.size)
        if (pairCount == 0) return 0f

        var totalScore = 0.0
        for (i in 0 until pairCount) {
            val pts1 = normInput[i].points
            val pts2 = normTemplate[i].points

            // Calculate stroke direction at every point
            val angles1 = calculateAngles(pts1)
            val angles2 = calculateAngles(pts2)

            // Direction-augmented DTW
            val dtw = calculateDirectionalDTW(pts1, angles1, pts2, angles2)

            // Normalise by path length
            val pathLen = max(pts1.size, pts2.size)
            val avgDist = if (pathLen > 0) dtw / pathLen else dtw

            // Exponential decay — stricter scale than before
            val score = exp(-avgDist / DTW_SCALE)
            totalScore += score
        }

        return (totalScore / pairCount).toFloat()
    }

    // -----------------------------------------------------------------
    //  Aspect ratio score
    // -----------------------------------------------------------------

    /**
     * Compares bounding box proportions. A cursive "L" (tall+narrow) vs
     * a cursive "S" (wider) will score low here even before DTW runs.
     */
    private fun calculateAspectRatioScore(
        input: SignatureTemplate,
        template: SignatureTemplate
    ): Float {
        val iW = max(input.boundingBox.width, 1f)
        val iH = max(input.boundingBox.height, 1f)
        val tW = max(template.boundingBox.width, 1f)
        val tH = max(template.boundingBox.height, 1f)

        val inputRatio = iW / iH
        val templateRatio = tW / tH

        // Ratio-of-ratios: 1.0 = identical proportions
        val minR = min(inputRatio, templateRatio)
        val maxR = max(inputRatio, templateRatio)

        return (minR / maxR).coerceIn(0f, 1f)
    }

    // -----------------------------------------------------------------
    //  Speed score
    // -----------------------------------------------------------------

    private fun calculateSpeedScore(inputTime: Long, templateTime: Long): Float {
        if (inputTime == 0L || templateTime == 0L) return 0.5f

        val ratio = inputTime.toFloat() / templateTime.toFloat()

        return when {
            ratio < 0.25f -> 0.15f            // Way too fast
            ratio < 0.4f  -> 0.45f            // Somewhat fast
            ratio in 0.4f..1.8f -> {          // Natural range
                val dev = abs(1f - ratio)
                max(0.2f, 1f - dev * 0.45f)
            }
            ratio <= 2.5f -> 0.30f            // Slow — suspicious
            ratio <= 3.5f -> 0.10f            // Very slow — tracing
            else          -> 0.05f            // Extremely slow
        }
    }

    // -----------------------------------------------------------------
    //  Stroke count score
    // -----------------------------------------------------------------

    private fun calculateStrokeScore(inputCount: Int, templateCount: Int): Float {
        if (inputCount == 0 || templateCount == 0) return 0f
        return when (abs(inputCount - templateCount)) {
            0 -> 1.0f
            1 -> 0.80f
            2 -> 0.50f
            else -> 0.20f
        }
    }

    // -----------------------------------------------------------------
    //  Normalisation, resampling & angle computation
    // -----------------------------------------------------------------

    private fun normalizeAndResample(signature: SignatureTemplate): List<SignatureStroke> {
        val bbox = signature.boundingBox
        val dim = max(bbox.width, bbox.height).coerceAtLeast(1f)
        val scale = 100f / dim   // Uniform scale — preserves aspect ratio

        return signature.strokes.map { stroke ->
            val scaled = stroke.points.map { pt ->
                SignaturePoint(
                    x = (pt.x - bbox.minX) * scale,
                    y = (pt.y - bbox.minY) * scale,
                    timestamp = pt.timestamp
                )
            }
            SignatureStroke(resamplePoints(scaled, RESAMPLE_COUNT))
        }
    }

    /**
     * Calculates the tangent angle (radians) at each point.
     * Uses the direction toward the next point; last point copies the previous angle.
     */
    private fun calculateAngles(points: List<SignaturePoint>): FloatArray {
        if (points.size < 2) return FloatArray(points.size) { 0f }

        val angles = FloatArray(points.size)
        for (i in 0 until points.size - 1) {
            val dx = points[i + 1].x - points[i].x
            val dy = points[i + 1].y - points[i].y
            angles[i] = atan2(dy, dx)
        }
        angles[points.size - 1] = angles[points.size - 2]  // copy last
        return angles
    }

    /**
     * Equi-distant point resampling ($1 Recogniser algorithm).
     */
    private fun resamplePoints(points: List<SignaturePoint>, n: Int): List<SignaturePoint> {
        if (points.size < 2) return points

        val totalLen = pathLength(points)
        if (totalLen < 0.001) return points

        val interval = totalLen / (n - 1)
        val result = mutableListOf(points[0])
        var accumulated = 0.0
        var prev = points[0]
        var idx = 1

        while (result.size < n - 1 && idx < points.size) {
            val d = euclideanDistance(prev, points[idx])

            if (accumulated + d >= interval) {
                val t = ((interval - accumulated) / d).toFloat().coerceIn(0f, 1f)
                val newPt = SignaturePoint(
                    x = prev.x + t * (points[idx].x - prev.x),
                    y = prev.y + t * (points[idx].y - prev.y),
                    timestamp = prev.timestamp +
                            ((points[idx].timestamp - prev.timestamp) * t).toLong()
                )
                result.add(newPt)
                prev = newPt
                accumulated = 0.0
            } else {
                accumulated += d
                prev = points[idx]
                idx++
            }
        }

        while (result.size < n) result.add(points.last())
        return result.take(n)
    }

    private fun pathLength(points: List<SignaturePoint>): Double {
        var len = 0.0
        for (i in 1 until points.size) {
            len += euclideanDistance(points[i - 1], points[i])
        }
        return len
    }

    // -----------------------------------------------------------------
    //  Direction-Augmented DTW
    // -----------------------------------------------------------------

    /**
     * DTW where the cost at each cell includes both positional distance
     * AND a penalty for differing stroke directions.
     *
     * Cost = euclidean_distance × (1 + angle_penalty × ANGLE_PENALTY)
     *
     * Same direction  → multiplier ≈ 1.0  (no extra cost)
     * 90° different   → multiplier ≈ 1.75
     * Opposite dir    → multiplier ≈ 2.5  (heavy cost)
     */
    private fun calculateDirectionalDTW(
        seq1: List<SignaturePoint>, angles1: FloatArray,
        seq2: List<SignaturePoint>, angles2: FloatArray
    ): Double {
        val n = seq1.size
        val m = seq2.size
        if (n == 0 || m == 0) return Double.MAX_VALUE

        val dtw = Array(n + 1) { DoubleArray(m + 1) { Double.MAX_VALUE } }
        dtw[0][0] = 0.0

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = augmentedCost(
                    seq1[i - 1], angles1[i - 1],
                    seq2[j - 1], angles2[j - 1]
                )
                dtw[i][j] = cost + minOf(
                    dtw[i - 1][j],
                    dtw[i][j - 1],
                    dtw[i - 1][j - 1]
                )
            }
        }
        return dtw[n][m]
    }

    /**
     * Combined distance: positional euclidean × directional multiplier.
     */
    private fun augmentedCost(
        p1: SignaturePoint, angle1: Float,
        p2: SignaturePoint, angle2: Float
    ): Double {
        val posDist = euclideanDistance(p1, p2)

        // Angle difference in [0, π]
        var angleDiff = abs(angle1 - angle2).toDouble()
        if (angleDiff > Math.PI) angleDiff = 2.0 * Math.PI - angleDiff

        // Normalise to [0, 1] and scale
        val anglePenalty = angleDiff / Math.PI

        return posDist * (1.0 + anglePenalty * ANGLE_PENALTY)
    }

    private fun euclideanDistance(a: SignaturePoint, b: SignaturePoint): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return sqrt(dx.pow(2) + dy.pow(2))
    }
}
