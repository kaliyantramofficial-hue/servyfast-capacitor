import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.myservyfast.app',
  appName: 'MyServyFast',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
    url: 'https://myservyfast.com',
    cleartext: false,
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 2000,
      backgroundColor: '#ffffff',
      androidScaleType: 'CENTER_CROP',
      showSpinner: true,
      spinnerColor: '#3b82f6',
      splashFullScreen: true,
      splashImmersive: true,
      useDialog: false,
      launchAutoHide: false,
    },
    StatusBar: {
      style: 'Dark',
      backgroundColor: '#ffffff',
      overlaysWebView: true,
      hide: false,
    },
    Keyboard: {
      resize: 'body',
      resizeOnFullScreen: true,
    },
    CapacitorHttp: {
      enabled: true,
    },
    Deeplinks: {
      scheme: 'myservyfast',
      customScheme: 'myservyfast://',
      androidIntentFilter: {
        scheme: 'https',
        host: 'myservyfast.com',
      },
    },
    Browser: {
      toolbarColor: '#ffffff',
      closeButtonColor: '#000000',
      presentationStyle: 'popover',
    },
    Camera: {
      preferFrontCamera: false,
      promptLabelPicture: 'Take Photo',
      promptLabelVideo: 'Record Video',
      promptLabelCancel: 'Cancel',
    },
    Geolocation: {
      enableHighAccuracy: true,
      timeout: 10000,
      maximumAge: 0,
    },
    PushNotifications: {
      presentationOptions: ['alert', 'sound', 'badge'],
      requestPermissionOnLaunch: true,
      sound: true,
      vibration: true,
      badge: true,
      clearBadgeOnLaunch: true,
    },
  },
  android: {
    allowMixedContent: false,
    captureInput: true,
    webContentsDebuggingEnabled: false,
    backgroundColor: '#ffffff',
    buildOptions: {
      keystorePath: null,
      keystorePassword: null,
      keystoreAlias: null,
      keystoreAliasPassword: null,
      signingType: 'apksigner',
    },
    theme: {
      darkMode: false,
    },
  },
  ios: {
    scheme: 'MyServyFast',
    contentInset: 'automatic',
    scrollEnabled: true,
    backgroundColor: '#ffffff',
  },
};

export default config;
