package com.cybereun.dayflow

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** User-approved updates only. Never clears planner data or sync credentials. */
class AppUpdater(private val activity: MainActivity) {
    private data class Release(val tag: String, val url: String, val size: Long, val digest: String)
    private val preferences = activity.getSharedPreferences("dayflow_updates", 0)
    private var busy = false
    private var pendingInstall: File? = null
    private val maxBytes = 150L * 1024 * 1024

    fun check(manual: Boolean = false) {
        if (busy || activity.isFinishing) return
        if (!manual && System.currentTimeMillis() - preferences.getLong("checked", 0) < 6 * 60 * 60 * 1000L) return
        busy = true
        activity.lifecycleScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { latest() }
                preferences.edit().putLong("checked", System.currentTimeMillis()).apply()
                if (release == null) { if (manual) message("최신 버전을 사용하고 있습니다."); return@launch }
                if (!manual && preferences.getString("later", "") == release.tag && System.currentTimeMillis() - preferences.getLong("laterAt", 0) < 24 * 60 * 60 * 1000L) return@launch
                AlertDialog.Builder(activity).setTitle("Dayflow 업데이트")
                    .setMessage("새 버전 ${release.tag}이 있습니다.\n현재 버전: ${BuildConfig.VERSION_NAME}\n\nAPK를 다운로드한 뒤 Android 설치 화면에서 승인해 주세요. 기존 기록과 동기화 연결은 유지됩니다.")
                    .setNegativeButton("나중에") { _, _ -> preferences.edit().putString("later", release.tag).putLong("laterAt", System.currentTimeMillis()).apply() }
                    .setPositiveButton("다운로드") { _, _ -> download(release) }.show()
            } catch (e: Exception) { if (manual) message("업데이트를 확인하지 못했습니다. 인터넷 연결을 확인하고 다시 시도해 주세요.") }
            finally { busy = false }
        }
    }

    private fun latest(): Release? {
        val connection = URL("https://api.github.com/repos/cybereun/Dayflow-apk/releases?per_page=30").openConnection() as HttpURLConnection
        connection.connectTimeout = 15000; connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Dayflow-Android/${BuildConfig.VERSION_NAME}")
        try {
            check(connection.responseCode == 200) { "Release feed unavailable" }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            check(text.length <= 2_000_000) { "Release feed too large" }
            val releases = JSONArray(text)
            val signerSha256 = installedSignerSha256()
            UpdatePolicy.assetName(BuildConfig.VERSION_NAME, signerSha256)
                ?: error("현재 설치된 Dayflow 서명에 맞는 업데이트 경로가 없습니다. 앱을 삭제하지 말고 고객 지원에 문의해 주세요.")
            var best: Release? = null
            for (index in 0 until releases.length()) {
                val item = releases.getJSONObject(index)
                val tag = item.optString("tag_name")
                if (item.optBoolean("draft") || item.optBoolean("prerelease") || !UpdatePolicy.isNewer(tag, BuildConfig.VERSION_NAME)) continue
                val assetName = UpdatePolicy.assetName(tag, signerSha256) ?: continue
                val assets = item.optJSONArray("assets") ?: continue
                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.getJSONObject(assetIndex)
                    val url = asset.optString("browser_download_url")
                    val size = asset.optLong("size")
                    if (asset.optString("name") != assetName || !UpdatePolicy.allowedAsset(url) || size !in 1..maxBytes) continue
                    if (best == null || UpdatePolicy.isNewer(tag, best.tag)) best = Release(tag, url, size, asset.optString("digest"))
                }
            }
            return best
        } finally { connection.disconnect() }
    }

    private fun download(release: Release) {
        if (busy) return
        busy = true
        val dialog = AlertDialog.Builder(activity).setTitle("업데이트 다운로드")
            .setMessage("APK를 다운로드하고 확인하고 있습니다…").setCancelable(false).show()
        activity.lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val directory = File(activity.cacheDir, "updates").apply { mkdirs() }
                    val target = File(directory, "Dayflow-update.apk")
                    val partial = File(directory, "Dayflow-update.part")
                    val connection = URL(release.url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000; connection.readTimeout = 30000
                    try {
                        check(connection.responseCode == 200 && connection.url.protocol == "https") { "다운로드 서버에 연결하지 못했습니다." }
                        var count = 0L
                        val sha = MessageDigest.getInstance("SHA-256")
                        connection.inputStream.use { input -> partial.outputStream().use { output ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                val length = input.read(buffer); if (length < 0) break
                                count += length
                                check(count <= maxBytes && count <= release.size) { "APK 크기가 올바르지 않습니다." }
                                sha.update(buffer, 0, length); output.write(buffer, 0, length)
                            }
                        } }
                        check(count == release.size) { "다운로드가 완료되지 않았습니다. 다시 시도해 주세요." }
                        val hash = sha.digest().joinToString("") { "%02x".format(it) }
                        if (release.digest.startsWith("sha256:")) check(hash.equals(release.digest.removePrefix("sha256:"), true)) { "APK 무결성 검사에 실패했습니다." }
                        validate(partial, release.tag)
                        check(!target.exists() || target.delete()) { "이전 다운로드 파일을 정리하지 못했습니다." }
                        check(partial.renameTo(target)) { "APK를 저장하지 못했습니다." }
                        target
                    } finally { connection.disconnect(); partial.delete() }
                }
                pendingInstall = file
                installPending()
            } catch (e: Exception) { message(e.message ?: "다운로드에 실패했습니다. 다시 시도해 주세요.") }
            finally { busy = false; dialog.dismiss() }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedSignerSha256(): String? {
        val packageInfo = if (android.os.Build.VERSION.SDK_INT >= 28) {
            activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNATURES)
        }
        val signatures = if (android.os.Build.VERSION.SDK_INT >= 28) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            packageInfo.signatures?.toList().orEmpty()
        }
        if (signatures.size != 1) return null
        return MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun validate(file: File, tag: String) {
        val manager = activity.packageManager
        val apk = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
            ?: error("올바른 Android APK가 아닙니다.")
        val installed = manager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNATURES)
        check(apk.packageName == activity.packageName && apk.versionName == tag.removePrefix("v") && PackageInfoCompat.getLongVersionCode(apk) > PackageInfoCompat.getLongVersionCode(installed)) { "앱 ID 또는 업데이트 버전이 올바르지 않습니다." }
        val expected = installed.signatures?.map { it.toCharsString() }?.toSet()
        check(!expected.isNullOrEmpty() && apk.signatures?.map { it.toCharsString() }?.toSet() == expected) { "기존 Dayflow와 서명이 달라 설치를 중단했습니다." }
    }

    fun onResume() {
        if (pendingInstall != null && activity.packageManager.canRequestPackageInstalls()) installPending()
    }

    private fun installPending() {
        val file = pendingInstall ?: return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity).setTitle("업데이트 설치 허용")
                .setMessage("다음 설정에서 ‘이 출처의 앱 설치 허용’을 켠 뒤 Dayflow로 돌아와 주세요.")
                .setNegativeButton("취소") { _, _ -> pendingInstall = null }
                .setPositiveButton("설정 열기") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                }.show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
        pendingInstall = null
        activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun message(text: String) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }
}
