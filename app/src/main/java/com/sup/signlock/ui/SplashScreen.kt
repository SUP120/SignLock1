package com.sup.signlock.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sup.signlock.R
import com.sup.signlock.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    var currentPhase by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        delay(1500)
        currentPhase = 1
        delay(1500)
        currentPhase = 2
        delay(1500)
        currentPhase = 3
        delay(2500)
        currentPhase = 4
        delay(1000)
        onSplashComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E27),
                        Color(0xFF1A1F3A),
                        Color(0xFF0A0E27)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            AnimatedVisibility(
                visible = currentPhase >= 0,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn()
            ) {
                LogoSection()
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            AnimatedVisibility(
                visible = currentPhase >= 1,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(800, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(800))
            ) {
                UniversitySection()
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            AnimatedVisibility(
                visible = currentPhase >= 2,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(800, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(800))
            ) {
                ProjectTitleSection()
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            AnimatedVisibility(
                visible = currentPhase >= 3,
                enter = fadeIn(animationSpec = tween(1000))
            ) {
                TeamSection()
            }
        }
        
        AnimatedVisibility(
            visible = currentPhase >= 4,
            enter = fadeIn(animationSpec = tween(1000))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0E27))
            )
        }
    }
}

@Composable
fun LogoSection() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )
    
    Surface(
        modifier = Modifier
            .size(120.dp)
            .scale(scale),
        shape = RoundedCornerShape(24.dp),
        color = Primary.copy(alpha = 0.2f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "SignLock Logo",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun UniversitySection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "USHA MARTIN UNIVERSITY",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AccentOrange,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Project Showcase 2026",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ProjectTitleSection() {
    Surface(
        modifier = Modifier.fillMaxWidth(0.9f),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "SignLock",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Signature-Based App Lock System",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TeamSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Developed By",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        val teamMembers = listOf(
            "Sudesh Kumar",
            "Vikash Kumar",
            "Anmol Harsh Tirkey",
            "Ashish Orao",
            "Akash Kumar Nayak"
        )
        
        teamMembers.forEachIndexed { index, name ->
            var visible by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                delay(index * 150L)
                visible = true
            }
            
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(
                    initialOffsetX = { if (index % 2 == 0) -it else it },
                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(500))
            ) {
                TeamMemberCard(name, index)
            }
            
            if (index < teamMembers.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun TeamMemberCard(name: String, index: Int) {
    val colors = listOf(Primary, AccentGreen, AccentOrange, Primary, AccentGreen)
    
    Surface(
        modifier = Modifier.fillMaxWidth(0.85f),
        shape = RoundedCornerShape(12.dp),
        color = Surface.copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = colors[index % colors.size]
            ) {}
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}
