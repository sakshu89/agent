# Preserve Vosk/JNA JNI entry points used by the optional offline speech engine.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# Keep Android components instantiated by the framework.
-keep class com.ultron.app.UltronListenService { *; }
-keep class com.ultron.app.UltronAccessibilityService { *; }
-keep class com.ultron.app.notifications.UltronNotificationListener { *; }

# Gson reads/writes the persisted task plan (AgentTaskQueue) by field name and generic type;
# R8 must not rename or strip these in release builds.
-keepattributes Signature, *Annotation*
-keep class com.ultron.app.agent.AgentTaskQueue$Step { *; }
-keep class com.ultron.app.agent.AgentTaskQueue$Task { *; }
