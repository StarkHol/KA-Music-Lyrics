# KA-Music歌词

一个**独立的歌词 Provider 模块**，为第三方音乐客户端 [KA Music](https://github.com/umr-xiaomai/kgka_Music_hl)（包名 `com.hoilai.mm.music`）提供在线歌词，使 **HyperLyrics Enhanced** / Lyricon 生态能显示状态栏歌词与翻译。

- 不依赖词幕（Lyricon）主模块
- 不修改任何官方插件，从零编写
- 只 hook `com.hoilai.mm.music`，不影响其它应用

## 功能

| 功能 | 说明 |
|---|---|
| 歌词来源 | 网易云搜索匹配（歌名 + 歌手 + 时长打分），与 KA Music 自身的歌词源一致 |
| 翻译 | 解析网易云 `tlyric`，填入 `RichLyricLine.translation`，HyperLyrics 会自动显示翻译行 |
| 注册方式 | Lyricon Provider 协议：`REGISTER_PROVIDER` 广播发往 `com.android.systemui` |
| 模块识别 | AndroidManifest 中的 `lyricon_module` 系列元数据 |
| 设置页 | 运行状态检测、一键重新注入、桌面图标开关 |

## 工作原理

```
KA Music (Flutter)
   │  MediaSession / audio_service
   ▼
XposedInit (LSPosed 注入 com.hoilai.mm.music)
   │  监听 MediaSession.setMetadata / setPlaybackState
   ▼
KaCore  ──创建──▶ LyriconProvider（Lyricon SDK）
   │                 │ register() → REGISTER_PROVIDER 广播
   │                 ▼
   │            SystemUI 内的 HyperLyrics 订阅端
   ▼
LyricRepo  搜索歌曲 → 拉取 LRC + 翻译 → 构造 RichLyricLine 列表 → pushLyric()
```

## 目录结构

```
src/com/ka/music/lyrics/
├── XposedInit.java    # LSPosed 入口：hook Application / MediaSession
├── KaCore.java        # Provider 创建与注册、歌词推送
├── LyricRepo.java     # 网易云搜索、LRC / tlyric 解析
└── MainActivity.java  # 设置界面（状态、重注入、桌面图标开关）
res/values/            # 字符串、lyricon_module_tags 数组
res/mipmap-*/          # 多密度图标
assets/xposed_init     # 内容：com.ka.music.lyrics.XposedInit
AndroidManifest.xml
build.sh               # 手工构建脚本（aapt2 + javac + D8 + zipalign + apksigner）
```

## 构建

项目不使用 Gradle，直接用 Android SDK 工具链构建，依赖见 `build.sh` 顶部变量：

| 依赖 | 用途 |
|---|---|
| `platform-30/android.jar` | 编译目标 |
| `api-82.jar` | Xposed API |
| Lyricon `provider-0.1.70.aar`、`model-0.1.70.jar` | Provider 协议 |
| Kotlin stdlib / kotlinx-serialization / kotlinx-coroutines | Lyricon SDK 运行时 |
| AndroidX core / core-ktx / appcompat-resources | Lyricon SDK 运行时 |
| D8（r8.jar） | dex 生成 |

```bash
# 准备依赖目录后执行
./build.sh
# 产物：KA_Music_Lyrics.apk
```

需要自行生成签名 keystore：

```bash
keytool -genkeypair -keystore kam.jks -storepass kam12345 -keypass kam12345 \
        -alias kam -keyalg RSA -keysize 2048 -validity 10000
```

## 安装与使用

1. 安装 APK，在 **LSPosed** 中启用模块，作用域勾选 **KA Music**
2. 打开模块设置页，点「重启模块（重新注入）」
3. 在 **HyperLyrics Enhanced → Lyricon 配置 → 已安装的独立 Provider 模块** 中会出现「KA-Music歌词」
4. 播放歌曲，状态栏即显示歌词与翻译

## 已知限制

- 歌词匹配依赖歌名 / 歌手 / 时长，冷门曲目可能匹配到翻唱版本
- 网易云接口无登录态，部分歌曲可能无翻译
- 首次状态检测需要授予 root（用于读取 logcat），未授权时仅显示进程状态

## 致谢

- [tomakino/lyricon](https://github.com/tomakino/lyricon) — Provider 协议与 SDK
- [umr-xiaomai/kgka_Music_hl](https://github.com/umr-xiaomai/kgka_Music_hl) — KA Music

## License

MIT
