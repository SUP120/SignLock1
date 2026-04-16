package com.sup.signlock.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sup.signlock.ui.theme.*

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
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(80.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "SignLock",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Signature-Based App Lock",
            fontSize = 16.sp,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Status Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Surface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Protection Status",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Switch(
                        checked = isServiceRunning && hasUsagePermission && hasOverlayPermission,
                        onCheckedChange = { enabled ->
                            if (enabled && (!hasUsagePermission || !hasOverlayPermission)) {
                                if (!hasUsagePermission) {
                                    requestUsageStatsPermission(context)
                                }
                                if (!hasOverlayPermission) {
                                    requestOverlayPermission(context)
                                }
                            } else {
                                onToggleService(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Success
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "$lockedAppsCount apps protected",
                    fontSize = 16.sp,
                    color = TextSecondary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Permissions status
        if (!hasUsagePermission || !hasOverlayPermission) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Warning.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "⚠️ Permissions Required",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Warning
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (!hasUsagePermission) {
                        Text(
                            text = "• Usage Access Permission",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                    if (!hasOverlayPermission) {
                        Text(
                            text = "• Display Over Other Apps",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            if (!hasUsagePermission) {
                                requestUsageStatsPermission(context)
                            } else if (!hasOverlayPermission) {
                                requestOverlayPermission(context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Warning
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Permissions")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // Manage Apps Button
        Button(
            onClick = onManageApps,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Manage Locked Apps",
                fontSize = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "Draw your signature to unlock apps",
            fontSize = 14.sp,
            color = TextSecondary
        )
    }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        hasUsagePermission = checkUsageStatsPermission(context)
        hasOverlayPermission = checkOverlayPermission(context)
    }
}

private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
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
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    intent.data = Uri.parse("package:${context.packageName}")
    context.startActivity(intent)
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
