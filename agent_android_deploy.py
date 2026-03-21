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
    print("❌ 錯誤：請先執行 export 設定環境變數！")
    exit(1)

GITHUB_REPO_URL = f"https://{GITHUB_TOKEN}@github.com/{GITHUB_USER}/{GITHUB_REPO}.git"

# ================= 核心 API 函數 =================

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
    raise Exception("無法獲取 Gemini 模型")

def call_gemini_api(prompt, model_id, api_ver):
    url = f"https://generativelanguage.googleapis.com/{api_ver}/{model_id}:generateContent?key={GEMINI_API_KEY}"
    payload = {"contents": [{"parts": [{"text": prompt}]}]}
    response = requests.post(url, headers={'Content-Type': 'application/json'}, data=json.dumps(payload))
    if response.status_code == 200:
        return response.json()['candidates'][0]['content']['parts'][0]['text']
    raise Exception(f"API Error: {response.text}")

# ================= 專案環境初始化 =================

def initialize_android_project():
    """建立符合 2026 Android 標準的專案結構"""
    os.makedirs(SRC_PATH, exist_ok=True)
    os.makedirs(".github/workflows", exist_ok=True)
    
    # 1. 修正 AndroidX 報錯的關鍵檔案
    print(f"📁 生成 {PROPERTIES_FILE}...")
    with open(PROPERTIES_FILE, "w") as f:
        f.write("android.useAndroidX=true\nandroid.enableJetifier=true\n")

    # 2. 生成 settings.gradle
    if not os.path.exists(SETTINGS_GRADLE):
        with open(SETTINGS_GRADLE, "w") as f: f.write("include ':app'\n")

    # 3. 生成根目錄 build.gradle (定義 Plugin 版本)
    print("📁 生成根目錄 build.gradle...")
    with open(GRADLE_ROOT_FILE, "w") as f:
        f.write("""
buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.2.2'
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22'
    }
}
allprojects { repositories { google(); mavenCentral() } }
""")

    # 4. 生成 app/build.gradle (Compose 配置)
    print("📁 生成 app/build.gradle...")
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
    implementation 'androidx.compose.ui:ui-tooling-preview'
}}
""")

    # 5. 生成 AndroidManifest.xml
    print("📁 生成 AndroidManifest.xml...")
    with open(MANIFEST_FILE, "w") as f:
        f.write(f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Gold Tracker AI" android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>""")

    # 6. 生成 GitHub Actions 自動編譯腳本 (強制指定 Gradle 8.5)
    print("🤖 生成 GitHub Actions 流程檔...")
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

# ================= 開發 Agent 邏輯 (強化後的 Prompt) =================

def developer_agent_android(task, model_id, api_ver):
    existing_code = ""
    if os.path.exists(APP_FILE):
        with open(APP_FILE, "r", encoding="utf-8") as f:
            existing_code = f.read()
    
    # 強制引導 AI 遵循標準範本，解決 "Unresolved reference" 問題
    system_instruction = (
        f"你是一個專精 Jetpack Compose 的 Android 專家。請為 package {PACKAGE_NAME} 撰寫 MainActivity.kt。\n"
        "【強制規範】檔案頂部必須包含以下 Import：\n"
        "import android.os.Bundle\n"
        "import androidx.activity.ComponentActivity\n"
        "import androidx.activity.compose.setContent\n"
        "import androidx.compose.foundation.layout.*\n"
        "import androidx.compose.material3.*\n"
        "import androidx.compose.runtime.*\n"
        "import androidx.compose.ui.Modifier\n"
        "import androidx.compose.ui.Alignment\n"
        "import androidx.compose.ui.unit.dp\n"
        "1. 類別必須定義為: class MainActivity : ComponentActivity() {}\n"
        "2. 在 onCreate 中使用 setContent { Surface { ... } }。\n"
        "3. 不要輸出任何 Markdown 標籤或是說明文字，只輸出代碼。"
    )

    prompt = f"{system_instruction}\n目前的任務目標：{task}\n現有代碼參考：\n{existing_code}"
    code = call_gemini_api(prompt, model_id, api_ver)
    
    # 清理可能的 markdown 殘留
    clean_code = code.replace("```kotlin", "").replace("```", "").strip()
    return clean_code

def github_release_agent(task_name, app_code, readme_content):
    print(f"🚀 [Release Agent] 同步至 GitHub Repo...")
    with open(APP_FILE, "w", encoding="utf-8") as f: 
        f.write(app_code)
    with open(README_FILE, "w", encoding="utf-8") as f: 
        f.write(readme_content)

    try:
        repo = Repo(".")
        repo.git.add(A=True)
        repo.index.commit(f"Android AI Auto-Build: {task_name}")
        repo.git.push(GITHUB_REPO_URL, 'main')
        print(f"✅ 成功推送！請至 GitHub 查看 Actions 並下載編譯產出的 APK。")
    except Exception as e:
        print(f"❌ Git 失敗: {e}")

# ================= 主程式執行區 =================

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("💡 使用方式: python3 agent_android_deploy.py \"任務描述\"")
        exit(1)
        
    my_task = sys.argv[1]
    
    # 第一步：初始化環境 (解決 Gradle/AndroidX 問題)
    initialize_android_project()
    
    try:
        # 第二步：偵測模型版本
        model_id, api_ver = get_available_model()
        
        # 第三步：開發 Agent 生成程式碼 (解決 Unresolved reference 問題)
        clean_code = developer_agent_android(my_task, model_id, api_ver)
        
        # 第四步：文件 Agent
        doc_prompt = f"請撰寫一份詳細的繁體中文 README.md，介紹這個由 AI Agent 自動開發的專案：{my_task}。"
        readme_md = call_gemini_api(doc_prompt, model_id, api_ver)
        
        # 第五步：推送 GitHub
        github_release_agent(my_task, clean_code, readme_md)
        
    except Exception as e:
        print(f"💥 錯誤: {e}")