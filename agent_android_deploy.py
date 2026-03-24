import os
import sys
import requests
import json
from git import Repo

# ================= 配置區域 =================
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")
GITHUB_USER = "bradzhan2023"
CURRENT_DIR = os.path.basename(os.getcwd())
GITHUB_REPO = CURRENT_DIR 

PACKAGE_NAME = "com.example.aiagent"
SRC_PATH = f"app/src/main/java/{PACKAGE_NAME.replace('.', '/')}"
APP_FILE = f"{SRC_PATH}/MainActivity.kt"
MANIFEST_FILE = "app/src/main/AndroidManifest.xml"
GRADLE_APP_FILE = "app/build.gradle"
GRADLE_ROOT_FILE = "build.gradle"
PROPERTIES_FILE = "gradle.properties"
SETTINGS_GRADLE = "settings.gradle"
GITHUB_ACTION_FILE = ".github/workflows/android_build.yml"
README_FILE = "README.md"

if not GEMINI_API_KEY or not GITHUB_TOKEN:
    print("❌ 錯誤：請先設定環境變數 export GEMINI_API_KEY=... 及 GITHUB_TOKEN=...")
    exit(1)

GITHUB_REPO_URL = f"https://{GITHUB_TOKEN}@github.com/{GITHUB_USER}/{GITHUB_REPO}.git"

# ================= 核心 API 函數 (含強健診斷邏輯) =================

def get_available_model():
    for version in ["v1", "v1beta"]:
        url = f"https://generativelanguage.googleapis.com/{version}/models?key={GEMINI_API_KEY}"
        try:
            response = requests.get(url)
            if response.status_code == 200:
                models = response.json().get('models', [])
                for m in models:
                    if 'flash' in m['name'] and 'generateContent' in m.get('supportedGenerationMethods', []):
                        return m['name'], version
        except: continue
    raise Exception("無法獲取 Gemini 模型，請檢查 API Key 或額度限制。")

def call_gemini_api(prompt, model_id, api_ver):
    url = f"https://generativelanguage.googleapis.com/{api_ver}/{model_id}:generateContent?key={GEMINI_API_KEY}"
    payload = {"contents": [{"parts": [{"text": prompt}]}]}
    
    try:
        response = requests.post(url, headers={'Content-Type': 'application/json'}, data=json.dumps(payload))
        res_json = response.json()
        
        if response.status_code != 200:
            print(f"⚠️ API 錯誤詳情: {json.dumps(res_json, indent=2)}")
            raise Exception(f"HTTP {response.status_code}: {res_json.get('error', {}).get('message', '未知錯誤')}")

        # 針對 'parts' 缺失問題的結構化檢查
        candidates = res_json.get('candidates', [])
        if not candidates:
            # 檢查是否被安全過濾器攔截
            feedback = res_json.get('promptFeedback', {})
            print(f"⚠️ 模型未產生候選內容。安全回饋: {json.dumps(feedback, indent=2)}")
            raise Exception("Gemini 未能產生內容，可能是觸發了安全過濾機制。")
            
        content = candidates[0].get('content', {})
        parts = content.get('parts', [])
        if not parts:
            raise Exception("API 回應結構中缺少 'parts' 欄位。")
            
        return parts[0]['text']
        
    except requests.exceptions.RequestException as e:
        raise Exception(f"網路請求失敗: {str(e)}")
    except (KeyError, IndexError, json.JSONDecodeError) as e:
        print(f"⚠️ 解析失敗的 JSON 原文: {json.dumps(res_json, indent=2)}")
        raise Exception(f"解析 API 回應失敗: {str(e)}")

# ================= 專案環境初始化 =================

