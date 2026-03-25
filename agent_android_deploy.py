import os
import sys
import requests
import json
import time
import subprocess
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
GITHUB_API_HEADERS = {
    "Authorization": f"token {GITHUB_TOKEN}",
    "Accept": "application/vnd.github.v3+json"
}

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
    raise Exception("無法獲取 Gemini 模型。")

def call_gemini_api(prompt, model_id, api_ver):
    url = f"https://generativelanguage.googleapis.com/{api_ver}/{model_id}:generateContent?key={GEMINI_API_KEY}"
    payload = {"contents": [{"parts": [{"text": prompt}]}]}
    try:
        response = requests.post(url, headers={'Content-Type': 'application/json'}, data=json.dumps(payload))
        res_json = response.json()
        return res_json['candidates'][0]['content']['parts'][0]['text']
    except Exception as e:
        raise Exception(f"API 請求失敗: {str(e)}")

# ================= GitHub Actions 監控與 Log 抓取 =================

def wait_for_github_action_result():
    api_url = f"https://api.github.com/repos/{GITHUB_USER}/{GITHUB_REPO}/actions/runs"
    
    # 增加輪詢次數，因為 Android 編譯至少需要 1-2 分鐘
    for _ in range(40):
        time.sleep(15)
        try:
            resp = requests.get(api_url, headers=GITHUB_API_HEADERS)
            runs = resp.json().get("workflow_runs", [])
            if not runs: continue
            
            latest_run = runs[0]
            status = latest_run.get("status")
            conclusion = latest_run.get("conclusion")
            run_id = latest_run.get("id")
            
            if status == "completed":
                if conclusion == "success":
                    return "success", ""
                else:
                    print(f"❌ 編譯失敗 (ID: {run_id})，正在抓取完整 Log...")
                    # 1. 取得 Job ID
                    jobs_url = latest_run.get("jobs_url")
                    job_data = requests.get(jobs_url, headers=GITHUB_API_HEADERS).json()
                    if not job_data.get("jobs"): return "failure", "無法取得 Job 資訊"
                    
                    job_id = job_data["jobs"][0]["id"]
                    
                    # 2. 取得純文字 Log (關鍵修復：使用 /logs API)
                    raw_log_url = f"https://api.github.com/repos/{GITHUB_USER}/{GITHUB_REPO}/actions/jobs/{job_id}/logs"
                    log_resp = requests.get(raw_log_url, headers=GITHUB_API_HEADERS)
                    
                    if log_resp.status_code == 200:
                        # 擷取最後 3000 個字元，確保包含 Gradle 的報錯核心
                        full_log = log_resp.text
                        error_snippet = full_log[-3000:] if len(full_log) > 3000 else full_log
                        return "failure", error_snippet
                    else:
                        return "failure", f"無法下載 Log, HTTP {log_resp.status_code}"
            
            print(f"   [Action 狀態: {status}...]")
        except Exception as e:
            print(f"⚠️ 檢查狀態時錯誤: {e}")
            
    return "timeout", "Wait timeout"

# ================= 專案環境初始化 =================

def initialize_android_project():
    os.makedirs(SRC_PATH, exist_ok=True)
    os.makedirs(".github/workflows", exist_ok=True)
    
    # 建立 gradle wrapper (關鍵修復：解決 ./gradlew missing)
    if not os.path.exists("gradlew"):
        print("🛠️ 正在生成 Gradle Wrapper...")
        try:
            subprocess.run(["gradle", "wrapper"], check=True)
        except:
            print("⚠️ 本地環境缺少 gradle 指令，嘗試手動寫入基礎配置...")

    with open(PROPERTIES_FILE, "w") as f:
        f.write("android.useAndroidX=true\nandroid.enableJetifier=true\n")

    with open(SETTINGS_GRADLE, "w") as f:
        f.write("""
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral(); maven { url 'https://jitpack.io' } 
    }
}
include ':app'
""")

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
    packagingOptions {{
        resources {{
            excludes += '/META-INF/{{AL2.0,LGPL2.1}}'
            excludes += 'META-INF/versions/9/previous-compilation-data.bin'
        }}
    }}
}}

