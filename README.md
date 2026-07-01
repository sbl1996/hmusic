# hmusic

`hmusic` 是一个 Android 音乐播放器，只做本地音频播放，并使用 S3 兼容对象存储做云端备份与恢复。

## 功能

- 本地音频播放
- 云端歌单同步到本地
- 本地歌曲备份到云端
- 云端歌曲按需下载到本地
- 支持 AWS S3、阿里云 OSS、七牛云、MinIO 等 S3 兼容存储

## 环境要求

- Android Studio
- JDK 11
- Android SDK 36

## 运行

```bash
./gradlew assembleDebug
```

也可以直接用 Android Studio 打开项目并运行 `app`。

## 配置

项目不再读取 `.env`。S3 / OSS 备份配置通过应用内设置页面维护，包括：

- Endpoint
- Region
- Force Path-Style
- Bucket
- AccessKey ID
- AccessKey Secret
- Prefix

不配置 S3 也可以启动应用，只是不能使用备份与恢复。

## 测试

```bash
./gradlew test
```
