package com.sup.signlock.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    lockedAppsCount: Int,
    isServiceRunning: Boolean,
    onManageApps: () -> Unit,
    onToggleService: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var hasUsagePermission by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission(context)) }

    // Easter egg state
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var showEasterEgg by remember { mutableStateOf(false) }

    // Re-check permissions periodically (user may return from settings)
    LaunchedEffect(Unit) {
        while (true) {
            hasUsagePermission = checkUsageStatsPermission(context)
            hasOverlayPermission = checkOverlayPermission(context)
            kotlinx.coroutines.delay(2000)
        }
    }

    // Easter egg screen
    if (showEasterEgg) {
        EasterEggScreen(onDismiss = { showEasterEgg = false })
        return
    }

    // Main content
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(52.dp))

        // ─── Lock icon (secret tap target) ───
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1B3A5C),
                            Color(0xFF0F1F33)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4ECDC4).copy(alpha = 0.6f),
                            Color(0xFF2196F3).copy(alpha = 0.3f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime > 3000) tapCount = 0
                    lastTapTime = now
                    tapCount++
                    if (tapCount >= 7) {
                        showEasterEgg = true
                        tapCount = 0
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFF4ECDC4),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ─── Title ───
        Text(
            text = "SignLock: A Demo",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Signature-Based App Lock",
            fontSize = 15.sp,
            color = Color(0xFF8899AA),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        // ─── Protection Status Card ───
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color(0xFF1E3A50),
                    shape = RoundedCornerShape(16.dp)
                ),
            color = Color(0xFF0F1923),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = if (isServiceRunning && hasUsagePermission && hasOverlayPermission)
                                Color(0xFF4ECDC4) else Color(0xFF556677),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Protection",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Switch(
                        checked = isServiceRunning && hasUsagePermission && hasOverlayPermission,
                        onCheckedChange = { enabled ->
                            if (enabled && (!hasUsagePermission || !hasOverlayPermission)) {
                                if (!hasUsagePermission) requestUsageStatsPermission(context)
                                if (!hasOverlayPermission) requestOverlayPermission(context)
                            } else {
                                onToggleService(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4ECDC4),
                            uncheckedThumbColor = Color(0xFF556677),
                            uncheckedTrackColor = Color(0xFF1A2535)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isServiceRunning && hasUsagePermission && hasOverlayPermission)
                                    Color(0xFF4ECDC4) else Color(0xFF3A3A50)
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (lockedAppsCount > 0) "$lockedAppsCount app${if (lockedAppsCount > 1) "s" else ""} protected"
                        else "No apps locked yet",
                        fontSize = 14.sp,
                        color = Color(0xFF8899AA)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── Permissions Warning ───
        if (!hasUsagePermission || !hasOverlayPermission) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFF4A3520),
                        shape = RoundedCornerShape(14.dp)
                    ),
                color = Color(0xFF1A1510),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "⚠  Permissions Required",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE8A838)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (!hasUsagePermission) {
                        Text(
                            text = "•  Usage Access Permission",
                            fontSize = 13.sp,
                            color = Color(0xFFCCA050)
                        )
                    }
                    if (!hasOverlayPermission) {
                        Text(
                            text = "•  Display Over Other Apps",
                            fontSize = 13.sp,
                            color = Color(0xFFCCA050)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (!hasUsagePermission) requestUsageStatsPermission(context)
                            else if (!hasOverlayPermission) requestOverlayPermission(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE8A838)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Permissions", color = Color(0xFF1A1510))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ─── Manage Locked Apps ───
        Button(
            onClick = onManageApps,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1B3A5C)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                tint = Color(0xFF4ECDC4),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Manage Locked Apps",
                fontSize = 15.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // ─── Divider ───
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(1.dp)
                .background(Color(0xFF2A3A4A))
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ─── Project Team ───
        Text(
            text = "Project Team",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF556677),
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        val teamMembers = listOf(
            "Sudesh Kumar",
            "Vikash Kumar",
            "Anmol Harsh Tirkey",
            "Ashish Orao",
            "Akash Kumar Nayak"
        )

        teamMembers.forEach { name ->
            Text(
                text = name,
                fontSize = 14.sp,
                color = Color(0xFF8899AA),
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Draw your signature to unlock apps",
            fontSize = 13.sp,
            color = Color(0xFF3A4A5A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────
//  Easter Egg Screen
// ─────────────────────────────────────────

@Composable
fun EasterEggScreen(onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")

    val heartScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A0A1A),
                        Color(0xFF0A0A0F)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(800)) + scaleIn(tween(800))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pulsing heart
                Text(
                    text = "❤️",
                    fontSize = 72.sp,
                    modifier = Modifier.graphicsLayer(
                        scaleX = heartScale,
                        scaleY = heartScale
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Made By",
                    fontSize = 18.sp,
                    color = Color(0xFF8899AA),
                    fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "SUP",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 8.sp
                )

                Text(
                    text = "<3",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B6B).copy(alpha = glowAlpha),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "tap anywhere to return",
                    fontSize = 12.sp,
                    color = Color(0xFF3A3A50),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────
//  Permission helpers
// ─────────────────────────────────────────

private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun requestUsageStatsPermission(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    } catch (e: Exception) {
        // Some devices don't support package-specific usage access settings
        val fallback = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        context.startActivity(fallback)
    }
}

private fun requestOverlayPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}