def initialize_android_project():
    os.makedirs(SRC_PATH, exist_ok=True)
    os.makedirs(".github/workflows", exist_ok=True)
    
    # 1. 啟用 AndroidX 與 MultiDex
    with open(PROPERTIES_FILE, "w") as f:
        f.write("android.useAndroidX=true\nandroid.enableJetifier=true\n")

    # 2. 生成 settings.gradle (加入 JitPack 支援繪圖庫)
    with open(SETTINGS_GRADLE, "w") as f:
        f.write("""
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' } 
    }
}
include ':app'
""")

    # 3. 生成根目錄 build.gradle
    with open(GRADLE_ROOT_FILE, "w") as f:
        f.write("""
buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.2.2'
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22'
    }
}
""")

    # 4. 生成 app/build.gradle (穩定依賴配置)
    with open(GRADLE_APP_FILE, "w") as f:
        f.write(f"""
apply plugin: 'com.android.application'
apply plugin: 'org.jetbrains.kotlin.android'
android {{
    namespace '{PACKAGE_NAME}'
    compileSdk 34
    defaultConfig {{
        applicationId '{PACKAGE_NAME}'
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
        multiDexEnabled true
    }}
    buildFeatures {{ compose true }}
    composeOptions {{ kotlinCompilerExtensionVersion '1.5.8' }}
    compileOptions {{ sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }}
    kotlinOptions {{ jvmTarget = '17' }}
}}
dependencies {{
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.activity:activity-compose:1.8.2'
    implementation platform('androidx.compose:compose-bom:2023.10.01')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.foundation:foundation'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'
    implementation 'androidx.multidex:multidex:2.0.1'
}}
""")

    # 5. 生成 AndroidManifest.xml (網路權限)
    with open(MANIFEST_FILE, "w") as f:
        f.write(f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application android:label="Gold Tracker AI" android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>""")

    # 6. 生成 GitHub Actions 工作流
    with open(GITHUB_ACTION_FILE, "w") as f:
        f.write("""
name: Android Build APK
on: [push]
env:
  FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 8.5
      - name: Build with Gradle
        run: gradle assembleDebug --stacktrace
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
""")

# ================= 開發 Agent 邏輯 =================

def developer_agent_android(task, model_id, api_ver):
    system_instruction = (
        f"你是一位精通 Jetpack Compose 與 MPAndroidChart 的 Android 專家。請為 package {PACKAGE_NAME} 撰寫 MainActivity.kt。\n"
        "【規範與限制】:\n"
        "1. 使用 AndroidView 嵌入 LineChart 來顯示走勢圖。\n"
        "2. 網路請求務必在 withContext(Dispatchers.IO) 內完成。\n"
        "3. 必須包含所有必要的 Import，不可省略。\n"
        "4. 直接輸出 Kotlin 程式碼，嚴禁包含任何 Markdown 格式標籤 (如 ```kotlin)。"
    )
    prompt = f"{system_instruction}\n任務：{task}"
    return call_gemini_api(prompt, model_id, api_ver)

def github_release_agent(task_name, app_code, readme_content):
    print(f"🚀 同步至 GitHub...")
    with open(APP_FILE, "w", encoding="utf-8") as f: f.write(app_code)
    with open(README_FILE, "w", encoding="utf-8") as f: f.write(readme_content)
    try:
        repo = Repo(".")
        repo.git.add(A=True)
        repo.index.commit(f"Android Dev: {task_name}")
        repo.git.push(GITHUB_REPO_URL, 'main')
        print(f"✅ 完成！請到 GitHub Actions 下載編譯成功的 APK。")
    except Exception as e:
        print(f"❌ Git 失敗: {e}")

# ================= 進入點 =================

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("💡 Usage: python3 agent_android_deploy.py \"指令內容\"")
        exit(1)
        
    my_task = sys.argv[1]
    initialize_android_project()
    
    try:
        model_id, api_ver = get_available_model()
        clean_code = developer_agent_android(my_task, model_id, api_ver)
        doc_prompt = f"撰寫 README.md 以說明功能: {my_task}"
        readme_md = call_gemini_api(doc_prompt, model_id, api_ver)
        github_release_agent(my_task, clean_code, readme_md)
    except Exception as e:
        print(f"💥 程式終止: {e}")