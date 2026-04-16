package com.sup.signlock.data

data class SignaturePoint(
    val x: Float,
    val y: Float,
    val timestamp: Long
)

data class SignatureStroke(
    val points: List<SignaturePoint>
)

data class SignatureTemplate(
    val strokes: List<SignatureStroke>,
    val totalTime: Long,
    val boundingBox: BoundingBox
)

data class BoundingBox(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}
