package com.cybereun.dayflow.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybereun.dayflow.data.PlannerDocument

@Composable fun TimetableScreen(p:PlannerDocument,accent:Color,edit:Edit,back:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        TextButton(back){Text("‹ 오늘로 돌아가기")}
        PaperHeading("TIMETABLE",accent,"◷")
        Timetable(p,accent,edit)
    }
}
@Composable fun Timetable(p:PlannerDocument,accent:Color,edit:Edit) {
    var category by remember{mutableStateOf(0)}
    var drawMode by remember{mutableStateOf(false)}
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            p.categories().forEach{c->val color=runCatching{Color(android.graphics.Color.parseColor("#"+c.hex))}.getOrDefault(accent);FilterChip(selected=category==c.id,onClick={category=c.id},label={Text(c.name,fontSize=12.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=color.copy(alpha=.5f)))}
            FilterChip(selected=category==-1,onClick={category=-1},label={Text("지우개")})
        }
        Row {Switch(drawMode,{drawMode=it});Text(if(drawMode)"연속 칠하기 · 스크롤 잠금" else "칸을 눌러 기록 · 위아래 스크롤",modifier=Modifier.padding(12.dp),fontSize=12.sp,color=Muted)}
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(34.dp)) {(0..23).forEach{hour->Text(((hour+6)%24).let{if(it%12==0)"12" else (it%12).toString()},Modifier.height(40.dp).padding(top=7.dp),fontSize=14.sp,color=Ink)}}
            val currentEdit by rememberUpdatedState(edit)
            Canvas(Modifier.weight(1f).height(960.dp).pointerInput(category,drawMode) {
                fun paint(pos:Offset) {if(pos.x<0||pos.y<0||pos.x>=size.width||pos.y>=size.height)return;val col=(pos.x/size.width*6).toInt().coerceIn(0,5);val row=(pos.y/size.height*24).toInt().coerceIn(0,23);currentEdit{it.paint(row*6+col,category)}}
                if(drawMode)detectDragGestures(onDragStart={paint(it)}){change,_->change.consume();paint(change.position)}
                else detectTapGestures(onTap={paint(it)})
            }) {
                val cellW=size.width/6;val cellH=size.height/24
                p.slots().forEachIndexed{index,c->if(c>=0){val cat=p.categories().find{it.id==c};val color=runCatching{Color(android.graphics.Color.parseColor("#"+cat?.hex))}.getOrDefault(accent);drawRoundRect(color.copy(alpha=.8f),Offset(index%6*cellW+2,index/6*cellH+2),androidx.compose.ui.geometry.Size(cellW-4,cellH-4),androidx.compose.ui.geometry.CornerRadius(4f))}}
                (0..6).forEach{col->drawLine(if(col==0)Ink.copy(alpha=.5f) else Color(0xFF96B5D2).copy(alpha=.55f),Offset(col*cellW,0f),Offset(col*cellW,size.height),1f,pathEffect=if(col==0)null else PathEffect.dashPathEffect(floatArrayOf(3f,5f)))}
                (0..24).forEach{row->drawLine(Color(0xFF96B5D2).copy(alpha=.55f),Offset(0f,row*cellH),Offset(size.width,row*cellH),1f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(3f,5f)))}
            }
        }
        Text("1칸 = 10분 · 오전 6시부터 다음 날 오전 6시까지",color=Muted,fontSize=12.sp,modifier=Modifier.padding(vertical=12.dp))
    }
}
