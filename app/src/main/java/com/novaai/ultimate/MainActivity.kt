package com.novaai.ultimate

import android.content.Intent
import android.provider.OpenableColumns
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Void = Color(0xFF070A14)
private val Panel = Color(0xFF111A2D)
private val Cyan = Color(0xFF55E7F2)
private val Purple = Color(0xFF9B83FF)
private val Ink = Color(0xFFEAF2FF)
private data class ChatMsg(val role: String, val text: String, val fileName: String = "")
private data class FileAttachment(val name: String, val mime: String, val base64: String = "", val text: String = "")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { NovaUltimate() } }
}

@Composable
private fun NovaUltimate() {
    val context = LocalContext.current
    val prefs = remember {
        PDFBoxResourceLoader.init(context)
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "ai_assistant_secure", masterKey, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    var localUrl by remember { mutableStateOf(prefs.getString("local_url", "http://10.0.2.2:11434/v1") ?: "") }
    var localModel by remember { mutableStateOf(prefs.getString("local_model", "qwen2.5:7b") ?: "") }
    var cloudUrl by remember { mutableStateOf(prefs.getString("cloud_url", "") ?: "") }
    var cloudModel by remember { mutableStateOf(prefs.getString("cloud_model", "") ?: "") }
    var cloudKey by remember { mutableStateOf(prefs.getString("cloud_key", "") ?: "") }
    var searchUrl by remember { mutableStateOf(prefs.getString("search_url", "") ?: "") }
    var imageUrl by remember { mutableStateOf(prefs.getString("image_url", "") ?: "") }
    var ocrUrl by remember { mutableStateOf(prefs.getString("ocr_url", "") ?: "") }
    var mode by remember { mutableStateOf(prefs.getString("mode", "Hybrid") ?: "Hybrid") }
    var searchEnabled by remember { mutableStateOf(prefs.getBoolean("search", true)) }
    var memoryEnabled by remember { mutableStateOf(prefs.getBoolean("memory_enabled", true)) }
    var memory by remember { mutableStateOf(prefs.getString("memory", "") ?: "") }
    var settings by remember { mutableStateOf(false) }
    var memoryDialog by remember { mutableStateOf(false) }
    var voiceReplies by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<FileAttachment?>(null) }
    val history = remember {
        mutableStateListOf<ChatMsg>().apply {
            try { val a = JSONArray(prefs.getString("history", "[]")); for (i in 0 until a.length()) { val o = a.getJSONObject(i); add(ChatMsg(o.getString("role"), o.getString("text"), o.optString("file"))) } } catch (_: Exception) { }
        }
    }
    val scope = rememberCoroutineScope()
    val tts = remember { TextToSpeech(context) {} }
    fun persist() {
        val a = JSONArray(); history.takeLast(80).forEach { a.put(JSONObject().put("role", it.role).put("text", it.text).put("file", it.fileName)) }
        prefs.edit().putString("history", a.toString()).apply()
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val name = queryDisplayName(context, uri)
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf() }
                if (bytes.size > 8 * 1024 * 1024) error = "File exceeds the 8 MB limit."
                else if (mime.startsWith("image/")) { attachment = FileAttachment(name, mime, Base64.encodeToString(bytes, Base64.NO_WRAP)); error = "" }
                else {
                    val extracted = withContext(Dispatchers.IO) { extractDocumentText(name, mime, bytes) }
                    attachment = FileAttachment(name, mime, text = extracted.take(50000))
                    error = if (extracted.startsWith("[Could not extract")) extracted else ""
                }
            } catch (e: Exception) { error = "Could not read file: ${e.message}" }
        }
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { draft = (draft + " " + it).trim() }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Void, surface = Panel, primary = Cyan, secondary = Purple, onBackground = Ink, onSurface = Ink)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF152748), Void)))) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(Cyan, Purple)), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.AllInclusive, null, tint = Void) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("AI ASSISTANT", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("HYBRID AI  •  $mode MODE", color = Cyan, fontSize = 9.sp, letterSpacing = 1.5.sp) }
                    IconButton(onClick = { memoryDialog = true }) { Icon(Icons.Default.Psychology, "Memory", tint = Purple) }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Default.Tune, "Settings", tint = Cyan) }
                    IconButton(onClick = { history.clear(); persist(); error = "" }) { Icon(Icons.Default.DeleteOutline, "Clear chat", tint = Ink) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Chip("LOCAL MODEL", localModel.isNotBlank()); Chip("WEB SEARCH", searchEnabled && searchUrl.isNotBlank()); Chip("MEMORY", memoryEnabled) }
                Spacer(Modifier.height(12.dp))
                if (history.isEmpty()) {
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Hub, null, tint = Cyan, modifier = Modifier.size(62.dp)); Spacer(Modifier.height(14.dp))
                        Text("Your intelligence hub.", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Think  •  Research  •  Create  •  Learn", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(Modifier.height(20.dp))
                        Quick("Minecraft Bedrock development") { draft = "Act as a senior Minecraft Bedrock Add-On developer. Target Bedrock version 26.30.32 unless I specify another version. Create complete, importable behavior/resource pack files. Include correct manifest UUIDs, modules, dependencies, paths and JSON. Validate pack structure and explain how to test it: " }
                        Spacer(Modifier.height(7.dp)); Quick("Java ↔ Bedrock converter") { draft = "Convert the following Minecraft Java Edition code/mod/plugin into the closest compatible Bedrock Edition add-on. First identify unsupported APIs and gameplay differences. Do not pretend Java code runs directly on Bedrock. Provide complete replacement files, manifest, scripts and compatibility notes: " }
                        Spacer(Modifier.height(7.dp)); Quick("Read Hindi / Malayalam / English image") { draft = "Read every visible word in this image. Detect Hindi (Devanagari), Malayalam and English. Preserve the original script, separate the languages, then translate into the language I request. Mark unclear words instead of guessing: " }
                        Spacer(Modifier.height(7.dp)); Quick("Create an Android APK project") { draft = "Create a complete Android Studio project that can build into a working APK. Include all source files, Gradle configuration, permissions, resources, build instructions and a GitHub Actions workflow. Explain any required API keys or services: " }
                        Spacer(Modifier.height(7.dp)); Quick("Generate an image") { draft = "/image Create: " }
                        Spacer(Modifier.height(7.dp)); Quick("OCR image (Hindi / Malayalam / English)") { draft = "/ocr Read all text in the attached image. Preserve Hindi, Malayalam and English scripts." }
                        Spacer(Modifier.height(7.dp)); Quick("Run an agent task") { draft = "/agent Plan and complete this task in clear stages. Check your work and report anything you could not do: " }
                        Spacer(Modifier.height(7.dp)); Quick("Research a topic") { draft = "Research this topic and include useful source URLs: " }
                        Spacer(Modifier.height(7.dp)); Quick("Write and debug code") { draft = "Help me write and debug: " }
                        Spacer(Modifier.height(7.dp)); Quick("Explain simply") { draft = "Explain this in simple steps: " }
                        Spacer(Modifier.height(18.dp)); Text("Connect a local or cloud AI model in Settings to start.", color = Color.Gray, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(history) { msg -> Bubble(msg) }; if (busy) item { Text("AI IS THINKING…", color = Cyan, fontSize = 11.sp) }
                    }
                }
                attachment?.let { file ->
                    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, null, tint = Cyan); Text(file.name, color = Ink, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 1); IconButton(onClick = { attachment = null }) { Icon(Icons.Default.Close, "Remove", tint = Ink) }
                    }; Spacer(Modifier.height(6.dp))
                }
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF9292), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pickFile.launch(arrayOf("*/*")) }) { Icon(Icons.Default.AttachFile, "Attach file", tint = Cyan) }
                    IconButton(onClick = { val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()) }; try { speech.launch(i) } catch (_: Exception) { error = "Speech recognition unavailable." } }) { Icon(Icons.Default.Mic, "Voice input", tint = Purple) }
                    BasicTextField(draft, { draft = it }, Modifier.weight(1f).padding(horizontal = 6.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 14.sp), decorationBox = { inner -> Box { if (draft.isEmpty()) Text("Ask AI Assistant anything…", color = Color.Gray); inner() } })
                    IconButton(onClick = { voiceReplies = !voiceReplies }) { Icon(if (voiceReplies) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "Voice replies", tint = if (voiceReplies) Cyan else Color.Gray) }
                    IconButton(enabled = !busy && (draft.isNotBlank() || attachment != null), onClick = {
                        val file = attachment; val prompt = draft.trim()
                        if (prompt.isNotBlank() || file != null) {
                            val userText = buildString { append(prompt.ifBlank { "Please analyse the attachment." }); if (!file?.text.isNullOrBlank()) append("\n\nATTACHED FILE ${file!!.name}:\n${file.text}") }
                            history.add(ChatMsg("user", userText, file?.name ?: "")); persist(); draft = ""; attachment = null; error = ""; busy = true
                            scope.launch {
                                try {
                                    val answer = withContext(Dispatchers.IO) {
                                        when {
                                            prompt.startsWith("/image ", true) -> generateImage(imageUrl, cloudKey, prompt.substringAfter(' ').trim())
                                            prompt.startsWith("/ocr", true) -> {
                                                val imageFile = file ?: throw IllegalStateException("Attach an image first.")
                                                if (imageFile.base64.isBlank()) throw IllegalStateException("OCR requires an image attachment.")
                                                if (ocrUrl.isBlank()) throw IllegalStateException("Set an OCR service endpoint in Settings. The endpoint must accept JSON fields image_base64, mime_type and languages.")
                                                runOcr(ocrUrl, imageFile)
                                            }
                                            else -> {
                                                val web = if (searchEnabled && searchUrl.isNotBlank() && looksLikeSearch(userText)) searchWeb(searchUrl, userText) else ""
                                                val taskHistory = if (prompt.startsWith("/agent ", true)) history.toList().dropLast(1) + ChatMsg("user", "Act as a task agent. Break the request into steps, complete as much as possible using your available model and configured search, verify your reasoning, and clearly list limitations. Do not claim to operate apps, browse pages, run code or build APKs unless a connected tool actually did it. Task: ${prompt.substringAfter(' ').trim()}") else history.toList()
                                                askModel(mode, localUrl, localModel, cloudUrl, cloudModel, cloudKey, taskHistory, memory.takeIf { memoryEnabled }, web, file)
                                            }
                                        }
                                    }
                                    history.add(ChatMsg("assistant", answer)); persist(); if (voiceReplies) tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "nova_reply")
                                } catch (e: Exception) { error = e.message ?: "Request failed." } finally { busy = false }
                            }
                        }
                    }) { Icon(Icons.Default.Send, "Send", tint = if (!busy && (draft.isNotBlank() || attachment != null)) Cyan else Color.Gray) }
                }
                Text("HYBRID INTELLIGENCE SYSTEM", color = Color.Gray, fontSize = 9.sp, letterSpacing = 1.2.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
            }
            if (settings) AlertDialog(onDismissRequest = { settings = false }, containerColor = Panel, title = { Text("SYSTEM CONFIGURATION", color = Ink) }, text = {
                Column(Modifier.heightIn(max = 560.dp).width(320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AI routing", color = Cyan, fontSize = 12.sp)
                    listOf("Hybrid", "Local only", "Cloud only").forEach { option -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == option, { mode = option }); Text(option, color = Ink, fontSize = 12.sp) } }
                    Field("Local API base URL", localUrl) { localUrl = it }; Field("Local model name", localModel) { localModel = it }
                    Field("Cloud API base URL", cloudUrl) { cloudUrl = it }; Field("Cloud model name", cloudModel) { cloudModel = it }; Field("Cloud API key", cloudKey) { cloudKey = it }
                    Field("SearXNG JSON search endpoint", searchUrl) { searchUrl = it }; Field("Image generation API endpoint", imageUrl) { imageUrl = it }; Field("OCR API endpoint (JSON)", ocrUrl) { ocrUrl = it }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(searchEnabled, { searchEnabled = it }); Text("Enable web search", color = Ink, fontSize = 12.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(memoryEnabled, { memoryEnabled = it }); Text("Use memory notes", color = Ink, fontSize = 12.sp) }
                    Text("Hybrid tries local first, then cloud if local fails. Cloud services may charge fees.", color = Color.LightGray, fontSize = 10.sp)
                }
            }, confirmButton = { TextButton(onClick = { prefs.edit().putString("local_url", localUrl.trim()).putString("local_model", localModel.trim()).putString("cloud_url", cloudUrl.trim()).putString("cloud_model", cloudModel.trim()).putString("cloud_key", cloudKey.trim()).putString("search_url", searchUrl.trim()).putString("image_url", imageUrl.trim()).putString("ocr_url", ocrUrl.trim()).putString("mode", mode).putBoolean("search", searchEnabled).putBoolean("memory_enabled", memoryEnabled).apply(); settings = false }) { Text("SAVE", color = Cyan) } }, dismissButton = { TextButton(onClick = { settings = false }) { Text("CLOSE", color = Ink) } })
            if (memoryDialog) AlertDialog(onDismissRequest = { memoryDialog = false }, containerColor = Panel, title = { Text("AI MEMORY", color = Ink) }, text = { Column { Text("Add useful details AI Assistant should use in future chats. Stored on this device.", color = Color.LightGray, fontSize = 11.sp); Spacer(Modifier.height(8.dp)); OutlinedTextField(memory, { memory = it }, modifier = Modifier.fillMaxWidth().height(170.dp), label = { Text("Memory notes") }) } }, confirmButton = { TextButton(onClick = { prefs.edit().putString("memory", memory).apply(); memoryDialog = false }) { Text("SAVE", color = Cyan) } }, dismissButton = { TextButton(onClick = { memory = ""; prefs.edit().putString("memory", "").apply(); memoryDialog = false }) { Text("CLEAR", color = Ink) } })
        }
    }
}

