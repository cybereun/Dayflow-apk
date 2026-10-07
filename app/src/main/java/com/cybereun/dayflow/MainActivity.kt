package com.cybereun.dayflow

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.cybereun.dayflow.data.*
import com.cybereun.dayflow.ui.DayflowApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlannerViewModel(application:Application):AndroidViewModel(application) {
    private val database=Room.databaseBuilder(application,PlannerDatabase::class.java,"dayflow.db").build()
    val repository=PlannerRepository(database,application)
    val syncStatus=repository.syncStatus
    val library=repository.library
    private val noticeState=MutableStateFlow<String?>(null)
    val notice=noticeState.asStateFlow()
    init {viewModelScope.launch{repository.load();while(isActive){delay(15000);repository.syncNow().exceptionOrNull()?.let{noticeState.value=it.message?:"동기화에 실패했습니다."}}}}
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
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars=true
            isAppearanceLightNavigationBars=true
        }
        setContent {val model:PlannerViewModel=viewModel();DayflowApp(model)}
    }
}
