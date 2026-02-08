# Open-AutoGLM-Android

English | [中文](README.md)

An Android smartphone automation assistant based on Accessibility Service, using AutoGLM vision language model to perform phone operations through natural language instructions.

## Features

- 🤖 **AI-Powered Automation**: Uses AutoGLM vision language model to understand screen content and perform operations
- 📱 **No ADB Required**: Completely based on Android Accessibility Service, no computer connection needed
- 🎯 **Natural Language Control**: Describe tasks in natural language, AI automatically plans and executes
- 🔄 **Conversational Interaction**: Supports multi-step tasks, AI remembers context and continues execution
- 🛠️ **Easy to Use**: Simple interface, just input task descriptions

## Interface Demo

![Interface Demo](interface.jpg)

## System Requirements

- **Android Version**: Android 11 (API 30) or higher
- **Device Requirements**: Android device with Accessibility Service support
- **Network Requirements**: Internet connection required to access AutoGLM API
- **Permission Requirements**:
  - Accessibility Service permission (required)
  - Network access permission (required)
  - Query all apps permission (for launching apps)

## Usage Steps

### 1. Install the App

Download the APK file from the Releases page and install it on your Android device.

### 2. Configure API

The app comes with two pre-configured models that are automatically added to the model configuration list on first launch:

#### Option 1: Zhipu AutoGLM (Recommended)
1. Open the app, go to "Settings" page, and click "Model Configuration"
2. Apply for an API Key on Zhipu AI platform (visit https://open.bigmodel.cn/)
3. Select "智谱 AutoGLM" configuration and edit:
   - **API Key**: Your Zhipu AI API Key
   - **Base URL**: `https://open.bigmodel.cn/api/paas/v4` (pre-configured)
   - **Model Name**: `autoglm-phone` (pre-configured)
4. Click "Save"

#### Option 2: Alibaba Cloud Bailian GUI-plus
1. Open the app, go to "Settings" page, and click "Model Configuration"
2. Apply for an API Key on Alibaba Cloud Bailian platform (visit https://bailian.console.aliyun.com/)
3. Select "阿里云百炼 GUI-plus" configuration and edit:
   - **API Key**: Your Alibaba Cloud DashScope API Key
   - **Base URL**: `https://dashscope.aliyuncs.com/compatible-mode/v1` (pre-configured)
   - **Model Name**: `gui-plus` (pre-configured)
4. Click "Save"

**Note**: Both models support vision understanding and mobile automation. You can choose either based on your needs.

### 3. Enable Accessibility Service

1. On the settings page, click the "Go to Settings" button
2. Find "AutoGLM Android" in the system accessibility settings
3. Enable the accessibility service
4. Return to the app and confirm the status shows "Enabled"
5. In system settings, set this app's **battery usage/battery policy** to **Unrestricted/Not restricted** to prevent the system from killing background tasks

### 4. Start Using

1. Return to the "Chat" page
2. Enter your task description in the input box, for example:
   - "Open QQ"
   - "Open QQ then open xxx group"
   - "Search for milk tea on Meituan"
   - "Open WeChat and send a message to Zhang San"
3. Click the "Send" button
4. AI will automatically analyze the screen and perform operations
5. A Toast notification will be displayed when the task is completed

### 5. Clear Conversation

- Click the delete icon on the right side of the top toolbar to clear conversation history and start a new session

## Supported Operations

- **Launch**: Launch an app
- **Tap**: Tap screen coordinates
- **Type**: Input text
- **Swipe**: Swipe the screen
- **Back**: Go back to previous page
- **Home**: Return to home screen
- **Long Press**: Long press
- **Double Tap**: Double tap
- **Wait**: Wait

## Notes

1. **Accessibility Service**: The app requires accessibility service permission to work properly, please make sure it's enabled
2. **Screenshot Function**: Requires Android 11 or higher, may not work on lower versions
3. **Emulator Limitations**: Screenshot function may not work properly when running on Android emulators, it's recommended to test on real devices
4. **Network Connection**: Stable network connection is required to access AutoGLM API
5. **API Costs**: Using AutoGLM API may incur costs, please check Zhipu AI platform pricing information

## FAQ

### Q: Why can't I get screenshots?

A: Please ensure:
- Android version is 11 (API 30) or higher
- Accessibility service is enabled
- If on an emulator, screenshot function may not work properly

### Q: Why did the task execution fail?

A: Possible reasons:
- Accessibility service is not enabled
- API Key configuration is incorrect
- Network connection issues
- Task description is not clear enough
- The system's battery optimization for this app is not set to "Unrestricted", causing background tasks to be killed

### Q: Getting 4000 error when using Alibaba Cloud GUI-plus model?

A: This issue has been fixed in the latest version. The 4000 error is typically caused by invalid request parameters:
- **Problem**: Alibaba Cloud Bailian API doesn't support the `frequency_penalty` parameter
- **Solution**: The app automatically detects the model provider and removes unsupported parameters for Alibaba Cloud API
- **Verification**: Please ensure you're using the latest version of the app; older versions may encounter this issue

If you still get a 4000 error, please check:
1. API Key is correct and valid
2. Model name is correct (should be "gui-plus")
3. Base URL is correctly configured as `https://dashscope.aliyuncs.com/compatible-mode/v1`

### Q: How can I view the AI's thinking process?

A: In the chat interface, click the "Expand thinking process" button in the assistant message to view it.

## Technical Architecture

- **UI Framework**: Jetpack Compose
- **Architecture Pattern**: MVVM (Model-View-ViewModel)
- **Network Requests**: Retrofit + OkHttp
- **Data Storage**: DataStore Preferences
- **Async Processing**: Kotlin Coroutines + Flow
- **Accessibility Service**: Android AccessibilityService API

## Development

### Build Requirements

- Android Studio Hedgehog or higher
- JDK 11 or higher
- Android SDK API 30 or higher

### Build Steps

```bash
# Clone the project
git clone https://github.com/xinzezhu/Open-AutoGLM-Android.git

# Open the project
cd Open-AutoGLM-Android

# Open the project in Android Studio and build
```

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Thanks to the [Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) project for inspiration
- Thanks to Zhipu AI for providing the AutoGLM model

## Support the Author

If this project is helpful to you, feel free to buy me a coffee ☕

![Pay](pay.jpg)

## Community

QQ Group: 734202636

---

**Note**: This project is for learning and research purposes only. When using this app, please comply with relevant laws and regulations and platform terms of use.

