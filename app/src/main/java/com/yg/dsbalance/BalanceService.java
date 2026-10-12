package com.yg.dsbalance;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 前台服务：定时查询余额并更新常驻通知。 */
public class BalanceService extends Service {

    public static final String ACTION_START = "com.yg.dsbalance.action.START";
    public static final String ACTION_REFRESH = "com.yg.dsbalance.action.REFRESH";
    public static final String ACTION_STOP = "com.yg.dsbalance.action.STOP";
    public static final String ACTION_SETTINGS_CHANGED = "com.yg.dsbalance.action.SETTINGS_CHANGED";

    private static final String CHANNEL_ID = "balance_monitor";
    private static final int NOTIFICATION_ID = 1024;
    private static final int REQ_OPEN = 1;
    private static final int REQ_REFRESH = 2;
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;
    private static final long RETRY_INTERVAL_MS = 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor;
    private Prefs prefs;
    private volatile boolean refreshing;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshNow();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        executor = Executors.newSingleThreadExecutor();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            prefs.setServiceEnabled(false);
            handler.removeCallbacksAndMessages(null);
            stopForegroundCompat();
            stopSelf();
            broadcastState();
            return START_NOT_STICKY;
        }

        if (prefs.getApiKey().isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // 任何启动路径都要先进入前台，避免 startForegroundService 的 5 秒超时。
        startForeground(NOTIFICATION_ID, buildNotification());
        prefs.setServiceEnabled(true);

        if (ACTION_SETTINGS_CHANGED.equals(action)) {
            handler.removeCallbacks(refreshTask);
            if (prefs.isAutoRefresh()) {
                handler.post(refreshTask);
            }
            return START_STICKY;
        }

        // START / REFRESH / 系统重启（null）
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (executor != null) {
            executor.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void refreshNow() {
        if (refreshing) {
            return;
        }
        final String key = prefs.getApiKey();
        if (key.isEmpty()) {
            return;
        }
        refreshing = true;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final DeepSeekApi.Result result = DeepSeekApi.fetchBalance(key);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshing = false;
                        onResult(result);
                    }
                });
            }
        });
    }

    private void onResult(DeepSeekApi.Result r) {
        if (r.ok) {
            prefs.saveResult(r.total, r.bonus, r.recharge, r.currency);
        } else {
            prefs.saveError(r.error);
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification());
        }
        scheduleNext(r.ok);
        broadcastState();
    }

    private void scheduleNext(boolean success) {
        handler.removeCallbacks(refreshTask);
        if (!prefs.isAutoRefresh()) {
            return;
        }
        long delay = success ? REFRESH_INTERVAL_MS : RETRY_INTERVAL_MS;
        handler.postDelayed(refreshTask, delay);
    }

    private void broadcastState() {
        Intent i = new Intent(MainActivity.ACTION_STATE_CHANGED).setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "余额监控", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("常驻显示 DeepSeek API 余额");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        String total = prefs.getLastTotal();
        String currency = prefs.getLastCurrency();
        String err = prefs.getLastError();
        long updated = prefs.getLastUpdated();

        String title;
        String text;
        String big;

        if (total != null) {
            String money = symbolOf(currency) + total;
            if (err != null) {
                title = "余额 " + money + "（刷新失败）";
                text = err;
                big = "上次余额：" + money + "\n刷新失败：" + err;
            } else {
                title = "余额 " + money;
                text = "更新于 " + timeShort(updated);
                big = "总余额：" + money + breakdownSuffix()
                        + "\n更新于 " + timeLong(updated)
                        + (prefs.isAutoRefresh() ? "\n每 5 分钟自动刷新" : "\n自动刷新已关闭（手动刷新）");
            }
        } else {
            title = "DeepSeek 余额";
            if (err != null) {
                text = "获取失败：" + err;
                big = text;
            } else {
                text = "正在获取余额…";
                big = text;
            }
        }

        PendingIntent contentPi = PendingIntent.getActivity(this, REQ_OPEN,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent refreshPi = PendingIntent.getForegroundService(this, REQ_REFRESH,
                new Intent(this, BalanceService.class).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(big))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(contentPi)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_notify),
                        "立即刷新",
                        refreshPi).build());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    private String breakdownSuffix() {
        String bonus = prefs.getLastBonus();
        String recharge = prefs.getLastRecharge();
        if (bonus == null && recharge == null) {
            return "";
        }
        String cur = symbolOf(prefs.getLastCurrency());
        StringBuilder sb = new StringBuilder("（");
        boolean first = true;
        if (recharge != null) {
            sb.append("充值 ").append(cur).append(recharge);
            first = false;
        }
        if (bonus != null) {
            if (!first) {
                sb.append(" + ");
            }
            sb.append("赠金 ").append(cur).append(bonus);
        }
        return sb.append("）").toString();
    }

    private static String symbolOf(String currency) {
        if (currency == null) {
            return "¥";
        }
        if ("USD".equalsIgnoreCase(currency)) {
            return "$";
        }
        if ("CNY".equalsIgnoreCase(currency)) {
            return "¥";
        }
        return currency + " ";
    }

    private static String timeShort(long t) {
        if (t <= 0) {
            return "--:--";
        }
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }

    private static String timeLong(long t) {
        if (t <= 0) {
            return "从未";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(t));
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 33) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }
}