📄 PRD: Signature-Based App Lock
1. 🧠 Product Overview

Product Name: SignLock (working title)

Goal:
Allow users to unlock apps using their hand-drawn signature, with intelligent tolerance for small variations while rejecting forged attempts.

Core Idea:
Instead of PIN/password → user draws their signature → system verifies:

Shape similarity
Stroke order
Drawing speed & pressure (if available)
Behavioral patterns

No login, no cloud, no auth — 100% offline local functionality

2. 🎯 Problem Statement

Traditional locks:

PINs → guessable
Patterns → shoulder-surfing risk
Fingerprint → not available on all devices / spoofable

Opportunity:
A signature is:

Personal
Hard to replicate perfectly
Has behavioral biometrics (speed, rhythm)
3. 👤 Target Users
Students (privacy apps)
People hiding chats/photos/apps
Users who want cool + secure unlock method
4. 🔑 Core Features
4.1 Signature Setup (Onboarding)
User draws signature 3–5 times
System creates a signature profile

Stores:

Stroke coordinates (x,y)
Time per stroke
Velocity
Stroke count
4.2 App Lock Selection
Show list of installed apps
User selects apps to lock
4.3 Unlock Flow
When locked app opens:
→ Fullscreen signature pad appears

User must:

Draw signature

System checks:

Shape similarity
Speed similarity
Stroke pattern
4.4 Smart Matching Engine
Matching Criteria:
Shape Matching
Compare curves using:
Dynamic Time Warping (DTW) OR
Hausdorff distance
Speed Profile
Compare time taken per stroke
Stroke Order
Sequence consistency
Tolerance Window
Allow ~10–20% variation
4.5 Security Logic
Accept if:
Shape similarity ≥ threshold
Speed deviation ≤ threshold
Reject if:
Too slow/fast (fake attempt)
Shape mismatch
4.6 Retry System
Max attempts: 5
After that:
Lock for 30 sec OR
Show fallback (optional: PIN)
5. 🚫 What’s NOT Included
❌ No login/signup
❌ No cloud sync
❌ No biometric APIs
❌ No backend

Everything = local storage

6. 🧱 Technical Architecture
6.1 Frontend (Mobile App)
Framework: Expo (React Native)
Canvas: react-native-skia OR react-native-svg
6.2 Signature Capture

Capture:

[
  { x: 120, y: 340, t: 0 },
  { x: 125, y: 345, t: 10 },
  ...
]
6.3 Storage

Use:

AsyncStorage OR MMKV

Store:

{
  signatureTemplates: [array of signatures],
  lockedApps: [package names]
}
6.4 Matching Algorithm (Core)
Step 1: Normalize
Scale to same size
Align center
Step 2: Compare

Use:

👉 Dynamic Time Warping (DTW)
Measures similarity between sequences

Step 3: Score
final_score =
  (shape_score * 0.6) +
  (speed_score * 0.3) +
  (stroke_score * 0.1)
6.5 Unlock Decision
if final_score > threshold:
    unlock
else:
    reject
7. ⚙️ Permissions Needed (Android)
PACKAGE_USAGE_STATS → detect app open
SYSTEM_ALERT_WINDOW → draw lock screen overlay
8. 🖥️ UX Flow
First Time:
Open app
Draw signature 3–5 times
Select apps to lock
Done
Unlock:
Open locked app
Signature screen appears
Draw signature
Match → unlock OR retry
9. 🎨 UI Requirements
Clean dark UI
Signature canvas (smooth)
Minimal lag
Real-time stroke rendering
10. ⚠️ Risks & Challenges
10.1 Forgery Risk
Someone copies visually
→ Mitigation: speed + stroke pattern
10.2 Performance
DTW can be heavy
→ Optimize or approximate
10.3 Android Restrictions
Background detection tricky in newer Android versions
11. 🚀 MVP Scope (Build First)
Signature capture
Signature matching (basic)
Lock 1–2 apps
Overlay screen
12. 🔥 Future Enhancements
AI-based signature recognition
Pressure sensitivity (stylus)
Adaptive learning (improves over time)
Fake attempt detection (ML)
13. 💡 Differentiation
Behavioral biometrics (speed + stroke)
Not just shape → makes it hard to hack
Unique UX → viral potential
14. 📦 Suggested Tech Stack
Expo + React Native
react-native-gesture-handler
Skia for drawing
MMKV for fast storage
15. 🧪 Testing Strategy
Same user multiple attempts
Different users try to copy
Test speed variations