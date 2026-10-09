package com.cybereun.dayflow

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.cybereun.dayflow.data.PlannerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Narrow Android adapter for the exact desktop renderer bundled in assets. */
class AndroidDayflowBridge(
    private val activity: MainActivity,
    private val repository: PlannerRepository,
    private val webView: WebView,
) {
    private val shared = activity.getSharedPreferences("dayflow_shared", Context.MODE_PRIVATE)
    private val tutorials = activity.getSharedPreferences("dayflow_tutorials", Context.MODE_PRIVATE)
    private val originalSync = OriginalSyncCredentials(activity)
    private val inkSync = InkSyncBridge(originalSync)
    @JavascriptInterface fun inkRequest(requestId: String, action: String, argument: String) {
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { inkSync.call(action, JSONObject(argument)) }
                .getOrElse { JSONObject().put("__error", it.message ?: "필기 동기화에 실패했습니다. 원본은 유지합니다.") } }
            webView.evaluateJavascript("window.__dayflowInkResolve?.(${JSONObject.quote(requestId)},$result)",null)
        }
    }
    @JavascriptInterface fun originalCredentials(): String = encoded { originalSync.read() }
    @JavascriptInterface fun originalSetCredentials(text: String): String = encoded { originalSync.write(text); JSONObject().put("ok", true) }
    @JavascriptInterface fun originalSettings(): String = encoded { originalSync.settings() }
    @JavascriptInterface fun originalSetSettings(text: String): String = encoded { originalSync.settings(text); JSONObject().put("ok", true) }
    @JavascriptInterface fun originalDeviceInfo(): String = encoded { JSONObject().put("computerName", android.os.Build.MODEL).put("hostname", android.os.Build.MODEL).put("platform", "android") }
    @JavascriptInterface fun originalSnapshot(): String = encoded { repository.rendererBackupNow() ?: error("백업을 만들지 못했습니다.") }

    @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME
    @JavascriptInterface fun checkForUpdates() { activity.checkForUpdates() }
    @JavascriptInterface fun remoteApplyVersion(): Int = repository.remoteApplyVersion.value

    @JavascriptInterface fun readLibrary(): String = encoded {
        val value = repository.rendererReadLibrary()
        if (value == null) JSONObject().put("status", "missing") else JSONObject().put("status", "ok").put("text", value.toString())
    }
    @JavascriptInterface fun writeLibrary(text: String): String = encoded {
        repository.rendererWriteLibrary(text)
        JSONObject().put("ok", true)
    }
    @JavascriptInterface fun readBook(id: String): String = encoded {
        val value = repository.rendererReadBook(id)
        if (value == null) JSONObject().put("status", "missing") else JSONObject().put("status", "ok").put("text", value.toString())
    }
    @JavascriptInterface fun writeBook(id: String, text: String): String = encoded {
        repository.rendererWriteBook(id, text)
        JSONObject().put("ok", true)
    }
    @JavascriptInterface fun deleteBook(id: String): String = encoded {
        repository.rendererDeleteBook(id)
        JSONObject().put("ok", true)
    }
    @JavascriptInterface fun listBooks(): String = encoded {
        JSONArray().also { rows -> repository.rendererBookIds().forEach { id -> rows.put(id) } }
    }
    @JavascriptInterface fun dataDir(): String = JSONObject.quote("Android 앱 내부 저장소")
    @JavascriptInterface fun backupNow(): String = encoded { repository.rendererBackupNow()?.let{JSONObject().put("path",it)} }
    @JavascriptInterface fun listBackups(): String = encoded { repository.rendererBackups() }
    @JavascriptInterface fun readBackupBook(day:String,id:String):String = encoded { repository.rendererReadBackupBook(day,id) }
    @JavascriptInterface fun bookFileInfo(id: String): String = encoded { repository.rendererBookInfo(id) }

    @JavascriptInterface fun syncStatus(): String = encoded { statusJson() }
    @JavascriptInterface fun syncCall(action: String, argumentJson: String): String = encoded {
        repository.load()
        val argument = JSONTokener(argumentJson).nextValue()
        when (action) {
            "status" -> statusJson()
            "create" -> { repository.createSync(argument as? String ?: "Android 기기").getOrThrow(); statusJson() }
            "sync" -> { repository.syncNow().getOrThrow(); JSONObject().put("ok", true) }
            "offer" -> repository.startOffer().getOrThrow().let { JSONObject().put("code", it.code).put("expires", it.expires) }
            "offerStatus" -> repository.offerStatus().getOrThrow()
            "approve" -> { repository.approveOffer(argument as? String ?: "").getOrThrow(); JSONObject().put("ok", true) }
            "cancelOffer" -> { repository.cancelOffer().getOrThrow(); JSONObject().put("ok", true) }
            "join" -> objectArg(argument).let { values ->
                val result = repository.joinGroup(values.optString("code"), values.optString("name", "Android 기기")).getOrThrow()
                JSONObject().put("digits", result.digits).put("expires", result.expires)
            }
            "joinStatus" -> JSONObject().put("state", repository.joinStatus().getOrThrow())
            "accept" -> { repository.acceptJoin().getOrThrow(); JSONObject().put("ok", true) }
            "cancelJoin" -> { repository.cancelJoin().getOrThrow(); JSONObject().put("ok", true) }
            "devices" -> {
                val rows = JSONArray()
                repository.syncDevices().getOrThrow().forEach { row -> rows.put(JSONObject().put("id", row.id).put("name", row.name).put("created", row.created)) }
                JSONObject().put("devices", rows)
            }
            "remove" -> { repository.removeSyncDevice(argument as? String ?: "").getOrThrow(); JSONObject().put("ok", true) }
            "leave", "wipe" -> { repository.leaveSync().getOrThrow(); JSONObject().put("ok", true) }
            "recovery" -> JSONObject().put("code", repository.recoveryCode().getOrThrow())
            "restore" -> objectArg(argument).let { values ->
                repository.restoreSync(values.optString("code"), values.optString("name", "Android 기기")).getOrThrow()
                statusJson()
            }
            else -> error("잘못된 동기화 요청입니다.")
        }
    }

    @JavascriptInterface fun sharedGet(): String = synchronized(shared) {
        shared.getString("state", "{}") ?: "{}"
    }
    @JavascriptInterface fun sharedSet(patchJson: String): String = encoded {
        val state = JSONObject(shared.getString("state", "{}") ?: "{}")
        val patch = JSONObject(patchJson)
        val keys = patch.keys()
        while (keys.hasNext()) { val key = keys.next(); state.put(key, patch.get(key)) }
        shared.edit().putString("state", state.toString()).apply()
        state
    }
    @JavascriptInterface fun tutorialFlags(): String = JSONObject(tutorials.getString("flags", "{}") ?: "{}").toString()
    @JavascriptInterface fun tutorialSetFlags(patchJson: String): String = encoded {
        val flags = JSONObject(tutorials.getString("flags", "{}") ?: "{}")
        val patch = JSONObject(patchJson);val keys = patch.keys()
        while (keys.hasNext()) { val key = keys.next(); flags.put(key, patch.get(key)) }
        tutorials.edit().putString("flags", flags.toString()).apply()
        JSONObject().put("ok", true)
    }
    @JavascriptInterface fun openExternal(url: String): String = encoded {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" || uri.scheme == "http") { "외부 링크 형식이 올바르지 않습니다." }
        activity.runOnUiThread { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        JSONObject().put("ok", true)
    }
    @JavascriptInterface fun closeApp(): String = encoded {
        activity.runOnUiThread { activity.finish() }
        JSONObject().put("ok", true)
    }
    @JavascriptInterface fun requestTextFile(action: String, name: String, text: String, requestId: String) {
        activity.requestTextFile(action, name, text, requestId)
    }
    @JavascriptInterface fun printCurrentPage(requestJson: String): String = encoded {
        activity.runOnUiThread {
            val manager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            manager?.print(
                "Dayflow",
                webView.createPrintDocumentAdapter("Dayflow"),
                PrintAttributes.Builder().setColorMode(PrintAttributes.COLOR_MODE_COLOR).build(),
            )
        }
        JSONObject().put("ok", true)
    }

    private suspend fun statusJson(): JSONObject {
        repository.load()
        val status = repository.syncStatus.value
        return JSONObject()
            .put("state", status.state)
            .put("error", status.error ?: JSONObject.NULL)
            .put("computerName", android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android 기기")
            .put("group", status.groupId?.let { JSONObject().put("id", it).put("deviceId", "") } ?: JSONObject.NULL)
            .put("lastSync", status.lastSync ?: JSONObject.NULL)
            .put("pending", status.pending)
            .put("server", "https://dayflow-sync.cybereunny.workers.dev")
    }

    private fun objectArg(value: Any?): JSONObject = value as? JSONObject ?: JSONObject()
    private fun encoded(block: suspend () -> Any?): String = try {
        val value = runBlocking(Dispatchers.IO) { block() }
        when (value) {
            null -> "null"
            is JSONObject -> value.toString()
            is JSONArray -> value.toString()
            is String -> JSONObject.quote(value)
            is Number, is Boolean -> value.toString()
            else -> JSONObject.quote(value.toString())
        }
    } catch (error: Exception) {
        JSONObject().put("__error", error.message ?: "Dayflow 요청을 완료하지 못했습니다.").toString()
    }
}
