# KA-Music 歌词

给 KA Music（com.hoilai.mm.music）用的歌词模块。装上之后，HyperLyrics Enhanced 这类走 Lyricon 协议的歌词工具就能读到 KA Music 正在放的歌，在状态栏显示歌词和翻译。

只对 KA Music 生效，别的应用不受影响。没有依赖词幕主模块，也不是拿官方插件改的。

## 先说清楚

这个模块是自己用的，写它的原因就是想让状态栏能看歌词，顺手开源出来。

只在 Redmi Turbo 5 Max（HyperOS 3 / Android 16）、LSPosed 2.2.1、HyperLyrics Enhanced 1.0.26 这一套上跑通过。别的机型、别的系统版本、别的歌词工具理论上能用，但我没测过，也不打算为兼容性到处改。代码不长，看不懂的地方可以直接改，改坏了重装就行。

哪天 KA Music 换歌词源、或者 Lyricon 的接口变了，这模块就可能失效。失效了不一定修。

## 做了什么

- 搜歌走网易云，按歌名、歌手、时长挑最接近的一条，和 KA Music 自己的歌词源保持一致
- 除了原文还会拉 tlyric 翻译，填进 RichLyricLine 的 translation 字段，HyperLyrics 会自己把翻译行显示出来
- 注册用 Lyricon 的 REGISTER_PROVIDER 广播，发到 com.android.systemui，因为 HyperLyrics 的歌词运行时在 SystemUI 里
- manifest 里写了 lyricon_module 这一组元数据，HyperLyrics 扫描已安装应用时才认得出这个模块
- 设置页有运行状态、一键重新注入、桌面图标开关

## 大概的流程

```
KA Music (Flutter)
      │ MediaSession / audio_service
      ▼
XposedInit（LSPosed 注入 com.hoilai.mm.music）
      │ hook setMetadata / setPlaybackState
      ▼
KaCore ──创建──▶ LyriconProvider
      │              │ register()
      │              ▼
      │        SystemUI 里的 HyperLyrics 订阅端
      ▼
LyricRepo：搜歌 → 拉 LRC 和翻译 → 组装 RichLyricLine → pushLyric()
```

## 文件

```
src/com/ka/music/lyrics/
  XposedInit.java    模块入口，hook Application 和 MediaSession
  KaCore.java        创建并注册 Provider，推送歌词
  LyricRepo.java     网易云搜索、LRC 与 tlyric 解析
  MainActivity.java  设置页
res/values/          字符串、lyricon_module_tags
res/mipmap-*/        各密度图标
assets/xposed_init   里面一行：com.ka.music.lyrics.XposedInit
AndroidManifest.xml
build.sh             构建脚本
```

## 构建

没有用 Gradle，直接调 aapt2 / javac / D8 / zipalign / apksigner，依赖路径写在 build.sh 开头，按自己机器改一下。

需要用到的东西：

- platform-30 的 android.jar
- Xposed API（api-82.jar）
- Lyricon 的 provider-0.1.70.aar 和 model-0.1.70.jar
- Kotlin stdlib、kotlinx-serialization、kotlinx-coroutines
- AndroidX core、core-ktx、appcompat-resources
- r8.jar 里的 D8

```bash
./build.sh
```

跑完得到 KA_Music_Lyrics.apk。

签名要自己生成一个 keystore：

```bash
keytool -genkeypair -keystore kam.jks -storepass kam12345 -keypass kam12345 \
        -alias kam -keyalg RSA -keysize 2048 -validity 10000
```

## 安装

懒得自己编的话，可以在 [Releases](https://github.com/StarkHol/KA-Music-Lyrics/releases) 里直接下现成的 APK。自己构建也行，见上一节。

1. 装 APK，在 LSPosed 里启用，作用域勾上 KA Music
2. 打开模块设置页，点一下重启模块
3. 到 HyperLyrics Enhanced 的 Lyricon 配置里，已安装的独立 Provider 模块下面会出现 KA-Music歌词
4. 放歌就行

## 目前的问题

- 靠歌名歌手时长匹配，冷门歌可能匹到翻唱
- 网易云接口没登录态，有些歌拿不到翻译
- 设置页的状态检测要读 logcat，第一次会要 root 权限，不给的话只能看进程在不在

## 谢谢

Lyricon 的协议和 SDK 来自 tomakino/lyricon，播放器是 umr-xiaomai 的 kgka_Music_hl。

## License

MIT

## 免责声明

本项目跟 KA Music、酷狗、网易云音乐、HyperLyrics Enhanced 的开发者都没关系，就是个人写的第三方模块。

歌词和翻译是通过公开接口取的，内容版权归各自版权方，仓库里不存也不分发任何音频文件。

如果有人觉得不妥，比如原作者或者平台方，跟我说一声，我把仓库和发行版一起删掉，不扯皮。