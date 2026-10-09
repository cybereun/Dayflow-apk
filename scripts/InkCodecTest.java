import com.cybereun.dayflow.InkCodec;
import java.util.Arrays;
import java.util.Base64;
public final class InkCodecTest {
 public static void main(String[] args)throws Exception{
  byte[] key=new byte[32];Arrays.fill(key,(byte)7);String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(key);
  try(InkCodec codec=new InkCodec("fixture-group",encoded);InkCodec other=new InkCodec("different-group",encoded)){
   String rid=codec.rid("book|daily|2026-10-09|stroke");if(!rid.equals("acamvfeqOKdgJv9vNwRQxSjxUskO_2s28BaMmRk5DhQ"))throw new AssertionError("RID mismatch");
   String ct="Evleo3zuDPrfsPeCQY3bMyMO-D4eC694Y5eotIPSGGuvhN2ahIAD4NttMihmfZPn0X5l2w";
   if(!codec.decrypt(rid,ct).equals("{\"text\":\"한글 필기\"}"))throw new AssertionError("Node ciphertext mismatch");
   try{other.decrypt(rid,ct);throw new AssertionError("Other group accepted");}catch(javax.crypto.AEADBadTagException expected){}
   String reverse=codec.encrypt(rid,"{\"text\":\"안드로이드 필기\"}");System.out.println(reverse);
  }
 }
}