@Composable private fun Chip(label: String, active: Boolean) { Surface(color = if (active) Color(0x3327D7E5) else Color(0x223F4B62), shape = RoundedCornerShape(30.dp), border = BorderStroke(.7.dp, if (active) Cyan else Color.Gray)) { Text(label, color = if (active) Cyan else Color.Gray, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) } }
@Composable private fun Quick(label: String, onClick: () -> Unit) { OutlinedButton(onClick = onClick, border = BorderStroke(1.dp, Purple), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text(label, color = Ink, fontSize = 11.sp) } }
@Composable private fun Bubble(msg: ChatMsg) { val user = msg.role == "user"; Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) { Column(Modifier.widthIn(max = 330.dp).background(if (user) Color(0xFF25395D) else Panel, RoundedCornerShape(15.dp)).padding(12.dp)) { Text(if (user) "YOU" else "AI ASSISTANT", color = if (user) Cyan else Purple, fontSize = 9.sp, fontWeight = FontWeight.Bold); if (msg.fileName.isNotBlank()) Text("Attachment: ${msg.fileName}", color = Cyan, fontSize = 10.sp); Spacer(Modifier.height(5.dp)); Text(msg.text, color = Ink, fontSize = 13.sp, lineHeight = 19.sp) } } }
@Composable private fun Field(label: String, value: String, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label, fontSize = 10.sp) }, singleLine = true) }

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    var name = "attachment"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) name = c.getString(0) ?: name
    }
    return name
}

