package com.yg.dsbalance;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    public static final String ACTION_STATE_CHANGED = "com.yg.dsbalance.STATE_CHANGED";

    private static final int PAGE_PERMISSIONS = 0;
    private static final int PAGE_KEY = 1;
    private static final int PAGE_MAIN = 2;
    private static final int REQ_NOTIFICATIONS = 100;

    private Prefs prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean updatingSwitch;
    private boolean receiverRegistered;

    private View pagePermissions;
    private View pageKey;
    private View pageMain;
    private Button btnGrantNotif;
    private Button btnGrantBattery;
    private Button btnToKey;
    private Button btnSaveKey;
    private Button btnRefreshNow;
    private Button btnStopStart;
    private TextView tvNotifStatus;
    private TextView tvBatteryStatus;
    private TextView tvKeyError;
    private TextView tvBalance;
    private TextView tvBreakdown;
    private TextView tvUpdated;
    private TextView tvServiceState;
    private TextView tvResetKey;
    private EditText etKey;
    private ProgressBar pbVerify;
    private Switch swAuto;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (pageMain.getVisibility() == View.VISIBLE) {
                refreshMainUi();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        bindViews();
        setupListeners();
        if (prefs.getApiKey().isEmpty()) {
            showPage(PAGE_PERMISSIONS);
        } else {
            showPage(PAGE_MAIN);
        }
    }

    private void bindViews() {
        pagePermissions = findViewById(R.id.pagePermissions);
        pageKey = findViewById(R.id.pageKey);
        pageMain = findViewById(R.id.pageMain);
        btnGrantNotif = findViewById(R.id.btnGrantNotif);
        btnGrantBattery = findViewById(R.id.btnGrantBattery);
        btnToKey = findViewById(R.id.btnToKey);
        btnSaveKey = findViewById(R.id.btnSaveKey);
        btnRefreshNow = findViewById(R.id.btnRefreshNow);
        btnStopStart = findViewById(R.id.btnStopStart);
        tvNotifStatus = findViewById(R.id.tvNotifStatus);
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        tvKeyError = findViewById(R.id.tvKeyError);
        tvBalance = findViewById(R.id.tvBalance);
        tvBreakdown = findViewById(R.id.tvBreakdown);
        tvUpdated = findViewById(R.id.tvUpdated);
        tvServiceState = findViewById(R.id.tvServiceState);
        tvResetKey = findViewById(R.id.tvResetKey);
        etKey = findViewById(R.id.etKey);
        pbVerify = findViewById(R.id.pbVerify);
        swAuto = findViewById(R.id.swAuto);
    }

    private void setupListeners() {
        btnGrantNotif.setOnClickListener(v -> onGrantNotifications());
        btnGrantBattery.setOnClickListener(v -> onGrantBattery());
        btnToKey.setOnClickListener(v -> showPage(PAGE_KEY));
        btnSaveKey.setOnClickListener(v -> onSaveKey());
        btnRefreshNow.setOnClickListener(v -> {
            startForegroundService(serviceIntent(BalanceService.ACTION_REFRESH));
            handler.postDelayed(this::refreshMainUi, 400);
        });
        btnStopStart.setOnClickListener(v -> onToggleService());
        tvResetKey.setOnClickListener(v -> onResetKey());
        swAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingSwitch) {
                return;
            }
            prefs.setAutoRefresh(isChecked);
            if (prefs.isServiceEnabled()) {
                startForegroundService(serviceIntent(BalanceService.ACTION_SETTINGS_CHANGED));
            }
        });
    }

    private Intent serviceIntent(String action) {
        return new Intent(this, BalanceService.class).setAction(action);
    }

    private void showPage(int page) {
        pagePermissions.setVisibility(page == PAGE_PERMISSIONS ? View.VISIBLE : View.GONE);
        pageKey.setVisibility(page == PAGE_KEY ? View.VISIBLE : View.GONE);
        pageMain.setVisibility(page == PAGE_MAIN ? View.VISIBLE : View.GONE);
        if (page == PAGE_PERMISSIONS) {
            refreshPermissionUi();
        } else if (page == PAGE_MAIN) {
            refreshMainUi();
        }
    }

    private void refreshPermissionUi() {
        tvNotifStatus.setText(notificationsGranted()
                ? getString(R.string.status_granted)
                : getString(R.string.status_not_granted));
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        tvBatteryStatus.setText(ignoring
                ? getString(R.string.status_granted)
                : getString(R.string.status_not_granted));
    }

    private void refreshMainUi() {
        String total = prefs.getLastTotal();
        String currency = prefs.getLastCurrency();
        tvBalance.setText(total != null ? symbolOf(currency) + total : "¥ --");

        String bonus = prefs.getLastBonus();
        String recharge = prefs.getLastRecharge();
        if (bonus != null || recharge != null) {
            StringBuilder sb = new StringBuilder();
            if (recharge != null) {
                sb.append("充值 ").append(symbolOf(currency)).append(recharge);
            }
            if (bonus != null) {
                if (sb.length() > 0) {
                    sb.append("  ·  ");
                }
                sb.append("赠金 ").append(symbolOf(currency)).append(bonus);
            }
            tvBreakdown.setText(sb.toString());
        } else {
            tvBreakdown.setText("");
        }

        long updated = prefs.getLastUpdated();
        String err = prefs.getLastError();
        if (err != null) {
            tvUpdated.setText("刷新失败：" + err);
            tvUpdated.setTextColor(getColor(R.color.error));
        } else if (updated > 0) {
            tvUpdated.setText("更新于 " + fmtTime(updated));
            tvUpdated.setTextColor(getColor(R.color.text_hint));
        } else {
            tvUpdated.setText(R.string.updated_never);
            tvUpdated.setTextColor(getColor(R.color.text_hint));
        }

        boolean enabled = prefs.isServiceEnabled();
        tvServiceState.setText(enabled ? "状态：余额通知已开启" : "状态：余额通知已停止");
        btnStopStart.setText(enabled ? R.string.btn_stop : R.string.btn_start);

        if (swAuto.isChecked() != prefs.isAutoRefresh()) {
            updatingSwitch = true;
            swAuto.setChecked(prefs.isAutoRefresh());
            updatingSwitch = false;
        }
    }

    private boolean notificationsGranted() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    private void onGrantNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void onGrantBattery() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private void onSaveKey() {
        final String key = etKey.getText().toString().trim();
        if (key.isEmpty()) {
            showKeyError("请填写 API 密钥");
            return;
        }
        tvKeyError.setVisibility(View.GONE);
        setVerifying(true);
        executor.execute(() -> {
            final DeepSeekApi.Result r = DeepSeekApi.fetchBalance(key);
            handler.post(() -> {
                setVerifying(false);
                if (r.ok) {
                    prefs.setApiKey(key);
                    prefs.saveResult(r.total, r.bonus, r.recharge, r.currency);
                    prefs.setServiceEnabled(true);
                    startForegroundService(serviceIntent(BalanceService.ACTION_START));
                    showPage(PAGE_MAIN);
                } else {
                    showKeyError(r.error);
                }
            });
        });
    }

    private void setVerifying(boolean v) {
        btnSaveKey.setEnabled(!v);
        etKey.setEnabled(!v);
        pbVerify.setVisibility(v ? View.VISIBLE : View.GONE);
    }

    private void showKeyError(String msg) {
        tvKeyError.setText(msg);
        tvKeyError.setVisibility(View.VISIBLE);
    }

    private void onToggleService() {
        if (prefs.isServiceEnabled()) {
            startService(serviceIntent(BalanceService.ACTION_STOP));
        } else {
            startForegroundService(serviceIntent(BalanceService.ACTION_START));
        }
        handler.postDelayed(this::refreshMainUi, 400);
    }

    private void onResetKey() {
        new AlertDialog.Builder(this)
                .setTitle("重置")
                .setMessage("将停止余额通知并清除本机保存的 API 密钥。")
                .setPositiveButton("重置", (d, w) -> {
                    startService(serviceIntent(BalanceService.ACTION_STOP));
                    prefs.clear();
                    etKey.setText("");
                    tvKeyError.setVisibility(View.GONE);
                    showPage(PAGE_PERMISSIONS);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String symbolOf(String currency) {
        if ("USD".equalsIgnoreCase(currency)) {
            return "$";
        }
        if ("CNY".equalsIgnoreCase(currency)) {
            return "¥";
        }
        return currency == null ? "¥" : currency + " ";
    }

    private static String fmtTime(long t) {
        return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(t));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            refreshPermissionUi();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter f = new IntentFilter(ACTION_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(stateReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(stateReceiver, f);
            }
            receiverRegistered = true;
        }
        if (pageMain.getVisibility() == View.VISIBLE) {
            refreshMainUi();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver);
            receiverRegistered = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pagePermissions.getVisibility() == View.VISIBLE) {
            refreshPermissionUi();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}