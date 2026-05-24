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
-optimizationpasses 5

#包名不混合大小写
-dontusemixedcaseclassnames

#不跳过非公共的库的类
-dontskipnonpubliclibraryclasses

#混淆时记录日志
-verbose

#关闭预校验
-dontpreverify

#不优化输入的类文件
-dontoptimize

#保护注解
-keepattributes *Annotation*

#保持所有拥有本地方法的类名及本地方法名
-keepclasseswithmembernames class * {
    native <methods>;
}

#保持自定义View的get和set相关方法
-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

#保持Activity中View及其子类入参的方法
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

#枚举
-keepclassmembers enum * {
    **[] $VALUES;
    public *;
}

#Parcelable
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

#R文件的静态成员
-keepclassmembers class **.R$* {
    public static <fields>;
}

-dontwarn android.support.**

#keep相关注解
-keep class android.support.annotation.Keep

-keep @android.support.annotation.Keep class * {*;}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <methods>;
}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <fields>;
}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <init>(...);
}

-dontwarn javax.naming.**
#--------------------------------------------------------------------------------------------------
#声明第三方jar包,不用管第三方jar包中的.so文件(如果有)
#-libraryjars libs/**
###################################################################################################
#---------------------------------------------1.实体类----------------------------------------------
-keepclassmembers enum hikvision.zhanyun.com.hikvision.HCNetSDKJNAInstance{ *;}
#---------------------------------------------2.第三方引用------------------------------------------
# 海康威视
-dontwarn com.ezviz.**
-dontwarn com.hik.**
-dontwarn com.hikvision.**
-dontwarn com.videogo.**
-dontwarn com.sun.**
-dontwarn com.sun.jna.**
-dontwarn org.MediaPlayer.PlayM4.**
-dontnote android.net.http.*
-dontnote org.apache.http.**
-keep class com.ezviz.** { *;}
-keep class com.hik.** { *;}
-keep class com.hikvision.** { *;}
-keep class com.videogo.** { *;}
-keep class com.sun.** { *;}
-keep class com.sun.jna.** { *;}
-keep class org.MediaPlayer.PlayM4.** { *;}
-keep class * extends com.sun.jna.Structure { *; }
-keep class * extends com.sun.jna.Library{ *; }
-dontwarn android.support.v8.renderscript.**
-keep class android.support.v8.renderscript.** {*;}
-keep public class android.support.v8.renderscript.** { *; }
-keep class hikvision.zhanyun.com.hikvision.** { *; }
-keep class com.tencent.** { *; }
-keep class org.xmlpull.** { *; }
-dontwarn org.xmlpull.v1.XmlPullParser
-dontwarn org.xmlpull.v1.XmlSerializer
-keep class okio.** { *; }
#Retrofit
-keepattributes Signature
-keepattributes Exceptions
#RxJava RxAndroid
-dontwarn sun.misc.**
#Gson
-keepattributes Signature
-keepattributes *Annotation*
#OkHttp3
-dontwarn okhttp3.**
-keep class okhttp3.** { *;}
-dontwarn okio.**
#mp4parser
-dontwarn com.googlecode.mp4parser.**
#RenderScript
-keep class androidx.renderscript.** { *; }
#IRay
-dontwarn com.serenegiant.**
-keep class com.serenegiant.usb.** {*; }
-keep class com.serenegiant.uvccamera.** {*; }
#GUIDE
-dontwarn a.**
-keep class a.** {*; }
-dontwarn b.**
-keep class b.** {*; }
-dontwarn c.**
-keep class c.** {*; }
-dontwarn com.guide.**
-keep class com.guide.sdk.** {*; }
-dontwarn com.herohan.uvcapp.**
-keep class com.herohan.uvcapp.** {*; }
-dontwarn com.hoho.android.usbserial.**
-keep class com.hoho.android.usbserial.** {*; }