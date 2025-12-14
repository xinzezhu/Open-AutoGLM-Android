# Android Studio 编译发布 APK 指南

## 方法一：通过 Android Studio GUI（最简单）

### 步骤 1：生成签名密钥（首次需要）

1. 在 Android Studio 中，点击菜单：`Build` → `Generate Signed Bundle / APK`
2. 选择 `APK`，点击 `Next`
3. 如果还没有密钥，点击 `Create new...`：
   - **Key store path**: 选择保存位置（建议放在项目根目录，如 `D:\project\Open-AutoGLM-Android\keystore.jks`）
   - **Password**: 设置密钥库密码（记住这个密码！）
   - **Key alias**: 输入别名（如 `autoglm-key`）
   - **Key password**: 设置密钥密码（可以与密钥库密码相同）
   - **Validity**: 建议设置为 25 年
   - **Certificate**: 填写姓名、组织等信息
   - 点击 `OK`
4. 选择刚才创建的密钥库，输入密码，点击 `Next`
5. 选择 `release` build variant，点击 `Finish`
6. 等待构建完成，APK 会生成在 `app/build/outputs/apk/release/app-release.apk`

### 步骤 2：配置签名信息（可选，用于自动签名）

如果希望每次构建时自动签名，需要配置签名信息：

1. 在项目根目录的 `local.properties` 文件中添加以下内容（如果文件不存在则创建）：
   ```properties
   keystore.path=keystore.jks
   keystore.password=你的密钥库密码
   key.alias=autoglm-key
   key.password=你的密钥密码
   ```

2. **重要**：`local.properties` 文件已经在 `.gitignore` 中，不会被提交到 Git

3. 配置完成后，可以直接使用下面的方法二构建

## 方法二：通过 Gradle 命令（推荐）

### 步骤 1：配置签名（如果还没配置）

按照上面的步骤 2 配置 `local.properties` 文件。

### 步骤 2：构建 Release APK

在 Android Studio 的 Terminal 中执行：

```bash
# Windows
gradlew.bat assembleRelease

# Linux/Mac
./gradlew assembleRelease
```

### 步骤 3：找到生成的 APK

构建完成后，APK 文件位于：
```
app/build/outputs/apk/release/app-release.apk
```

## 方法三：通过 Android Studio Build Variants

1. 点击 Android Studio 左下角的 `Build Variants` 标签
2. 在 `app` 模块的 `Active Build Variant` 中选择 `release`
3. 点击菜单：`Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
4. 等待构建完成，点击通知中的 `locate` 链接即可找到 APK

## 重命名 APK（可选）

为了方便发布，可以重命名 APK：

```bash
# Windows PowerShell
Rename-Item -Path "app\build\outputs\apk\release\app-release.apk" -NewName "Open-AutoGLM-Android-v1.0.apk"

# Linux/Mac
mv app/build/outputs/apk/release/app-release.apk app/build/outputs/apk/release/Open-AutoGLM-Android-v1.0.apk
```

## 验证 APK

构建完成后，可以：

1. **检查 APK 大小**：通常应该在 10-50MB 之间
2. **安装测试**：将 APK 传输到 Android 设备并安装测试
3. **检查签名**：使用以下命令验证 APK 是否已签名：
   ```bash
   # 需要 Android SDK 的 build-tools
   jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk
   ```

## 发布到 GitHub Releases

1. 将 APK 重命名为友好名称（如 `Open-AutoGLM-Android-v1.0.apk`）
2. 创建 Git tag：
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```
3. 在 GitHub 上创建 Release：
   - 进入仓库的 Releases 页面
   - 点击 "Create a new release"
   - 选择 tag `v1.0.0`
   - 上传 APK 文件
   - 填写发布说明
   - 点击 "Publish release"

## 常见问题

### Q: 构建时提示 "keystore file not found"

A: 检查 `local.properties` 中的 `keystore.path` 是否正确，路径应该是相对于项目根目录的。

### Q: 构建时提示密码错误

A: 检查 `local.properties` 中的密码是否正确，注意不要有多余的空格。

### Q: 如何更新版本号？

A: 在 `app/build.gradle.kts` 中修改：
```kotlin
defaultConfig {
    versionCode = 2  // 递增版本号
    versionName = "1.1"  // 更新版本名称
}
```

### Q: 如何不配置签名直接构建？

A: 如果不配置签名，构建的 APK 是未签名的，无法直接安装。可以：
1. 使用 Debug 版本：`gradlew assembleDebug`（会自动使用 debug 签名）
2. 或者配置签名信息

## 安全提示

⚠️ **重要**：
- 不要将 `keystore.jks` 和 `local.properties` 提交到 Git
- 妥善保管密钥库文件和密码
- 如果密钥丢失，将无法更新已发布的应用
- 建议备份密钥库文件到安全的地方


