package com.sup.signlock

import android.app.KeyguardManager
import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sup.signlock.data.PreferencesManager
import com.sup.signlock.signature.SignatureMatcher
import com.sup.signlock.ui.SignatureCanvas
import com.sup.signlock.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LockScreenActivity : ComponentActivity() {
    
    private lateinit var lockedPackage: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        lockedPackage = intent.getStringExtra("locked_package") ?: ""
        
        setContent {
            SignLockTheme {
                LockScreen(
                    packageName = lockedPackage,
                    onUnlocked = { finish() },
                    onFailed = {
                        moveTaskToBack(true)
                        finish()
                    }
                )
            }
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
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
    var attempts by remember { mutableStateOf(0) }
    val maxAttempts = 5
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLocked by remember { mutableStateOf(false) }
    var lockTimeRemaining by remember { mutableStateOf(0) }
    
    LaunchedEffect(packageName) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appName = pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            appName = packageName
        }
    }
    
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
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Error,
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "App Locked",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = appName,
                fontSize = 18.sp,
                color = TextSecondary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (isLocked) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Error.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Too Many Attempts",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Try again in",
                            fontSize = 16.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${lockTimeRemaining}s",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Warning
                        )
                    }
                }
            } else {
                Text(
                    text = "Draw your signature to unlock",
                    fontSize = 16.sp,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Attempts: $attempts / $maxAttempts",
                    fontSize = 14.sp,
                    color = if (attempts >= maxAttempts - 2) Error else TextSecondary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
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
                        strokeColor = Primary,
                        strokeWidth = 6f
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (showError) {
                    Text(
                        text = errorMessage,
                        color = Error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            TextButton(
                onClick = onFailed,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = TextSecondary
                )
            ) {
                Text("Cancel")
            }
        }
    }
}
