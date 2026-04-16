package com.sup.signlock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sup.signlock.data.PreferencesManager
import com.sup.signlock.service.AppMonitorService
import com.sup.signlock.signature.SignatureMatcher
import com.sup.signlock.ui.SignatureCanvas
import com.sup.signlock.ui.theme.SignLockTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LockScreenActivity : ComponentActivity() {
    
    private lateinit var lockedPackage: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make this activity show over lock screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        lockedPackage = intent.getStringExtra("locked_package") ?: ""
        
        setContent {
            SignLockTheme {
                LockScreen(
                    packageName = lockedPackage,
                    onUnlocked = {
                        // Mark app as temporarily unlocked so the service
                        // doesn't immediately re-lock it
                        AppMonitorService.temporarilyUnlockedApps.add(lockedPackage)
                        finish()
                    },
                    onFailed = {
                        // Navigate to home screen — don't let user stay in locked app
                        navigateToHome()
                    }
                )
            }
        }
    }
    
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Send user to home screen — don't allow back into the locked app
        navigateToHome()
    }

    private fun navigateToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
fun LockScreen(
    packageName: String,
    onUnlocked: () -> Unit,
    onFailed: () -> Unit
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val signatureMatcher = remember { SignatureMatcher() }
    val scope = rememberCoroutineScope()

    var appName by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    val maxAttempts = 5
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLocked by remember { mutableStateOf(false) }
    var lockTimeRemaining by remember { mutableIntStateOf(0) }

    LaunchedEffect(packageName) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appName = pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            appName = packageName
        }
    }

    // Lock timer
    LaunchedEffect(isLocked) {
        if (isLocked) {
            for (i in 30 downTo 0) {
                lockTimeRemaining = i
                kotlinx.coroutines.delay(1000)
            }
            isLocked = false
            attempts = 0
        }
    }

    Box(
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // Lock icon with glow ring
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF6B6B).copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "App Locked",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = appName,
                fontSize = 16.sp,
                color = Color(0xFF8899AA)
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (isLocked) {
                // ─── Lockout card ───
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Color(0xFF4A2020),
                            RoundedCornerShape(16.dp)
                        ),
                    color = Color(0xFF1A0F0F),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Too Many Attempts",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF6B6B)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Try again in",
                            fontSize = 14.sp,
                            color = Color(0xFF8899AA)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${lockTimeRemaining}s",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE8A838)
                        )
                    }
                }
            } else {
                // ─── Unlock UI ───
                Text(
                    text = "Draw your signature to unlock",
                    fontSize = 15.sp,
                    color = Color(0xFF8899AA)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Attempt ${attempts + 1} of $maxAttempts",
                    fontSize = 13.sp,
                    color = if (attempts >= maxAttempts - 2) Color(0xFFFF6B6B) else Color(0xFF556677)
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
                            scope.launch {
                                val templates = preferencesManager.getSignatureTemplates().first()
                                val inputTemplate = com.sup.signlock.signature.SignatureUtils.createTemplate(strokes, totalTime)

                                val result = signatureMatcher.matchSignature(inputTemplate, templates)

                                if (result.isMatch) {
                                    onUnlocked()
                                } else {
                                    attempts++
                                    if (attempts >= maxAttempts) {
                                        isLocked = true
                                    } else {
                                        showError = true
                                        errorMessage = "Signature doesn't match. ${maxAttempts - attempts} attempts left."
                                        kotlinx.coroutines.delay(2000)
                                        showError = false
                                    }
                                }
                            }
                        },
                        strokeColor = Color(0xFF4ECDC4),
                        strokeWidth = 6f
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (showError) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            TextButton(
                onClick = onFailed,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF556677)
                )
            ) {
                Text("Cancel", fontSize = 14.sp)
            }
        }
    }
}
