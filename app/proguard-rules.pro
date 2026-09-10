# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.margin.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.margin.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.margin.app.**$$serializer { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
