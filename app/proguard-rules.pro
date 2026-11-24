# Правила для release-сборки (сейчас minify выключен, правила на будущее).

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer {
    *;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Модели данных не должны переименовываться, если включите minify.
-keep class com.energypoints.finder.data.** { *; }
