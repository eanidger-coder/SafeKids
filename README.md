# SafeKids 🛡️

**SafeKids** is an intelligent Android application designed to protect children from inappropriate and violent content on YouTube Kids. 

Unlike traditional filters that only look for explicit keywords, SafeKids uses a **3-Layer Detection Engine** that tracks content degradation and escalation over time, preventing children from falling into "ELSAGATE" or "Brainrot" content rabbit holes.

## 🚀 Key Features

- **Real-Time Monitoring**: Uses Android Accessibility Services to monitor YouTube Kids content without requiring root.
- **3-Layer Detection**:
    - **Layer 1 (Keyword Classifier)**: Weighted scoring for 200+ keywords in Hebrew, English, and Arabic.
    - **Layer 2 (Escalation Tracker)**: Gradient analysis of violence scores across a viewing session.
    - **Layer 3 (Channel Blacklist)**: Parent-managed blocklist for repeat offending channels.
- **Parental Controls**:
    - PIN-protected dashboard.
    - 3 sensitivity levels (Strict, Balanced, Relaxed).
    - Custom keyword and channel blacklists.
    - Detailed activity logs of blocked events.
- **Child-Friendly UX**: A futuristic, non-scary blocking overlay that encourages safe viewing habits.
- **RTL Support**: Fully localized in Hebrew (RTL).

## 🛠️ Tech Stack

- **Language**: Kotlin
- **Persistence**: Room Database (SQLite)
- **UI Framework**: AndroidX Material 3 (Futuristic Design)
- **Background Support**: Coroutines & Flow
- **Service**: AccessibilityService

## 📱 Installation

1. Download the `app-debug.apk` from the latest build.
2. Install on an Android device.
3. Follow the onboarding wizard to grant permissions:
    - **Accessibility Service** (Required for monitoring)
    - **System Overlay** (Required for blocking)
4. Set your Parent PIN (Default is `1234`).

## 🎨 Design

Designed with a premium, futuristic aesthetic using **StitchMCP**. 
- Primary Color: `#FF6B35` (Warm Orange)
- Secondary Color: `#2EC4B6` (Teal)
- Background: `#FFF8F0` (Cream)

---
*Created with ❤️ by Antigravity AI for SafeKids.*
