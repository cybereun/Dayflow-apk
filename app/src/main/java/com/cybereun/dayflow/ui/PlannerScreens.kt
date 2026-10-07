package com.cybereun.dayflow.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybereun.dayflow.data.PlannerDocument
import java.time.LocalDate
import java.time.temporal.ChronoUnit

typealias Edit=((PlannerDocument)->PlannerDocument)->Unit

@Composable fun DailyScreen(p:PlannerDocument,accent:Color,wide:Boolean,edit:Edit,openTime:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=if(wide)28.dp else 18.dp).padding(bottom=24.dp)) {
        if(wide)Row(horizontalArrangement=Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1.7f)) {DailyHeader(p,accent);DailyNotes(p,accent,edit);TaskList(p,accent,edit);Memo(p,accent,edit)}
            Column(Modifier.weight(1f)) {Total(p,accent);PaperHeading("TIMETABLE",accent,"◷");Timetable(p,accent,edit)}
        } else {
            DailyHeader(p,accent);Total(p,accent)
            TextButton(openTime,Modifier.fillMaxWidth().background(Color(0xFFE6EDFA),RoundedCornerShape(12.dp))){Text("◷  TIMETABLE · 시간표 펼치기",color=Ink)}
            DailyNotes(p,accent,edit);TaskList(p,accent,edit);Memo(p,accent,edit)
        }
        Text("dayflow ♡",color=accent,modifier=Modifier.align(Alignment.End).padding(top=22.dp),fontSize=22.sp)
    }
}
@Composable private fun DailyHeader(p:PlannerDocument,accent:Color) {
    PaperHeading("DATE",accent,"✿")
    Text(p.date.toString().replace("-","")+"  "+p.date.dayOfWeek.name.take(3),fontSize=if(p.date.year<10000)30.sp else 24.sp,color=Ink,fontWeight=FontWeight.Bold,modifier=Modifier.background(accent.copy(alpha=.16f),RoundedCornerShape(8.dp)).padding(horizontal=9.dp,vertical=4.dp))
}
@Composable private fun Total(p:PlannerDocument,accent:Color) {
    PaperHeading("TOTAL TIME",accent,"◷")
    Text(PlannerDocument.duration(p.minutes()),fontSize=40.sp,fontWeight=FontWeight.Bold,color=accent,modifier=Modifier.padding(bottom=8.dp))
}
@Composable private fun DailyNotes(p:PlannerDocument,accent:Color,edit:Edit) {
    PaperHeading("COMMENT",accent,"♧")
    PaperInput(p.text("comment"),{v->edit{it.setText("comment",v)}},"오늘의 한마디",accent,lines=2)
}
@Composable private fun Memo(p:PlannerDocument,accent:Color,edit:Edit) {
    PaperHeading("MEMO",accent,"✎")
    PaperInput(p.text("memo"),{v->edit{it.setText("memo",v)}},"자유롭게 메모해요",accent,lines=4)
}
@Composable private fun TaskList(p:PlannerDocument,accent:Color,edit:Edit) {
    PaperHeading("TASKS",accent,"☑")
    var draft by rememberSaveable(p.date.toString()){mutableStateOf("")}
    p.tasks().forEach{task->
        Row(Modifier.fillMaxWidth().heightIn(min=50.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("•",color=accent,fontSize=22.sp,modifier=Modifier.padding(end=8.dp))
            TextField(task.text,{v->edit{it.editTask(task.id,v)}},Modifier.weight(1f),textStyle=LocalTextStyle.current.copy(fontSize=16.sp),colors=TextFieldDefaults.colors(unfocusedContainerColor=Color.Transparent,focusedContainerColor=Color.Transparent,unfocusedIndicatorColor=Muted.copy(alpha=.3f),focusedIndicatorColor=accent))
            TextButton({edit{it.cycleMark(task.id)}},Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics{contentDescription="${task.text} 상태 변경"}){Text(listOf("□","○","△","×","→")[task.mark.coerceIn(0,4)],color=accent,fontSize=24.sp)}
            TextButton({edit{it.deleteTask(task.id)}},Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics{contentDescription="${task.text} 삭제"}){Text("−",color=Muted)}
        }
    }
    Row(verticalAlignment=Alignment.CenterVertically) {
        TextField(draft,{draft=it},Modifier.weight(1f).testTag("new-task"),placeholder={Text("할 일을 적어 주세요",color=Muted)},singleLine=true,colors=TextFieldDefaults.colors(unfocusedContainerColor=Color.Transparent,focusedContainerColor=Color.Transparent))
        TextButton({if(draft.isNotBlank()){val text=draft;edit{it.task(text)};draft=""}},Modifier.semantics{contentDescription="할 일 추가"}){Text("+",color=accent,fontSize=25.sp)}
    }
    Text("□ 미표시   ○ 완료   △ 일부   × 못함   → 미룸",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
}

@Composable fun WeeklyScreen(p:PlannerDocument,accent:Color,wide:Boolean,edit:Edit,openDay:(LocalDate)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        PaperHeading("MY GOAL",accent,"⚑")
        PaperInput(p.week().optString("goal"),{v->edit{it.weekText("goal",v)}},"이번 주 목표",accent,lines=1)
        PaperHeading("REVIEW OF THE WEEK",accent,"☆")
        Row { (1..5).forEach{value->TextButton({edit{it.stars(if(p.week().optInt("stars")==value)0 else value)}}){Text(if(value<=p.week().optInt("stars"))"★" else "☆",color=accent,fontSize=24.sp)} } }
        PaperInput(p.week().optString("review"),{v->edit{it.weekText("review",v)}},"이번 주를 돌아봐요",accent,lines=1)
        Spacer(Modifier.height(18.dp))
        val monday=PlannerDocument.weekStart(p.date)
        if(wide)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (0..6).forEach {offset->WeekDay(p.day(monday.plusDays(offset.toLong())),accent,Modifier.weight(1f)){openDay(monday.plusDays(offset.toLong()))} }
        }else (0..6).forEach {offset->val date=monday.plusDays(offset.toLong());WeekDay(p.day(date),accent,Modifier.fillMaxWidth()){openDay(date)};Spacer(Modifier.height(10.dp))}
    }
}
@Composable private fun WeekDay(p:PlannerDocument,accent:Color,modifier:Modifier,open:()->Unit) {
    Column(modifier.border(1.dp,accent.copy(alpha=.3f),RoundedCornerShape(14.dp)).background(accent.copy(alpha=.025f),RoundedCornerShape(14.dp)).clickable(onClick=open).padding(10.dp)) {
        Text("${p.date.monthValue}/${p.date.dayOfMonth}",fontSize=23.sp,color=Ink,modifier=Modifier.background(accent.copy(alpha=.13f),RoundedCornerShape(7.dp)).padding(4.dp))
        Text(p.date.dayOfWeek.name,fontSize=10.sp,color=Muted,modifier=Modifier.padding(vertical=6.dp))
        p.tasks().take(10).forEach{Text("${listOf("□","○","△","×","→")[it.mark.coerceIn(0,4)]} ${it.text}",fontSize=14.sp,color=Ink,modifier=Modifier.padding(vertical=6.dp))}
        if(p.tasks().isEmpty())Text("할 일을 기록해요",color=Muted,fontSize=13.sp,modifier=Modifier.padding(vertical=20.dp))
        HorizontalDivider(color=Muted.copy(alpha=.35f));Text(PlannerDocument.duration(p.minutes()),color=accent,modifier=Modifier.padding(top=8.dp))
        Text("눌러서 하루 펼치기",fontSize=11.sp,color=Muted)
    }
}

