package com.sup.signlock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sup.signlock.data.SignatureStroke
import com.sup.signlock.signature.SignatureUtils
import com.sup.signlock.ui.theme.*

@Composable
fun SetupScreen(
    onSetupComplete: (List<com.sup.signlock.data.SignatureTemplate>) -> Unit
) {
    var currentAttempt by remember { mutableStateOf(1) }
    var signatures by remember { mutableStateOf<List<com.sup.signlock.data.SignatureTemplate>>(emptyList()) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val totalAttempts = 3
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "Setup Your Signature",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Draw your signature $totalAttempts times",
            fontSize = 16.sp,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Progress indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(totalAttempts) { index ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (index < signatures.size) Success
                            else if (index == currentAttempt - 1) Primary
                            else Surface,
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < signatures.size) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Attempt $currentAttempt of $totalAttempts",
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Signature canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(2.dp, Primary, RoundedCornerShape(16.dp))
                .background(Surface, RoundedCornerShape(16.dp))
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
                strokeColor = Primary,
                strokeWidth = 6f
            )
            
            if (signatures.isEmpty() && currentAttempt == 1) {
                Text(
                    text = "Draw your signature here",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AnimatedVisibility(visible = showError) {
            Text(
                text = errorMessage,
                color = Error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Tips:\n• Draw naturally\n• Use the same signature each time\n• Include enough detail",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
