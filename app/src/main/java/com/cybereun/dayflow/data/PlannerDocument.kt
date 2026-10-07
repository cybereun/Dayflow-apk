package com.cybereun.dayflow.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class TaskEntry(val id:String,val text:String,val mark:Int)
data class Category(val id:Int,val name:String,val hex:String,val counts:Boolean)
data class DdayEntry(val id:String,val title:String,val date:LocalDate)
data class BookInfo(val id:String,val name:String,val start:LocalDate,val cover:Int,val created:String,val isSample:Boolean)

/** Desktop-compatible library index. The actual planner pages are separate book documents. */
class PlannerLibrary(val json:String) {
    private val root=JSONObject(json).also { require(it.optJSONArray("books")!=null){"플래너 목록 형식이 올바르지 않습니다."} }
    private fun edit(action:(JSONObject)->Unit):PlannerLibrary { val next=JSONObject(json);action(next);return PlannerLibrary(next.toString()) }
    fun books():List<BookInfo> { val a=root.getJSONArray("books");return (0 until a.length()).map { i->
        val b=a.getJSONObject(i);BookInfo(b.getString("id").uppercase(),b.optString("name","새 플래너"),LocalDate.parse(b.optString("start",LocalDate.now().toString())),b.optInt("cover",0).coerceIn(0,8),b.optString("created",LocalDate.now().toString()),b.optBoolean("isSample",false))
    } }
    fun activeId():String?=root.optString("activeID","").ifBlank{null}?.uppercase()
    fun active():BookInfo?=books().firstOrNull{it.id==activeId()}?:books().firstOrNull()
    fun activate(id:String)=edit { it.put("activeID",id.uppercase()) }
    fun rename(id:String,name:String):PlannerLibrary { require(name.trim().isNotEmpty());return edit { n->val a=n.getJSONArray("books");for(i in 0 until a.length())if(a.getJSONObject(i).optString("id").equals(id,true))a.getJSONObject(i).put("name",name.trim().take(60)) } }
    fun add(name:String,start:LocalDate=LocalDate.now(),cover:Int=0):Pair<PlannerLibrary,BookInfo> {
        require(name.trim().isNotEmpty());val id=UUID.randomUUID().toString().uppercase();val item=JSONObject().put("id",id).put("name",name.trim().take(60)).put("start",start.toString()).put("cover",cover.coerceIn(0,8)).put("created",LocalDate.now().toString())
        val next=edit { n->val a=n.getJSONArray("books");a.put(item);n.put("activeID",id) }
        return next to BookInfo(id,item.getString("name"),start,cover.coerceIn(0,8),item.getString("created"),false)
    }
    fun remove(id:String):PlannerLibrary { require(books().size>1){"마지막 플래너는 삭제할 수 없습니다."};return edit { n->val a=n.getJSONArray("books");val result=JSONArray();for(i in 0 until a.length())if(!a.getJSONObject(i).optString("id").equals(id,true))result.put(a.get(i));n.put("books",result);if(n.optString("activeID").equals(id,true))n.put("activeID",result.getJSONObject(0).getString("id")) } }
    companion object {
        fun initial():Pair<PlannerLibrary,BookInfo> { val id=UUID.randomUUID().toString().uppercase();val today=LocalDate.now().toString();val book=JSONObject().put("id",id).put("name","나의 플래너").put("start",today).put("cover",0).put("created",today);return PlannerLibrary(JSONObject().put("books",JSONArray().put(book)).put("activeID",id).put("sampleSeeded",false).toString()) to BookInfo(id,"나의 플래너",LocalDate.parse(today),0,today,false) }
    }
}

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
    fun ddays():List<DdayEntry> {
        val global=root.getJSONObject("prefs").optJSONArray("ddays")?:JSONArray()
        val local=value().optJSONArray("ddays")?:JSONArray()
        val source=if(ddaysPerDay())local else global
        return (0 until source.length()).mapNotNull { i->runCatching { val d=source.getJSONObject(i);DdayEntry(d.getString("id"),d.optString("title"),LocalDate.parse(d.getString("date"))) }.getOrNull() }
    }
    fun ddaysPerDay():Boolean=root.getJSONObject("prefs").optBoolean("ddaysPerDay",false)
    fun setDdaysPerDay(enabled:Boolean):PlannerDocument {val n=JSONObject(json);n.getJSONObject("prefs").put("ddaysPerDay",enabled);return PlannerDocument(n.toString(),date)}
    fun addDday(title:String,target:LocalDate):PlannerDocument { require(title.trim().isNotEmpty());val entry=JSONObject().put("id",UUID.randomUUID().toString().uppercase()).put("title",title.trim().take(80)).put("date",target.toString());return if(ddaysPerDay())edit { d->val a=d.optJSONArray("ddays")?:JSONArray();a.put(entry);d.put("ddays",a) } else {val n=JSONObject(json);val prefs=n.getJSONObject("prefs");val a=prefs.optJSONArray("ddays")?:JSONArray();a.put(entry);prefs.put("ddays",a);PlannerDocument(n.toString(),date)} }
    fun deleteDday(id:String):PlannerDocument { return if(ddaysPerDay())edit { d->val a=d.optJSONArray("ddays")?:JSONArray();d.put("ddays",withoutId(a,id)) } else {val n=JSONObject(json);val prefs=n.getJSONObject("prefs");prefs.put("ddays",withoutId(prefs.optJSONArray("ddays")?:JSONArray(),id));PlannerDocument(n.toString(),date)} }
    companion object {
        val defaults=listOf(Category(0,"집중 업무","8EDCD2",true),Category(1,"미팅","F8B38A",true),Category(2,"소통·메일","A9CFF3",true),Category(3,"기획","F3DB78",true),Category(4,"학습","CDB6EF",true),Category(5,"개인","F6AEC5",false),Category(6,"휴식·이동","CFCFD4",false))
        fun empty():PlannerDocument {val cats=JSONArray();defaults.forEach{cats.put(JSONObject().put("id",it.id).put("name",it.name).put("hex",it.hex).put("counts",it.counts))};return PlannerDocument(JSONObject().put("days",JSONObject()).put("weeks",JSONObject()).put("prefs",JSONObject().put("categories",cats).put("lastKind","daily").put("ddays",JSONArray()).put("ddaysPerDay",false).put("defaultTheme",0)).toString()) }
        private fun newDay()=JSONObject().put("tasks",JSONArray()).put("slots",JSONArray(List(144){-1})).put("comment","").put("memoTags",JSONArray(listOf("","",""))).put("memos",JSONArray(listOf("","",""))).put("notes",JSONArray()).put("ddays",JSONArray()).put("dayOff",false)
        fun weekStart(date:LocalDate):LocalDate=date.minusDays((date.dayOfWeek.value-1).toLong())
        fun slotLabel(index:Int):String {require(index in 0..143);val minute=(360+index*10)%1440;return "%02d:%02d".format(minute/60,minute%60) }
        fun duration(minutes:Int)="${minutes/60}h${(minutes%60).toString().padStart(2,'0')}m"
        private fun withoutId(input:JSONArray,id:String):JSONArray { val out=JSONArray();for(i in 0 until input.length()){val item=input.getJSONObject(i);if(item.optString("id")!=id)out.put(item)};return out }
    }
}
