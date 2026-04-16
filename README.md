# 🔒 SignLock — Signature-Based App Lock

**SignLock** is an Android app that lets users lock and unlock apps using their hand-drawn signature. Instead of PINs or passwords, users draw their unique signature to authenticate — combining shape recognition, stroke direction analysis, and speed profiling for a secure yet natural unlock experience.

> 🎓 College Demo Project

---

## ✨ Features

- **Signature Authentication** — Draw your signature to unlock protected apps
- **Direction-Augmented DTW** — Matches stroke shape *and* direction for high accuracy
- **Anti-Forgery Protection** — Slow/deliberate drawing is penalised (likely someone copying)
- **Aspect Ratio Analysis** — Different letter proportions are automatically detected
- **App Locking** — Select any installed app to protect with your signature
- **Foreground Monitoring** — Background service detects when locked apps open
- **Retry System** — 5 attempts, then 30-second lockout
- **100% Offline** — No login, no cloud, no backend. Everything stored locally

## 🏗️ Architecture

```
com.sup.signlock/
├── data/
│   ├── AppInfo.kt              # App data model
│   ├── SignaturePoint.kt       # Signature data models
│   └── PreferencesManager.kt   # DataStore persistence
├── service/
│   └── AppMonitorService.kt    # Foreground service — monitors locked apps
├── signature/
│   ├── SignatureMatcher.kt     # Direction-augmented DTW matching engine
│   └── SignatureUtils.kt       # Normalisation & validation utilities
├── ui/
│   ├── HomeScreen.kt           # Main dashboard
│   ├── SetupScreen.kt          # Signature registration (3 attempts)
│   ├── AppSelectionScreen.kt   # Choose apps to lock
│   ├── SignatureCanvas.kt      # Drawing canvas (Compose Canvas)
│   └── theme/                  # Material 3 dark theme
├── MainActivity.kt             # Navigation host
└── LockScreenActivity.kt       # Lock overlay shown over protected apps
```

## 🔐 How the Matching Works

1. **Capture** — Records x, y coordinates + timestamp at every touch point
2. **Normalise** — Scales signature to a 100×100 coordinate space (preserving aspect ratio)
3. **Resample** — Redistributes points equidistantly (64 points per stroke)
4. **Direction Analysis** — Calculates tangent angle at every point
5. **DTW Matching** — Compares position AND direction using Dynamic Time Warping
6. **Scoring** — Weighted combination:
   - Shape + Direction: 55%
   - Speed profile: 20%
   - Aspect ratio: 15%
   - Stroke count: 10%
7. **Decision** — Score ≥ 0.50 → unlock, else reject

## 📱 Permissions

| Permission | Purpose |
|---|---|
| `PACKAGE_USAGE_STATS` | Detect which app is in the foreground |
| `SYSTEM_ALERT_WINDOW` | Show lock screen overlay |
| `FOREGROUND_SERVICE` | Keep the monitoring service alive |

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Storage**: DataStore Preferences + Gson
- **Algorithm**: Dynamic Time Warping (DTW) with directional augmentation
- **Min SDK**: 28 (Android 9)

## 👥 Project Team

- Sudesh Kumar
- Vikash Kumar
- Anmol Harsh Tirkey
- Ashish Orao
- Akash Kumar Nayak

## 📄 License

This project is for educational/demonstration purposes.
