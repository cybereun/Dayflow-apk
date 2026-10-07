package com.cybereun.dayflow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Paper=Color(0xFFFFFBF3)
val Ink=Color(0xFF393638)
val Muted=Color(0xFF92909A)
val Themes=listOf(Color(0xFFEE8597),Color(0xFFEFA474),Color(0xFFE7C55F),Color(0xFF90B388),Color(0xFF6EBDB8),Color(0xFF88ACDE),Color(0xFFB99ADC),Color(0xFFD995B9),Color(0xFF9B9693))

@Composable fun PaperHeading(text:String,accent:Color,icon:String="",modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(icon.isNotEmpty())Text(icon,color=accent,fontSize=21.sp)
        Text(text,color=Ink,fontSize=16.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.background(accent.copy(alpha=.13f),RoundedCornerShape(8.dp)).padding(horizontal=7.dp,vertical=3.dp))
        HorizontalDivider(Modifier.weight(1f),color=Ink.copy(alpha=.65f),thickness=1.dp)
    }
}
@Composable fun PaperTexture(modifier:Modifier=Modifier) {
    Canvas(modifier) {
        drawRect(Paper)
        // Deterministic subtle paper fibers, not a captured full-page image.
        for(i in 0..1200){val x=((i*97)%1009)/1009f*size.width;val y=((i*193)%1013)/1013f*size.height;drawCircle(Color(0xFFB6A58C).copy(alpha=.05f),.65f,Offset(x,y))}
    }
}
@Composable fun Rings(modifier:Modifier=Modifier) {
    Canvas(modifier.height(24.dp).fillMaxWidth()) {
        val step=40.dp.toPx();var x=20.dp.toPx()
        while(x<size.width) {
            drawRoundRect(Color(0xFF765747),topLeft=Offset(x-5.dp.toPx(),12.dp.toPx()),size=androidx.compose.ui.geometry.Size(10.dp.toPx(),9.dp.toPx()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawRoundRect(brush=Brush.horizontalGradient(listOf(Color(0xFF946344),Color(0xFFF3D6B9),Color(0xFFAE7756))),topLeft=Offset(x-3.dp.toPx(),-9.dp.toPx()),size=androidx.compose.ui.geometry.Size(6.dp.toPx(),28.dp.toPx()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            x+=step
        }
    }
}
@Composable fun PlannerPage(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier,color=Paper,shape=RoundedCornerShape(3.dp),tonalElevation=0.dp,shadowElevation=7.dp) {
        Box {
            PaperTexture(Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=12.dp),content=content)
        }
    }
}
@Composable fun VerticalBinding(modifier:Modifier=Modifier) {
    Canvas(modifier.width(38.dp).fillMaxHeight()) {
        val center=size.width/2
        drawRect(Color(0xFF725D51).copy(alpha=.14f),topLeft=Offset(center-3.dp.toPx(),0f),size=androidx.compose.ui.geometry.Size(6.dp.toPx(),size.height))
        val step=46.dp.toPx();var y=18.dp.toPx()
        while(y<size.height) {
            drawRoundRect(Color(0xFF765747),topLeft=Offset(center-10.dp.toPx(),y-5.dp.toPx()),size=androidx.compose.ui.geometry.Size(20.dp.toPx(),10.dp.toPx()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            drawRoundRect(brush=Brush.horizontalGradient(listOf(Color(0xFF946344),Color(0xFFF3D6B9),Color(0xFFAE7756))),topLeft=Offset(center-3.dp.toPx(),y-14.dp.toPx()),size=androidx.compose.ui.geometry.Size(6.dp.toPx(),28.dp.toPx()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            y+=step
        }
    }
}
@Composable fun PaperInput(value:String,onChange:(String)->Unit,hint:String,accent:Color,modifier:Modifier=Modifier,lines:Int=2) {
    OutlinedTextField(value,onChange,modifier.fillMaxWidth(),placeholder={Text(hint,color=Muted)},minLines=lines,shape=RoundedCornerShape(14.dp),
        colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=accent,unfocusedBorderColor=accent.copy(alpha=.35f),focusedContainerColor=accent.copy(alpha=.035f),unfocusedContainerColor=accent.copy(alpha=.035f)),textStyle=LocalTextStyle.current.copy(color=Ink,fontSize=15.sp))
}
