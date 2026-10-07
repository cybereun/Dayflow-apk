package com.cybereun.dayflow.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybereun.dayflow.PlannerViewModel
import com.cybereun.dayflow.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun DayflowApp(model:PlannerViewModel) {
    val document by model.repository.document.collectAsStateWithLifecycle()
    val error by model.repository.error.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var page by rememberSaveable {mutableStateOf(0)}
    val date=LocalDate.parse(selected)
    val p=document?.day(date)
    val accent=Themes[p?.theme()?:0]
    val font=FontFamily(Font(R.font.poor_story))
    val typography=Typography().let{t->t.copy(bodyLarge=t.bodyLarge.copy(fontFamily=font),bodyMedium=t.bodyMedium.copy(fontFamily=font),bodySmall=t.bodySmall.copy(fontFamily=font),titleLarge=t.titleLarge.copy(fontFamily=font),titleMedium=t.titleMedium.copy(fontFamily=font),labelLarge=t.labelLarge.copy(fontFamily=font),labelMedium=t.labelMedium.copy(fontFamily=font))}
    MaterialTheme(colorScheme=lightColorScheme(primary=accent,onPrimary=Ink,background=Paper,surface=Paper,onSurface=Ink),typography=typography) {
        Box(Modifier.fillMaxSize()) {
            PaperTexture(Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Rings()
                if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(12.dp))
                if(p==null) {Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text(if(error==null)"플래너를 펼치는 중…" else "원본을 보존했습니다. 앱을 다시 열어 주세요.")}}
                else {
                    val context=LocalContext.current
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        TextButton({selected=date.minusDays(1).toString()}){Text("‹",color=Ink)}
                        TextButton({DatePickerDialog(context,{_,y,m,d->selected=LocalDate.of(y,m+1,d).toString()},date.year,date.monthValue-1,date.dayOfMonth).show()},Modifier.weight(1f)){
                            Text(date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd  EEE",java.util.Locale.ENGLISH)),color=Ink)
                        }
                        TextButton({selected=date.plusDays(1).toString()}){Text("›",color=Ink)}
                        TextButton({selected=LocalDate.now().toString()}){Text("오늘")}
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val wide=maxWidth>=840.dp
                        // Always edit the most recent repository document, not a captured UI snapshot.
                        val change:((com.cybereun.dayflow.data.PlannerDocument)->com.cybereun.dayflow.data.PlannerDocument)->Unit={fn->model.edit{fn(it.day(date))}}
                        when(page) {
                            0->DailyScreen(p,accent,wide,change,{page=4})
                            1->WeeklyScreen(p,accent,wide,change,{selected=it.toString();page=0})
                            2->StatisticsScreen(p,accent)
                            3->SettingsScreen(p,accent,change)
                            4->TimetableScreen(p,accent,change,{page=0})
                        }
                    }
                    NavigationBar(containerColor=Paper.copy(alpha=.96f),tonalElevation=0.dp) {
                        listOf("오늘" to "◷","주간" to "▦","통계" to "▥","설정" to "⚙").forEachIndexed {index,item->
                            NavigationBarItem(selected=page==index||index==0&&page==4,onClick={page=index},icon={Text(item.second)},label={Text(item.first)},colors=NavigationBarItemDefaults.colors(indicatorColor=accent.copy(alpha=.17f)))
                        }
                    }
                }
            }
        }
    }
}
