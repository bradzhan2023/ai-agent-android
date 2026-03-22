Starting a Gradle Daemon (subsequent builds will be faster)
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:checkKotlinGradlePluginConfigurationErrors
> Task :app:generateDebugResValues
> Task :app:checkDebugAarMetadata
> Task :app:mapDebugSourceSetPaths
> Task :app:generateDebugResources
> Task :app:packageDebugResources
> Task :app:mergeDebugResources
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:parseDebugLocalResources
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:javaPreCompileDebug
> Task :app:mergeDebugShaders
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:processDebugManifestForPackage
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets
> Task :app:compressDebugAssets
> Task :app:desugarDebugFileDependencies
> Task :app:processDebugResources
> Task :app:checkDebugDuplicateClasses

> Task :app:compileDebugKotlin FAILED
e: file:///home/runner/work/ai-agent-android/ai-agent-android/app/src/main/java/com/example/aiagent/MainActivity.kt:20:28 Unresolved reference: tooling
e: file:///home/runner/work/ai-agent-android/ai-agent-android/app/src/main/java/com/example/aiagent/MainActivity.kt:22:28 Unresolved reference: ui
e: file:///home/runner/work/ai-agent-android/ai-agent-android/app/src/main/java/com/example/aiagent/MainActivity.kt:35:13 Unresolved reference: AiAgentTheme
e: file:///home/runner/work/ai-agent-android/ai-agent-android/app/src/main/java/com/example/aiagent/MainActivity.kt:36:17 @Composable invocations can only happen from the context of a @Composable function
e: file:///home/runner/work/ai-agent-android/ai-agent-android/app/src/main/java/com/example/aiagent/MainActivity.kt:38:43 @Composable invocations can only happen from the context of a @Composable function
e: file:///home/runner/work/ai-agent-android/ai-agent-android/app/src/main/java/com/example/aiagent/MainActivity.kt:56:5 Unresolved reference: LaunchedEffect
e: file:///home/runner/work/ai-agent-android/ai-agent-android/app/src/main/java/com/example/aiagent/MainActivity.kt:130:30 Composable calls are not allowed inside the calculation parameter of inline fun <T> remember(crossinline calculation: () -> TypeVariable(T)): TypeVariable(T)
	at org.gradle.workers.internal.DefaultWorkerExecutor.lambda$submitWork$0(DefaultWorkerExecutor.java:170)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runExecution(DefaultConditionalExecutionQueue.java:187)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.access$700(DefaultConditionalExecutionQueue.java:120)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner$1.run(DefaultConditionalExecutionQueue.java:162)
	at org.gradle.internal.Factories$1.create(Factories.java:31)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:264)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:128)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:133)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runBatch(DefaultConditionalExecutionQueue.java:157)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.run(DefaultConditionalExecutionQueue.java:126)
	... 2 more


BUILD FAILED in 31s
21 actionable tasks: 21 executed
Error: Process completed with exit code 1.
