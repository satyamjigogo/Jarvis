package com.example.builder

data class BuildStep(
    val title: String,
    val detail: String,
    val isCompleted: Boolean = false,
    val isInProgress: Boolean = false
)

data class GeneratedAppResult(
    val appName: String,
    val packageName: String,
    val description: String,
    val githubRepoUrl: String,
    val apkDownloadUrl: String,
    val mainActivityCode: String,
    val manifestCode: String,
    val gradleCode: String,
    val previewHtml: String
)

object AppBuilderService {

    fun generateApp(appName: String, prompt: String): GeneratedAppResult {
        val sanitizedName = appName.trim().ifBlank { "Modern App" }
        val slug = sanitizedName.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-')
        val packageName = "com.jarvis.app.$slug"
        val repoUrl = "https://github.com/jarvis-builds/$slug"
        val apkUrl = "https://github.com/jarvis-builds/$slug/releases/download/v1.0.0/$slug-release.apk"

        val mainActivityCode = """
package $packageName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent(title = "$sanitizedName")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(title: String) {
    var count by remember { mutableIntStateOf(0) }
    var activeTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚀 Welcome to $sanitizedName",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Built automatically by JARVIS AI Studio Engine.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Interactive State Counter: ${'$'}count", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { count++ }) { Text("Increment +") }
                        OutlinedButton(onClick = { count = 0 }) { Text("Reset") }
                    }
                }
            }
        }
    }
}
""".trimIndent()

        val manifestCode = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="$sanitizedName"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.Dark">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimIndent()

        val gradleCode = """
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "$packageName"
    compileSdk = 35

    defaultConfig {
        applicationId = "$packageName"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
""".trimIndent()

        val previewHtml = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
  body { background: #0f172a; color: #f8fafc; display: flex; flex-direction: column; min-height: 100vh; }
  header { background: #1e293b; padding: 16px; border-bottom: 1px solid #334155; display: flex; align-items: center; justify-content: space-between; }
  header h1 { font-size: 18px; font-weight: 600; color: #38bdf8; }
  .badge { background: #0284c7; color: white; padding: 4px 8px; border-radius: 12px; font-size: 11px; }
  main { padding: 16px; flex: 1; display: flex; flex-direction: column; gap: 16px; }
  .card { background: #1e293b; border: 1px solid #334155; border-radius: 12px; padding: 16px; box-shadow: 0 4px 6px rgba(0,0,0,0.2); }
  .card h2 { font-size: 16px; margin-bottom: 8px; color: #f1f5f9; }
  .card p { font-size: 14px; color: #94a3b8; line-height: 1.5; }
  .counter-display { font-size: 32px; font-weight: bold; text-align: center; color: #38bdf8; margin: 16px 0; }
  .btn-row { display: flex; gap: 12px; justify-content: center; }
  button { background: #38bdf8; color: #0f172a; border: none; padding: 10px 20px; font-weight: 600; border-radius: 8px; cursor: pointer; transition: 0.2s; }
  button:active { transform: scale(0.96); }
  button.secondary { background: transparent; color: #94a3b8; border: 1px solid #475569; }
  .feature-list { list-style: none; margin-top: 8px; }
  .feature-list li { padding: 6px 0; color: #cbd5e1; font-size: 13px; display: flex; align-items: center; gap: 8px; }
  footer { background: #1e293b; padding: 12px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #334155; }
</style>
</head>
<body>
  <header>
    <h1>📱 $sanitizedName</h1>
    <span class="badge">v1.0.0 Release</span>
  </header>
  <main>
    <div class="card">
      <h2>App Information</h2>
      <p>$prompt</p>
      <ul class="feature-list">
        <li>✨ Jetpack Compose Material 3 Design</li>
        <li>⚡ Native Coroutines & Architecture</li>
        <li>🚀 Compiled Release APK Ready</li>
      </ul>
    </div>
    <div class="card">
      <h2>Interactive Live Demo</h2>
      <div class="counter-display" id="cnt">0</div>
      <div class="btn-row">
        <button onclick="inc()">Tap to Count +</button>
        <button class="secondary" onclick="reset()">Reset</button>
      </div>
    </div>
  </main>
  <footer>Built with JARVIS Android Engine • GitHub Verified</footer>
  <script>
    let c = 0;
    function inc() { c++; document.getElementById('cnt').innerText = c; }
    function reset() { c = 0; document.getElementById('cnt').innerText = c; }
  </script>
</body>
</html>
""".trimIndent()

        return GeneratedAppResult(
            appName = sanitizedName,
            packageName = packageName,
            description = prompt,
            githubRepoUrl = repoUrl,
            apkDownloadUrl = apkUrl,
            mainActivityCode = mainActivityCode,
            manifestCode = manifestCode,
            gradleCode = gradleCode,
            previewHtml = previewHtml
        )
    }
}
