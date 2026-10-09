package com.cybereun.dayflow

import android.util.Base64
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** No raw credentials leave this bridge. Uses a domain-separated HKDF key. */
internal class InkSyncBridge(private val credentials: OriginalSyncCredentials) {
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun decode(value: String) = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP)
    fun call(action: String, argument: JSONObject): JSONObject {
        val envelope = credentials.read()
        check(envelope.isNull("problem")) { "동기화 인증 정보를 읽지 못했습니다. 원본은 보존했습니다." }
        val creds = envelope.optJSONObject("credentials") ?: run {
            if (action == "context") return JSONObject().put("gid",JSONObject.NULL).put("deviceId","local")
            error("동기화를 먼저 연결해 주세요.")
        }
        val gid = creds.getString("gid")
        if (action == "context") return JSONObject().put("gid", gid).put("deviceId", creds.getString("deviceId"))
        check(argument.optString("expectedGid") == gid) { "동기화 그룹이 변경되었습니다. 기존 필기는 원래 그룹에 보존합니다." }
        val codec=InkCodec(gid,creds.getString("key"))
        fun decrypt(record: JSONObject): JSONObject {
            return JSONObject(codec.decrypt(record.getString("rid"),record.getString("ct")))
        }
        var path = "https://dayflow-sync.cybereunny.workers.dev/original/v1/groups/${android.net.Uri.encode(gid)}/ink"
        var body: String? = null
        if (action == "pull") {
            val since = argument.getLong("since"); check(since >= 0) { "필기 동기화 위치가 올바르지 않습니다." }
            path += "?since=$since"
        } else if (action == "push") {
            val stroke = argument.getJSONObject("stroke")
            check(stroke.toString().toByteArray().size <= 262144 && stroke.getString("kind") in listOf("daily","weekly")) { "필기 전송 요청이 올바르지 않습니다." }
            val logical = "${stroke.getString("bookId")}|${stroke.getString("kind")}|${stroke.getString("date")}|${stroke.getString("id")}"
            val rid=codec.rid(logical)
            val ct=codec.encrypt(rid,stroke.toString())
            val baseSeq = argument.getLong("baseSeq"); check(baseSeq >= 0) { "필기 순번이 올바르지 않습니다." }
            path += "/$rid"; body = JSONObject().put("v",1).put("baseSeq",baseSeq).put("ct",ct).toString()
        } else error("허용되지 않은 필기 요청입니다.")
        val connection = URL(path).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000; connection.readTimeout = 25000
        connection.setRequestProperty("Authorization", "Bearer ${creds.getString("token")}")
        try {
            if (body != null) {
                connection.requestMethod = "PUT"; connection.doOutput = true
                connection.setRequestProperty("Content-Type","application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use {
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(65536)
                while (true) { val n = it.read(buffer); if (n < 0) break; check(output.size() + n <= 60*1024*1024) { "필기 응답이 너무 큽니다." }; output.write(buffer,0,n) }
                output.toString("UTF-8")
            } ?: error("필기 서버 응답을 읽지 못했습니다.")
            val result = JSONObject(text)
            if (code == 409 && action == "push") {
                val record = result.optJSONObject("record")
                return JSONObject().put("ok",false).put("record", if (record == null) JSONObject.NULL else JSONObject().put("seq",record.getLong("seq")).put("stroke",decrypt(record)))
            }
            check(code in 200..299) { "필기 동기화에 실패했습니다 ($code). 기록은 유지됩니다." }
            if (action == "pull") {
                val records = result.getJSONArray("records"); val decoded = JSONArray()
                for (i in 0 until records.length()) { val record = records.getJSONObject(i); decoded.put(JSONObject().put("seq",record.getLong("seq")).put("stroke",decrypt(record))) }
                return JSONObject().put("cursor",result.getLong("cursor")).put("more",result.getBoolean("more")).put("records",decoded)
            }
            return result
        } finally { connection.disconnect(); codec.close() }
    }
}
