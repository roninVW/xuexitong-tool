# Release 混淆规则（保守策略：只保证反射/序列化相关代码可用）
# 业务代码仍由 R8 优化与混淆；Compose / Room / OkHttp / Jsoup 自带 consumer rules。

-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod

# kotlinx.serialization：生成的 serializer 与 Companion 通过反射查找
-keep class kotlinx.serialization.** { *; }
-keep class **$$serializer { *; }
-keepclassmembers class **$Companion { *; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# Room 生成的实现类由 KSP 产出，保留其构造与数据库类名
-keep class com.xxt.board.data.AppDatabase { *; }
-keep class com.xxt.board.data.**_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# DataStore / WorkManager 反射实例化
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# 保留模型字段（序列化 + Room 列名映射）
-keep class com.xxt.board.data.ModelsKt { *; }
-keepclassmembers class com.xxt.board.data.** { <fields>; }

# WebView 中供页面调用的 JS 接口（若后续新增 @JavascriptInterface 需保留）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
