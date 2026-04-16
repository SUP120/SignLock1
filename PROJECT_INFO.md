# SignLock - Signature-Based App Lock System

## 🎓 Academic Project Information

**Institution:** Usha Martin University  
**Project Showcase:** 2026  
**Project Type:** Mobile Application Development  

### 👥 Development Team

- **Sudesh Kumar**
- **Vikash Kumar**
- **Anmol Harsh Tirkey**
- **Ashish Orao**
- **Akash Kumar Nayak**

---

## 📱 Project Overview

SignLock is an innovative Android application that replaces traditional PIN/pattern-based app locks with a sophisticated signature recognition system. The app uses behavioral biometrics and the Dynamic Time Warping (DTW) algorithm to authenticate users based on their unique handwritten signatures.

### 🎯 Problem Statement

Traditional app lock mechanisms have several limitations:
- **PINs** - Easy to guess or observe
- **Patterns** - Vulnerable to shoulder surfing
- **Fingerprint** - Not available on all devices, can be spoofed

**Our Solution:** A signature-based authentication system that analyzes not just the shape, but also the speed, rhythm, and behavioral patterns of how a user draws their signature.

---

## 🔬 Technical Innovation

### Core Algorithm: Dynamic Time Warping (DTW)

SignLock implements a multi-factor signature matching system:

1. **Shape Analysis (60% weight)**
   - Uses DTW algorithm to compare signature curves
   - Normalizes signatures to handle size variations
   - Calculates similarity between stroke sequences

2. **Speed Profile (30% weight)**
   - Analyzes drawing time and velocity
   - Detects slow/traced forgery attempts
   - Compares temporal patterns

3. **Stroke Pattern (10% weight)**
   - Validates stroke count and order
   - Ensures consistency in signature structure

**Match Threshold:** 65% combined score required for authentication

### Key Features

✅ **Behavioral Biometrics** - Analyzes how you sign, not just what you sign  
✅ **100% Offline** - No cloud, no backend, complete privacy  
✅ **Real-time Monitoring** - Foreground service tracks app launches  
✅ **Instant Lock Screen** - Overlay appears immediately when locked app opens  
✅ **Attempt Limiting** - 5 attempts with 30-second lockout  
✅ **Professional UI** - Modern Material Design 3 interface  

---

## 🏗️ Architecture & Technology Stack

### Frontend
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Design System:** Material Design 3
- **Navigation:** Compose Navigation

### Data Layer
- **Storage:** DataStore (encrypted preferences)
- **Serialization:** Gson
- **Data Models:** Signature templates, app info

### Services
- **Foreground Service:** App monitoring
- **Permissions:** Usage Stats, Overlay Window

### Algorithms
- **DTW Implementation:** Custom Kotlin implementation
- **Signature Normalization:** Bounding box scaling
- **Euclidean Distance:** Point-to-point comparison

---

## 📊 Project Statistics

- **Lines of Code:** ~2,500+
- **Kotlin Files:** 15+
- **UI Screens:** 5 (Splash, Setup, Home, App Selection, Lock Screen)
- **Data Models:** 4 (SignaturePoint, SignatureStroke, SignatureTemplate, AppInfo)
- **Algorithms:** DTW, Signature Matching, Normalization

---

## 🎨 User Interface Design

### Color Scheme (Professional Theme)
- **Primary:** Deep Blue (#1976D2)
- **Secondary:** Green (#388E3C)
- **Accent:** Orange (#F57C00)
- **Background:** Dark Navy (#0A0E27)
- **Surface:** Lighter Navy (#1A1F3A)

### Screens

1. **Splash Screen**
   - University branding
   - Team credits with animations
   - Smooth transitions

2. **Setup Screen**
   - 3-attempt signature capture
   - Real-time validation
   - Progress indicators

3. **Home Screen**
   - Protection status
   - Permission management
   - App management

4. **App Selection**
   - List of installed apps
   - Toggle lock status
   - Visual feedback

5. **Lock Screen**
   - Signature canvas
   - Attempt counter
   - Error handling

---

## 🔐 Security Analysis

### Strengths
- **Multi-factor authentication** (shape + speed + pattern)
- **Behavioral biometrics** make forgery difficult
- **Offline operation** prevents data leaks
- **Attempt limiting** prevents brute force

### Limitations
- Not as secure as hardware biometrics
- Can be bypassed by:
  - Uninstalling the app
  - Booting in safe mode
  - Clearing app data
- Best suited for privacy, not high-security scenarios

### Use Cases
- Protecting social media apps
- Securing messaging apps
- Hiding photo galleries
- Privacy from casual access

---

## 📱 System Requirements

- **Minimum SDK:** Android 9.0 (API 28)
- **Target SDK:** Android 14 (API 36)
- **Permissions Required:**
  - Usage Access (PACKAGE_USAGE_STATS)
  - Display Over Other Apps (SYSTEM_ALERT_WINDOW)
  - Foreground Service

---

## 🚀 Installation & Setup

### For Judges/Evaluators

1. **Install APK**
   ```
   adb install app-debug.apk
   ```

2. **First Launch**
   - Watch splash screen with team credits
   - Draw signature 3 times
   - Grant required permissions
   - Select apps to lock

3. **Testing**
   - Open a locked app
   - Draw signature to unlock
   - Try incorrect signature (max 5 attempts)

### For Development

1. **Open in Android Studio**
2. **Sync Gradle dependencies**
3. **Connect device/emulator**
4. **Run app**

---

## 🎯 Project Objectives Achieved

✅ **Innovation** - Novel use of signature biometrics for app locking  
✅ **Technical Complexity** - DTW algorithm implementation  
✅ **User Experience** - Smooth, intuitive interface  
✅ **Security** - Multi-factor authentication  
✅ **Performance** - Real-time signature matching  
✅ **Scalability** - Handles multiple locked apps  
✅ **Privacy** - 100% offline operation  

---

## 📈 Future Enhancements

- [ ] AI/ML-based signature recognition
- [ ] Pressure sensitivity support (stylus)
- [ ] Adaptive learning (improves over time)
- [ ] Intruder selfie capture
- [ ] Break-in alerts
- [ ] Multiple signature profiles
- [ ] Backup/restore functionality
- [ ] Fake attempt detection using ML

---

## 📚 Learning Outcomes

### Technical Skills Developed
- Android app development with Kotlin
- Jetpack Compose UI framework
- Algorithm implementation (DTW)
- Foreground services
- Permission handling
- Data persistence
- Material Design principles

### Soft Skills
- Team collaboration
- Project planning
- Problem-solving
- Time management
- Documentation

---

## 🏆 Project Highlights

1. **Unique Approach** - First signature-based app lock in our institution
2. **Algorithm Implementation** - Custom DTW from scratch
3. **Professional UI** - College project with production-quality design
4. **Complete Solution** - End-to-end implementation
5. **Real-world Application** - Solves actual privacy concerns

---

## 📖 References

- Dynamic Time Warping Algorithm: [Wikipedia](https://en.wikipedia.org/wiki/Dynamic_time_warping)
- Android Foreground Services: [Android Developers](https://developer.android.com/guide/components/foreground-services)
- Jetpack Compose: [Android Developers](https://developer.android.com/jetpack/compose)
- Material Design 3: [Material Design](https://m3.material.io/)

---

## 📞 Contact

For questions or demonstrations, please contact any team member through Usha Martin University.

---

## 📄 License

This project is developed for academic purposes as part of the Usha Martin University Project Showcase 2026.

---

**Thank you for evaluating our project!** 🙏

We hope SignLock demonstrates our technical capabilities, innovative thinking, and commitment to creating practical solutions for real-world problems.
