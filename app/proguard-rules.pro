# ============================================
# SafeKids ProGuard Rules — Release Build
# ============================================

# --- Keep all SafeKids classes (small app, no need to strip) ---
-keep class com.safekids.** { *; }
-keepclassmembers class com.safekids.** { *; }

# --- Room Database ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
# Room generated code
-keep class * implements androidx.room.RoomDatabase$Callback { *; }
-dontwarn androidx.room.**

# --- Kotlin ---
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --- Material Design ---
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# --- AndroidX ---
-keep class androidx.** { *; }
-dontwarn androidx.**

# --- AccessibilityService (critical — must not be stripped) ---
-keep class com.safekids.service.SafeKidsAccessibilityService { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# --- Prevent stripping of view binding / layout inflation ---
-keepclassmembers class * {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# --- General Android ---
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --- Prevent crashes from reflection ---
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
