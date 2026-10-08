package com.cybereun.dayflow

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.cybereun.dayflow.data.*
import com.cybereun.dayflow.ui.DayflowApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

class PlannerViewModel(application:Application):AndroidViewModel(application) {
    private val database=Room.databaseBuilder(application,PlannerDatabase::class.java,"dayflow.db").build()
    val repository=PlannerRepository(database,application)
    val syncStatus=repository.syncStatus
    val library=repository.library
    private val noticeState=MutableStateFlow<String?>(null)
    val notice=noticeState.asStateFlow()
    init {viewModelScope.launch{repository.load();runCatching{repository.rendererBackupNow()};while(isActive){delay(6*60*60*1000L);runCatching{repository.rendererBackupNow()}}}}
    fun edit(block:(PlannerDocument)->PlannerDocument){viewModelScope.launch{repository.mutate(block)}}
    fun selectBook(id:String){viewModelScope.launch{repository.selectBook(id)}}
    fun addBook(name:String){viewModelScope.launch{repository.addBook(name)}}
    fun renameBook(id:String,name:String){viewModelScope.launch{repository.renameBook(id,name)}}
    fun deleteBook(id:String){viewModelScope.launch{repository.deleteBook(id)}}
    fun syncNow(){viewModelScope.launch{repository.syncNow().exceptionOrNull()?.let{noticeState.value=it.message?:"동기화에 실패했습니다."}}}
    fun createSync(name:String){viewModelScope.launch{repository.createSync(name).fold({noticeState.value="암호화 동기화를 시작했습니다."},{noticeState.value=it.message?:"동기화를 시작하지 못했습니다."})}}
    fun joinSync(code:String,name:String,onReady:(SyncJoin)->Unit){viewModelScope.launch{repository.joinGroup(code,name).fold(onReady,{noticeState.value=it.message?:"연결 요청을 시작하지 못했습니다."})}}
    fun acceptJoin(){viewModelScope.launch{repository.acceptJoin().fold({noticeState.value="기기 연결과 초기 동기화를 완료했습니다."},{noticeState.value=it.message?:"연결을 완료하지 못했습니다."})}}
    fun startOffer(onReady:(SyncOffer)->Unit){viewModelScope.launch{repository.startOffer().fold(onReady,{noticeState.value=it.message?:"연결 코드를 만들지 못했습니다."})}}
    fun offerRequest(onResult:(String?)->Unit){viewModelScope.launch{repository.offerRequest().fold(onResult,{noticeState.value=it.message?:"연결 요청을 확인하지 못했습니다."})}}
    fun approveOffer(digits:String){viewModelScope.launch{repository.approveOffer(digits).fold({noticeState.value="새 기기를 승인했습니다."},{noticeState.value=it.message?:"기기를 승인하지 못했습니다."})}}
    fun recovery(onReady:(String)->Unit){viewModelScope.launch{repository.recoveryCode().fold(onReady,{noticeState.value=it.message?:"복구 코드를 만들지 못했습니다."})}}
    fun restore(code:String,name:String){viewModelScope.launch{repository.restoreSync(code,name).fold({noticeState.value="복구 연결과 동기화를 완료했습니다."},{noticeState.value=it.message?:"복구하지 못했습니다."})}}
    fun leaveSync(){viewModelScope.launch{repository.leaveSync().fold({noticeState.value="이 기기의 연결을 해제했습니다."},{noticeState.value=it.message?:"연결을 해제하지 못했습니다."})}}
    fun exportBackup(uri:Uri){viewModelScope.launch{runCatching{getApplication<Application>().contentResolver.openOutputStream(uri)?.use{repository.exportBackup(it)}?:error("백업 파일을 열지 못했습니다.")}.fold({noticeState.value="백업 ZIP을 저장했습니다."},{noticeState.value=it.message?:"백업을 저장하지 못했습니다."})}}
    fun importBackup(uri:Uri){viewModelScope.launch{runCatching{getApplication<Application>().contentResolver.openInputStream(uri)?.use{repository.importBackup(it)}?:error("백업 파일을 열지 못했습니다.")}.fold({noticeState.value="백업을 가져왔습니다. 이전 기록은 앱 내부 복구 사본으로 보존했습니다."},{noticeState.value=it.message?:"백업을 가져오지 못했습니다."})}}
    fun clearNotice(){noticeState.value=null}
    override fun onCleared(){database.close();super.onCleared()}
}
class MainActivity:ComponentActivity() {
    private lateinit var plannerView:WebView
    private lateinit var saveTextLauncher:ActivityResultLauncher<String>
    private lateinit var openTextLauncher:ActivityResultLauncher<Array<String>>
    private data class PendingText(val name:String,val text:String,val id:String)
    private val pendingLock=Any()
    private val pendingText=mutableMapOf<String,PendingText>()
    private var activeTextRequest:String?=null

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        saveTextLauncher=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->finishTextRequest(uri,true)}
        openTextLauncher=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->finishTextRequest(uri,false)}
        androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars=true
            isAppearanceLightNavigationBars=true
        }
        val model=ViewModelProvider(this)[PlannerViewModel::class.java]
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        plannerView=WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(252,251,247))
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            settings.allowFileAccessFromFileURLs=false
            settings.allowUniversalAccessFromFileURLs=false
            settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient=WebChromeClient()
            val assets=androidx.webkit.WebViewAssetLoader.Builder()
                .addPathHandler("/assets/",androidx.webkit.WebViewAssetLoader.AssetsPathHandler(this@MainActivity)).build()
            webViewClient=object:WebViewClient(){
                override fun shouldInterceptRequest(view:WebView,request:android.webkit.WebResourceRequest):android.webkit.WebResourceResponse? = assets.shouldInterceptRequest(request.url)
                override fun shouldOverrideUrlLoading(view:WebView,request:android.webkit.WebResourceRequest):Boolean =
                    request.url.scheme!="https" || request.url.host!="appassets.androidplatform.net"
            }
            addJavascriptInterface(AndroidDayflowBridge(this@MainActivity,model.repository,this),"AndroidDayflow")
        }
        val container=android.widget.FrameLayout(this)
        container.addView(plannerView,android.widget.FrameLayout.LayoutParams(-1,-1))
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(container){view,insets->
            val safe=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        setContentView(container)
        androidx.core.view.ViewCompat.requestApplyInsets(container)
        plannerView.loadUrl("https://appassets.androidplatform.net/assets/desktop/index.html?view=main")
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){
            override fun handleOnBackPressed(){plannerView.evaluateJavascript("window.__dayflowAndroidBack?.()",null)}
        })
    }

    fun requestTextFile(action:String,name:String,text:String,requestId:String){
        val item=PendingText(name.ifBlank{"Dayflow-data.json"},text,requestId)
        synchronized(pendingLock){pendingText[requestId]=item}
        runOnUiThread {
            activeTextRequest=requestId
            runCatching {
                if(action=="save")saveTextLauncher.launch(File(item.name).name.ifBlank{"Dayflow-data.json"})
                else if(action=="open")openTextLauncher.launch(arrayOf("application/json","text/plain","application/octet-stream"))
                else error("지원하지 않는 파일 요청입니다.")
            }.onFailure { finishTextRequest(null,action=="save",it.message) }
        }
    }

    private fun finishTextRequest(uri:Uri?,save:Boolean,failureMessage:String?=null){
        val requestId=activeTextRequest?:return
        activeTextRequest=null
        val item=synchronized(pendingLock){pendingText.remove(requestId)}?:return
        val response=runCatching {
            if(uri==null)JSONObject().put("ok",false).put("canceled",true)
            else if(save){
                contentResolver.openOutputStream(uri)?.use{it.write(item.text.toByteArray(Charsets.UTF_8))}?:error("파일을 열지 못했습니다.")
                JSONObject().put("ok",true).put("path",uri.toString()).put("name",File(item.name).name)
            }else{
                val text=contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:error("파일을 읽지 못했습니다.")
                JSONObject().put("ok",true).put("name",uri.lastPathSegment?:"Dayflow-data.json").put("text",text)
            }
        }.getOrElse { JSONObject().put("ok",false).put("error",failureMessage?:it.message?:"파일 작업을 완료하지 못했습니다.") }
        plannerView.evaluateJavascript("window.__dayflowAndroidResolve?.(${JSONObject.quote(requestId)},$response)",null)
    }

    override fun onResume(){super.onResume();if(::plannerView.isInitialized)plannerView.onResume()}
    override fun onPause(){if(::plannerView.isInitialized)plannerView.onPause();super.onPause()}
    override fun onDestroy(){if(::plannerView.isInitialized){plannerView.removeJavascriptInterface("AndroidDayflow");plannerView.destroy()};super.onDestroy()}
}
