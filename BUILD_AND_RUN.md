# 🚀 Build and Run Instructions

## Quick Start Guide for SignLock

---

## ✅ Pre-Build Checklist

All errors have been fixed:
- ✅ SignatureCanvas coroutine issue resolved
- ✅ Professional color theme implemented (Blue/Green/Orange)
- ✅ Splash screen with team credits added
- ✅ Logo integrated
- ✅ All UI components updated with new theme
- ✅ No purple/pink colors remaining

---

## 📱 Building the App

### Method 1: Using Android Studio (Recommended)

1. **Open Project**
   - Launch Android Studio
   - File → Open → Select SignLock folder
   - Wait for Gradle sync to complete

2. **Connect Device**
   - Enable USB Debugging on your Android device
   - Connect via USB
   - Allow USB debugging when prompted

3. **Run App**
   - Click the green "Run" button (▶️)
   - Or press `Shift + F10`
   - Select your device
   - Wait for installation

### Method 2: Using Command Line

```bash
# Build Debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Or build and install in one command
./gradlew installDebug
```

The APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎬 First Run Experience

### 1. Splash Screen (8 seconds)
- University logo appears with animation
- "USHA MARTIN UNIVERSITY" text slides in
- "SignLock" project title appears
- Team member names animate in one by one:
  - Sudesh Kumar
  - Vikash Kumar
  - Anmol Harsh Tirkey
  - Ashish Orao
  - Akash Kumar Nayak
- Smooth fade to main app

### 2. Signature Setup
- Draw your signature 3 times
- Each attempt is validated
- Progress indicators show completion
- Tips provided for best results

### 3. Permission Requests
- **Usage Access Permission**
  - Tap "Grant Permissions"
  - Find "SignLock" in the list
  - Enable "Permit usage access"
  - Press back to return

- **Display Over Other Apps**
  - Tap "Grant Permissions" again
  - Toggle "Allow display over other apps"
  - Press back to return

### 4. App Selection
- Browse installed apps
- Tap apps to lock/unlock them
- Selected apps show lock icon
- Tap "Save & Continue"

### 5. Home Screen
- View protection status
- See locked app count
- Toggle protection on/off
- Manage locked apps anytime

---

## 🧪 Testing the App

### Test Scenario 1: Lock an App
1. Go to Home → "Manage Locked Apps"
2. Select an app (e.g., Instagram, WhatsApp)
3. Tap "Save & Continue"
4. Open the locked app
5. Lock screen should appear immediately

### Test Scenario 2: Unlock with Signature
1. Open a locked app
2. Draw your signature on the canvas
3. If correct → App opens
4. If incorrect → Error message, try again

### Test Scenario 3: Failed Attempts
1. Open a locked app
2. Draw incorrect signatures 5 times
3. App locks for 30 seconds
4. Countdown timer appears
5. After 30s, can try again

### Test Scenario 4: Toggle Protection
1. Open SignLock
2. Toggle "Protection Status" off
3. Locked apps now open normally
4. Toggle back on to re-enable

---

## 🎨 UI Features to Showcase

### Professional Theme
- **Deep Blue** primary color (not purple!)
- **Green** for success states
- **Orange** for warnings
- **Dark Navy** background
- Clean, modern design

### Animations
- Splash screen team credits
- Progress indicators
- Smooth transitions
- Real-time signature drawing
- Error/success feedback

### User Experience
- Clear instructions
- Visual feedback
- Error messages
- Permission guidance
- Intuitive navigation

---

## 📊 Demo Script for Judges

### Introduction (1 minute)
"Good morning/afternoon. We present SignLock, a signature-based app lock system that uses behavioral biometrics and the Dynamic Time Warping algorithm to authenticate users."

### Problem Statement (30 seconds)
"Traditional app locks use PINs or patterns which are easy to observe or guess. SignLock analyzes not just what you draw, but how you draw it - including speed, rhythm, and stroke patterns."

### Technical Overview (1 minute)
"Our app uses the DTW algorithm to compare signatures, analyzing three factors:
- Shape similarity (60% weight)
- Speed profile (30% weight)
- Stroke pattern (10% weight)

A combined score of 65% or higher is required for authentication."

### Live Demo (2-3 minutes)
1. **Show splash screen** - "Notice our university branding and team credits"
2. **Setup signature** - "I'll draw my signature 3 times"
3. **Select apps** - "Let me lock Instagram"
4. **Test lock** - "Opening Instagram... lock screen appears"
5. **Unlock** - "Drawing my signature... unlocked!"
6. **Show failed attempt** - "Wrong signature... error message"

### Key Features (1 minute)
- "100% offline - no cloud, complete privacy"
- "Real-time monitoring with foreground service"
- "Attempt limiting prevents brute force"
- "Professional UI with Material Design 3"

### Conclusion (30 seconds)
"SignLock demonstrates our ability to implement complex algorithms, create professional UIs, and solve real-world privacy concerns. Thank you!"

---

## 🐛 Troubleshooting

### Issue: App won't install
**Solution:** 
- Enable "Install from Unknown Sources"
- Check device has Android 9.0+
- Uninstall old version first

### Issue: Lock screen doesn't appear
**Solution:**
- Check permissions are granted
- Ensure protection is enabled
- Restart the app
- Check if service is running

### Issue: Signature not matching
**Solution:**
- Draw more naturally
- Check drawing speed
- Re-setup signature
- Ensure enough detail in signature

### Issue: Gradle sync failed
**Solution:**
- Check internet connection
- File → Invalidate Caches → Restart
- Delete .gradle folder and re-sync

---

## 📸 Screenshots to Capture

For documentation/presentation:
1. Splash screen with team names
2. Signature setup screen
3. App selection screen
4. Home screen with status
5. Lock screen overlay
6. Successful unlock
7. Failed attempt error
8. Permission screens

---

## 🎯 Key Points for Judges

1. **Innovation** - Novel use of signature biometrics
2. **Algorithm** - Custom DTW implementation
3. **Security** - Multi-factor authentication
4. **UX** - Professional, intuitive design
5. **Performance** - Real-time matching
6. **Privacy** - 100% offline
7. **Completeness** - Fully functional app

---

## 📝 Final Checklist

Before presentation:
- [ ] App builds without errors
- [ ] Splash screen shows correctly
- [ ] Signature setup works
- [ ] Permissions can be granted
- [ ] Apps can be locked/unlocked
- [ ] Lock screen appears instantly
- [ ] Signature matching works
- [ ] Failed attempts handled correctly
- [ ] UI looks professional
- [ ] No crashes or bugs

---

## 🎓 Academic Integrity

This project was developed by:
- Sudesh Kumar
- Vikash Kumar
- Anmol Harsh Tirkey
- Ashish Orao
- Akash Kumar Nayak

For Usha Martin University Project Showcase 2026.

---

**Good luck with your presentation! 🍀**

The app is ready to impress the judges with its technical sophistication, professional design, and practical utility.
