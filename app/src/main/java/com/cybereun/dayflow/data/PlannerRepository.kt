package com.cybereun.dayflow.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Room-backed desktop-compatible library. Each planner book remains its own JSON record. */
class PlannerRepository(private val db:PlannerDatabase,context:Context?=null) {
    private val lock=Mutex()
    private val current=MutableStateFlow<PlannerDocument?>(null)
    private val currentLibrary=MutableStateFlow<PlannerLibrary?>(null)
    private val failure=MutableStateFlow<String?>(null)
    private val noSync=MutableStateFlow(SyncStatus())
    val document=current.asStateFlow()
    val library=currentLibrary.asStateFlow()
    val error=failure.asStateFlow()
    private val syncManager=context?.applicationContext?.let { app->SyncManager(app,object:SyncManager.Host {
        override suspend fun read(name:String)=readJson(name)
        override suspend fun write(name:String,value:JSONObject?){ writeRaw(name,value?.toString()) }
        override suspend fun names()=db.records().all().map{it.id}
        override suspend fun readState()=readJson(SYNC_STATE)
        override suspend fun writeState(value:JSONObject){writeRaw(SYNC_STATE,value.toString())}
        override suspend fun preserveBeforeRiskyChange(reason:String){preserveSnapshot(reason)}
        override suspend fun onRemoteApplied(){reloadFromStorage()}
    }) }
    val syncStatus:StateFlow<SyncStatus> = syncManager?.status?:noSync

