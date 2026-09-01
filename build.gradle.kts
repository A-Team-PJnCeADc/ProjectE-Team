
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.jar {
    manifest {
        // gtnhgradle 在 usesMixins=true 时会自动注入 MixinConfigs: mixins.projecteteam.json,
        // 这会让 mixin 配置在 bootstrap 期(ProjectE 尚未进 classpath)提前处理,导致
        // launchwrapper 把 TransmutationInventory 记为无法加载(负缓存),运行期点转化桌
        // 即 NoClassDefFoundError 崩溃。mixin 配置改由 LateMixinLoader 在 FML
        // CONSTRUCTING 阶段延迟注册,故此处移除该属性。
        attributes.remove("MixinConfigs")
    }
}
