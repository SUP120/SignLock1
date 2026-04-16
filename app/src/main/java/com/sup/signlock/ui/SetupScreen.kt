package com.sup.signlock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sup.signlock.data.SignatureStroke
import com.sup.signlock.signature.SignatureUtils

@Composable
fun SetupScreen(
    onSetupComplete: (List<com.sup.signlock.data.SignatureTemplate>) -> Unit
) {
    var currentAttempt by remember { mutableIntStateOf(1) }
    var signatures by remember { mutableStateOf<List<com.sup.signlock.data.SignatureTemplate>>(emptyList()) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val totalAttempts = 3

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A0F),
                        Color(0xFF111128),
                        Color(0xFF0D1B2A)
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(44.dp))

        Text(
            text = "Setup Your Signature",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Draw your signature $totalAttempts times",
            fontSize = 15.sp,
            color = Color(0xFF8899AA)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Progress dots
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(totalAttempts) { index ->
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                index < signatures.size -> Color(0xFF4ECDC4)
                                index == currentAttempt - 1 -> Color(0xFF1B3A5C)
                                else -> Color(0xFF1A1A2E)
                            }
                        )
                        .then(
                            if (index == currentAttempt - 1 && index >= signatures.size)
                                Modifier.border(1.5.dp, Color(0xFF4ECDC4).copy(alpha = 0.5f), CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < signatures.size) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            color = if (index == currentAttempt - 1) Color(0xFF4ECDC4) else Color(0xFF556677),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Attempt $currentAttempt of $totalAttempts",
            fontSize = 16.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Signature canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4ECDC4).copy(alpha = 0.4f),
                            Color(0xFF2196F3).copy(alpha = 0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .background(Color(0xFF0F1923), RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            SignatureCanvas(
                onSignatureComplete = { strokes, totalTime ->
                    if (SignatureUtils.isValidSignature(strokes)) {
                        val template = SignatureUtils.createTemplate(strokes, totalTime)
                        signatures = signatures + template

                        if (signatures.size >= totalAttempts) {
                            onSetupComplete(signatures)
                        } else {
                            currentAttempt++
                        }
                        showError = false
                    } else {
                        showError = true
                        errorMessage = "Signature too small or simple. Please try again."
                    }
                },
                strokeColor = Color(0xFF4ECDC4),
                strokeWidth = 6f
            )

            if (signatures.isEmpty() && currentAttempt == 1) {
                Text(
                    text = "Draw your signature here",
                    color = Color(0xFF3A4A5A),
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        AnimatedVisibility(visible = showError) {
            Text(
                text = errorMessage,
                color = Color(0xFFFF6B6B),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            color = Color(0xFF0F1923),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Tips:\n• Sign naturally — don't overthink it\n• Use the same signature each time\n• Speed matters — sign in a flow",
                fontSize = 13.sp,
                color = Color(0xFF556677),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
