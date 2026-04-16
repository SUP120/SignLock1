package com.sup.signlock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sup.signlock.data.SignaturePoint
import com.sup.signlock.data.SignatureStroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SignatureCanvas(
    modifier: Modifier = Modifier,
    onSignatureComplete: (List<SignatureStroke>, Long) -> Unit,
    strokeColor: Color = Color.White,
    strokeWidth: Float = 5f
) {
    var strokes by remember { mutableStateOf<List<SignatureStroke>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<SignaturePoint>>(emptyList()) }
    var startTime by remember { mutableStateOf(0L) }
    var isDrawing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (!isDrawing) {
                            startTime = System.currentTimeMillis()
                            isDrawing = true
                        }
                        currentStroke = listOf(
                            SignaturePoint(
                                x = offset.x,
                                y = offset.y,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentStroke = currentStroke + SignaturePoint(
                            x = change.position.x,
                            y = change.position.y,
                            timestamp = System.currentTimeMillis()
                        )
                    },
                    onDragEnd = {
                        if (currentStroke.isNotEmpty()) {
                            strokes = strokes + SignatureStroke(currentStroke)
                            currentStroke = emptyList()
                            
                            // Wait a bit to see if user continues drawing
                            scope.launch {
                                delay(800)
                                if (currentStroke.isEmpty() && strokes.isNotEmpty()) {
                                    val totalTime = System
.currentTimeMillis() - startTime
                                    onSignatureComplete(strokes, totalTime)
                                    strokes = emptyList()
                                    isDrawing = false
                                }
                            }
                        }
                    }
                )
            }
    ) {
        // Draw completed strokes
        strokes.forEach { stroke ->
            if (stroke.points.size > 1) {
                val path = Path()
                path.moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) {
                    path.lineTo(stroke.points[i].x, stroke.points[i].y)
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        
        // Draw current stroke
        if (currentStroke.size > 1) {
            val path = Path()
            path.moveTo(currentStroke[0].x, currentStroke[0].y)
            for (i in 1 until currentStroke.size) {
                path.lineTo(currentStroke[i].x, currentStroke[i].y)
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
