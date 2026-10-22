# PocketAgent Proguard / R8 Configuration

# Preserve kotlinx.serialization models
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepattributes *Annotation*,InnerClasses,EnclosingMethod

# Preserve Room Database Entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface * { *; }

# Preserve JavascriptInterface for Monaco WebView bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve Ktor client engines and HTTP models
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Preserve ModelProvider implementations
-keep class dev.pocketagent.core.model.** { *; }
-keep class dev.pocketagent.core.providers.** { *; }
