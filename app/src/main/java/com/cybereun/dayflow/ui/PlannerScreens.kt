package com.cybereun.dayflow.ui

import android.app.DatePickerDialog
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybereun.dayflow.data.PlannerDocument
import com.cybereun.dayflow.data.PlannerLibrary
import com.cybereun.dayflow.data.SyncJoin
import com.cybereun.dayflow.data.SyncOffer
import com.cybereun.dayflow.data.SyncStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

typealias Edit=((PlannerDocument)->PlannerDocument)->Unit

@Composable fun DailyScreen(p:PlannerDocument,accent:Color,wide:Boolean,edit:Edit,openTime:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp).padding(bottom=24.dp)) {
        if(wide)Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).testTag("daily-open-spread"),verticalAlignment=Alignment.Top) {
            PlannerPage(Modifier.weight(1f).fillMaxHeight().testTag("daily-left-page")) {
                DailyHeader(p,accent);Ddays(p,accent,edit);DailyNotes(p,accent,edit);TaskList(p,accent,edit);Memo(p,accent,edit)
                Text("dayflow ♡",color=accent,modifier=Modifier.align(Alignment.End).padding(top=22.dp),fontSize=22.sp)
            }
            VerticalBinding(Modifier.testTag("daily-binding"))
            PlannerPage(Modifier.weight(1f).fillMaxHeight().testTag("daily-right-page")) {
                Total(p,accent);PaperHeading("TIMETABLE",accent,"◷");Timetable(p,accent,edit)
                Text("dayflow ♡",color=accent,modifier=Modifier.align(Alignment.End).padding(top=22.dp),fontSize=22.sp)
            }
        } else {
            DailyHeader(p,accent);Ddays(p,accent,edit);Total(p,accent)
            TextButton(openTime,Modifier.fillMaxWidth().background(Color(0xFFE6EDFA),RoundedCornerShape(12.dp))){Text("◷  TIMETABLE · 시간표 펼치기",color=Ink)}
            DailyNotes(p,accent,edit);TaskList(p,accent,edit);Memo(p,accent,edit)
            Text("dayflow ♡",color=accent,modifier=Modifier.align(Alignment.End).padding(top=22.dp),fontSize=22.sp)
        }
    }
}
@Composable private fun Ddays(p:PlannerDocument,accent:Color,edit:Edit) {
    val values=p.ddays();if(values.isEmpty())return
    PaperHeading("D-DAY",accent,"✦")
    values.take(4).forEach { item->
        val days=ChronoUnit.DAYS.between(p.date,item.date);val label=when{days==0L->"D-DAY";days>0L->"D-$days";else->"D+${-days}"}
        Row(Modifier.fillMaxWidth().padding(vertical=3.dp).background(accent.copy(alpha=.08f),RoundedCornerShape(10.dp)).padding(horizontal=10.dp,vertical=6.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(item.title,color=Ink);Text(label,color=accent,fontWeight=FontWeight.Bold)}
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
    Text("□ 미표시   ○ 완료   △ 일부   × 못함   → 내일로",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
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
@Composable fun SettingsScreen(p:PlannerDocument,accent:Color,edit:Edit,library:PlannerLibrary?,activeId:String?,sync:SyncStatus,
    onSelectBook:(String)->Unit,onAddBook:(String)->Unit,onRenameBook:(String,String)->Unit,onDeleteBook:(String)->Unit,onExport:()->Unit,onImport:()->Unit,
    onSync:()->Unit,onCreateSync:(String)->Unit,onJoinSync:(String,String,(SyncJoin)->Unit)->Unit,onAcceptJoin:()->Unit,onStartOffer:((SyncOffer)->Unit)->Unit,onOfferRequest:((String?)->Unit)->Unit,onApproveOffer:(String)->Unit,onRecovery:((String)->Unit)->Unit,onRestore:(String,String)->Unit,onLeave:()->Unit) {
    var newBook by rememberSaveable{mutableStateOf("")};var rename by rememberSaveable{mutableStateOf("")};var renameId by rememberSaveable{mutableStateOf<String?>(null)};var deleteId by rememberSaveable{mutableStateOf<String?>(null)}
    var ddayTitle by rememberSaveable{mutableStateOf("")};var ddayDate by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    var joinCode by rememberSaveable{mutableStateOf("")};var restoreCode by rememberSaveable{mutableStateOf("")};var deviceName by rememberSaveable{mutableStateOf("Android ${Build.MODEL}".take(60))};var joinPending by remember{mutableStateOf<SyncJoin?>(null)};var offer by remember{mutableStateOf<SyncOffer?>(null)};var offerRequest by remember{mutableStateOf<String?>(null)};var confirmation by rememberSaveable{mutableStateOf("")};var recovery by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        PaperHeading("THEME",accent,"✿")
        Text("데스크톱과 같은 종이 플래너",fontSize=20.sp,color=Ink)
        Text("기본 컬러가 제목·포인트·배경에 함께 반영됩니다.",color=Muted,modifier=Modifier.padding(vertical=12.dp))
        Themes.chunked(3).forEachIndexed{row,colors->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)){colors.forEachIndexed{col,color->val index=row*3+col;TextButton({edit{it.theme(index)}},Modifier.weight(1f).height(56.dp).background(color.copy(alpha=.2f),RoundedCornerShape(14.dp)).border(if(p.theme()==index)2.dp else 0.dp,color,RoundedCornerShape(14.dp))){Text(if(p.theme()==index)"✓" else "●",color=color,fontSize=26.sp)}}};Spacer(Modifier.height(12.dp))}
        PaperHeading("PLANNERS",accent,"▤")
        Text("플래너별 기록은 따로 보관되고, 동기화하면 데스크톱과 같은 목록으로 관리됩니다.",color=Muted,fontSize=13.sp)
        library?.books()?.forEach { book->
            Row(Modifier.fillMaxWidth().padding(top=7.dp).border(if(book.id==activeId)2.dp else 1.dp,if(book.id==activeId)accent else Muted.copy(alpha=.25f),RoundedCornerShape(12.dp)).clickable{onSelectBook(book.id)}.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(book.name,color=Ink,fontWeight=FontWeight.Bold);Text("${book.start} · ${if(book.id==activeId)"사용 중" else "눌러서 열기"}",color=Muted,fontSize=12.sp)}
                TextButton({renameId=book.id;rename=book.name}){Text("이름",color=accent)}
                if(library.books().size>1)TextButton({deleteId=book.id}){Text("삭제",color=Muted)}
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextField(newBook,{newBook=it},Modifier.weight(1f).testTag("new-book"),singleLine=true,placeholder={Text("새 플래너 이름")},colors=TextFieldDefaults.colors(unfocusedContainerColor=Color.Transparent,focusedContainerColor=Color.Transparent));TextButton({if(newBook.isNotBlank()){onAddBook(newBook);newBook=""}}){Text("+ 추가",color=accent)}}
        PaperHeading("D-DAY",accent,"✦")
        Row(verticalAlignment=Alignment.CenterVertically){Switch(p.ddaysPerDay(),{enabled->edit{document->document.setDdaysPerDay(enabled)}});Text(if(p.ddaysPerDay())"날짜별 D-day" else "플래너 공통 D-day",modifier=Modifier.padding(start=10.dp),color=Ink)}
        p.ddays().forEach { item->Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("${item.title} · ${item.date}",color=Ink);TextButton({edit{it.deleteDday(item.id)}}){Text("삭제",color=Muted)}}}
        Row(verticalAlignment=Alignment.CenterVertically){TextField(ddayTitle,{ddayTitle=it},Modifier.weight(1f),singleLine=true,placeholder={Text("D-day 이름")},colors=TextFieldDefaults.colors(unfocusedContainerColor=Color.Transparent,focusedContainerColor=Color.Transparent));TextButton({val date=LocalDate.parse(ddayDate);DatePickerDialog(context,{_,y,m,d->ddayDate=LocalDate.of(y,m+1,d).toString()},date.year,date.monthValue-1,date.dayOfMonth).show()}){Text(ddayDate.substring(5),color=accent)};TextButton({if(ddayTitle.isNotBlank()){val title=ddayTitle;edit{it.addDday(title,LocalDate.parse(ddayDate))};ddayTitle=""}}){Text("추가",color=accent)}}
        PaperHeading("SYNC",accent,"↻")
        Text(when(sync.state){"idle","syncing"->"암호화 동기화 연결됨";"off"->"동기화 꺼짐";else->"동기화 확인 필요"},color=Ink,fontSize=20.sp)
        Text("AES-256-GCM으로 암호화해 PC와 같은 Dayflow 서버에 보냅니다. 앱이 열려 있을 때 약 15초마다 확인하며, 수동 동기화도 가능합니다.",color=Muted,modifier=Modifier.padding(vertical=8.dp))
        sync.error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
        if(sync.groupId==null){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({onCreateSync(deviceName)}){Text("새 동기화 시작")};OutlinedButton({onSync}){Text("다시 확인")}}
            PaperInput(joinCode,{joinCode=it},"PC에 표시된 8자리 연결 코드",accent,lines=1)
            Button({onJoinSync(joinCode,deviceName){joinPending=it}},Modifier.padding(top=6.dp)){Text("기존 PC에 연결")}
            PaperInput(restoreCode,{restoreCode=it},"DF1. 로 시작하는 복구 코드",accent,lines=1)
            OutlinedButton({onRestore(restoreCode,deviceName)},Modifier.padding(top=6.dp)){Text("복구 코드로 연결")}
        } else {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onSync){Text(if(sync.state=="syncing")"동기화 중…" else "지금 동기화")};OutlinedButton({onStartOffer{offer=it;offerRequest=null}}){Text("다른 기기 연결")}}
            offer?.let { activeOffer->
                Text("새 기기에 입력할 코드: ${activeOffer.code}",color=accent,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp));Text("10분 동안만 유효합니다. 코드 입력 후 아래에서 요청을 확인하세요.",color=Muted,fontSize=12.sp)
                OutlinedButton({onOfferRequest{offerRequest=it}},Modifier.padding(top=5.dp)){Text("연결 요청 확인")}
                offerRequest?.let { name->Text("요청한 기기: $name",color=Ink,modifier=Modifier.padding(top=7.dp));PaperInput(confirmation,{confirmation=it},"두 기기에 같은 6자리 숫자인지 확인",accent,lines=1);Button({onApproveOffer(confirmation)}){Text("숫자 확인 후 승인")}}
            }
            OutlinedButton({onRecovery{recovery=it}},Modifier.padding(top=10.dp)){Text("새 복구 코드 만들기")}
            recovery?.let{Text("한 번만 안전한 곳에 보관하세요:\n$it",color=accent,fontSize=13.sp,modifier=Modifier.padding(top=6.dp))}
            OutlinedButton({onLeave()},Modifier.padding(top=10.dp)){Text("이 기기 연결 해제")}
        }
        joinPending?.let { pending->
            PaperHeading("PAIRING CHECK",accent,"↻");Text("PC에서 요청을 승인한 뒤 두 화면의 6자리 숫자가 같은지 확인하세요.",color=Ink);Text(pending.digits,color=accent,fontSize=30.sp,fontWeight=FontWeight.Bold);Button({onAcceptJoin()}){Text("PC 승인 후 연결 완료")}
        }
        PaperHeading("DATA",accent,"▤")
        Text("백업은 플래너 목록과 모든 책을 하나의 ZIP으로 내보냅니다. 가져오기 전 기존 기록은 앱 내부 복구 사본으로 남깁니다.",color=Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=8.dp)){Button(onExport){Text("백업 ZIP 만들기")};OutlinedButton(onImport){Text("백업 가져오기")}}
        Spacer(Modifier.height(24.dp));Text("Dayflow 1.0.14",color=Ink)
        Text("자유 필기·필기 인식은 지원하지 않습니다. 연결된 동기화도 별도 백업을 대신하지 않습니다.",color=Muted,fontSize=12.sp)
    }
    if(renameId!=null)AlertDialog(onDismissRequest={renameId=null},title={Text("플래너 이름")},text={TextField(rename,{rename=it},singleLine=true)},confirmButton={TextButton({onRenameBook(renameId!!,rename);renameId=null}){Text("저장")}},dismissButton={TextButton({renameId=null}){Text("취소")}})
    if(deleteId!=null)AlertDialog(onDismissRequest={deleteId=null},title={Text("플래너를 삭제할까요?")},text={Text("이 플래너의 기록도 함께 삭제됩니다. 동기화 중이라면 삭제 내용도 전송됩니다.")},confirmButton={TextButton({onDeleteBook(deleteId!!);deleteId=null}){Text("삭제",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton({deleteId=null}){Text("취소")}})
}
