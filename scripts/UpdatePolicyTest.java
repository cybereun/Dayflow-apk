import com.cybereun.dayflow.UpdatePolicy;
public class UpdatePolicyTest {
  public static void main(String[] args) {
    if (!UpdatePolicy.isNewer("v1.0.16", "1.0.15")) throw new AssertionError("new version");
    if (!UpdatePolicy.isNewer("v1.0.100", "1.0.99")) throw new AssertionError("numeric comparison");
    if (UpdatePolicy.isNewer("v1.0.15", "1.0.15")) throw new AssertionError("same version");
    if (UpdatePolicy.isNewer("v1.0.14", "1.0.15")) throw new AssertionError("downgrade");
    if (UpdatePolicy.isNewer("invalid", "1.0.15")) throw new AssertionError("invalid version");
    if (!UpdatePolicy.allowedAsset("https://github.com/cybereun/Dayflow-apk/releases/download/v1.0.16/Dayflow-1.0.16.apk")) throw new AssertionError("valid URL");
    for (String url : new String[]{"http://github.com/cybereun/Dayflow-apk/releases/download/v1/a.apk", "https://github.com.evil.test/cybereun/Dayflow-apk/releases/download/v1/a.apk", "https://github.com/other/Dayflow-apk/releases/download/v1/a.apk", "https://github.com/cybereun/Dayflow-apk/releases/download/v1/../a.apk"})
      if (UpdatePolicy.allowedAsset(url)) throw new AssertionError("unsafe URL");
    System.out.println("PASS: version comparison and trusted APK URLs");
  }
}