private fun extractDocumentText(name: String, mime: String, bytes: ByteArray): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return try {
        when {
            ext == "pdf" || mime == "application/pdf" -> {
                PDDocument.load(bytes).use { doc -> PDFTextStripper().getText(doc).take(50000) }
            }
            ext in listOf("docx", "pptx", "xlsx") -> extractOfficeXml(bytes, ext)
            ext in listOf("txt", "md", "csv", "json", "xml", "html", "htm", "kt", "java", "py", "js", "ts", "css", "log") || mime.startsWith("text/") -> bytes.toString(Charsets.UTF_8).take(50000)
            else -> "[Could not extract text from $name. Supported: PDF, DOCX, PPTX, XLSX and common text/code files.]"
        }
    } catch (e: Exception) { "[Could not extract text from $name: ${e.message ?: "unsupported or damaged file"}]" }
}

private fun extractOfficeXml(bytes: ByteArray, ext: String): String {
    val wanted = when (ext) {
        "docx" -> Regex("word/document\\.xml|word/header[0-9]*\\.xml|word/footer[0-9]*\\.xml")
        "pptx" -> Regex("ppt/slides/slide[0-9]+\\.xml|ppt/notesSlides/notesSlide[0-9]+\\.xml")
        else -> Regex("xl/sharedStrings\\.xml|xl/worksheets/sheet[0-9]+\\.xml")
    }
    val out = StringBuilder()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && wanted.matches(entry.name)) {
                val xml = zip.readBytes().toString(Charsets.UTF_8)
                val values = Regex("<t(?:\\s+[^>]*)?>(.*?)</t>|<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
                values.findAll(xml).forEach { m ->
                    val raw = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
                    val decoded = raw.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                        .replace("&quot;", "\"").replace("&apos;", "'")
                    if (decoded.isNotBlank()) out.append(decoded).append(' ')
                }
                out.append('\n')
            }
            zip.closeEntry()
            if (out.length > 50000) break
        }
    }
    return out.toString().take(50000).ifBlank { "[No readable text found in this Office document.]" }
}

private fun generateImage(endpoint: String, key: String, prompt: String): String {
    require(endpoint.isNotBlank()) { "Set an OpenAI-compatible image generation endpoint in Settings." }
    require(prompt.isNotBlank()) { "Add an image prompt after /image." }
    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 180000; doOutput = true
        setRequestProperty("Content-Type", "application/json")
        if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer $key")
    }
    try {
        val body = JSONObject().put("prompt", prompt).put("n", 1).put("size", "1024x1024")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode; val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("Image API HTTP $code: ${raw.take(250)}")
        val data = JSONObject(raw).optJSONArray("data") ?: throw IllegalStateException("Image API response has no data array.")
        val item = data.optJSONObject(0) ?: throw IllegalStateException("Image API returned no image.")
        val b64 = item.optString("b64_json")
        val url = item.optString("url")
        return when {
            url.isNotBlank() -> "Image generated successfully. Open image: $url"
            b64.isNotBlank() -> "Image generated successfully (base64 image data received). This provider returns image data instead of a public URL; configure a provider that returns a URL to preview it in chat."
            else -> "Image API completed, but returned no supported image URL or b64_json field."
        }
    } finally { conn.disconnect() }
}

private fun runOcr(endpoint: String, file: FileAttachment): String {
    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 120000; doOutput = true
        setRequestProperty("Content-Type", "application/json")
    }
    try {
        val body = JSONObject().put("image_base64", file.base64).put("mime_type", file.mime).put("languages", JSONArray(listOf("eng", "hin", "mal")))
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode; val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("OCR service HTTP $code: ${raw.take(250)}")
        val json = JSONObject(raw)
        return json.optString("text").ifBlank { json.optString("result") }.ifBlank { "OCR service returned no text. Expected JSON field 'text' or 'result'." }
    } finally { conn.disconnect() }
}

