# Room/Media3/Compose ship their own consumer rules. Add app-specific rules here if release builds break.

# jaudiotagger (tag writing) uses reflection and desktop-only classes that don't exist on Android
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# Remove debug/verbose logging from release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