    suspend fun load()=lock.withLock {
        try { loadCore();syncManager?.initialize();failure.value=null } catch(_:Exception) { failure.value="기록을 읽지 못했습니다. 원본은 보존했습니다. 앱을 다시 열어 확인해 주세요.";current.value=null;currentLibrary.value=null }
    }
    private suspend fun loadCore() {
        val stored=readJson(LIBRARY)
        val plannerLibrary=if(stored==null)migrateLegacy() else PlannerLibrary(stored.toString())
        val active=plannerLibrary.active()?:throw IllegalStateException("플래너가 없습니다.")
        val book=readJson(bookKey(active.id))?:throw IllegalStateException("선택한 플래너 기록을 찾을 수 없습니다.")
        currentLibrary.value=plannerLibrary;current.value=PlannerDocument(book.toString())
    }
    private suspend fun migrateLegacy():PlannerLibrary {
        val legacy=readJson(LEGACY_MAIN)
        val pair=PlannerLibrary.initial();val plannerLibrary=pair.first
        val book=legacy?.also{PlannerDocument(it.toString())}?:JSONObject(PlannerDocument.empty().json)
        db.withTransaction { if(legacy!=null)db.records().put(PlannerRecord("backup:$LEGACY_MAIN",legacy.toString()));db.records().put(PlannerRecord(LIBRARY,plannerLibrary.json));db.records().put(PlannerRecord(bookKey(pair.second.id),book.toString())) }
        return plannerLibrary
    }
    fun activeBook():BookInfo?=currentLibrary.value?.active()
    suspend fun mutate(edit:(PlannerDocument)->PlannerDocument)=lock.withLock {
        val before=current.value?:return@withLock
        try { val after=edit(before);val key=bookKey(activeBook()?.id?:return@withLock);writeRaw(key,after.json);current.value=after;failure.value=null;syncManager?.localChange(key,JSONObject(before.json),JSONObject(after.json)) } catch(_:Exception) { failure.value="저장하지 못했습니다. 입력을 유지하고 다시 시도해 주세요." }
    }
    suspend fun selectBook(id:String)=lock.withLock { val before=currentLibrary.value?:return@withLock;try { val after=before.activate(id);val active=after.active()?:error("플래너가 없습니다.");val book=readJson(bookKey(active.id))?:error("플래너 기록을 찾을 수 없습니다.");writeRaw(LIBRARY,after.json);currentLibrary.value=after;current.value=PlannerDocument(book.toString());syncManager?.localChange(LIBRARY,JSONObject(before.json),JSONObject(after.json)) } catch(_:Exception){failure.value="플래너를 열지 못했습니다. 기존 기록은 보존했습니다."} }
    suspend fun addBook(name:String)=lock.withLock { val before=currentLibrary.value?:return@withLock;try { val added=before.add(name);val after=added.first;val info=added.second;val empty=PlannerDocument.empty().json;writeRaw(bookKey(info.id),empty);writeRaw(LIBRARY,after.json);currentLibrary.value=after;current.value=PlannerDocument(empty);syncManager?.localChange(bookKey(info.id),null,JSONObject(empty));syncManager?.localChange(LIBRARY,JSONObject(before.json),JSONObject(after.json)) } catch(e:Exception){failure.value=e.message?:"새 플래너를 만들지 못했습니다."} }
    suspend fun renameBook(id:String,name:String)=lock.withLock { val before=currentLibrary.value?:return@withLock;try { val after=before.rename(id,name);writeRaw(LIBRARY,after.json);currentLibrary.value=after;syncManager?.localChange(LIBRARY,JSONObject(before.json),JSONObject(after.json)) } catch(e:Exception){failure.value=e.message?:"이름을 바꾸지 못했습니다."} }
    suspend fun deleteBook(id:String)=lock.withLock { val before=currentLibrary.value?:return@withLock;try { val source=readJson(bookKey(id));val after=before.remove(id);writeRaw(bookKey(id),null);writeRaw(LIBRARY,after.json);currentLibrary.value=after;val active=after.active()?:error("플래너가 없습니다.");current.value=PlannerDocument((readJson(bookKey(active.id))?:error("플래너 기록을 찾을 수 없습니다.")).toString());syncManager?.localChange(bookKey(id),source,null);syncManager?.localChange(LIBRARY,JSONObject(before.json),JSONObject(after.json)) } catch(e:Exception){failure.value=e.message?:"플래너를 삭제하지 못했습니다."} }
    suspend fun exportBackup(output:OutputStream)=lock.withLock {
        val stored=readJson(LIBRARY)?:throw IllegalStateException("내보낼 플래너가 없습니다.")
        ZipOutputStream(output).use { zip->fun entry(name:String,text:String){zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray(Charsets.UTF_8));zip.closeEntry()};entry("manifest.json",JSONObject().put("format","dayflow-backup").put("version",1).put("created",Instant.now().toString()).toString());entry("library.json",stored.toString());PlannerLibrary(stored.toString()).books().forEach { info->readJson(bookKey(info.id))?.let{entry("books/${info.id}.json",it.toString())} } }
    }
    suspend fun importBackup(input:InputStream)=lock.withLock {
        val files=linkedMapOf<String,String>();var total=0
        ZipInputStream(input).use { zip->while(true){val entry=zip.nextEntry?:break;require(!entry.isDirectory && entry.name.length<=180 && (entry.name=="library.json"||entry.name=="manifest.json"||entry.name.matches(Regex("books/[0-9A-Fa-f-]{36}\\.json")))){"백업 파일 구성이 올바르지 않습니다."};val bytes=zip.readBytes();total+=bytes.size;require(bytes.size<=4*1024*1024&&total<=20*1024*1024){"백업 파일이 너무 큽니다."};files[entry.name]=String(bytes,Charsets.UTF_8);zip.closeEntry()}}
        val libraryText=files["library.json"]?:error("library.json이 없는 백업입니다.");val imported=PlannerLibrary(libraryText);val books=imported.books();require(books.isNotEmpty()){"플래너가 없는 백업입니다."};val values=books.associate { info->val text=files["books/${info.id}.json"]?:error("${info.name} 기록이 빠져 있습니다.");PlannerDocument(text);bookKey(info.id) to text }
        preserveSnapshot("backup-import");val before=db.records().all().filter{it.id==LIBRARY||it.id.startsWith(BOOK_PREFIX)}.associate{it.id to it.json}
        db.withTransaction { db.records().put(PlannerRecord(LIBRARY,libraryText));values.forEach{(key,text)->db.records().put(PlannerRecord(key,text))};before.keys.filter{it.startsWith(BOOK_PREFIX)&&it !in values}.forEach{db.records().delete(it)} }
        loadCore();val after=(mapOf(LIBRARY to libraryText)+values);(before.keys+after.keys).forEach { key->syncManager?.localChange(key,before[key]?.let(::JSONObject),after[key]?.let(::JSONObject)) }
    }
    suspend fun syncNow()=syncManager?.syncNow()?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun createSync(name:String)=syncManager?.create(name)?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun startOffer()=syncManager?.startOffer()?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun offerRequest()=syncManager?.offerRequest()?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun approveOffer(digits:String)=syncManager?.approveOffer(digits)?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun cancelOffer()=syncManager?.cancelOffer()?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun joinGroup(code:String,name:String)=syncManager?.joinGroup(code,name)?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun joinStatus()=syncManager?.joinStatus()?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun acceptJoin()=syncManager?.acceptJoin()?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun cancelJoin()=syncManager?.cancelJoin()?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun recoveryCode()=syncManager?.recoveryCode()?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun restoreSync(code:String,name:String)=syncManager?.restore(code,name)?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun syncDevices()=syncManager?.devices()?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun removeSyncDevice(id:String)=syncManager?.removeDevice(id)?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    suspend fun leaveSync()=syncManager?.leave()?:Result.failure(IllegalStateException("동기화 기능을 시작할 수 없습니다."))
    private suspend fun readJson(id:String):JSONObject?=db.records().get(id)?.let{JSONObject(it.json)}
    private suspend fun writeRaw(id:String,text:String?){db.withTransaction { val before=db.records().get(id);if(before!=null&&id!=SYNC_STATE)db.records().put(PlannerRecord("backup:$id",before.json));if(text==null)db.records().delete(id) else db.records().put(PlannerRecord(id,text))}}
    private suspend fun preserveSnapshot(reason:String){val records=db.records().all().filter{it.id==LIBRARY||it.id.startsWith(BOOK_PREFIX)};val snapshot=JSONObject().put("reason",reason).put("created",Instant.now().toString()).put("records",JSONObject().also{o->records.forEach{o.put(it.id,it.json)}});db.records().put(PlannerRecord("recovery:${System.currentTimeMillis()}",snapshot.toString()))}
    private suspend fun reloadFromStorage(){lock.withLock { try{loadCore();failure.value=null}catch(_:Exception){failure.value="동기화한 기록을 안전하게 열지 못했습니다. 원본은 보존했습니다."} }}
    companion object { private const val LEGACY_MAIN="main";private const val LIBRARY="library";private const val BOOK_PREFIX="book:";private const val SYNC_STATE="sync-state";private fun bookKey(id:String)="$BOOK_PREFIX${id.uppercase()}" }
}
