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
    for version in ["v1beta", "v1"]:
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
    # 【關鍵修正】加入 safetySettings 以避免 API 因為內容審核而拒絕回傳 candidates
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0.2, "topP": 0.8},
        "safetySettings": [
            {"category": "HARM_CATEGORY_HARASSMENT", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_HATE_SPEECH", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE"}
        ]
    }
    try:
        response = requests.post(url, headers={'Content-Type': 'application/json'}, data=json.dumps(payload))
        res_json = response.json()
        
        # 【關鍵修正】增加欄位檢查，若被阻擋則拋出具體錯誤訊息
        if 'candidates' not in res_json:
            error_msg = res_json.get('error', {}).get('message', '未知錯誤（可能是被安全性篩選阻擋，請檢查 Prompt）')
            raise Exception(f"Gemini 回傳異常: {error_msg}")
            
        return res_json['candidates'][0]['content']['parts'][0]['text']
    except Exception as e:
        raise Exception(f"API 請求失敗: {str(e)}")

# ================= GitHub Actions 監控 =================

def wait_for_github_action_result():
    api_url = f"https://api.github.com/repos/{GITHUB_USER}/{GITHUB_REPO}/actions/runs"
    print(f"   [等待 GitHub Action 編譯中...]")
    
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
                    jobs_url = latest_run.get("jobs_url")
                    job_data = requests.get(jobs_url, headers=GITHUB_API_HEADERS).json()
                    if not job_data.get("jobs"): return "failure", "無法取得 Job 資訊"
                    
                    job_id = job_data["jobs"][0]["id"]
                    raw_log_url = f"https://api.github.com/repos/{GITHUB_USER}/{GITHUB_REPO}/actions/jobs/{job_id}/logs"
                    log_resp = requests.get(raw_log_url, headers=GITHUB_API_HEADERS)
                    
                    if log_resp.status_code == 200:
                        full_log = log_resp.text
                        error_lines = [l for l in full_log.split('\n') if "e: " in l or "error:" in l.lower()]
                        return "failure", "\n".join(error_lines) if error_lines else full_log[-2000:]
                    else:
                        return "failure", f"無法下載 Log, HTTP {log_resp.status_code}"
            
            print(f"   [Action 目前狀態: {status}...]")
        except Exception as e:
            print(f"⚠️ 檢查狀態時錯誤: {e}")
            
    return "timeout", "Wait timeout"

# ================= 專案環境初始化 =================

def initialize_android_project():
    os.makedirs(SRC_PATH, exist_ok=True)
    os.makedirs(".github/workflows", exist_ok=True)
    
    with open(PROPERTIES_FILE, "w") as f:
        f.write("android.useAndroidX=true\nandroid.enableJetifier=true\n")

    with open(SETTINGS_GRADLE, "w") as f:
        f.write("pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n"
                "dependencyResolutionManagement {\n"
                "    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n"
                "    repositories { google(); mavenCentral(); maven { url 'https://jitpack.io' } }\n"
                "}\nrootProject.name='ai-agent-android'\ninclude ':app'\n")

    # 【關鍵修正】根目錄 build.gradle：加入 kotlin-serialization classpath
    with open(GRADLE_ROOT_FILE, "w") as f:
        f.write("buildscript {\n"
                "    repositories { google(); mavenCentral() }\n"
                "    dependencies {\n"
                "        classpath 'com.android.tools.build:gradle:8.4.0'\n"
                "        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22'\n"
                "        classpath 'org.jetbrains.kotlin:kotlin-serialization:1.9.22'\n"
                "    }\n}\n")

    # 【關鍵修正】App build.gradle：套用 kotlinx-serialization 插件與 implementation
    with open(GRADLE_APP_FILE, "w") as f:
        f.write(f"apply plugin: 'com.android.application'\n"
                f"apply plugin: 'org.jetbrains.kotlin.android'\n"
                f"apply plugin: 'kotlinx-serialization'\n"
                f"android {{\n    namespace '{PACKAGE_NAME}'\n    compileSdk 34\n"
                f"    defaultConfig {{ applicationId '{PACKAGE_NAME}'; minSdk 24; targetSdk 34; versionCode 1; versionName '1.0'; multiDexEnabled true }}\n"
                f"    buildFeatures {{ compose true }}\n"
                f"    composeOptions {{ kotlinCompilerExtensionVersion '1.5.8' }}\n"
                f"    compileOptions {{ sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }}\n"
                f"    kotlinOptions {{ jvmTarget = '17' }}\n}}\n"
                f"dependencies {{\n"
                f"    implementation 'androidx.core:core-ktx:1.12.0'\n"
                f"    implementation 'androidx.activity:activity-compose:1.8.2'\n"
                f"    implementation platform('androidx.compose:compose-bom:2023.10.01')\n"
                f"    implementation 'androidx.compose.ui:ui'\n"
                f"    implementation 'androidx.compose.material3:material3'\n"
                f"    implementation 'com.squareup.okhttp3:okhttp:4.12.0'\n"
                f"    implementation 'com.google.code.gson:gson:2.10.1'\n"
                f"    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'\n"
                f"    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2'\n"
                f"    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3'\n"
                f"}}\n")

    with open(MANIFEST_FILE, "w") as f:
        f.write(f'<?xml version="1.0" encoding="utf-8"?>\n<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n'
                f'    <uses-permission android:name="android.permission.INTERNET" />\n'
                f'    <application android:label="AI App" android:theme="@android:style/Theme.DeviceDefault.NoActionBar">\n'
                f'        <activity android:name=".MainActivity" android:exported="true">\n'
                f'            <intent-filter>\n'
                f'                <action android:name="android.intent.action.MAIN" />\n'
                f'                <category android:name="android.intent.category.LAUNCHER" />\n'
                f'            </intent-filter>\n'
                f'        </activity>\n'
                f'    </application>\n</manifest>')

    with open(GITHUB_ACTION_FILE, "w") as f:
        f.write("name: Android Build\non: [push]\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n"
                "      - uses: actions/checkout@v4\n"
                "      - uses: actions/setup-java@v4\n"
                "        with: { java-version: '17', distribution: 'temurin' }\n"
                "      - name: Build\n"
                "        run: |\n"
                "          chmod +x gradlew\n"
                "          ./gradlew assembleDebug --no-daemon\n")

