package com.cybereun.dayflow

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** v1.0.17 engine credentials. Never reads or overwrites the retired protocol's keys. */
internal class OriginalSyncCredentials(context: Context) {
    private val preferences = context.getSharedPreferences("dayflow_original_sync_v17", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): JSONObject {
        val envelope = preferences.getString("credentials", null)
            ?: return JSONObject().put("credentials", JSONObject.NULL).put("secure", true).put("problem", JSONObject.NULL)
        return try {
            val value = JSONObject(envelope)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(value.getString("iv"), Base64.NO_WRAP)))
            val plain = cipher.doFinal(Base64.decode(value.getString("data"), Base64.NO_WRAP))
            JSONObject().put("credentials", JSONObject(String(plain, Charsets.UTF_8))).put("secure", true).put("problem", JSONObject.NULL)
        } catch (_: Exception) {
            // Do not silently reset a group or destroy unreadable credentials.
            JSONObject().put("credentials", JSONObject.NULL).put("secure", true).put("problem", "동기화 인증 정보를 읽지 못했습니다. 기존 기록은 보존했습니다.")
        }
    }
    @Synchronized fun write(text: String) {
        if (text == "null") {
            check(preferences.edit().remove("credentials").commit()) { "인증 정보를 삭제하지 못했습니다." }
            return
        }
        JSONObject(text)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val envelope = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
        check(preferences.edit().putString("credentials", envelope.toString()).commit()) { "인증 정보를 저장하지 못했습니다." }
    }
    fun settings(): String = preferences.getString("settings", "{}") ?: "{}"
    fun settings(text: String) {
        JSONObject(text)
        check(preferences.edit().putString("settings", text).commit()) { "동기화 설정을 저장하지 못했습니다." }
    }
    private companion object { const val ALIAS = "dayflow-original-sync-v17" }
}
