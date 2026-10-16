# 徽章收藏 / Badge Collection

一个**完全离线**的光遇（Sky）徽章工具，面向**你已经实际持有的实体徽章**。

解决的问题很具体：官方徽章给游戏内加的道具有时限（刷一次约 10 分钟效果、15 分钟冷却），
想持续用就得反复贴卡，而部分官方 NFC 标签感应区小、难贴。本工具让你**读一次自己的卡**，
把链接保存在本机，之后直接在列表或悬浮窗里点一下就把链接交还给游戏，不必再翻卡片。

- 包名：`com.lanteng.star.app`
- 应用名：`Star徽章助手`（中文）/ `Star Badge Helper`（英文）
- Android 26+ (8.0)，Kotlin，Gradle + AGP 8.6

---

## 许可证与来源（重要）

本工程以 **GPL-3.0** 发布，见 `LICENSE` 与 `NOTICE`。原因是它的上游：

- 上游项目：**[useSkyBadge](https://gitee.com/rainerosion/useSkyBadge)**，作者 **rainerosion**，许可证 **GPL-3.0**
- 本工程是该方向的 Android 重写实现；贵方的分发版是在同一上游基础上的 Android 二次开发

**GPL-3.0 对分发者的硬性要求**，请务必核对：

1. 分发二进制时**必须同时提供完整对应源码**（§6）。
2. 必须**显著标注你修改过**并给出日期（§5a），保留原有版权声明（§4）。
3. 整个作品必须以 **GPL-3.0** 授权，不能改成 MIT 或闭源（§5c）。
4. §3 是反 DRM 条款：**不得把 GPL 作品包装成"有效技术措施"来阻止用户运行修改版**。

第 4 条对你们有直接影响：**原 APK 用 360 加固（`com.qihoo.stub.StubApp`）打包分发、且未放出源码，
这在 GPL-3.0 下是侵权的。** 把源码放到 GitHub 并以 GPL-3.0 授权，正好是修正做法。

原 App 自带的免责声明里也写着"并非本人作品""原始项目与源代码的著作权归原作者 rainerosion 所有"，
与此一致。`NOTICE` 中的版权署名与修改日期已填写为 **AnyLose，2026-10-16**；如分发主体不同请自行调整。

---

## 界面文案来源

界面措辞**直接取自上游资源的原文**（`recovered/resources/strings-zh.xml` 的 `zh` 配置与
`strings-default.xml` 的英文），共 **42 条**，由 `build-strings.py` 生成，不手抄，
因此格式参数（`%1$s` 等）保持逐字节一致。

有 **8 条刻意没有沿用上游措辞**，因为上游文字描述的是上游的行为，照搬会让界面说谎：

| 键 | 上游描述 | 本实现的实际行为 |
|---|---|---|
| `icon_only_floating_panel_summary` | 展开面板只显示图标、不显示名称备注 | 面板保持收起 |
| `volume_keys_toggle_floating_summary` | 音量−隐藏、音量+恢复 | 两个音量键同时按切换 |
| `shake_toggle_floating_summary` | 显示全部 / 隐藏全部 | 展开 / 收起 |
| `custom_floating_icon_summary` | 长按悬浮窗 10 秒切换样式 | 在设置里选图片 |
| `number_invalid` | 密码长度规则 | 时长必须 1–600 |
| `toast_deleted` | "账号已删除" | 删除的是徽章 |
| `toast_exported` | 需要文件路径的模板 | 纯提示 |
| `toast_overlay_denied` | 对话框标题，不是提示语 | 需要一句解释 |

上游文案里标注 `【会员功能】` 的段落被**去掉**了——本工程没有付费档，留着会误导用户。

`about_body`（关于页）也保留本工程自己的说明：它必须准确描述**这个构建**的行为，
而上游那段在介绍"模拟启动 / 写入白卡"等本工程没有的功能。

重新生成文案：`python build-strings.py`（幂等，可重复运行）。

---

## 离线保证（强制，不是承诺）

`AndroidManifest.xml` **不声明 `android.permission.INTERNET`**，也没有 `ACCESS_NETWORK_STATE`，
并且 `android:allowBackup="false"`。所以：

- 进程在系统层面**无法建立任何 socket**；
- 连平台自带的备份通道也不会把数据带出去；
- 不存在"云端"代码路径——不是删掉，而是从来没写。

实测（`aapt2 dump permissions app/build/outputs/apk/release/app-release.apk`）：

```
uses-permission: android.permission.NFC
uses-permission: android.permission.CAMERA
uses-permission: android.permission.SYSTEM_ALERT_WINDOW
uses-permission: android.permission.FOREGROUND_SERVICE
uses-permission: android.permission.FOREGROUND_SERVICE_DATA_SYNC
uses-permission: android.permission.POST_NOTIFICATIONS
uses-permission: android.permission.RECEIVE_BOOT_COMPLETED
uses-permission: android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
uses-permission: android.permission.VIBRATE
```

原版里的「立即检测云端更新」「上传云端」「云端徽章资源同步」在本实现中不存在。

---

## 功能

- **收藏管理**：账号、分类、备注、获得日期、徽章图片（SAF 持久化读权限，不需要存储权限）
- **徽章链接**：NFC 读取**你自己的卡片**即可保存；也可手动粘贴。列表/详情默认**打码**，点「显示」才展开
- **呼起**：把已保存的链接交给系统或指定应用；列表每行一个按钮
- **冷却倒计时**：效果剩余与冷却剩余每秒刷新；效果时长与冷却时长可在设置里改（默认 10 / 15 分钟）
- **悬浮窗**：可拖动图标 + 展开面板，含快捷呼起按钮、自动隐藏（用徽章后收起）、点击外部隐藏、
  仅图标、音量键切换、摇一摇切换、自定义图标、账号切换标签
- **NFC 只读读取**：UID、技术类型、NDEF 记录结构、Type 2 标签原始数据页
- **二维码**：本地生成/扫描**收藏记录**（ZXing，解码器打包在 APK 内，无需联网）
- **本地备份/恢复**：导出/导入同一份 JSON（SAF，自选文件位置）
- **设置页**：复刻上游的五个分组并新增「呼起与冷却」；**云端同步项已去掉**
- **多语言**：英文（`values`）+ 简体中文（`values-zh`，与上游的 `zh` 配置一致，可回退覆盖 zh-rTW/HK）

## 刻意不做（不是遗漏）

| 不做 | 原因 |
|---|---|
| **写入白卡 / 写入新卡** | 把徽章载荷写到空白标签会制造**新的实物副本**。代码中没有任何 `WRITE`(0xA2)、`PWD_AUTH`、锁定或格式化指令。 |
| **从别处导入链接 / 内置链接库 / 云端徽章资源目录** | 会让**未购买者**拿到链接。链接在本工程里**只有一个来源：你自己的卡片**；默认分类不含任何徽章数据。 |
| **加密导出 / 云端上传下载 / 分享链接** | 这是链接在**人与人之间**流通的管线。二维码只承载收藏记录，`badgeToShareableJson()` 会显式移除链接与标签数据。 |
| **徽章链接解密** | 上游的"解密徽章"用于处理加密分享的链接，属上一条的配套功能。 |

一句话：**本工程只负责把你自己卡上的链接保存下来再交还回去**，不负责链接的产生、解密与传播。

## 未实现

- **导入语言文件**：Android 在布局 inflate 时从已编译资源取字符串，运行时无法注入翻译。
  要加语言请新增 `app/src/main/res/values-<locale>/strings.xml` 后重新构建。
- **剥离赞助凭证门禁**：本实现里根本没有门禁可剥（全部功能免费）。

---

## 构建

需要 JDK 17+ 与 Android SDK（platform 35、build-tools 35）。

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug     # 可安装的调试包
./gradlew assembleRelease   # 发布包
```

产物在 `app/build/outputs/apk/`。推送到 `main` 会自动构建（`.github/workflows/android.yml`），
打 `v*` 标签会把 APK 附到 Release。

## 签名

Android 强制要求签名，"去掉签名"只能理解为**换成你自己的密钥**。原 APK 的签名是第三方签名服务的：

```
Subject : CN=BaiRan, C=CN
Issuer  : CN=BaiRan, C=CN
NotAfter: 2049-12-07
```

本工程默认让 `release` 复用 debug 签名，CI 出来的包开箱可装。要发布请换成自己的密钥：

```bash
keytool -genkeypair -v -keystore my-release.jks \
        -alias mykey -keyalg RSA -keysize 2048 -validity 10000
```

然后改 `app/build.gradle.kts` 里的 `signingConfig`，密钥走 CI secrets，**不要提交进仓库**
（`.gitignore` 已排除 `*.jks` / `*.keystore`）。换签名后无法覆盖安装原 App，需先卸载。

---

## 素材与 IP 注意

- `recovered/` 是从原 APK 导出的参考资料，**已被 `.gitignore` 排除**，内含
  `resources/`（8340 个资源、1216 条中文文案、89 个语言配置，以及 `build-strings.py` 读取的
  `upstream-strings.json`）、`assets/`（40 张 `xfc_*.png` 徽章图）、`evidence/original-signer.txt`。
  其中有 **105 个光遇官方徽章名称**与徽章立绘，属 thatgamecompany / 网易的 IP，**不要提交到公开仓库**。
- 本工程自带自绘占位图 `ic_badge_placeholder`，徽章图片由用户在应用内自行选择。
- 你们自己写的 UI 文案可以合法复用（GPL-3.0 下亦是你们的作品），已按上文取自原文。

## 已知限制

- **音量键切换是近似实现**：后台应用无法拦截按键事件，这里监听两个音量设置在 700ms 内先后变化。
- **从最近任务隐藏 / 锁定**：Android 无公开 API。`lock_recent_tasks` 用 `FLAG_SECURE` 屏蔽缩略图；
  `hide_recent_tasks` 在离开界面时结束任务。两者都是近似。
- **呼起机制就是 `ACTION_VIEW`**：本工程不解析、不构造徽章参数，只把已保存的链接交给系统。
  上游那套 `AppLaunchHelper` 的五种启动策略（ComponentName+Data+Action、隐式 Intent+Package、
  `getLaunchIntentForPackage`、fullScreenIntent…）用于绕过 OEM 后台启动限制；如果某些机型呼不起来，
  扩展点是 `launch/BadgeLauncher.trigger()`。
- **targetSdk 35**，原 APK 声明的是 37（本机 SDK 最高 35）。
- 使用本工具可能违反游戏服务条款并导致账号处罚，这是使用者需要自行判断的风险。

## 开发注意：不要用 PowerShell 往返改含中文的文件

源码（`.kt`）与 `res/xml/` 中**不应出现非 ASCII 字符**——所有面向用户的文字都放在 `res/values*/`。

这不是洁癖。改包名时用 Windows PowerShell 5.1 的 `Get-Content -Raw` + `Set-Content -Encoding utf8`
做批量替换，它以 cp936 读、以**带 BOM** 的 UTF-8 写，把 `Store.kt`、`BadgeAdapter.kt`、
`preferences.xml`、`README.md` 四个文件写坏（Kotlin 源码里出现 `季节` → `瀛ｈ妭` 这类不可逆乱码，
`.kt` 文件还被塞进了 U+FEFF）。现已全部修复，并落实以下约定：

- 改含中文的文件请用支持 UTF-8 的工具，**不要经过 PowerShell 的读写往返**；
- 默认分类名已移入 `res/values*/arrays.xml`，源码内不再有中文字面量；
- 仓库自带三个防护脚本（`dev-tools/encoding/`），可随时复查：
  - `verify-encoding.py` —— 字节级校验 + 三项不变式（源码无 CJK、`res/xml` 无 CJK、无 BOM），
    非零退出即失败。**已接入 CI**：`.github/workflows/android.yml` 在 checkout 后、装 JDK 前先跑它，
    所以这类损坏会在构建开始前就失败；
  - `scan-encoding.py` —— 列出所有含非 ASCII 的文件；
  - `repair-encoding.py` —— 自动修复**可逆**的双重编码（`--apply` 才写入）。
- 从 `recovered/` 重新生成上游文案表与本地文案：`dev-tools/strings/` 下的
  `dump-upstream-strings.py` 与 `build-strings.py`（需要 `recovered/` 存在，故未提交）。
