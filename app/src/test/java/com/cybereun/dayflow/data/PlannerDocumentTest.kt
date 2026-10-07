package com.cybereun.dayflow.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PlannerDocumentTest {
    private val date = LocalDate.of(2026,10,7)
    @Test fun tenMinuteSlotsCoverTwentyFourHours() {
        val p = PlannerDocument.empty().day(date)
        assertEquals(144,p.slots().size)
        assertEquals(0,p.minutes())
        val painted=p.paint(0,0).paint(143,1)
        assertEquals(20,painted.minutes())
        assertEquals("06:00",PlannerDocument.slotLabel(0))
        assertEquals("05:50",PlannerDocument.slotLabel(143))
    }
    @Test fun taskStatusMovesPostponedTaskToNextDay() {
        val p=PlannerDocument.empty().day(date).task("한국어 할 일")
        val id=p.tasks().single().id
        var next=p
        for (mark in listOf(1,2,3)) { next=next.cycleMark(id); assertEquals(mark,next.tasks().single().mark) }
        val postponed=next.cycleMark(id)
        assertTrue(postponed.tasks().isEmpty())
        val moved=postponed.day(date.plusDays(1)).tasks().single()
        assertEquals(id,moved.id)
        assertEquals("한국어 할 일",moved.text)
        assertEquals(0,moved.mark)
        assertTrue(postponed.day(date.plusDays(1)).deleteTask(id).tasks().isEmpty())
    }
    @Test fun oldPostponedTaskMovesOnItsNextStatusTap() {
        val initial=PlannerDocument.empty().day(date).task("이미 미룬 할 일")
        val id=initial.tasks().single().id
        val legacy=org.json.JSONObject(initial.json)
        legacy.getJSONObject("days").getJSONObject(date.toString()).getJSONArray("tasks").getJSONObject(0).put("mark",4)
        val moved=PlannerDocument(legacy.toString(),date).cycleMark(id)
        assertTrue(moved.tasks().isEmpty())
        assertEquals("이미 미룬 할 일",moved.day(date.plusDays(1)).tasks().single().text)
    }
    @Test fun editsKeepUnknownDesktopFields() {
        val raw="""{"days":{"2026-10-07":{"futureField":{"a":42},"memos":["","두번째","세번째"]}},"weeks":{},"prefs":{"futureSetting":true},"extension":[1,2]}"""
        val p=PlannerDocument(raw,date).setText("comment","오늘 코멘트").setText("memo","첫번째")
        val json=org.json.JSONObject(p.json)
        assertEquals(42,json.getJSONObject("days").getJSONObject("2026-10-07").getJSONObject("futureField").getInt("a"))
        assertEquals("두번째",json.getJSONObject("days").getJSONObject("2026-10-07").getJSONArray("memos").getString(1))
        assertTrue(json.getJSONObject("prefs").getBoolean("futureSetting"))
        assertEquals(2,json.getJSONArray("extension").length())
    }
    @Test fun weekBeginsMondayAcrossYearBoundary() {
        assertEquals(LocalDate.of(2025,12,29),PlannerDocument.weekStart(LocalDate.of(2026,1,1)))
    }
    @Test fun invalidCellsAndCategoriesAreRejected() {
        val p=PlannerDocument.empty()
        assertThrows(IllegalArgumentException::class.java){p.paint(-1,0)}
        assertThrows(IllegalArgumentException::class.java){p.paint(144,0)}
        assertThrows(IllegalArgumentException::class.java){p.paint(0,12)}
    }
    @Test fun malformedDocumentDoesNotBecomeAnEmptyPlanner() {
        assertThrows(Exception::class.java){PlannerDocument("{broken",date)}
    }
    @Test fun ddayCanBeGlobalOrLimitedToTheSelectedDate() {
        val global=PlannerDocument.empty().day(date).addDday("발표",LocalDate.of(2026,10,10))
        assertEquals("발표",global.day(date.plusDays(1)).ddays().single().title)
        val perDay=global.setDdaysPerDay(true).addDday("시험",LocalDate.of(2026,10,11))
        assertEquals("시험",perDay.ddays().single().title)
        assertTrue(perDay.day(date.plusDays(1)).ddays().isEmpty())
    }
    @Test fun libraryKeepsDesktopBookMetadataAndNeverDeletesLastBook() {
        val initial=PlannerLibrary.initial().first
        val added=initial.add("두 번째",LocalDate.of(2026,10,7))
        assertEquals(2,added.first.books().size)
        assertEquals("두 번째",added.first.active()!!.name)
        assertEquals("바꾼 이름",added.first.rename(added.second.id,"바꾼 이름").active()!!.name)
        assertThrows(IllegalArgumentException::class.java){initial.remove(initial.active()!!.id)}
    }
}
