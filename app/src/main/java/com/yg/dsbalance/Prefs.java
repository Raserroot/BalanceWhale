package com.yg.dsbalance;

import android.content.Context;
import android.content.SharedPreferences;

/** 本地配置与最后一次结果的缓存。 */
public final class Prefs {

    private static final String FILE = "ds_balance";
    private static final String K_API_KEY = "api_key";
    private static final String K_AUTO = "auto_refresh";
    private static final String K_ENABLED = "service_enabled";
    private static final String K_TOTAL = "last_total";
    private static final String K_BONUS = "last_bonus";
    private static final String K_RECHARGE = "last_recharge";
    private static final String K_CURRENCY = "last_currency";
    private static final String K_UPDATED = "last_updated";
    private static final String K_ERROR = "last_error";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String getApiKey() { return sp.getString(K_API_KEY, ""); }

    public void setApiKey(String key) { sp.edit().putString(K_API_KEY, key).apply(); }

    public boolean isAutoRefresh() { return sp.getBoolean(K_AUTO, true); }

    public void setAutoRefresh(boolean v) { sp.edit().putBoolean(K_AUTO, v).apply(); }

    public boolean isServiceEnabled() { return sp.getBoolean(K_ENABLED, false); }

    public void setServiceEnabled(boolean v) { sp.edit().putBoolean(K_ENABLED, v).apply(); }

    public String getLastTotal() { return sp.getString(K_TOTAL, null); }

    public String getLastBonus() { return sp.getString(K_BONUS, null); }

    public String getLastRecharge() { return sp.getString(K_RECHARGE, null); }

    public String getLastCurrency() { return sp.getString(K_CURRENCY, "CNY"); }

    public long getLastUpdated() { return sp.getLong(K_UPDATED, 0L); }

    public String getLastError() { return sp.getString(K_ERROR, null); }

    public void saveResult(String total, String bonus, String recharge, String currency) {
        sp.edit()
                .putString(K_TOTAL, total)
                .putString(K_BONUS, bonus)
                .putString(K_RECHARGE, recharge)
                .putString(K_CURRENCY, currency)
                .putLong(K_UPDATED, System.currentTimeMillis())
                .remove(K_ERROR)
                .apply();
    }

    public void saveError(String message) {
        sp.edit().putString(K_ERROR, message).apply();
    }

    public void clear() {
        sp.edit().clear().apply();
    }
}