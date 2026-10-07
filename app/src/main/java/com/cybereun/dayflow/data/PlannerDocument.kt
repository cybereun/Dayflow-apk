package com.cybereun.dayflow.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class TaskEntry(val id:String,val text:String,val mark:Int)
data class Category(val id:Int,val name:String,val hex:String,val counts:Boolean)

/** Immutable edits of the PC wire document; unknown fields are retained. */
class PlannerDocument(val json:String,val date:LocalDate=LocalDate.now()) {
    private val root=JSONObject(json).also { require(it.optJSONObject("days")!=null && it.optJSONObject("weeks")!=null && it.optJSONObject("prefs")!=null){"플래너 형식이 올바르지 않습니다."} }
    private fun value():JSONObject=root.optJSONObject("days")?.optJSONObject(date.toString())?:newDay()
    private fun edit(action:(JSONObject)->Unit):PlannerDocument {
        val next=JSONObject(json);val days=next.getJSONObject("days")
        val day=days.optJSONObject(date.toString())?:newDay()
        action(day);days.put(date.toString(),day)
        return PlannerDocument(next.toString(),date)
    }
    fun day(date:LocalDate)=PlannerDocument(json,date)
    fun tasks():List<TaskEntry> { val a=value().optJSONArray("tasks")?:JSONArray();return (0 until a.length()).map { val t=a.getJSONObject(it);TaskEntry(t.getString("id"),t.getString("text"),t.optInt("mark",0)) } }
    fun task(text:String):PlannerDocument {require(text.isNotBlank());return edit { d->val a=d.optJSONArray("tasks")?:JSONArray();a.put(JSONObject().put("id",UUID.randomUUID().toString().uppercase()).put("text",text.trim()).put("mark",0));d.put("tasks",a) } }
    fun editTask(id:String,text:String)=edit { d->val a=d.optJSONArray("tasks")?:JSONArray();for(i in 0 until a.length())if(a.getJSONObject(i).getString("id")==id)a.getJSONObject(i).put("text",text) }
    fun cycleMark(id:String)=edit { d->val a=d.optJSONArray("tasks")?:JSONArray();for(i in 0 until a.length())if(a.getJSONObject(i).getString("id")==id){val t=a.getJSONObject(i);t.put("mark",(t.optInt("mark",0)+1)%5)} }
    fun deleteTask(id:String)=edit { d->val a=d.optJSONArray("tasks")?:JSONArray();val out=JSONArray();for(i in 0 until a.length())if(a.getJSONObject(i).getString("id")!=id)out.put(a.get(i));d.put("tasks",out) }
    fun slots():List<Int> {val a=value().optJSONArray("slots");return List(144){a?.optInt(it,-1)?:-1} }
    fun paint(index:Int,category:Int):PlannerDocument {require(index in 0..143);require(category==-1||categories().any{it.id==category});return edit { d->val a=JSONArray(slots());a.put(index,category);d.put("slots",a) } }
    fun minutes():Int {val valid=categories().filter{it.counts}.map{it.id}.toSet();return slots().count{it in valid}*10 }
    fun text(field:String):String=if(field=="memo")value().optJSONArray("memos")?.optString(0,"")?:"" else value().optString(field,"")
    fun setText(field:String,text:String):PlannerDocument {require(field in setOf("comment","memo"));return edit { d->if(field=="memo"){val a=d.optJSONArray("memos")?:JSONArray(listOf("","",""));a.put(0,text);d.put("memos",a)}else d.put(field,text) } }
    fun theme():Int=root.getJSONObject("prefs").optInt("defaultTheme",0).coerceIn(0,8)
    fun theme(index:Int):PlannerDocument {require(index in 0..8);val next=JSONObject(json);next.getJSONObject("prefs").put("defaultTheme",index);return PlannerDocument(next.toString(),date) }
    fun categories():List<Category> {val a=root.getJSONObject("prefs").optJSONArray("categories")?:JSONArray();return if(a.length()==0)defaults else (0 until a.length()).map{val v=a.getJSONObject(it);Category(v.getInt("id"),v.getString("name"),v.getString("hex"),v.optBoolean("counts",true))} }
    fun week():JSONObject=root.getJSONObject("weeks").optJSONObject(weekStart(date).toString())?:JSONObject().put("goal","").put("review","").put("stars",0)
    fun weekText(field:String,text:String):PlannerDocument {require(field in setOf("goal","review"));val n=JSONObject(json);n.getJSONObject("weeks").put(weekStart(date).toString(),week().put(field,text));return PlannerDocument(n.toString(),date) }
    fun stars(value:Int):PlannerDocument {require(value in 0..5);val n=JSONObject(json);n.getJSONObject("weeks").put(weekStart(date).toString(),week().put("stars",value));return PlannerDocument(n.toString(),date) }
    fun recordedDates():List<LocalDate> =root.getJSONObject("days").keys().asSequence().mapNotNull{runCatching{LocalDate.parse(it)}.getOrNull()}.sorted().toList()
    companion object {
        val defaults=listOf(Category(0,"집중 업무","8EDCD2",true),Category(1,"미팅","F8B38A",true),Category(2,"소통·메일","A9CFF3",true),Category(3,"기획","F3DB78",true),Category(4,"학습","CDB6EF",true),Category(5,"개인","F6AEC5",false),Category(6,"휴식·이동","CFCFD4",false))
        fun empty():PlannerDocument {val cats=JSONArray();defaults.forEach{cats.put(JSONObject().put("id",it.id).put("name",it.name).put("hex",it.hex).put("counts",it.counts))};return PlannerDocument(JSONObject().put("days",JSONObject()).put("weeks",JSONObject()).put("prefs",JSONObject().put("categories",cats).put("lastKind","daily").put("ddays",JSONArray()).put("ddaysPerDay",true).put("defaultTheme",0)).toString()) }
        private fun newDay()=JSONObject().put("tasks",JSONArray()).put("slots",JSONArray(List(144){-1})).put("comment","").put("memoTags",JSONArray(listOf("","",""))).put("memos",JSONArray(listOf("","",""))).put("notes",JSONArray()).put("ddays",JSONArray()).put("dayOff",false)
        fun weekStart(date:LocalDate):LocalDate=date.minusDays((date.dayOfWeek.value-1).toLong())
        fun slotLabel(index:Int):String {require(index in 0..143);val minute=(360+index*10)%1440;return "%02d:%02d".format(minute/60,minute%60) }
        fun duration(minutes:Int)="${minutes/60}h${(minutes%60).toString().padStart(2,'0')}m"
    }
}
