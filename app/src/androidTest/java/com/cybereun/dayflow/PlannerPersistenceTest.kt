package com.cybereun.dayflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.cybereun.dayflow.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlannerPersistenceTest {
    @Test fun recordsSurviveDatabaseReopen()= runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-${UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,PlannerDatabase::class.java,name).build()
        try {
            val repo=PlannerRepository(db);repo.load()
            repo.mutate { it.day(LocalDate.of(2026,10,7)).task("저장 확인").paint(143,0) }
            db.close();db=Room.databaseBuilder(context,PlannerDatabase::class.java,name).build()
            val reopened=PlannerRepository(db);reopened.load()
            val day=reopened.document.value!!.day(LocalDate.of(2026,10,7))
            assertEquals("저장 확인",day.tasks().single().text);assertEquals(10,day.minutes())
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun corruptedPlannerIsNotSilentlyOverwritten()= runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,PlannerDatabase::class.java).build()
        try {
            db.records().put(PlannerRecord("main","{broken"))
            val repo=PlannerRepository(db);repo.load()
            assertNotNull(repo.error.value);assertNull(repo.document.value)
            assertEquals("{broken",db.records().get("main")!!.json)
        } finally { db.close() }
    }
}
