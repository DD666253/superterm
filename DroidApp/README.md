# DroidApp（超级TERM 应用源码）

## 简介

超级TERM（superterm）是一个最高权限终端应用，打开即可使用完整 root shell。
本目录 `DroidApp/` 为应用本体文件源码（Android Studio / Gradle 工程）。

## 功能

- 打开即用，内置完整 root shell（sh / fish 可选）
- 真实 PTY 终端：字符网格、ANSI/VT 转义、SGR 颜色、光标寻址、滚动区
- 内置包管理器 `pkg` 与工具 `eggtool`（随 `assets/skel/` 部署）
- 仅需被权限管理器授权即可使用，无需额外环境依赖

## 工程结构

```
DroidApp/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── skel/          # shell 配置与内置脚本（bashrc / config.fish / eggtool / pkg）
│       │   ├── payload/       # 用户态载荷 zip（按 ABI，安装时解包）
│       │   └── lib/           # 原生 pty 桥 .so（按 ABI）
│       ├── java/com/mucre/superterm/
│       │   ├── MainActivity.java   # 欢迎/安装入口
│       │   ├── TermActivity.java    # 终端界面与会话启动
│       │   ├── TerminalView.java    # 终端渲染与转义解析
│       │   ├── TermSession.java     # PTY 会话 JNI 绑定
│       │   └── Installer.java       # 设备目录布局与载荷解包
│       └── res/               # 图标、布局、主题、颜色
├── jni/
│   └── pty.c                 # PTY 原生桥（fork/exec/resize/wait/close）
├── build.gradle
├── settings.gradle
├── gradle.properties
└── gradle/wrapper/
```

## 构建要求

- Android SDK，compileSdk 34，minSdk 21，targetSdk 28
- JDK 17
- Gradle 8.7（wrapper 已内置）
- 原生桥：将 `jni/pty.c` 用 NDK 交叉编译为各 ABI 的 `libpty.so`，放入 `assets/lib/<abi>.so`

## 包信息

- 应用包名：`com.mucre.superterm`
- 应用名：超级TERM

## 许可证

GNU GPL v3，见仓库根目录 `LICENSE`。
