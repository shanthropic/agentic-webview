# Add project specific ProGuard rules here.

# Preserves Koog ToolSet reflection and annotations
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-keep class ai.koog.agents.core.tools.reflect.ToolSet { *; }
-keep class * implements ai.koog.agents.core.tools.reflect.ToolSet { *; }
-keepclassmembers class * implements ai.koog.agents.core.tools.reflect.ToolSet {
    <methods>;
}
-keep @interface ai.koog.agents.core.tools.annotations.Tool
-keep @interface ai.koog.agents.core.tools.annotations.LLMDescription
-keepclassmembers class * {
    @ai.koog.agents.core.tools.annotations.Tool *;
}

# Room & Kotlinx Serialization
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }