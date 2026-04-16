package com.sup.signlock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.sup.signlock.data.PreferencesManager
import com.sup.signlock.service.AppMonitorService
import com.sup.signlock.ui.AppSelectionScreen
import com.sup.signlock.ui.HomeScreen
import com.sup.signlock.ui.SetupScreen
import com.sup.signlock.ui.SplashScreen
import com.sup.signlock.ui.theme.Background
import com.sup.signlock.ui.theme.SignLockTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var preferencesManager: PreferencesManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        preferencesManager = PreferencesManager(this)
        
        setContent {
            SignLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    MainScreen()
                }
            }
        }
    }
    
    @Composable
    fun MainScreen() {
        val scope = rememberCoroutineScope()
        
        var showSplash by remember { mutableStateOf(true) }
        var isSetupComplete by remember { mutableStateOf(false) }
        var showAppSelection by remember { mutableStateOf(false) }
        var lockedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
        var isServiceRunning by remember { mutableStateOf(false) }
        
        LaunchedEffect(Unit) {
            isSetupComplete = preferencesManager.isSetupComplete().first()
            lockedApps = preferencesManager.getLockedApps().first()
            isServiceRunning = preferencesManager.isServiceEnabled().first()
            
            // Start service if enabled
            if (isServiceRunning && isSetupComplete) {
                startMonitorService()
            }
        }
        
        when {
            showSplash -> {
                SplashScreen(
                    onSplashComplete = {
                        showSplash = false
                    }
                )
            }
            !isSetupComplete -> {
                SetupScreen(
                    onSetupComplete = { templates ->
                        scope.launch {
                            preferencesManager.saveSignatureTemplates(templates)
                            preferencesManager.setSetupComplete(true)
                            isSetupComplete = true
                            showAppSelection = true
                        }
                    }
                )
            }
            showAppSelection -> {
                AppSelectionScreen(
                    initialLockedApps = lockedApps,
                    onAppsSelected = { apps ->
                        scope.launch {
                            preferencesManager.saveLockedApps(apps)
                            preferencesManager.setServiceEnabled(true)
                            lockedApps = apps
                            isServiceRunning = true
                            showAppSelection = false
                            startMonitorService()
                        }
                    }
                )
            }
            else -> {
                HomeScreen(
                    lockedAppsCount = lockedApps.size,
                    isServiceRunning = isServiceRunning,
                    onManageApps = {
                        showAppSelection = true
                    },
                    onToggleService = { enabled ->
                        scope.launch {
                            preferencesManager.setServiceEnabled(enabled)
                            isServiceRunning = enabled
                            
                            if (enabled) {
                                startMonitorService()
                            } else {
                                stopMonitorService()
                            }
                        }
                    }
                )
            }
        }
    }
    
    private fun startMonitorService() {
        val intent = Intent(this, AppMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun stopMonitorService() {
        val intent = Intent(this, AppMonitorService::class.java)
        stopService(intent)
    }
}
