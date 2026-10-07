package com.cybereun.dayflow.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SyncCoreTest {
    @Test fun decryptsTheDesktopNodeAesGcmFixture() {
        val key="AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
        val gid="11111111-2222-3333-4444-555555555555"
        val rid="a".repeat(64)
        val desktopCiphertext="AAECAwQFBgcICQoLBYwGOxb5OuOfSu4V16AL-TwgoDn_1O454yD67pPTWgHqtPVVggJ9UBoUkeRpDCKIejL1oY37aboAhkXP5-VCXY0tQqF4pYHgZKYGKTTBkYuDV7IVo_N7HGEp"
        val decoded=SyncCore.decrypt(key,gid,rid,desktopCiphertext)
        assertEquals("library",decoded.getString("name"))
        assertEquals("a433a99b3bea607c9537ac9db1024029c6378d747a75e86622e0383a59185a32",SyncCore.rid(key,"library"))
    }
    @Test fun encryptedRecordsUseRecordBoundAad() {
        val key=SyncCore.newKey()
        val encrypted=SyncCore.encrypt(key,"group-a","record-a",JSONObject("{\"message\":\"안전한 기록\"}"))
        assertEquals("안전한 기록",SyncCore.decrypt(key,"group-a","record-a",encrypted).getString("message"))
        assertThrows(Exception::class.java){SyncCore.decrypt(key,"group-a","record-b",encrypted)}
    }
    @Test fun mergePreservesIndependentEditsLikeDesktopSync() {
        val base=JSONObject("{\"comment\":\"old\",\"memo\":\"old\"}")
        val baseline=SyncCore.update(JSONObject(),null,base,org.json.JSONArray().put(1).put(0).put("seed"))
        val local=SyncCore.update(baseline,base,JSONObject("{\"comment\":\"old\",\"memo\":\"mobile\"}"),org.json.JSONArray().put(2).put(0).put("mobile"))
        val remote=SyncCore.update(baseline,base,JSONObject("{\"comment\":\"pc\",\"memo\":\"old\"}"),org.json.JSONArray().put(3).put(0).put("desktop"))
        val materialized=SyncCore.materialize(SyncCore.merge(local,remote)) as JSONObject
        assertEquals("pc",materialized.getString("comment"))
        assertEquals("mobile",materialized.getString("memo"))
    }
    @Test fun x25519PairSecretMatchesOnBothSides() {
        val first=SyncCore.pairKeys();val second=SyncCore.pairKeys();val context="00000000-0000-0000-0000-000000000001/00000000-0000-0000-0000-000000000002"
        assertEquals(SyncCore.pairSecret(first.privateKey,second.publicKey,context),SyncCore.pairSecret(second.privateKey,first.publicKey,context))
    }
}