@Composable fun StatisticsScreen(p:PlannerDocument,accent:Color) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        PaperHeading("OVERVIEW",accent,"☼")
        val dates=p.recordedDates();val monday=PlannerDocument.weekStart(p.date)
        val month=dates.filter{it.year==p.date.year&&it.month==p.date.month}
        val weekly=dates.filter{it>=monday&&it<monday.plusDays(7)}.sumOf{p.day(it).minutes()}
        val monthly=month.sumOf{p.day(it).minutes()};val recorded=month.count{p.day(it).minutes()>0}
        listOf("THIS WEEK" to PlannerDocument.duration(weekly),"THIS MONTH" to PlannerDocument.duration(monthly),"DAILY AVERAGE" to PlannerDocument.duration(if(recorded==0)0 else monthly/recorded),"RECORDED DAYS" to "$recorded / ${p.date.lengthOfMonth()}").chunked(2).forEach {row->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.padding(bottom=12.dp)) {
                row.forEach {item->
                    Column(Modifier.weight(1f).background(accent.copy(alpha=.065f),RoundedCornerShape(15.dp)).padding(16.dp)) {
                        Text(item.first,color=Muted,fontSize=12.sp)
                        Text(item.second,color=accent,fontSize=28.sp,fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
        PaperHeading("HIGHLIGHTERS",accent,"✎")
        p.categories().forEach{category->
            val count=month.sumOf{day->p.day(day).slots().count{it==category.id}*10}
            Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(category.name,color=Ink);Text(PlannerDocument.duration(count),color=accent)}
            HorizontalDivider(color=Muted.copy(alpha=.25f))
        }
        PaperHeading("LAST 35 DAYS",accent,"▦")
        (0..4).forEach{week->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            (0..6).forEach{day->val date=monday.minusWeeks(4).plusDays((week*7+day).toLong());val minutes=p.day(date).minutes();Column(Modifier.weight(1f).padding(vertical=3.dp).background(accent.copy(alpha=(.03f+minutes.coerceAtMost(360)/360f*.35f)),RoundedCornerShape(7.dp)).padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(date.dayOfMonth.toString(),fontSize=14.sp);if(minutes>0)Text("${minutes/60}h",fontSize=10.sp,color=Muted)} }
        } }
        Text("선택한 날짜가 속한 주·월 기준입니다.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=12.dp))
    }
}
@Composable fun SettingsScreen(p:PlannerDocument,accent:Color,edit:Edit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        PaperHeading("THEME",accent,"✿")
        Text("데스크톱과 같은 종이 플래너",fontSize=20.sp,color=Ink)
        Text("기본 컬러가 제목·포인트·배경에 함께 반영됩니다.",color=Muted,modifier=Modifier.padding(vertical=12.dp))
        Themes.chunked(3).forEachIndexed{row,colors->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)){colors.forEachIndexed{col,color->val index=row*3+col;TextButton({edit{it.theme(index)}},Modifier.weight(1f).height(56.dp).background(color.copy(alpha=.2f),RoundedCornerShape(14.dp)).border(if(p.theme()==index)2.dp else 0.dp,color,RoundedCornerShape(14.dp))){Text(if(p.theme()==index)"✓" else "●",color=color,fontSize=26.sp)}}};Spacer(Modifier.height(12.dp))}
        PaperHeading("SYNC",accent,"↻")
        Text("동기화 연결 준비 중",color=Ink,fontSize=20.sp)
        Text("이 개발 APK는 아직 PC·다른 기기로 기록을 보내지 않습니다. 암호화 호환 검증 후 연결 기능을 제공합니다.",color=Muted,modifier=Modifier.padding(vertical=12.dp))
        PaperHeading("DATA",accent,"▤")
        Text("기록은 이 기기에 자동 저장됩니다. 서버 전송·계정·광고는 없습니다.",color=Muted)
        Spacer(Modifier.height(24.dp));Text("Dayflow 0.1.0 · 개발 테스트용",color=Ink)
        Text("자유 필기·필기 인식은 이번 범위에서 제외했습니다.",color=Muted,fontSize=12.sp)
    }
}
