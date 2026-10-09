package com.cybereun.dayflow;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

/** Pure codec shared by Android transport and JVM interoperability tests. */
public final class InkCodec implements AutoCloseable {
 private final byte[] key=new byte[32]; private final String gid;
 private static byte[] utf8(String value){return value.getBytes(StandardCharsets.UTF_8);}
 private static String encode(byte[] value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
 public InkCodec(String gid,String encodedKey){
  this.gid=gid;byte[] raw=Base64.getUrlDecoder().decode(encodedKey);
  if(raw.length!=32)throw new IllegalArgumentException("Invalid ink key");
  try{HKDFBytesGenerator hkdf=new HKDFBytesGenerator(new SHA256Digest());hkdf.init(new HKDFParameters(raw,utf8(gid),utf8("dayflow/ink/v1")));hkdf.generateBytes(key,0,32);}finally{Arrays.fill(raw,(byte)0);}
 }
 public String rid(String logical)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return encode(mac.doFinal(utf8(logical)));}
 private byte[] aad(String rid){return utf8(gid+":"+rid+":ink:v1");}
 public String encrypt(String rid,String text)throws Exception{
  byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD(aad(rid));
  byte[] encrypted=cipher.doFinal(utf8(text)),payload=Arrays.copyOf(iv,iv.length+encrypted.length);System.arraycopy(encrypted,0,payload,iv.length,encrypted.length);return encode(payload);
 }
 public String decrypt(String rid,String ct)throws Exception{
  byte[] payload=Base64.getUrlDecoder().decode(ct);if(payload.length<28)throw new IllegalArgumentException("Invalid ink ciphertext");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(payload,0,12)));cipher.updateAAD(aad(rid));return new String(cipher.doFinal(Arrays.copyOfRange(payload,12,payload.length)),StandardCharsets.UTF_8);
 }
 public void close(){Arrays.fill(key,(byte)0);}
}
