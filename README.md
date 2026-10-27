# Elytra Flight Control — 0.1.0

面向 Minecraft 26.2 的 Fabric 鞘翅飞行控制模组：鼠标指定飞行目标，飞行教官协调姿态，可用键盘直接控制俯仰、滚转、偏航及油门。支持第三人称追尾、V 自由观察和飞行 HUD。

这是 **v0.1.0 GitHub 正式 Release**。发行身份不代表全部质量或安全条件均已验证。当前范围限单人世界和单控制玩家的本机回环专用服务器；不支持以此版本承诺公网、局域网或多人并发飞控。

运行条件：Minecraft **26.2**、Java **25 或更高**、Fabric Loader **0.19.5 或更高**、适配 26.2 的 Fabric API **0.159.0+26.2 或更高**。实际准备与构建使用上述固定最低版本，较新版本及其它组合未逐一验证。

见 [安装与操作](INSTALL.md)、[版本说明](CHANGELOG.md)、[已知限制](KNOWN_LIMITATIONS.md)。切换键默认未绑定，须先在游戏原版“控制”界面设置。

从本仓库获取源码后，使用 JDK 25，在源码根目录执行 Windows `gradlew.bat --no-daemon build`，其它平台可尝试 `sh gradlew --no-daemon build`（本次未验证非 Windows 构建）。构建工具固定为 Gradle 9.5.1 / Fabric Loom 1.17.21；Wrapper 校验发行下载 SHA-256。首次构建需要正常的公开依赖下载，完整缓存存在时可用 `--offline`。生成的安装 JAR 位于 `build/libs/`，名称中带 `-sources` 的文件不是安装包。

源码在 [GitHub](https://github.com/zbw3790/3790s-elytra-flight-mode) 公开可查阅，目前未为 EFC 源代码指定开源许可证；请勿将源码公开误认为授予第三方修改和再分发的开放许可。现有第三方构建工具头部声明继续适用。正式发行不代表所有质量条件均已验证；请先阅读已知限制。

安装文件：`elytra-flight-control-0.1.0.jar`。SHA-256：`26c64cf1560b467f85e9dab7bf8866e54153c53c10d093063bc5d3bca4d450f7`。
