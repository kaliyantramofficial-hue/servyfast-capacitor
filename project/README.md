# MyServyFast Android App

Production-ready Capacitor Android application for myservyfast.com.

## Quick Start

```bash
npm install
npm run build
npx cap sync android
npx cap open android
```

## Firebase Setup

Replace `android/app/src/main/assets/google-services.json` with your Firebase config.

## Build APK

In Android Studio: Build > Generate Signed Bundle/APK

Or:
```bash
cd android
./gradlew assembleRelease
```

## Features

- Fullscreen edge-to-edge experience
- Session persistence
- Deep linking (https + custom scheme)
- Firebase Cloud Messaging
- Camera & file upload support
- GPS location
- Share intent support
- Pull to refresh
- Network detection with offline banner
- Android 10-15 support
