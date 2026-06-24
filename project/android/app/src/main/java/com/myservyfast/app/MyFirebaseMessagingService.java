package com.myservyfast.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyServyFast-FCM";
    private static final String PREFS_NAME = "MyServyFastPrefs";
    private static final String FCM_TOKEN_KEY = "fcm_token";

    // Notification Channels
    public static final String CHANNEL_ID_DEFAULT = "myservyfast_default";
    public static final String CHANNEL_ID_ORDERS = "myservyfast_orders";
    public static final String CHANNEL_ID_MESSAGES = "myservyfast_messages";
    public static final String CHANNEL_ID_PROMOTIONS = "myservyfast_promotions";

    private static final AtomicInteger notificationIdGenerator = new AtomicInteger(1000);

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "FCM Token received: " + token);
        super.onNewToken(token);

        // Store token locally
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(FCM_TOKEN_KEY, token);
        editor.apply();

        // Send registration to server
        sendRegistrationToServer(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        // Handle data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            handleDataMessage(remoteMessage.getData(), remoteMessage.getMessageId());
        }

        // Handle notification payload for foreground messages
        if (remoteMessage.getNotification() != null) {
            RemoteMessage.Notification notification = remoteMessage.getNotification();
            Log.d(TAG, "Message Notification Body: " + notification.getBody());

            // If app is in foreground, show custom notification
            sendNotification(
                notification.getTitle(),
                notification.getBody(),
                remoteMessage.getData(),
                notification.getImageUrl(),
                CHANNEL_ID_DEFAULT
            );
        }
    }

    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
        Log.d(TAG, "Messages were deleted on server");
    }

    @Override
    public void onMessageSent(@NonNull String msgId) {
        super.onMessageSent(msgId);
        Log.d(TAG, "Message sent successfully: " + msgId);
    }

    @Override
    public void onSendError(@NonNull String msgId, @NonNull Exception exception) {
        super.onSendError(msgId, exception);
        Log.e(TAG, "Error sending message: " + msgId, exception);
    }

    private void handleDataMessage(Map<String, String> data, String messageId) {
        String title = data.get("title");
        String body = data.get("body");
        String message = data.get("message");
        String channelType = data.get("channel");
        String action = data.get("action");
        String url = data.get("url");

        if (title == null) title = data.get("title");
        if (body == null) body = message;

        // Determine channel based on type
        String channelId = CHANNEL_ID_DEFAULT;
        if (channelType != null) {
            switch (channelType.toLowerCase()) {
                case "order":
                case "orders":
                    channelId = CHANNEL_ID_ORDERS;
                    break;
                case "message":
                case "chat":
                case "messages":
                    channelId = CHANNEL_ID_MESSAGES;
                    break;
                case "promotion":
                case "promo":
                case "promotions":
                    channelId = CHANNEL_ID_PROMOTIONS;
                    break;
            }
        }

        if (title != null && body != null) {
            int notificationId = generateNotificationId(action, data.get("id"));
            sendNotificationWithId(title, body, data, null, channelId, notificationId);
        }
    }

    private void sendNotification(String title, String messageBody, Map<String, String> data,
                                  Uri imageUri, String channelId) {
        sendNotificationWithId(title, messageBody, data, imageUri, channelId,
            generateNotificationId(data.get("action"), data.get("id")));
    }

    private void sendNotificationWithId(String title, String messageBody, Map<String, String> data,
                                         Uri imageUri, String channelId, int notificationId) {

        Intent intent = createNotificationIntent(data);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder =
            new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setLights(0xFF3B82F6, 1000, 1000)
                .setVibrate(new long[]{0, 250, 250, 250});

        // Add big text style for longer messages
        notificationBuilder.setStyle(
            new NotificationCompat.BigTextStyle()
                .bigText(messageBody)
                .setBigContentTitle(title)
        );

        // Add action buttons if specified
        addActionButtons(notificationBuilder, data);

        Notification notification = notificationBuilder.build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        try {
            notificationManager.notify(notificationId, notification);
        } catch (SecurityException e) {
            Log.e(TAG, "Notification permission not granted", e);
        }
    }

    private Intent createNotificationIntent(Map<String, String> data) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);

        // Handle URL deep link
        if (data != null) {
            String url = data.get("url");
            if (url != null) {
                intent.setData(Uri.parse(url));
            }

            // Pass all data to intent extras
            for (Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }

            // Add action type
            String action = data.get("action");
            if (action != null) {
                intent.putExtra("notification_action", action);
            }
        }

        return intent;
    }

    private void addActionButtons(NotificationCompat.Builder builder, Map<String, String> data) {
        if (data == null) return;

        String action1 = data.get("action1_label");
        String action2 = data.get("action2_label");

        if (action1 != null) {
            Intent actionIntent = createNotificationIntent(data);
            actionIntent.putExtra("action_button", "action1");
            PendingIntent actionPendingIntent = PendingIntent.getActivity(
                this,
                generateNotificationId("action1", data.get("id")),
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(android.R.drawable.ic_menu_send, action1, actionPendingIntent);
        }

        if (action2 != null) {
            Intent actionIntent = createNotificationIntent(data);
            actionIntent.putExtra("action_button", "action2");
            PendingIntent actionPendingIntent = PendingIntent.getActivity(
                this,
                generateNotificationId("action2", data.get("id")),
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(android.R.drawable.ic_menu_send, action2, actionPendingIntent);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                getSystemService(NotificationManager.class);

            // Default channel
            NotificationChannel defaultChannel = new NotificationChannel(
                CHANNEL_ID_DEFAULT,
                getString(R.string.default_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            );
            defaultChannel.setDescription(getString(R.string.default_notification_channel_description));
            defaultChannel.enableLights(true);
            defaultChannel.setLightColor(0xFF3B82F6);
            defaultChannel.enableVibration(true);
            defaultChannel.setVibrationPattern(new long[]{0, 250, 250, 250});
            defaultChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(defaultChannel);

            // Orders channel
            NotificationChannel ordersChannel = new NotificationChannel(
                CHANNEL_ID_ORDERS,
                "Order Updates",
                NotificationManager.IMPORTANCE_HIGH
            );
            ordersChannel.setDescription("Notifications about your orders");
            ordersChannel.enableLights(true);
            ordersChannel.setLightColor(0xFF22C55E);
            ordersChannel.enableVibration(true);
            ordersChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(ordersChannel);

            // Messages channel
            NotificationChannel messagesChannel = new NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            );
            messagesChannel.setDescription("Chat and message notifications");
            messagesChannel.enableLights(true);
            messagesChannel.setLightColor(0xFF3B82F6);
            messagesChannel.enableVibration(true);
            messagesChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(messagesChannel);

            // Promotions channel
            NotificationChannel promotionsChannel = new NotificationChannel(
                CHANNEL_ID_PROMOTIONS,
                "Promotions",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            promotionsChannel.setDescription("Promotional offers and deals");
            promotionsChannel.enableLights(true);
            promotionsChannel.setLightColor(0xFFF59E0B);
            promotionsChannel.enableVibration(false);
            promotionsChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(promotionsChannel);
        }
    }

    private int generateNotificationId(String action, String id) {
        if (id != null && !id.isEmpty()) {
            try {
                return Integer.parseInt(id);
            } catch (NumberFormatException e) {
                return Math.abs(id.hashCode());
            }
        }
        if (action != null) {
            return Math.abs((action + System.currentTimeMillis()).hashCode());
        }
        return notificationIdGenerator.incrementAndGet();
    }

    private void sendRegistrationToServer(String token) {
        // TODO: Send token to your backend server
        // Example: POST to https://myservyfast.com/api/register-fcm-token
        // Include device info and user ID if available
        Log.d(TAG, "Token should be sent to server: " + token);
    }

    public static String getStoredToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(FCM_TOKEN_KEY, null);
    }
}
