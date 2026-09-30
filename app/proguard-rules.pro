
# ============================================

# Netije — Android ProGuard / R8 Keep Rules

# Apply ONLY to release build (minifyEnabled=true)

# ============================================

# ---------- 1. Default Android good practice ----------
-keepattributes *Annotation*,EnclosingMethod,Signature,InnerClasses,SourceFile,LineNumberTable
-dontwarn org.jetbrains.annotations.**

# ---------- 2. Hilt / Dagger 2 ----------
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class **_HiltModules* { *; }
-keep class **_MembersInjector { *; }
-keepnames class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory
-if @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep,allowobfuscation,allowshrinking interface dagger.hilt.internal.GeneratedComponent
-keep,allowobfuscation,allowshrinking interface dagger.hilt.internal.GeneratedComponentManager
-keep @dagger.hilt.EntryPoint class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep class **Hilt_* { *; }

# ---------- 3. Retrofit / OkHttp ----------
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.KotlinExtensions
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class com.example.netije.data.remote.dto.** { *; }

# Keep Auth/Person/Sync/Transaction DTOs (API models)
-keep class com.example.netije.data.remote.dto.** { <fields>; }

# Keep Retrofit service interfaces
-keep interface com.example.netije.data.remote.api.** { *; }

# ---------- 4. Room Database ----------
-keep class androidx.room.RoomDataClass { *; }
-keep @androidx.room.Entity class * { <fields>; }
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# Keep Room Entities (local db)
-keep class com.example.netije.data.local.db.**Entity { <fields>; }

# ---------- 5. Domain models (used in Room/Room queries + serialization) ----------
-keep class com.example.netije.domain.model.** { <fields>; public <init>(...); }

# ---------- 6. Coil (AsyncImage) ----------
-dontwarn coil.**
-keep class coil.** { *; }
-keep interface coil.** { *; }

# ---------- 7. Kotlinx Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlin.coroutines.** { *; }

# ---------- 8. DataStore / Proto (if any) ----------
-keep class androidx.datastore.** { *; }

# ---------- 9. Google Sign-In (used from Settings screen) ----------
-keep class com.google.android.gms.auth.api.signin.** { *; }
-dontwarn com.google.android.gms.auth.api.signin.**

# ---------- 10. Serializable / Parcelable if ever added later ----------
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---------- 11. Android PDF Document + text layout ----------
-keep class android.graphics.pdf.** { *; }
-keep class android.text.StaticLayout { *; }
-keep class android.text.** { *; }

# ---------- 12. Report generator helpers (keep entry points) ----------
-keep class com.example.netije.presentation.util.LocalReportGenerator { *; }
-keep class com.example.netije.presentation.util.ReportExportHelper { *; }
-keep enum com.example.netije.presentation.util.ReportFormat { *; }
