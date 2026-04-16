package com.sup.signlock.signature

import com.sup.signlock.data.BoundingBox
import com.sup.signlock.data.SignatureStroke
import com.sup.signlock.data.SignatureTemplate

object SignatureUtils {
    
    fun createTemplate(strokes: List<SignatureStroke>, totalTime: Long): SignatureTemplate {
        val boundingBox = calculateBoundingBox(strokes)
        return SignatureTemplate(
            strokes = strokes,
            totalTime = totalTime,
            boundingBox = boundingBox
        )
    }
    
    private fun calculateBoundingBox(strokes: List<SignatureStroke>): BoundingBox {
        if (strokes.isEmpty() || strokes.all { it.points.isEmpty() }) {
            return BoundingBox(0f, 0f, 0f, 0f)
        }
        
        val allPoints = strokes.flatMap { it.points }
        
        return BoundingBox(
            minX = allPoints.minOf { it.x },
            maxX = allPoints.maxOf { it.x },
            minY = allPoints.minOf { it.y },
            maxY = allPoints.maxOf { it.y }
        )
    }
    
    fun isValidSignature(strokes: List<SignatureStroke>): Boolean {
        if (strokes.isEmpty()) return false
        
        val totalPoints = strokes.sumOf { it.points.size }
        if (totalPoints < 10) return false // Too few points
        
        val bbox = calculateBoundingBox(strokes)
        if (bbox.width < 50 || bbox.height < 20) return false // Too small
        
        return true
    }
}
