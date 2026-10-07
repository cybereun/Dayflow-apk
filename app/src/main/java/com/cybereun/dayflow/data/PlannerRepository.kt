package com.cybereun.dayflow.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlannerRepository(private val db:PlannerDatabase) {
    private val lock=Mutex()
    private val current=MutableStateFlow<PlannerDocument?>(null)
    private val failure=MutableStateFlow<String?>(null)
    val document=current.asStateFlow()
    val error=failure.asStateFlow()
    suspend fun load()=lock.withLock {
        try {
            val record=db.records().get("main")
            val parsed=record?.let{PlannerDocument(it.json)}?:PlannerDocument.empty()
            if(record==null)db.records().put(PlannerRecord("main",parsed.json))
            current.value=parsed;failure.value=null
        } catch(e:Exception){ failure.value="기록을 읽지 못했습니다. 원본은 보존했습니다. 앱을 다시 열어 확인해 주세요." }
    }
    suspend fun mutate(edit:(PlannerDocument)->PlannerDocument)=lock.withLock {
        val before=current.value?:return@withLock
        try {
            val after=edit(before)
            db.withTransaction {
                db.records().put(PlannerRecord("backup",before.json))
                db.records().put(PlannerRecord("main",after.json))
            }
            current.value=after;failure.value=null
        } catch(e:Exception){failure.value="저장하지 못했습니다. 입력을 유지하고 다시 시도해 주세요."}
    }
}
