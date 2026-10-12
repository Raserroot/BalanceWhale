# BalanceWhale · DeepSeek 余额通知

在 Android 通知栏常驻显示 DeepSeek API 余额的小工具。每 5 分钟自动刷新，也可以随时手动刷新；总余额、充值余额、赠金直接显示在通知里。

余额接口用法与解析逻辑参考 [DeepSeek-Balance-Whale-Widget](https://github.com/MeteorNOX/DeepSeek-Balance-Whale-Widget)（DSH 小鲸鱼记账挂件的余额功能），本项目将其移植为独立的安卓应用。

## 功能

- 通知栏常驻显示余额：总余额 / 充值余额 / 赠金（CNY 优先，USD 兜底）
- 每 5 分钟自动刷新（界面可开关）；通知自带「立即刷新」按钮，App 内也有
- 首次进入引导：授予通知权限（建议同时忽略电池优化）→ 输入 API 密钥 → 完成
- 密钥仅存本机 SharedPreferences，只用于请求 GET https://api.deepseek.com/user/balance
- 刷新失败保留上次余额并提示原因；5xx 自动重试一次，失败 60 秒后重试
- 开机自启恢复监控；点击通知直接打开 App

## 使用

1. 安装 `DeepSeekBalance-v1.0.apk`（Releases 页面）
2. 打开后按引导授予通知权限（建议同时把 App 加入电池优化白名单）
3. 填入 DeepSeek API 密钥（在 platform.deepseek.com 的 API Keys 页面创建），点「保存并验证」
4. 完成。通知栏会出现余额，每 5 分钟自动刷新

## 构建

- JDK 17+、Gradle 8.14.5（已含 wrapper）、AGP 8.13.2
- compileSdk / targetSdk 36，minSdk 26（Android 8.0+）
- `gradlew assembleDebug` 直接构建可安装的 debug 包
- Release 签名：在 `app/build.gradle` 的 `signingConfigs.release` 配置自己的 keystore（仓库不含任何签名文件；没有 keystore 时也能构建未签名的 release）

## 代码结构

- `MainActivity`：权限引导页 / 密钥输入页 / 主页（余额显示与控制）
- `BalanceService`：前台服务，5 分钟轮询 + 常驻通知更新
- `DeepSeekApi`：余额接口封装（Bearer 认证、超时 8s、5xx 重试）
- `Prefs`：配置与最近一次结果缓存
- `BootReceiver`：开机自启

## License

[MIT](LICENSE)