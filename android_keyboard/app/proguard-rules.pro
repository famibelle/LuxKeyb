# 🔒 ProGuard Rules pour Clavier Kreyòl Karukera
# Règles d'obfuscation et d'optimisation pour publication Play Store

# =====================================
# RÈGLES DE BASE ANDROID
# =====================================

# Conserver les classes d'activité et de service principales
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.inputmethodservice.InputMethodService

# =====================================
# RÈGLES SPÉCIFIQUES IME (CLAVIER)
# =====================================

# Conserver le service IME principal
-keep class com.example.kreyolkeyboard.KreyolInputMethodService* {
    public *;
}
-keep class com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored* {
    public *;
}

# Conserver l'activité de paramètres
-keep class com.example.kreyolkeyboard.SettingsActivity* {
    public *;
}

# Conserver les interfaces de callback Android
-keep class * extends android.view.inputmethod.InputConnection
-keep class * extends android.inputmethodservice.KeyboardView

# =====================================
# RÈGLES POUR LES ASSETS ET DONNÉES
# =====================================

# Conserver les classes de gestion du dictionnaire
-keep class com.example.kreyolkeyboard.*Dictionary* {
    public *;
}

# Conserver les classes de suggestion
-keep class com.example.kreyolkeyboard.*Suggestion* {
    public *;
}

# Conserver les handlers d'accent
-keep class com.example.kreyolkeyboard.AccentHandler* {
    public *;
}

# =====================================
# RÈGLES KOTLIN ET ANDROIDX
# =====================================

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# =====================================
# RÈGLES POUR LA RÉFLEXION
# =====================================

# Conserver les annotations importantes
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes InnerClasses

# =====================================
# RÈGLES POUR LES RESSOURCES
# =====================================

# Conserver les ressources utilisées dynamiquement
-keep class **.R
-keep class **.R$* {
    <fields>;
}

# Conserver les layouts XML
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# =====================================
# OPTIMISATIONS SPÉCIALES
# =====================================

# Optimiser mais garder les noms de méthodes importantes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Conserver les énumérations
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Conserver Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# =====================================
# RÈGLES DE SÉCURITÉ
# =====================================

# Supprimer les logs en production
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# =====================================
# RÈGLES POUR JSON (DICTIONNAIRE)
# =====================================

# Si utilisation de JSON (pour le dictionnaire créole)
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# =====================================
# WARNINGS À IGNORER
# =====================================

# Ignorer les warnings non critiques
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# =====================================
# OPTIMISATIONS AVANCÉES
# =====================================

# Optimisation aggressive autorisée pour release
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Réduire la taille du fichier
-repackageclasses ''
-allowaccessmodification