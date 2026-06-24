import { useEffect, useState, useRef, useCallback } from 'react';
import { Capacitor } from '@capacitor/core';
import { App } from '@capacitor/app';
import { Browser } from '@capacitor/browser';
import { SplashScreen } from '@capacitor/splash-screen';
import { StatusBar, Style } from '@capacitor/status-bar';
import { Network } from '@capacitor/network';
import { Haptics, ImpactStyle } from '@capacitor/haptics';
import { PushNotifications } from '@capacitor/push-notifications';
import { Preferences } from '@capacitor/preferences';
import { WifiOff, RefreshCw } from 'lucide-react';

const WEBSITE_URL = 'https://myservyfast.com';
const FCM_TOKEN_KEY = 'fcm_token';

export default function App() {
  const [isOnline, setIsOnline] = useState(true);
  const [isLoading, setIsLoading] = useState(true);
  const [showOfflineBanner, setShowOfflineBanner] = useState(false);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [pullDistance, setPullDistance] = useState(0);
  const touchStartY = useRef(0);
  const isPulling = useRef(false);

  // Firebase Cloud Messaging Setup
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;

    const setupPushNotifications = async () => {
      try {
        // Request notification permission (Android 13+ requires this)
        const permissionResult = await PushNotifications.requestPermissions();

        if (permissionResult.receive === 'granted') {
          // Register for push notifications
          await PushNotifications.register();

          // Listen for registration success
          await PushNotifications.addListener('registration', async (token) => {
            console.log('FCM Token received:', token.value);

            // Store token locally
            await Preferences.set({
              key: FCM_TOKEN_KEY,
              value: token.value,
            });

            // Send token to your backend server if needed
            // await sendTokenToServer(token.value);
          });

          // Listen for registration errors
          await PushNotifications.addListener('registrationError', (error) => {
            console.error('FCM Registration error:', error);
          });

          // Listen for push notification received (foreground)
          await PushNotifications.addListener(
            'pushNotificationReceived',
            async (notification) => {
              console.log('Push notification received:', notification);

              // Trigger haptic feedback
              try {
                await Haptics.impact({ style: ImpactStyle.Medium });
              } catch {
                // Haptics not available
              }

              // Notification data may contain URL to open
              const notificationData = notification.data;
              if (notificationData?.url && iframeRef.current) {
                // Handle URL from notification data
                const url = notificationData.url as string;
                if (url.startsWith('myservyfast://') || url.includes('myservyfast.com')) {
                  const path = url.replace('myservyfast://', '').replace('https://myservyfast.com', '');
                  iframeRef.current.src = `${WEBSITE_URL}${path}`;
                }
              }
            }
          );

          // Listen for push notification action performed (user tapped)
          await PushNotifications.addListener(
            'pushNotificationActionPerformed',
            async (action) => {
              console.log('Push notification action:', action);

              const notificationData = action.notification.data;

              // Handle deep link from notification
              if (notificationData?.url) {
                const url = notificationData.url as string;

                if (url.startsWith('myservyfast://')) {
                  // Custom scheme deep link
                  const path = url.replace('myservyfast://', '/');
                  if (iframeRef.current) {
                    iframeRef.current.src = `${WEBSITE_URL}${path}`;
                  }
                } else if (url.includes('myservyfast.com')) {
                  // HTTPS deep link
                  if (iframeRef.current) {
                    iframeRef.current.src = url;
                  }
                } else {
                  // External URL - open in browser
                  try {
                    await Browser.open({ url });
                  } catch {
                    window.open(url, '_system');
                  }
                }
              }

              // Handle specific action types
              if (notificationData?.action) {
                const actionType = notificationData.action as string;
                switch (actionType) {
                  case 'OPEN_PRODUCT':
                    if (notificationData.productId && iframeRef.current) {
                      iframeRef.current.src = `${WEBSITE_URL}/product/${notificationData.productId}`;
                    }
                    break;
                  case 'OPEN_ORDER':
                    if (notificationData.orderId && iframeRef.current) {
                      iframeRef.current.src = `${WEBSITE_URL}/my-account/orders/${notificationData.orderId}`;
                    }
                    break;
                  case 'OPEN_CHAT':
                    if (iframeRef.current) {
                      iframeRef.current.src = `${WEBSITE_URL}/my-account/messages`;
                    }
                    break;
                }
              }
            }
          );

          // Check for existing stored token
          const { value: storedToken } = await Preferences.get({ key: FCM_TOKEN_KEY });
          if (storedToken) {
            console.log('Using stored FCM token:', storedToken);
          }
        } else {
          console.log('Notification permission denied');
        }
      } catch (error) {
        console.error('Push notification setup error:', error);
      }
    };

    setupPushNotifications();

    // Cleanup listeners on unmount
    return () => {
      PushNotifications.removeAllListeners();
    };
  }, []);

  useEffect(() => {
    const initializeApp = async () => {
      try {
        await SplashScreen.hide();

        if (Capacitor.isNativePlatform()) {
          try {
            await StatusBar.setStyle({ style: Style.Dark });
            await StatusBar.setOverlaysWebView({ overlay: true });
          } catch {
            // Status bar may fail on some devices
          }
        }
      } catch (error) {
        console.log('Initialization error:', error);
      }
    };

    initializeApp();
  }, []);

  // Network status monitoring
  useEffect(() => {
    const checkNetworkStatus = async () => {
      try {
        const status = await Network.getStatus();
        setIsOnline(status.connected);
        setShowOfflineBanner(!status.connected);
      } catch {
        // Network status may fail
      }
    };

    checkNetworkStatus();

    Network.addListener('networkStatusChange', (status) => {
      setIsOnline(status.connected);
      setShowOfflineBanner(!status.connected);
      if (status.connected && iframeRef.current) {
        iframeRef.current.src = WEBSITE_URL;
      }
    });
  }, []);

  // Deep link handling
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;

    App.addListener('appUrlOpen', (data) => {
      try {
        const url = new URL(data.url);
        const path = url.pathname + url.search;
        if (iframeRef.current) {
          iframeRef.current.src = `${WEBSITE_URL}${path}`;
        }
      } catch {
        const path = data.url.replace('myservyfast://', '/');
        if (iframeRef.current) {
          iframeRef.current.src = `${WEBSITE_URL}${path}`;
        }
      }
    });
  }, []);

  // Back button handling
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;

    App.addListener('backButton', () => {
      if (iframeRef.current?.contentWindow) {
        try {
          iframeRef.current.contentWindow.postMessage({ type: 'CAPACITOR_BACK' }, '*');
        } catch {
          // Cross-origin restrictions
        }
      }
    });

    App.addListener('appStateChange', ({ isActive }) => {
      if (isActive && iframeRef.current) {
        // App became active - could refresh session
      }
    });
  }, []);

  const handleIframeLoad = useCallback(() => {
    setIsLoading(false);
    setRefreshing(false);
    setPullDistance(0);
  }, []);

  const handleRefresh = useCallback(async () => {
    if (!isOnline) return;

    setRefreshing(true);
    try {
      await Haptics.impact({ style: ImpactStyle.Light });
    } catch {
      // Haptics may not be available
    }

    if (iframeRef.current) {
      iframeRef.current.src = WEBSITE_URL;
    }
    setPullDistance(0);
  }, [isOnline]);

  // Pull to refresh implementation
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const handleTouchStart = (e: TouchEvent) => {
      if (container.scrollTop === 0) {
        touchStartY.current = e.touches[0].clientY;
        isPulling.current = true;
      }
    };

    const handleTouchMove = (e: TouchEvent) => {
      if (!isPulling.current) return;

      const currentY = e.touches[0].clientY;
      const diff = currentY - touchStartY.current;

      if (diff > 0 && container.scrollTop === 0) {
        const distance = Math.min(diff * 0.5, 100);
        setPullDistance(distance);
      }
    };

    const handleTouchEnd = () => {
      if (pullDistance > 60) {
        handleRefresh();
      }
      setPullDistance(0);
      isPulling.current = false;
    };

    container.addEventListener('touchstart', handleTouchStart, { passive: true });
    container.addEventListener('touchmove', handleTouchMove, { passive: true });
    container.addEventListener('touchend', handleTouchEnd, { passive: true });

    return () => {
      container.removeEventListener('touchstart', handleTouchStart);
      container.removeEventListener('touchmove', handleTouchMove);
      container.removeEventListener('touchend', handleTouchEnd);
    };
  }, [pullDistance, handleRefresh]);

  // Handle external links via message from WebView
  useEffect(() => {
    const handleMessage = async (event: MessageEvent) => {
      const { type, url } = event.data || {};

      if (type === 'OPEN_EXTERNAL' && url) {
        try {
          await Browser.open({ url });
        } catch {
          window.open(url, '_system');
        }
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, []);

  return (
    <div className="app-container" ref={containerRef}>
      {/* Pull to refresh indicator */}
      <div
        className="pull-indicator"
        style={{
          height: pullDistance,
          opacity: Math.min(pullDistance / 60, 1),
          transform: `translateY(${Math.min(pullDistance, 80) - 80}px)`,
        }}
      >
        <RefreshCw
          className={`refresh-icon ${refreshing ? 'spin' : ''}`}
          size={24}
          style={{
            transform: `rotate(${pullDistance * 2.5}deg)`,
          }}
        />
      </div>

      {/* Offline banner */}
      {showOfflineBanner && (
        <div className="offline-banner">
          <WifiOff className="offline-icon" size={20} />
          <span>No internet connection</span>
          <button
            onClick={() => {
              Network.getStatus().then((status) => {
                if (status.connected && iframeRef.current) {
                  iframeRef.current.src = WEBSITE_URL;
                }
              });
              setShowOfflineBanner(false);
            }}
            className="retry-button"
          >
            <RefreshCw size={16} />
            Retry
          </button>
        </div>
      )}

      {/* Loading overlay */}
      {isLoading && (
        <div className="loading-overlay">
          <div className="loading-spinner"></div>
          <p className="loading-text">Loading myservyfast...</p>
        </div>
      )}

      {/* Main iframe */}
      <iframe
        ref={iframeRef}
        src={WEBSITE_URL}
        title="MyServyFast"
        className="website-frame"
        onLoad={handleIframeLoad}
        allow="camera; microphone; geolocation; autoplay; fullscreen; payment"
      />
    </div>
  );
}
