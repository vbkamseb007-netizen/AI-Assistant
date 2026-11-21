# Advanced AI capabilities in this build

## Image generation
Use the `/image <prompt>` command. In Settings, configure **Image generation API endpoint** with the full URL for an OpenAI Images-compatible `POST` endpoint (normally ending in `/v1/images/generations`). The app sends `{prompt, n: 1, size: "1024x1024"}` and accepts a response containing `data[0].url` or `data[0].b64_json`. The app currently displays a returned URL; base64-only responses are reported but are not yet rendered as an in-chat image. The cloud API key is sent as a Bearer token when configured. Provider compatibility and pricing vary.

## OCR for Hindi, Malayalam and English
Attach an image and use `/ocr`. Configure **OCR API endpoint (JSON)** with a service that accepts `image_base64`, `mime_type`, and `languages` (`eng`, `hin`, `mal`) and returns JSON containing `text` or `result`. This is a pluggable OCR service interface, not an included offline OCR engine and not model training. Use HTTPS for remote endpoints. The regular chat image path still uses the configured vision model.

## Agent task mode
Use `/agent <task>` for a structured multi-step plan, execution reasoning, and self-check by the connected model. This is model-assisted task planning, not a fully autonomous computer-use agent: it cannot click arbitrary apps, run shell commands, install software, or control websites from this app.

## Minecraft pack validation
Run `python tools/validate_bedrock_pack.py path/to/pack.mcpack` from the project root. It checks ZIP integrity, root `manifest.json`, required metadata, UUID uniqueness, module types/versions and dependency UUID syntax. It does not test in Minecraft or prove compatibility with a particular Bedrock version. Java-to-Bedrock conversion remains a platform-specific reimplementation, not direct conversion.

## APK builds
The GitHub Actions workflow builds this AI Assistant project as a debug APK and uploads it as a workflow artifact. It does not compile arbitrary projects created by the AI inside the app. Review generated source before building. No code execution sandbox or remote arbitrary-project build service is included.
