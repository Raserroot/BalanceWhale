package com.yg.dsbalance;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** DeepSeek 官方余额接口封装：GET https://api.deepseek.com/user/balance */
public final class DeepSeekApi {

    public static final String BALANCE_URL = "https://api.deepseek.com/user/balance";
    private static final int TIMEOUT_MS = 10000;

    private DeepSeekApi() {}

    public static final class Result {
        public final boolean ok;
        public final String total;
        public final String bonus;
        public final String recharge;
        public final String currency;
        public final String error;

        private Result(boolean ok, String total, String bonus, String recharge, String currency, String error) {
            this.ok = ok;
            this.total = total;
            this.bonus = bonus;
            this.recharge = recharge;
            this.currency = currency;
            this.error = error;
        }

        static Result ok(String total, String bonus, String recharge, String currency) {
            return new Result(true, total, bonus, recharge, currency, null);
        }

        static Result fail(String error) {
            return new Result(false, null, null, null, null, error);
        }
    }

    public static Result fetchBalance(String apiKey) {
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(BALANCE_URL).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);

                int code = conn.getResponseCode();
                String body = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());

                if (code == 401) {
                    return Result.fail("密钥无效（HTTP 401）");
                }
                if (code != 200) {
                    if (code >= 500 && attempt == 0) {
                        sleep(600);
                        continue;
                    }
                    return Result.fail("接口返回 HTTP " + code + (body.isEmpty() ? "" : "：" + shorten(body)));
                }

                JSONObject obj = new JSONObject(body);
                JSONArray infos = obj.optJSONArray("balance_infos");
                if (infos == null || infos.length() == 0) {
                    return Result.fail("接口返回结构异常（缺少 balance_infos）");
                }

                JSONObject info = null;
                for (int i = 0; i < infos.length(); i++) {
                    JSONObject it = infos.optJSONObject(i);
                    if (it != null && "CNY".equalsIgnoreCase(it.optString("currency", ""))) {
                        info = it;
                        break;
                    }
                }
                if (info == null) {
                    info = infos.optJSONObject(0);
                }
                if (info == null) {
                    return Result.fail("接口返回结构异常");
                }

                String total = normalizeAmount(strOrNull(info, "total_balance"));
                if (total == null) {
                    return Result.fail("接口返回结构异常（缺少 total_balance）");
                }

                return Result.ok(
                        total,
                        normalizeAmount(strOrNull(info, "granted_balance")),
                        normalizeAmount(strOrNull(info, "topped_up_balance")),
                        info.optString("currency", "CNY"));

            } catch (Exception e) {
                if (attempt == 0) {
                    sleep(600);
                    continue;
                }
                return Result.fail("网络错误：" + shortReason(e));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        return Result.fail("请求失败");
    }

    private static String readBody(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String strOrNull(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) {
            return null;
        }
        String s = o.optString(key, null);
        if (s == null || s.isEmpty() || "null".equals(s)) {
            return null;
        }
        return s;
    }

    private static String normalizeAmount(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static String shorten(String s) {
        s = s.trim();
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    private static String shortReason(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) {
            m = e.getClass().getSimpleName();
        }
        return m.length() > 100 ? m.substring(0, 100) : m;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}