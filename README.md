# AI Assistant — Android project (development build)

This is an Android Studio source project, not a prebuilt APK. It uses Kotlin and Jetpack Compose.

## Included in this revision
- Local, cloud, and local-first hybrid chat through OpenAI-compatible `/v1/chat/completions` APIs.
- Encrypted on-device preferences for API credentials, recent chat history, and editable memory notes.
- Optional SearXNG JSON search integration.
- Android speech recognition input and Android text-to-speech output.
- Image attachments passed to a vision-capable model.
- PDF text extraction and basic DOCX, PPTX, XLSX, text, and code-file extraction.
- Dedicated starter prompts for Hindi, Malayalam, and English image transcription/translation.
- Minecraft specialist instructions for Bedrock Add-Ons and Java mods/plugins/datapacks, including edition-aware conversion guidance and Bedrock manifest/ZIP validation reminders.
- Android project/APK specialist instructions and a GitHub Actions workflow to build this AI Assistant project as a debug APK.
- Dark Compose interface.

## Important limits
**The AI model has not been trained or fine-tuned.** This revision adds specialist system instructions and prompt shortcuts. Actual skill depends on the model connected in Settings. For image text understanding, configure a multimodal/vision model. Malayalam, Hindi, and English OCR accuracy depends on that model; this app does not include a dedicated offline Malayalam OCR engine. A text-only model cannot read image content.

This project does not include a native video understanding pipeline, full browser automation, arbitrary code execution, offline multilingual OCR engine, or secure remote arbitrary-project build service. The added image-generation, OCR, and task-agent modes are integrations with the limitations described in ADVANCED_FEATURES.md. The GitHub Actions workflow in this project builds **this AI Assistant app**; to build a different app generated in chat, its complete source project must be placed in a GitHub repository and given its own compatible build workflow. No APK was compiled or device-tested during this update.

Java Edition mods/plugins and Bedrock Add-Ons use different APIs and formats. Conversion is a reimplementation, not direct source-code translation. Always provide the exact Minecraft version and mod loader/API. The default Bedrock target in the specialist instructions is beta 26.30.32, but verify version-specific compatibility before use.

## Local AI setup
1. Install Ollama on a computer: https://ollama.com
2. Pull a model suitable for your hardware. For image understanding, choose a vision-capable model supported by your Ollama version.
3. Configure Ollama to accept LAN connections and allow port 11434 through the computer firewall.
4. Connect the Android device and computer to the same Wi-Fi.
5. In Settings, set Local API base URL to `http://YOUR-PC-LAN-IP:11434/v1` and Local model name to the exact installed model name.
6. `http://10.0.2.2:11434/v1` is for the Android emulator only.

For cloud fallback, configure a provider supporting OpenAI-compatible chat completions. Cloud services may charge or impose limits. Never share API keys or commit them to Git.

## Optional web search
Run SearXNG with JSON output enabled and enter its `/search` endpoint in Settings. Search results are reference leads, not guaranteed fact-checking.

## Build locally
- Open this `AI_Assistant` folder in Android Studio.
- Use JDK 17 and Android SDK 35.
- Allow Gradle sync, then run `gradle :app:assembleDebug` or use **Build > Build APK(s)**.
- The debug APK output is `app/build/outputs/apk/debug/app-debug.apk`.

## Build with GitHub Actions
See [APK_BUILD_GUIDE.md](APK_BUILD_GUIDE.md). The workflow `.github/workflows/android-apk.yml` builds a debug APK and uploads it as a workflow artifact. Release APKs require signing credentials stored as GitHub Actions Secrets.

## Security and testing
Encrypted preferences use Android Keystore-backed encryption. This does not replace server-side security or device security. HTTP is enabled for local-network development; use HTTPS for remote services. Review generated code before building or running it. The source ZIP can be checked for archive integrity, but that does not prove the Android app compiles or works on a device. Run Android Studio/CI build and real-device tests before release.

## Advanced integrations in v5
- `/image <prompt>` calls a configured OpenAI Images-compatible generation endpoint. Configure the full endpoint URL in Settings. URL responses are shown as links; base64-only image responses are detected but not yet displayed in-chat.
- `/ocr` sends an attached image to a configured OCR JSON endpoint with language hints `eng`, `hin`, and `mal`. This is a pluggable remote OCR integration, not offline OCR and not model training.
- `/agent <task>` asks the connected model to plan, work through, and self-check a task. It is not an autonomous computer-use agent and cannot control other apps or execute arbitrary code.
- `tools/validate_bedrock_pack.py` checks Bedrock `.mcpack` ZIP integrity and manifest structure. It cannot perform in-game testing.
- The CI workflow verifies the debug APK output exists before uploading it as an artifact.

See [ADVANCED_FEATURES.md](ADVANCED_FEATURES.md) for endpoint formats and limitations.
