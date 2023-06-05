# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Needed for kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
#-keepclassmembers class kotlinx.serialization.json.** {
#    *** Companion;
#}
#-keepclasseswithmembers class kotlinx.serialization.json.** {
#    kotlinx.serialization.KSerializer serializer(...);
#}

# Change here com.yourcompany.yourpackage
-keep,includedescriptorclasses class com.github.cleveard.bibliotech.**$$serializer { *; } # <-- change package name to your app's
-keepclassmembers class com.github.cleveard.bibliotech.** { # <-- change package name to your app's
    *** Companion;
}
-keepclasseswithmembers class com.github.cleveard.bibliotech.** { # <-- change package name to your app's
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.yanzhenjie.zbar.* { *; }

# The Google books api library used a Key annotaion to create the request urls.
# We need to keep it for the library to work correctly
-keepattributes *Key*
-keep class com.google.api.** { *; }

-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
