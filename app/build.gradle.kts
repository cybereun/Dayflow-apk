plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.kotlin.kapt") }
android {
    namespace = "com.cybereun.dayflow"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.cybereun.dayflow"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.0.18"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    val releaseStore=System.getenv("DAYFLOW_RELEASE_STORE_FILE")
    val releasePassword=System.getenv("DAYFLOW_RELEASE_STORE_PASSWORD")
    val releaseAlias=System.getenv("DAYFLOW_RELEASE_KEY_ALIAS")
    val releaseKeyPassword=System.getenv("DAYFLOW_RELEASE_KEY_PASSWORD")
    val releaseReady=listOf(releaseStore,releasePassword,releaseAlias,releaseKeyPassword).all{!it.isNullOrBlank()}
    signingConfigs {
        create("dayflowRelease") {
            if(releaseReady) {
                storeFile=file(releaseStore!!)
                storePassword=releasePassword
                keyAlias=releaseAlias
                keyPassword=releaseKeyPassword
            }
        }
    }
    buildTypes { debug { if(releaseReady) signingConfig=signingConfigs.getByName("dayflowRelease") }; release {
        isMinifyEnabled = false
        if(releaseReady) signingConfig=signingConfigs.getByName("dayflowRelease")
        else if(gradle.startParameter.taskNames.any{it.contains("release",ignoreCase=true)}) throw GradleException("A signed release requires the Dayflow release-key environment variables. Use scripts/Build-Release.ps1.")
    } }
}
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    kapt("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
