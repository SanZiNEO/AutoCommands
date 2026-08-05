# AutoCommand mod ProGuard 混淆配置（release 构建）
# 策略：只混淆，不缩减不优化（-dontshrink -dontoptimize，稳定性优先）
# 参考 外部模组 的 proguard-rules.pro 写法

-dontshrink
-dontoptimize
-allowaccessmodification
-useuniqueclassmembernames
-adaptclassstrings
-overloadaggressively

-keepattributes Exceptions,InnerClasses,Signature,Deprecated,*Annotation*,EnclosingMethod
-renamesourcefileattribute Source

# ── Forge 模组入口与事件订阅类（FML 注解反射发现） ──
-keep @net.minecraftforge.fml.common.Mod class *
-keep @net.minecraftforge.fml.common.Mod$EventBusSubscriber class *
-keepclassmembers class * {
    @net.minecraftforge.eventbus.api.SubscribeEvent *;
}
-keep class com.autocommand.AutoCommand { *; }

# ── Gson 数据模型：字段名按反射解析，混淆字段名会破坏 JSON 协议/配置 ──
# Packet（TCP 协议）/ Receipt（回执）/ Config（配置文件）及其内部类
# 只保留字段名，类名与方法名照常混淆
-keepclassmembers class com.autocommand.Packet**, com.autocommand.Receipt**, com.autocommand.Config** {
    public <fields>;
}

-dontwarn
-dontnote
-ignorewarnings
