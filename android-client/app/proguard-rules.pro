# Retrofit reads endpoint annotations and generic response types at runtime.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep interface com.example.familytreeplatform.network.ApiService { *; }

# Gson maps these DTOs by field name. Keep fields while allowing class-name optimization.
-keepclassmembers,allowoptimization class com.example.familytreeplatform.models.** {
    <fields>;
}

# Room and WorkManager use generated/reflective entry points.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.example.familytreeplatform.sync.** { *; }
