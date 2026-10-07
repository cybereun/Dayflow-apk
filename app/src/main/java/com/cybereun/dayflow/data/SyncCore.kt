package com.cybereun.dayflow.data

import org.bouncycastle.math.ec.rfc7748.X25519
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Exact Android counterpart of Dayflow desktop's v1 encrypted record format. */
internal object SyncCore {
    private const val MAX_ENTRIES=250000
    private val random=SecureRandom()
    private fun pathKey(path:List<String>)=JSONArray(path).toString()
    private fun copy(value:JSONObject)=JSONObject(value.toString())
    private fun values(value:JSONArray)=List(value.length()){value.get(it)}
    private fun objectValues(value:JSONObject)=value.keys().asSequence().associateWith{value.get(it)}
    fun b64(bytes:ByteArray)=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun unb64(value:String)=Base64.getUrlDecoder().decode(value)
    fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it.toInt() and 255)}
    fun sha256(value:String)=hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)))
    fun newKey():String=ByteArray(32).also{random.nextBytes(it)}.let(::b64)
    fun rid(key:String,name:String):String { val mac=Mac.getInstance("HmacSHA256");mac.init(SecretKeySpec(unb64(key),"HmacSHA256"));return hex(mac.doFinal(name.toByteArray(StandardCharsets.UTF_8))) }
    fun deepEquals(a:Any?,b:Any?):Boolean=when {
        a is JSONObject && b is JSONObject -> a.length()==b.length() && a.keys().asSequence().all { b.has(it) && deepEquals(a.get(it),b.get(it)) }
        a is JSONArray && b is JSONArray -> a.length()==b.length() && (0 until a.length()).all { deepEquals(a.get(it),b.get(it)) }
        a is Number && b is Number -> a.toDouble()==b.toDouble()
        a===JSONObject.NULL && b===JSONObject.NULL -> true
        else -> a==b
    }
    private fun valueEntry(value:Any?)=JSONObject().put("t","value").put("v",value?:JSONObject.NULL)
    fun flatten(value:Any?,path:List<String> = emptyList(),out:JSONObject=JSONObject()):JSONObject {
        val key=pathKey(path)
        when(value) {
            is JSONArray -> {
                out.put(key,JSONObject().put("t","array"))
                val entries=values(value)
                val ids=entries.isNotEmpty() && entries.all { it is JSONObject && it.has("id") && it.opt("id") is String } && entries.map{(it as JSONObject).getString("id")}.toSet().size==entries.size
                entries.forEachIndexed { index,item ->
                    val child=path+(if(ids)"k:"+(item as JSONObject).getString("id") else "i:$index")
                    flatten(item,child,out)
                    if(ids)out.put(pathKey(child+"order:"),valueEntry(index))
                }
            }
            is JSONObject -> {
                out.put(key,JSONObject().put("t","object"))
                value.keys().asSequence().forEach { name->flatten(value.get(name),path+"p:$name",out) }
            }
            else -> out.put(key,valueEntry(value))
        }
        return out
    }
    fun validate(state:JSONObject):JSONObject {
        require(state.length()<=MAX_ENTRIES){"동기화 레코드가 너무 큽니다."}
        state.keys().asSequence().forEach { key->
            val path=JSONArray(key);require(path.length()<=40){"동기화 경로가 너무 깁니다."}
            for(i in 0 until path.length()){val part=path.getString(i);require(part.length<=1000 && (part.startsWith("p:")||part.startsWith("i:")||part.startsWith("k:")||part=="order:")){"동기화 경로가 올바르지 않습니다."}}
            val entry=state.getJSONObject(key);require(entry.optString("t") in setOf("object","array","value","deleted")){"동기화 레코드 형식이 올바르지 않습니다."}
            val stamp=entry.getJSONArray("s");require(stamp.length()==3 && stamp.getLong(0)>=0 && stamp.getLong(1)>=0 && stamp.getString(2).length<=100){"동기화 시각이 올바르지 않습니다."}
        }
        return state
    }
    fun update(state:JSONObject,before:Any?,after:Any?,stamp:JSONArray):JSONObject {
        val prev=if(before==null)JSONObject() else flatten(before)
        val next=if(after==null)JSONObject() else flatten(after)
        val result=copy(state)
        (prev.keys().asSequence().toSet()+next.keys().asSequence().toSet()).forEach { key->
            val old=prev.optJSONObject(key);val fresh=next.optJSONObject(key)
            if(!deepEquals(old,fresh))result.put(key,(fresh?.let(::copy)?:JSONObject().put("t","deleted")).put("s",JSONArray(stamp.toString())))
        }
        return result
    }
    fun compareStamp(a:JSONArray,b:JSONArray):Int { val first=a.getLong(0).compareTo(b.getLong(0));if(first!=0)return first;val second=a.getLong(1).compareTo(b.getLong(1));if(second!=0)return second;return a.getString(2).compareTo(b.getString(2)) }
    fun merge(a:JSONObject,b:JSONObject):JSONObject { validate(a);validate(b);val result=copy(a);b.keys().asSequence().forEach { key->val incoming=b.getJSONObject(key);val existing=result.optJSONObject(key);if(existing==null||compareStamp(incoming.getJSONArray("s"),existing.getJSONArray("s"))>0)result.put(key,copy(incoming)) };return result }
    private object Missing
    fun materialize(state:JSONObject):Any? {
        validate(state)
        val children=mutableMapOf<String,MutableList<String>>()
        state.keys().asSequence().forEach { key->val path=JSONArray(key);if(path.length()>0){val parent=JSONArray((0 until path.length()-1).map{path.getString(it)}).toString();children.getOrPut(parent){mutableListOf()}.add(path.getString(path.length()-1))} }
        fun build(path:List<String>):Any? {
            val entry=state.optJSONObject(pathKey(path))?:return Missing
            if(entry.optString("t")=="deleted")return Missing
            if(entry.optString("t")=="value")return if(entry.has("v"))entry.get("v") else JSONObject.NULL
            val parts=(children[pathKey(path)]?:emptyList()).filter{it!="order:"}
            if(entry.optString("t")=="array") {
                return JSONArray(parts.mapNotNull { part->val value=build(path+part);if(value===Missing)null else {val order=state.optJSONObject(pathKey(path+part+"order:"))?.opt("v") as? Number ?: part.removePrefix("i:").toIntOrNull()?:0;Triple(part,value,order.toInt())} }.sortedWith(compareBy<Triple<String,Any?,Int>>{it.third}.thenBy{it.first}).map{it.second})
            }
            val objectValue=JSONObject()
            parts.filter{it.startsWith("p:")}.forEach { part->val value=build(path+part);if(value!==Missing)objectValue.put(part.removePrefix("p:"),value) }
            return objectValue
        }
        return build(emptyList()).let { if(it===Missing)null else it }
    }
    fun encrypt(key:String,gid:String,recordId:String,value:JSONObject):String {
        val nonce=ByteArray(12).also{random.nextBytes(it)}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(unb64(key),"AES"),GCMParameterSpec(128,nonce));cipher.updateAAD("dayflow-sync/v1/$gid/$recordId".toByteArray(StandardCharsets.UTF_8))
        val encrypted=cipher.doFinal(value.toString().toByteArray(StandardCharsets.UTF_8));val data=encrypted.copyOfRange(0,encrypted.size-16);val tag=encrypted.copyOfRange(encrypted.size-16,encrypted.size)
        return b64(nonce+tag+data)
    }
    fun decrypt(key:String,gid:String,recordId:String,value:String):JSONObject {
        val all=unb64(value);require(all.size in 29..(8*1024*1024)){"암호문 크기가 올바르지 않습니다."};val nonce=all.copyOfRange(0,12);val tag=all.copyOfRange(12,28);val data=all.copyOfRange(28,all.size)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(unb64(key),"AES"),GCMParameterSpec(128,nonce));cipher.updateAAD("dayflow-sync/v1/$gid/$recordId".toByteArray(StandardCharsets.UTF_8));return JSONObject(String(cipher.doFinal(data+tag),StandardCharsets.UTF_8))
    }
    data class PairKeys(val publicKey:String,val privateKey:ByteArray)
    fun pairKeys():PairKeys { val private=ByteArray(32);X25519.generatePrivateKey(random,private);val public=ByteArray(32);X25519.generatePublicKey(private,0,public,0);val spki=byteArrayOf(0x30,0x2a,0x30,0x05,0x06,0x03,0x2b,0x65,0x6e,0x03,0x21,0x00)+public;return PairKeys(b64(spki),private) }
    private fun hkdf(ikm:ByteArray,salt:ByteArray,info:ByteArray,length:Int):ByteArray { val extract=Mac.getInstance("HmacSHA256");extract.init(SecretKeySpec(salt,"HmacSHA256"));val prk=extract.doFinal(ikm);var previous=ByteArray(0);val out=ArrayList<Byte>();var counter=1;while(out.size<length){val expand=Mac.getInstance("HmacSHA256");expand.init(SecretKeySpec(prk,"HmacSHA256"));previous=expand.doFinal(previous+info+byteArrayOf(counter.toByte()));out.addAll(previous.toList());counter++};return out.take(length).toByteArray() }
    fun pairSecret(privateKey:ByteArray,publicKey:String,context:String):String { val encoded=unb64(publicKey);require(encoded.size==44 && encoded.copyOfRange(0,12).contentEquals(byteArrayOf(0x30,0x2a,0x30,0x05,0x06,0x03,0x2b,0x65,0x6e,0x03,0x21,0x00))){"연결 공개키 형식이 올바르지 않습니다."};val shared=ByteArray(32);require(X25519.calculateAgreement(privateKey,0,encoded,12,shared,0)){"연결 키를 계산하지 못했습니다."};return b64(hkdf(shared,context.toByteArray(StandardCharsets.UTF_8),"dayflow-pair-v1".toByteArray(StandardCharsets.UTF_8),32)) }
    fun digits(key:String):String="%06d".format((ByteBuffer.wrap(unb64(key),0,4).int.toLong() and 0xffffffffL)%1000000)
    fun recoveryKey(secret:String,gid:String)=b64(hkdf(unb64(secret),gid.toByteArray(StandardCharsets.UTF_8),"dayflow-recovery-key".toByteArray(StandardCharsets.UTF_8),32))
}
