# Минификация выключена, но правила оставлены на будущее.
# Защита классов ML Kit при включённом R8/ProGuard.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**