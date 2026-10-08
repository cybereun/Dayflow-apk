package com.cybereun.dayflow.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

data class SyncStatus(val state:String="off",val error:String?=null,val lastSync:String?=null,val pending:Int=0,val groupId:String?=null)
data class SyncOffer(val code:String,val expires:Long)
data class SyncJoin(val digits:String,val expires:Long)
data class SyncDevice(val id:String,val name:String,val created:Long)

/** The Android owner of sync state. Plain planner data never leaves this class unencrypted. */
internal class SyncManager(private val context:Context,private val host:Host) {
    interface Host {
        suspend fun read(name:String):JSONObject?
        suspend fun write(name:String,value:JSONObject?)
        suspend fun names():List<String>
        suspend fun readState():JSONObject?
        suspend fun writeState(value:JSONObject)
        suspend fun preserveBeforeRiskyChange(reason:String)
        suspend fun onRemoteApplied()
    }
    private data class Credentials(val gid:String,val deviceId:String,val token:String,val key:String)
    private data class PendingOffer(val id:String,val expires:Long,val keys:SyncCore.PairKeys,var wrong:Int=0,var request:JSONObject?=null)
    private data class PendingJoin(val gid:String,val joinId:String,val deviceId:String,val token:String,val secret:String,val expires:Long)
    private val mutex=Mutex()
    private val secure=SecurePreferences(context)
    private val _status=MutableStateFlow(SyncStatus())
    val status=_status.asStateFlow()
    private var credentials:Credentials?=null
    private var db=emptyState()
    private var initialized=false
    private var syncing=false
    private var offer:PendingOffer?=null
    private var join:PendingJoin?=null
    private suspend fun <T> guarded(block:suspend ()->T):T { mutex.lock();return try{block()}finally{mutex.unlock()} }
    private fun emptyState()=JSONObject().put("v",1).put("node",UUID.randomUUID().toString()).put("clock",JSONArray().put(0).put(0).put("")).put("head",0).put("records",JSONObject()).put("lastSync",JSONObject.NULL)
    private fun records()=db.getJSONObject("records")
    private fun record(name:String,create:Boolean=true):JSONObject? { val found=records().optJSONObject(name);if(found!=null||!create)return found;return JSONObject().put("state",JSONObject()).put("seq",0).put("dirty",false).put("version",0).also{records().put(name,it)} }
    private fun currentStatus(state:String=_status.value.state,error:String?=_status.value.error):SyncStatus=SyncStatus(state,error,db.optString("lastSync","").ifBlank{null},records().keys().asSequence().count{records().getJSONObject(it).optBoolean("dirty",false)},credentials?.gid)
    private fun emit(state:String=_status.value.state,error:String?=_status.value.error){_status.value=currentStatus(state,error)}
    suspend fun initialize()=guarded { if(initialized)return@guarded;initialized=true
        try { credentials=secure.read()?.let { Credentials(it.getString("gid"),it.getString("deviceId"),it.getString("token"),it.getString("key")) } } catch(_:Exception) { emit("error","동기화 인증 정보를 읽지 못했습니다. 기존 기록은 보존했습니다.");return@guarded }
        val saved=host.readState()
        if(saved!=null)try { require(saved.optInt("v")==1 && saved.optJSONObject("records")!=null){"version"};saved.getJSONObject("records").keys().asSequence().forEach{SyncCore.validate(saved.getJSONObject("records").getJSONObject(it).getJSONObject("state"))};db=saved;normalizeLegacyLibraryMetadata() } catch(_:Exception) { emit("error","동기화 상태를 읽지 못했습니다. 기존 기록은 보존했습니다.");return@guarded }
        emit(if(credentials==null)"off" else "idle")
    }
    private suspend fun normalizeLegacyLibraryMetadata(){
        val entry=record("library",false)?:return
        val before=SyncCore.materialize(entry.getJSONObject("state")) as? JSONObject?:return
        val after=sanitize("library",before)?:return
        if(SyncCore.deepEquals(before,after))return
        entry.put("state",SyncCore.update(entry.getJSONObject("state"),before,after,tick())).put("dirty",true).put("version",entry.optLong("version",0)+1)
        persist()
    }
    private suspend fun persist(){host.writeState(db)}
    private fun tick(seed:Boolean=false):JSONArray { if(seed)return JSONArray().put(0).put(0).put(db.getString("node"));val old=db.getJSONArray("clock");val now=maxOf(System.currentTimeMillis(),old.getLong(0));val next=JSONArray().put(now).put(if(now==old.getLong(0))old.getLong(1)+1 else 0).put(db.getString("node"));db.put("clock",next);return next }
    private fun sanitize(name:String,value:JSONObject?):JSONObject? {
        if(name!="library"||value==null)return value
        val copy=JSONObject(value.toString());copy.remove("activeID")
        val books=copy.optJSONArray("books")?:return copy
        val filtered=JSONArray();for(i in 0 until books.length())books.optJSONObject(i)?.takeUnless{it.optBoolean("isSample",false)}?.let(filtered::put)
        copy.put("books",filtered);return copy
    }
    private fun change(name:String,before:JSONObject?,after:JSONObject?,seed:Boolean=false):JSONObject { val r=record(name)!!;val next=SyncCore.update(r.getJSONObject("state"),sanitize(name,before),sanitize(name,after),tick(seed));if(!SyncCore.deepEquals(next,r.getJSONObject("state"))){r.put("state",next).put("dirty",true).put("version",r.optLong("version",0)+1)};return r }
    private suspend fun capture(seed:Boolean) { val library=host.read("library")?:return;change("library",null,library,seed);for(book in library.optJSONArray("books")?.let{List(it.length()){i->it.getJSONObject(i)}}?:emptyList()){if(book.optBoolean("isSample",false))continue;val id=book.optString("id").uppercase();if(id.matches(Regex("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}")))host.read("book:$id")?.let{change("book:$id",null,it,seed)}};persist() }
    private suspend fun api(path:String,body:JSONObject?=null,token:String?=credentials?.token,method:String=if(body==null)"GET" else "POST",allowConflict:Boolean=false):JSONObject=withContext(Dispatchers.IO) {
        val connection=(URL(SERVER+path).openConnection() as HttpURLConnection).apply { requestMethod=method;connectTimeout=20000;readTimeout=20000;setRequestProperty("Accept","application/json");if(token!=null)setRequestProperty("Authorization","Bearer $token");if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json");outputStream.use{it.write(body.toString().toByteArray(StandardCharsets.UTF_8))}} }
        val code=connection.responseCode;val stream=if(code in 200..299)connection.inputStream else connection.errorStream;val text=stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty();val result=runCatching{JSONObject(text)}.getOrElse{JSONObject().put("error","invalid_response")};result.put("httpStatus",code);connection.disconnect()
        if(code !in 200..299 && !(allowConflict&&code==409))throw SyncFailure(code,message(code,result.optString("error")))
        result
    }
    private fun message(code:Int,error:String)=when(code){401->"이 기기의 동기화 인증이 해제되었습니다.";429->"연결 요청이 많습니다. 잠시 후 다시 시도해 주세요.";413->"동기화 저장 용량을 초과했습니다.";404,410->"연결 코드가 만료되었거나 그룹을 찾을 수 없습니다.";else->"동기화 서버 요청이 실패했습니다 ($code${if(error.isBlank())"" else ": $error"})."}
    private fun groupPath(s:String=""):String { val c=credentials?:error("동기화를 먼저 시작해 주세요.");return "/v1/groups/${c.gid}$s" }
    suspend fun create(name:String):Result<Unit> = runAction { initialize();require(credentials==null){"이미 동기화에 연결되어 있습니다."};host.preserveBeforeRiskyChange("sync-create");val response=api("/v1/groups",JSONObject().put("name",name.take(80)),null);credentials=Credentials(response.getString("gid"),response.getString("deviceId"),response.getString("token"),SyncCore.newKey());saveCredentials();capture(false);emit("idle");syncLocked() }
    /** Queue a local edit first. Foreground polling or the explicit Sync button transmits it afterwards. */
    suspend fun localChange(name:String,before:JSONObject?,after:JSONObject?){initialize();guarded {if(credentials==null||_status.value.state=="error")return@guarded;change(name,before,after);persist()} }
    suspend fun syncNow():Result<Unit> = runAction { initialize();guarded { if(credentials==null||syncing)return@guarded;syncing=true;emit("syncing",null) };try{syncLocked()}finally{guarded{syncing=false;if(_status.value.state=="syncing")emit("idle",null)}} }
    private suspend fun syncLocked() {
        val c=credentials?:return;val affected=linkedSetOf<String>();var since=db.optLong("head",0)
        try {
            for(page in 0 until 100){val response=api(groupPath("/changes?since=$since"));require(response.getLong("head")>=since){"서버 동기화 순번이 되돌아갔습니다. 원본을 보존하고 중단했습니다."};val rows=response.getJSONArray("records");for(i in 0 until rows.length()){ingest(rows.getJSONObject(i))?.let(affected::add)};if(rows.length()>0)since=rows.getJSONObject(rows.length()-1).getLong("seq");db.put("head",if(response.optBoolean("more",false))since else response.getLong("head"));persist();if(!response.optBoolean("more",false))break;if(page==99)error("기록이 너무 많습니다. 다시 동기화해 주세요.")}
            records().keys().asSequence().toList().forEach { name->repeat(12) { attempt->val r=record(name,false)?:return@repeat;if(!r.optBoolean("dirty",false))return@repeat;val version=r.optLong("version");val state=JSONObject(r.getJSONObject("state").toString());val recordId=SyncCore.rid(c.key,name);val payload=JSONObject().put("v",1).put("name",name).put("state",state);val response=api(groupPath("/records"),JSONObject().put("rid",recordId).put("base",r.optLong("seq")).put("ct",SyncCore.encrypt(c.key,c.gid,recordId,payload)),allowConflict=true);if(response.getInt("httpStatus")==409){response.optString("ct").takeIf{it.isNotBlank()}?.let{ingest(JSONObject().put("rid",recordId).put("seq",response.getLong("seq")).put("ct",it))?.let(affected::add)}}else{r.put("seq",response.getLong("seq"));if(r.optLong("version")==version)r.put("dirty",false)};persist();if(!r.optBoolean("dirty",false))return@repeat;if(attempt==11)error("동시 수정이 많습니다. 잠시 후 다시 동기화합니다.") } }
            affected.sortedBy{if(it=="library")1 else 0}.forEach { name->val r=record(name,false)?:return@forEach;val materialized=SyncCore.materialize(r.getJSONObject("state")) as? JSONObject;host.write(name,sanitize(name,materialized)) }
            if(affected.isNotEmpty())host.onRemoteApplied();db.put("lastSync",java.time.Instant.now().toString());persist();emit("idle",null)
        } catch(error:Exception) { val failure=if(error is SyncFailure&&(error.code==401||error.code==404))"removed" else "error";emit(failure,error.message?:"동기화를 완료하지 못했습니다.") }
    }
    private fun ingest(row:JSONObject):String? { val c=credentials?:return null;val rid=row.getString("rid");val payload=SyncCore.decrypt(c.key,c.gid,rid,row.getString("ct"));val name=payload.getString("name");require(payload.optInt("v")==1&&(name=="library"||name.matches(Regex("book:[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}")))){"호환되지 않는 동기화 기록입니다."};require(SyncCore.rid(c.key,name)==rid){"동기화 기록 이름이 올바르지 않습니다."};val incoming=payload.getJSONObject("state");SyncCore.validate(incoming);incoming.keys().asSequence().forEach { key->val stamp=incoming.getJSONObject(key).getJSONArray("s");require(stamp.getLong(0)<=System.currentTimeMillis()+86400000){"다른 기기의 시계가 하루 이상 앞서 있습니다."};if(SyncCore.compareStamp(stamp,db.getJSONArray("clock"))>0)db.put("clock",stamp) };val r=record(name)!!;if(row.getLong("seq")<=r.optLong("seq"))return null;val merged=SyncCore.merge(r.getJSONObject("state"),incoming);r.put("dirty",!SyncCore.deepEquals(merged,incoming)).put("state",merged).put("seq",row.getLong("seq")).put("version",r.optLong("version")+1);return name }
    suspend fun startOffer():Result<SyncOffer> = runAction { initialize();val c=credentials?:error("먼저 동기화를 시작해 주세요.");offer?.let{api(groupPath("/invites/${it.id}"),null,c.token,"DELETE")};val code="%08d".format(java.security.SecureRandom().nextInt(100000000));val keys=SyncCore.pairKeys();val response=api(groupPath("/invites"),JSONObject().put("codeHash",SyncCore.sha256("dayflow-pair-v1:$code")).put("publicKey",keys.publicKey));offer=PendingOffer(response.getString("id"),response.getLong("expires"),keys);SyncOffer(code,response.getLong("expires")) }
    suspend fun offerRequest():Result<String?> = runAction { val o=offer?:return@runAction null;val response=api(groupPath("/invites/${o.id}"));o.request=response.optJSONObject("request");o.request?.optString("name") }
    suspend fun offerStatus():Result<JSONObject> = runAction { val o=offer?:return@runAction JSONObject().put("request",JSONObject.NULL).put("expires",0);val response=api(groupPath("/invites/${o.id}"));o.request=response.optJSONObject("request");JSONObject().put("request",o.request?.let{JSONObject().put("name",it.optString("name"))}?:JSONObject.NULL).put("expires",response.optLong("expires",o.expires)) }
    suspend fun approveOffer(digits:String):Result<Unit> = runAction { val o=offer?:error("새 기기의 연결 요청을 기다려 주세요.");val request=o.request?:error("새 기기의 연결 요청을 기다려 주세요.");val c=credentials?:error("동기화를 먼저 시작해 주세요.");val key=SyncCore.pairSecret(o.keys.privateKey,request.getString("publicKey"),"${c.gid}/${request.getString("joinId")}");if(digits.replace(Regex("\\s"),"")!=SyncCore.digits(key)){o.wrong++;if(o.wrong>=3)cancelOffer();error("확인 숫자가 다릅니다. 새 기기의 숫자를 확인해 주세요.")};val wrapped=SyncCore.encrypt(key,c.gid,"pair:${request.getString("joinId")}",JSONObject().put("key",c.key));api(groupPath("/invites/${o.id}/approve"),JSONObject().put("joinId",request.getString("joinId")).put("wrappedKey",wrapped));offer=null }
    suspend fun cancelOffer():Result<Unit> = runAction { offer?.let{api(groupPath("/invites/${it.id}"),null,null,"DELETE")};offer=null }
    suspend fun joinGroup(code:String,name:String):Result<SyncJoin> = runAction { initialize();require(credentials==null){"이미 동기화에 연결되어 있습니다."};val clean=code.replace(Regex("[\\s-]"),"");require(clean.matches(Regex("\\d{8}"))){"8자리 연결 코드를 입력해 주세요."};val keys=SyncCore.pairKeys();val r=api("/v1/join",JSONObject().put("codeHash",SyncCore.sha256("dayflow-pair-v1:$clean")).put("publicKey",keys.publicKey).put("name",name.take(80)),null);val secret=SyncCore.pairSecret(keys.privateKey,r.getString("publicKey"),"${r.getString("gid")}/${r.getString("joinId")}");join=PendingJoin(r.getString("gid"),r.getString("joinId"),r.getString("deviceId"),r.getString("token"),secret,r.getLong("expires"));SyncJoin(SyncCore.digits(secret),r.getLong("expires")) }
    suspend fun joinStatus():Result<String> = runAction { val j=join?:return@runAction "none";api("/v1/groups/${j.gid}/joins/${j.joinId}",null,j.token).getString("state") }
    suspend fun acceptJoin():Result<Unit> = runAction { val j=join?:error("연결 요청이 없습니다.");val r=api("/v1/groups/${j.gid}/joins/${j.joinId}",null,j.token);require(r.getString("state")=="approved"){"기존 기기에서 먼저 승인해 주세요."};val key=SyncCore.decrypt(j.secret,j.gid,"pair:${j.joinId}",r.getString("wrappedKey")).getString("key");require(SyncCore.unb64(key).size==32){"그룹 키가 올바르지 않습니다."};host.preserveBeforeRiskyChange("sync-join");credentials=Credentials(j.gid,j.deviceId,j.token,key);saveCredentials();db=emptyState();capture(true);join=null;emit("idle");syncLocked() }
    suspend fun cancelJoin():Result<Unit> = runAction { join?.let{api("/v1/groups/${it.gid}/joins/${it.joinId}",null,it.token,"DELETE")};join=null }
    suspend fun recoveryCode():Result<String> = runAction { val c=credentials?:error("동기화를 먼저 시작해 주세요.");val secret=SyncCore.newKey();val auth=SyncCore.sha256("dayflow-recovery-auth:$secret");val wrapKey=SyncCore.recoveryKey(secret,c.gid);val wrapped=SyncCore.encrypt(wrapKey,c.gid,"recovery",JSONObject().put("key",c.key));api(groupPath("/recovery"),JSONObject().put("authHash",SyncCore.sha256(auth)).put("wrappedKey",wrapped));"DF1.${c.gid}.$secret" }
    suspend fun restore(code:String,name:String):Result<Unit> = runAction { initialize();require(credentials==null){"이미 동기화에 연결되어 있습니다."};val parts=Regex("^DF1\\.([0-9a-f-]{36})\\.([A-Za-z0-9_-]{43})$",RegexOption.IGNORE_CASE).matchEntire(code.trim())?:error("복구 코드 형식이 올바르지 않습니다.");val gid=parts.groupValues[1];val secret=parts.groupValues[2];val r=api("/v1/recover",JSONObject().put("gid",gid).put("auth",SyncCore.sha256("dayflow-recovery-auth:$secret")).put("name",name.take(80)),null);val groupKey=SyncCore.decrypt(SyncCore.recoveryKey(secret,gid),gid,"recovery",r.getString("wrappedKey")).getString("key");host.preserveBeforeRiskyChange("sync-restore");credentials=Credentials(r.getString("gid"),r.getString("deviceId"),r.getString("token"),groupKey);saveCredentials();db=emptyState();capture(true);emit("idle");syncLocked() }
    suspend fun devices():Result<List<SyncDevice>> = runAction { val rows=api(groupPath("/devices")).getJSONArray("devices");List(rows.length()){i->rows.getJSONObject(i).let{SyncDevice(it.getString("id"),it.optString("name"),it.optLong("created"))}} }
    suspend fun removeDevice(id:String):Result<Unit> = runAction { require(id.matches(Regex("[0-9a-f-]{36}",RegexOption.IGNORE_CASE))){"기기 ID가 올바르지 않습니다."};api(groupPath("/devices/$id"),null,null,"DELETE");if(id.equals(credentials?.deviceId,true))forget() }
    suspend fun leave():Result<Unit> = runAction { credentials?.let{api(groupPath("/devices/${it.deviceId}"),null,null,"DELETE")};forget() }
    private suspend fun saveCredentials(){val c=credentials?:return;secure.write(JSONObject().put("gid",c.gid).put("deviceId",c.deviceId).put("token",c.token).put("key",c.key))}
    private suspend fun forget(){credentials=null;offer=null;join=null;secure.clear();db=emptyState();host.writeState(db);emit("off",null)}
    private suspend fun <T> runAction(block:suspend()->T):Result<T> = try { Result.success(block()) } catch(error:Exception) { if(error !is SyncFailure)emit(if(credentials==null)"off" else "error",error.message?:"요청을 완료하지 못했습니다.");Result.failure(error) }
    private class SyncFailure(val code:Int,message:String):Exception(message)
    private class SecurePreferences(context:Context) {
        private val prefs=context.getSharedPreferences("dayflow_secure",Context.MODE_PRIVATE)
        private fun key():javax.crypto.SecretKey { val alias="dayflow-sync-credentials-v1";val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};return (store.getKey(alias,null) as? javax.crypto.SecretKey)?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey() }
        fun read():JSONObject? { val text=prefs.getString("credentials",null)?:return null;val all=Base64.decode(text,Base64.NO_WRAP);require(all.size>28){"credential"};val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,all.copyOfRange(0,12)));return JSONObject(String(cipher.doFinal(all.copyOfRange(12,all.size)),StandardCharsets.UTF_8)) }
        fun write(value:JSONObject) { val iv=ByteArray(12).also{java.security.SecureRandom().nextBytes(it)};val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(),GCMParameterSpec(128,iv));prefs.edit().putString("credentials",Base64.encodeToString(iv+cipher.doFinal(value.toString().toByteArray(StandardCharsets.UTF_8)),Base64.NO_WRAP)).commit() }
        fun clear(){prefs.edit().remove("credentials").commit()}
    }
    companion object { const val SERVER="https://dayflow-sync.cybereunny.workers.dev" }
}
