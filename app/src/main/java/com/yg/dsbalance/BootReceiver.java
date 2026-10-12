package com.yg.dsbalance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机自启：如果用户之前开着余额通知，则恢复前台服务。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Prefs prefs = new Prefs(context);
        if (!prefs.isServiceEnabled() || prefs.getApiKey().isEmpty()) {
            return;
        }
        try {
            Intent svc = new Intent(context, BalanceService.class).setAction(BalanceService.ACTION_START);
            context.startForegroundService(svc);
        } catch (Exception ignored) {
            // 个别系统对开机启动前台服务有限制，失败时等待用户手动打开。
        }
    }
}