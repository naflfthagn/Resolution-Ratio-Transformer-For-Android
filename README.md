# 分辨率工坊 · ResizeLab

一个面向高版本 Android 的本地图片 / 视频分辨率调整工具。所有解码、缩放与重新编码都在手机上完成，应用不申请网络权限，素材不会上传。

## 功能

- 通过系统**文件选择器（Storage Access Framework）**选择一张图片或一段视频，无需授予整个相册的访问权限。
- **图片改分辨率**：支持 JPEG、PNG、WebP 输出；按长边规格或自定义宽高缩放；可选适应、居中裁剪、拉伸三种画面布局；JPEG / WebP 支持质量调节。
- **GIF 动图缩放**：导入 GIF 后默认按 GIF 导出，逐帧调整分辨率并保留动画帧时长、循环次数和透明区域；编辑页提供动画预览。
- **视频改分辨率**：基于 AndroidX Media3 Transformer 重新编码为 H.264 + AAC MP4，保留音轨；支持 4K / 2K / 1080P / 720P / 480P 与自定义尺寸。
- **声音清晰度**：视频导出时可选自动（保留原音轨）、96 / 128 / 192 / 256 / 320 kbps AAC 码率；手动码率会强制重新编码音轨，码率越高通常声音细节越多、文件越大。
- 自动校正图片 EXIF 方向、视频旋转元数据；视频宽高自动取整为偶数。
- 导出结果直接写入系统相册：
  - 图片：`Pictures/ResizeLab`
  - 视频：`Movies/ResizeLab`
- 导出进度、取消、打开、分享；深色模式、边到边布局、主题图标与 Android 12+ 启动画面。
- 使用 `ACTION_OPEN_DOCUMENT` 而不是厂商定制差异较大的虚拟 Photo Picker URI，避免部分机型选中图片后无法再次读取文件。
- 全部处理在本机完成，Manifest 中没有 `INTERNET` 权限。


## 直接安装

仓库的 `dist/` 目录包含已签名的 release APK：

```bash
adb install -r dist/ResizeLab-1.3.0.apk
```

也可以把 APK 传到手机上，在文件管理器中允许“安装未知应用”后打开安装。安装包使用本地 release keystore 签名；如需上架应用商店，请替换为自己的正式签名并妥善保管密钥。

`dist/SHA256SUMS.txt` 提供 APK 与源码包的 SHA-256 校验值。`dist/ResizeLab-1.3.0-source.zip` 是移除了本机构建缓存、`local.properties` 和签名密钥的完整工程源码。

## 更新记录

### 1.3.0

- 新增 GIF 动图缩放：导入 GIF 后自动选择 GIF 输出，逐帧缩放并重新编码。
- 保留原始帧时长、循环次数和透明区域；提供 GIF 动画预览。
- GIF 使用专用逐帧处理管线，不会像普通图片一样只导出首帧。
- 新增 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，记录 GIF 解码与编码依赖。

### 1.2.0

- 精简首页和编辑页：移除大面积营销视觉、功能卡片堆叠和重复说明文字，只保留选择素材、分辨率、导出设置和导出按钮。
- 首页“功能与使用说明”默认折叠；编辑页“导出设置”默认折叠，并在标题行显示当前格式/质量或声音码率摘要。
- 统一改为**钝角方形**视觉：14dp 卡片、10dp 按钮与输入框、8dp 选项按钮，移除渐变按钮、圆形徽章和胶囊按钮。
- 顶栏“更换”改为矩形描边按钮；导出与选择素材使用扁平实色按钮。

### 1.1.0

- 新增视频**声音清晰度**设置：自动 / 96k / 128k / 192k / 256k / 320k。
- 选择固定码率时会强制将音轨重新编码为所选的 AAC 码率，避免 Media3 直接复用原始音轨导致设置无效。
- 自动模式保持原有行为，不额外强制音频转码。
- 界面会提示：更高码率无法恢复原片已经丢失的声音细节。

### 1.0.2

- 修复部分图片能够正常预览、但导出时提示“图片解码失败”的问题：选中图片后会先复制到应用私有缓存，预览和导出都使用这份稳定副本，不再在导出阶段重新打开相册 Provider URI。
- 导出解码增加 Android 10+ 的 `ImageDecoder` 备用路径，可处理部分 BitmapFactory 无法解码的 HEIC / AVIF / 10-bit 图片，并自动处理 EXIF 方向。
- 解码失败提示改为建议先将图片另存为 JPEG / PNG，避免误报“文件已损坏”。
- 离开编辑页或应用进程重启后清理图片缓存副本。

