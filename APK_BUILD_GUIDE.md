# Building an APK

## Local build
1. Open this project in Android Studio with JDK 17 and Android SDK 35 installed.
2. Sync Gradle.
3. Run `gradle :app:assembleDebug` from the project root, or use **Build > Build APK(s)**.
4. The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Cloud build with GitHub Actions
1. Create a private GitHub repository and upload the complete contents of this project.
2. Open the repository's **Actions** tab.
3. Select **Build Android APK** and choose **Run workflow**, or push to `main`.
4. When the workflow finishes, open its run and download the `AI-Assistant-debug-apk` artifact.

The workflow builds a debug APK. A release APK needs a signing key. Keep signing keys and passwords in GitHub Actions Secrets; never commit them to source control. The workflow does not upload your API keys.
