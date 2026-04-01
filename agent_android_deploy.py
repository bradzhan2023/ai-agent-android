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
    
    for _ in range(40):
        time.sleep(15)
        try:
            resp = requests.get(api_url, headers=GITHUB_API_HEADERS)
            runs = resp.json().get("workflow_runs", [])
            if not runs: continue
            
            latest_run = runs[0]
            status = latest_run.get("status")
            conclusion = latest_run.get("conclusion")
            
            if status == "completed":
                if conclusion == "success":
                    return "success", ""
                else:
                    # 抓取 Job 日誌
                    jobs_url = latest_run.get("jobs_url")
                    job_data = requests.get(jobs_url, headers=GITHUB_API_HEADERS).json()
                    if not job_data.get("jobs"): return "failure", "無法取得 Job 資訊"
                    
                    job_id = job_data["jobs"][0]["id"]
                    raw_log_url = f"https://api.github.com/repos/{GITHUB_USER}/{GITHUB_REPO}/actions/jobs/{job_id}/logs"
                    log_resp = requests.get(raw_log_url, headers=GITHUB_API_HEADERS)
                    
                    if log_resp.status_code == 200:
                        full_log = log_resp.text
                        
                        # 關鍵優化：如果能定位到 Kotlin 編譯任務，就截取該段落之後的內容
                        # 這能過濾掉數千行的 "Transforming jar..." 廢話
                        if "> Task :app:compileDebugKotlin" in full_log:
                            relevant_log = full_log.split("> Task :app:compileDebugKotlin")[-1]
                        else:
                            relevant_log = full_log[-10000:] # 否則抓取最後一萬字
                        
                        # 再次過濾，只取出包含錯誤關鍵字的行
                        lines = relevant_log.split('\n')
                        error_lines = [l for l in lines if "e: " in l or "error:" in l.lower() or "Compilation error" in l]
                        
                        if error_lines:
                            return "failure", "\n".join(error_lines)
                        else:
                            return "failure", relevant_log
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
    
    if not os.path.exists("gradlew"):
        print("🛠️ 正在生成 Gradle Wrapper...")
        try:
            subprocess.run(["gradle", "wrapper"], check=True)
        except:
            pass

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
        # 升級版本以減少環境轉型錯誤
        classpath 'com.android.tools.build:gradle:8.4.0'
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
    <application android:label="AI Agent App" android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
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
      - name: Build with Gradle
        run: |
          chmod +x gradlew
          # 關鍵修正：強制 Kotlin 在進程內編譯，並關閉 Daemon，確保 Stdout 能抓到所有 e: 報錯
          ./gradlew assembleDebug --no-daemon -Pkotlin.compiler.execution.strategy="in-process"
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
        "1. 使用 Material3 Compose。\n"
        "2. 必須包含所有需要的 Import。\n"
        "3. 嚴禁使用 getAxisLabel (MPAndroidChart)，請改用 valueFormatter。\n"
        "4. 確保代碼可以直接編譯，不要包含 Markdown 代碼塊標籤，直接輸出代碼內容。"
    )
    return call_gemini_api(f"{system_instruction}\n任務：{task}", model_id, api_ver)

def push_to_github(task_name, app_code):
    print(f"🚀 同步代碼至 GitHub...")
    # 移除可能存在的 Markdown 標籤
    clean_code = app_code.replace("```kotlin", "").replace("```", "").strip()
    with open(APP_FILE, "w", encoding="utf-8") as f: f.write(clean_code)
    try:
        repo = Repo(".")
        if not os.path.exists(".git"): repo = Repo.init(".")
        repo.git.add(A=True)
        repo.index.commit(f"Update: {task_name}")
        repo.git.push(GITHUB_REPO_URL, 'main', force=True)
        print(f"✅ 推送成功")
    except Exception as e:
        print(f"❌ Git 失敗: {e}")

def auto_fix_loop(task, model_id, api_ver, max_retries=3):
    print("🛠️ 正在生成初始代碼...")
    code = developer_agent_android(task, model_id, api_ver)
    push_to_github("Initial Commit", code)
    
    for i in range(max_retries):
        print(f"🔄 等待結果 (第 {i+1}/{max_retries} 次嘗試)...")
        status, log_output = wait_for_github_action_result()
        
        if status == "success":
            print("✨ 編譯成功！")
            return True
        
        print(f"❌ 編譯失敗，正在請求修復...")
        
        # 傳送經過過濾後的 Log 給 Gemini
        fix_prompt = (
            f"原任務：{task}\n\n"
            f"--- 這是編譯器噴出的真實錯誤日誌 ---\n{log_output}\n---\n\n"
            f"請根據上述錯誤內容（特別是標註為 'e:' 的行或 MainActivity.kt 的報錯行號）修復代碼。"
            f"僅輸出完整的 MainActivity.kt 代碼。"
        )
        code = call_gemini_api(fix_prompt, model_id, api_ver)
        push_to_github(f"Auto-fix Attempt {i+1}", code)
        
    return False

if __name__ == "__main__":
    if len(sys.argv) < 2: 
        print("使用方式: python agent_android_deploy.py '你的任務描述'")
        exit(1)
    my_task = sys.argv[1]
    initialize_android_project()
    try:
        model_id, api_ver = get_available_model()
        if auto_fix_loop(my_task, model_id, api_ver):
            print("📝 完成！")
        else:
            print("😢 嘗試次數已達上限，請檢查日誌手動調整。")
    except Exception as e:
        print(f"💥 錯誤: {e}")