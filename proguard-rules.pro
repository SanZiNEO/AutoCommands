# AutoCommand ProGuard 配置文件

-dontoptimize
-dontshrink

# 保留所有公共类和方法
-keep public class * {
    public protected *;
}

# 保留模组入口
-keep class com.autocommand.AutoCommand { *; }
-keep class com.autocommand.config.** { *; }
-keep class com.autocommand.network.** { *; }
-keep class com.autocommand.mixin.** { *; }

# 保留 Forge 事件监听器
-keepclassmembers class * {
    @net.minecraftforge.eventbus.api.SubscribeEvent *;
}

# 保留 Mixin
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.spongepowered.asm.mixin.* <fields>;
    @org.spongepowered.asm.mixin.* <methods>;
}

# 保留 Minecraft/Forge 类
-keep class net.minecraftforge.** { *; }
-keep class net.minecraft.** { *; }
-keep class com.mojang.** { *; }