dependencies {{
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.activity:activity-compose:1.8.2'
    implementation platform('androidx.compose:compose-bom:2023.10.01')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.foundation:foundation'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'
    implementation 'androidx.multidex:multidex:2.0.1'
}}
""")

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
        run: |
          chmod +x gradlew
          ./gradlew assembleDebug --stacktrace
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
""")

# ================= Agent 開發邏輯 =================

def developer_agent_android(task, model_id, api_ver):
    system_instruction = (
        f"你是一個 Android 專家。請為 package {PACKAGE_NAME} 撰寫 MainActivity.kt。\n"
        "【技術要求】:\n"
        "1. 使用 Gson 庫解析 JSON (com.google.gson.Gson)。\n"
        "2. 使用 OkHttp 抓取 Binance API。\n"
        "3. 禁止引用 ui.tooling 或自定義 Theme，僅使用 MaterialTheme。\n"
        "4. 禁止使用 getAxisLabel 覆寫，請使用預設標籤或是正確的 ValueFormatter。\n"
        "5. 包含所有必要的 Import (如 com.google.gson.*, okhttp3.*)。\n"
        "6. 直接輸出 Kotlin 碼，不要 Markdown 標籤。"
    )
    return call_gemini_api(f"{system_instruction}\n任務：{task}", model_id, api_ver)

def push_to_github(task_name, app_code):
    print(f"🚀 同步代碼至 GitHub...")
    with open(APP_FILE, "w", encoding="utf-8") as f: f.write(app_code)
    try:
        if os.path.exists("gradlew"):
            os.chmod("gradlew", 0o755) # 確保本地權限正確
            
        repo = Repo(".")
        if not os.path.exists(".git"): repo = Repo.init(".")
        
        repo.git.add(A=True)
        repo.index.commit(f"Update: {task_name}")
        repo.git.push(GITHUB_REPO_URL, 'main', force=True)
        print(f"✅ 推送成功")
    except Exception as e:
        print(f"❌ Git 失敗: {e}")

# ================= 自動修復迴圈 =================

def auto_fix_loop(task, model_id, api_ver, max_retries=3):
    print("🛠️ 正在生成初始代碼...")
    code = developer_agent_android(task, model_id, api_ver)
    push_to_github("Initial Commit", code)
    
    for i in range(max_retries):
        print(f"🔄 正在等待 GitHub Actions 編譯結果 (第 {i+1}/{max_retries} 次嘗試)...")
        status, log_output = wait_for_github_action_result()
        
        if status == "success":
            print("✨ [SUCCESS] 編譯成功！APK 已生成。")
            return True
        
        print(f"❌ [FAILURE] 編譯失敗，正在分析日誌並修復...")
        
        # 讓 Gemini 根據真實 Log 進行修復
        fix_prompt = (
            f"原任務：{task}\n\n"
            f"--- GitHub 編譯日誌最後部分 ---\n{log_output}\n---\n\n"
            f"以上日誌顯示了編譯失敗的原因（例如找不到引用、語法錯誤或 Import 缺失）。"
            f"請修正錯誤並重新提供完整的 MainActivity.kt 代碼。"
        )
        code = call_gemini_api(fix_prompt, model_id, api_ver)
        push_to_github(f"Auto-fix Attempt {i+1}", code)
        
    print("🛑 已達到最大重試次數，任務失敗。")
    return False

# ================= 主程式 =================

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("💡 Usage: python3 agent_android_deploy.py \"任務描述\"")
        exit(1)
        
    my_task = sys.argv[1]
    
    print("🏗️ 初始化 Android 專案結構...")
    initialize_android_project()
    
    try:
        model_id, api_ver = get_available_model()
        print(f"🤖 使用模型: {model_id}")
        
        # 執行自動修復迴圈
        success = auto_fix_loop(my_task, model_id, api_ver)
        
        if success:
            doc_prompt = f"為這個 Android 專案撰寫一個漂亮的 README.md，包含功能介紹與截圖說明: {my_task}"
            readme_md = call_gemini_api(doc_prompt, model_id, api_ver)
            with open(README_FILE, "w", encoding="utf-8") as f: f.write(readme_md)
            print("📝 README 已更新，請至 GitHub 下載 APK！")
            
    except Exception as e:
        print(f"💥 程式執行出錯: {e}")