### 1.0.1

- 修复部分机型选择图片后提示“无法读取这个文件，请重新选择”的问题。根因是厂商 Photo Picker 返回的虚拟 `content://media/picker/...` URI 在再次打开时可能抛出 `FileNotFoundException` / `SecurityException`。现改用 `ACTION_OPEN_DOCUMENT` 获取更稳定的内容 URI，并保存持久读取权限。
- 媒体信息查询改为容错处理：`DISPLAY_NAME`、`SIZE`、`getType` 查询失败不会再中断素材加载。
- 增加 `openInputStream` / `openAssetFileDescriptor` 双通道读取与文件头格式嗅探。
- 选择失败时会记录 `EditorViewModel` 日志，便于定位特殊机型问题。

## 环境与版本

| 项目 | 版本 |
| --- | --- |
| minSdk | 26（Android 8.0） |
| targetSdk / compileSdk | 35（Android 15） |
| Kotlin | 2.1.21 |
| Android Gradle Plugin | 8.10.0 |
| Gradle | 8.13 |
| JDK | 17 |
| UI | Jetpack Compose + Material 3 |
| 视频 | AndroidX Media3 1.5.1 |

## 构建

1. 使用 Android Studio 打开项目，或准备 JDK 17 / Android SDK Platform 35 / Build-Tools 35.0.0。
2. 在项目根目录创建 `local.properties`，指向 SDK：
   ```properties
   sdk.dir=C\:/path/to/Android/Sdk
   ```
3. 构建：

```bash
# macOS / Linux
./gradlew :app:assembleDebug

# Windows
gradlew.bat :app:assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

项目中的 `gradle-wrapper.properties` 默认指向腾讯云 Gradle 8.13 镜像，便于在当前网络环境直接构建。
若希望使用 Gradle 官方分发，把 `distributionUrl` 改为
`https://services.gradle.org/distributions/gradle-8.13-bin.zip` 即可；也可以直接使用本机 Gradle 8.13。

### 检查与测试

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

单元测试覆盖了比例计算、长边规格、视频偶数取整、尺寸上限、裁剪 / 适应区域计算与文件命名。

## 目录结构

```
app/src/main/java/com/resizelab/app/
├── MainActivity.kt              # 启动入口、边到边、启动画面
├── ResizeLabApplication.kt     # 清理过期的临时导出文件
├── data/                        # MediaInfo、媒体检查、格式与导出模型
├── media/
│   ├── ImageResizer.kt         # 图片采样、EXIF 方向、Canvas 重绘、压缩
│   ├── ImagePreviewLoader.kt   # 内存友好的图片预览
│   ├── VideoResizer.kt         # Media3 Transformer 视频缩放
│   └── MediaStoreSaver.kt      # 写入系统相册
├── ui/
│   ├── ResizeLabRoot.kt        # 导航、Photo Picker、权限、Snackbar
│   ├── HomeScreen.kt           # 首页
│   ├── EditorScreen.kt         # 编辑 / 导出页
│   ├── EditorViewModel.kt      # 状态与导出流程
│   └── theme/                  # 颜色、字体、形状、系统栏
└── util/                       # 分辨率数学、格式化工具
```

## 处理说明与边界

- 图片最长边上限 **6144 px**，总像素上限 **32 MP**，避免大图导致内存不足；视频最长边上限 **4096 px**。
- 图片默认按长边规格等比缩放。关闭“锁定比例”后，可选择适应（黑边 / PNG 透明边）、填充（居中裁剪）或拉伸。
- 视频重新编码依赖设备硬件 / 软件编码器；如果设备不支持目标分辨率，会提示降低分辨率。HDR 视频会按 Media3 能力进行回退。
- 导出任务运行在应用进程内。视频较长时请保持应用处于前台；导出页提供取消按钮。屏幕在导出期间保持常亮。
- Android 8.0–9.0 保存到公共相册需要 `WRITE_EXTERNAL_STORAGE`，应用会在首次导出时申请；Android 10+ 使用分区存储，无需存储权限。
- HEIC / AVIF 等图片格式是否可解码取决于设备系统解码器。
- 图片重新编码后会移除原始 EXIF（包括定位信息），输出画面会先按 EXIF 方向摆正。

## 签名

本地 `keystore.properties` 存在时 release 构建会自动使用它；否则 release 回退到 debug 签名，方便本地安装验证。发布到应用商店前请生成并妥善保管自己的 release keystore，不要提交到版本库。