# ================= Agent 開發與修復邏輯 =================

def developer_agent_android(task, model_id, api_ver, context_log=""):
    system_instruction = (
        f"你是一個 Android 與 Jetpack Compose 專家。請為 package {PACKAGE_NAME} 撰寫完整的 MainActivity.kt。\n"
        "【強制規範】:\n"
        "1. 只輸出 Kotlin 代碼，嚴禁包含 build.gradle 或 XML 配置內容。\n"
        "2. 必須包含所有需要的 import，特別是 kotlinx.serialization.Serializable 而非 java.io.Serializable。\n"
        "3. MPAndroidChart 規範：使用 3.1.0 語法。例如使用 chart.xAxis.setDrawGridLines(false)。\n"
        "4. 確保所有 Composable 都在正確的 @Composable 作用域內調用。\n"
        "5. 嚴禁使用 Markdown 代碼塊標籤，從 'package com.example.aiagent' 開始直接輸出代碼。"
    )
    
    prompt = f"{system_instruction}\n任務目標：{task}"
    if context_log:
        prompt += f"\n\n--- 這是目前的編譯錯誤，請根據此錯誤修復 ---\n{context_log}"
        
    return call_gemini_api(prompt, model_id, api_ver)

def push_to_github(task_name, app_code):
    print(f"🚀 同步代碼至 GitHub...")
    # 確保代碼純淨
    clean_code = app_code.replace("```kotlin", "").replace("```", "").strip()
    with open(APP_FILE, "w", encoding="utf-8") as f: f.write(clean_code)
    try:
        repo = Repo(".") if os.path.exists(".git") else Repo.init(".")
        repo.git.add(A=True)
        repo.index.commit(f"Dev: {task_name}")
        repo.git.push(GITHUB_REPO_URL, 'main', force=True)
        print(f"✅ 推送成功")
    except Exception as e:
        print(f"❌ Git 失敗: {e}")

def auto_fix_loop(task, model_id, api_ver, max_retries=3):
    print("🛠️ 正在生成代碼...")
    code = developer_agent_android(task, model_id, api_ver)
    push_to_github("Initial", code)
    
    for i in range(max_retries):
        status, log_output = wait_for_github_action_result()
        if status == "success":
            print("✨ 編譯成功！APK 已生成。")
            return True
        
        print(f"❌ 第 {i+1} 次嘗試失敗，分析錯誤中...")
        code = developer_agent_android(task, model_id, api_ver, context_log=log_output)
        push_to_github(f"Fix-Attempt-{i+1}", code)
        
    return False

if __name__ == "__main__":
    if len(sys.argv) < 2: 
        print("Usage: python agent_android_deploy.py 'Task Description'")
        exit(1)
    
    my_task = sys.argv[1]
    initialize_android_project()
    try:
        mid, ver = get_available_model()
        if auto_fix_loop(my_task, mid, ver):
            print("📝 完成任務！")
        else:
            print("😢 無法修復編譯錯誤，請手動介入。")
    except Exception as e:
        print(f"💥 崩潰: {e}")