private fun looksLikeSearch(s: String): Boolean { val t = s.lowercase(); return listOf("search", "research", "latest", "today", "current", "news", "source", "website", "internet", "2026").any { t.contains(it) } }
private fun searchWeb(endpoint: String, query: String): String {
    val u = endpoint.trimEnd('/') + "?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&format=json"
    val c = (URL(u).openConnection() as HttpURLConnection).apply { connectTimeout = 12000; readTimeout = 15000; setRequestProperty("Accept", "application/json") }
    try { if (c.responseCode !in 200..299) return ""; val a = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: JSONArray(); return buildString { append("WEB SEARCH RESULTS. Use these as leads and include source URLs:\n"); for (i in 0 until minOf(6, a.length())) { val r = a.getJSONObject(i); append("- ").append(r.optString("title")).append("\n").append(r.optString("content")).append("\n").append(r.optString("url")).append("\n") } } } finally { c.disconnect() }
}
private fun askModel(mode: String, localUrl: String, localModel: String, cloudUrl: String, cloudModel: String, cloudKey: String, history: List<ChatMsg>, memory: String?, web: String, file: FileAttachment?): String {
    val system = buildString {
        append("""You are AI Assistant, a multilingual, multimodal software-development assistant. Be accurate, practical and honest. Reply in the language the user uses: English, Hindi (Devanagari) or Malayalam. You can explain and translate between all three. When an image is attached, inspect it carefully. For text in images, transcribe Hindi, Malayalam and English in their original scripts, preserve line order, distinguish uncertain characters, and translate only when requested. Do not claim perfect OCR; ask for a clearer image if needed. Image reading requires a vision-capable model.

MINECRAFT SPECIALIST MODE: Be highly skilled in Minecraft Bedrock Add-Ons and Java Edition development. Handle Bedrock behavior packs, resource packs, manifests, JSON, entities, components, events, functions, loot tables, recipes, animations, Molang, Script API, and Java mods/plugins/datapacks using the correct platform APIs. Before writing code, identify edition, exact game version, loader/API version (such as Fabric, Forge, NeoForge, Paper, Spigot, or Bukkit), and target device if missing. The user's usual Bedrock target is beta 26.30.32, but confirm when version-specific APIs matter. Java mods/plugins cannot simply be copied to Bedrock: explain unsupported features and implement the closest native Bedrock equivalent. For conversions, provide a feature mapping, every required file, folder tree, dependencies, setup and test steps. Never invent API methods; flag uncertain version compatibility. For Bedrock packs, validate manifest format_version, header name/description/UUID/version, module type/UUID/version, dependencies, min_engine_version where applicable, unique UUIDs, archive root layout and ZIP integrity. Prevent the 'Failed to import: Unknown pack name' error. Do not claim in-game testing unless actually performed.

ANDROID/APK SPECIALIST MODE: Generate complete Android Studio projects, not isolated snippets, when asked for an APK. Include Kotlin/Java source, Gradle files, manifest, resources, permissions, dependency versions, build steps and tests. Prefer reproducible builds. Provide a GitHub Actions workflow or local Gradle commands to compile APKs. Do not claim an APK exists or builds unless a build tool actually produced and verified it. Explain signing requirements for release APKs. Treat generated code as untrusted: do not run arbitrary code without user approval and sandboxing.

For all code: state assumptions, provide complete files with exact paths, explain how to run/test, and distinguish implemented features from planned ones. Never claim that a model has been trained or a task completed when only instructions/prompts were added. For current information, rely on supplied search results and include their URLs.""")
        if (!memory.isNullOrBlank()) append("\nUSER MEMORY NOTES:\n").append(memory)
        if (web.isNotBlank()) append("\n").append(web)
    }
    fun request(base: String, model: String, key: String): String {
        require(base.isNotBlank() && model.isNotBlank()) { "Configure the model URL and model name in Settings." }
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.takeLast(30).forEachIndexed { index, m ->
            if (index == history.takeLast(30).lastIndex && m.role == "user" && file != null && file.base64.isNotBlank()) {
                val content = JSONArray().put(JSONObject().put("type", "text").put("text", m.text)).put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:${file.mime};base64,${file.base64}")))
                messages.put(JSONObject().put("role", "user").put("content", content))
            } else messages.put(JSONObject().put("role", m.role).put("content", m.text))
        }
        val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.65)
        val conn = (URL(base.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 20000; readTimeout = 180000; doOutput = true; setRequestProperty("Content-Type", "application/json"); if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer $key") }
        try { conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }; val code = conn.responseCode; val stream = if (code in 200..299) conn.inputStream else conn.errorStream; val raw = stream.bufferedReader().use { it.readText() }; if (code !in 200..299) throw IllegalStateException("HTTP $code: ${raw.take(220)}"); return JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content") } finally { conn.disconnect() }
    }
    return when (mode) { "Local only" -> request(localUrl, localModel, ""); "Cloud only" -> request(cloudUrl, cloudModel, cloudKey); else -> try { request(localUrl, localModel, "") } catch (e: Exception) { if (cloudUrl.isBlank() || cloudModel.isBlank()) throw IllegalStateException("Local model failed (${e.message}). Configure cloud fallback in Settings."); request(cloudUrl, cloudModel, cloudKey) } }
}
