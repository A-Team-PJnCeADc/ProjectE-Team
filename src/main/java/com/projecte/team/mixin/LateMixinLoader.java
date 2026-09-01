package com.projecte.team.mixin;

import java.util.Collections;
import java.util.List;

import io.github.tox1cozz.mixinbooterlegacy.ILateMixinLoader;
import io.github.tox1cozz.mixinbooterlegacy.LateMixin;

/**
 * 延迟加载 mixin(GTNH Late Mixin 机制)。
 *
 * 背景:gtnhgradle 在 usesMixins=true 时会把 MixinConfigs 写进 jar manifest,
 * 导致 mixin 配置在 bootstrap 期(所有 mod 尚未加载)就被处理。此时 ProjectE 的
 * TransmutationInventory 还不在 classpath 上,启动时探测目标类必然失败,
 * 而且 launchwrapper 会把这次失败永久记入 invalidClasses 负缓存——之后运行时
 * 点转化桌打开 GUI 时该类再也加载不出来,直接 NoClassDefFoundError 崩服。
 *
 * 解法:@LateMixin + ILateMixinLoader 让 FML 在 CONSTRUCTING 阶段(所有 mod
 * jar 已进入 classloader)实例化本类并延迟注册 mixins.projecteteam.json,
 * 此时 ProjectE 类可正常解析,mixin 真正生效,也不再污染 launchwrapper 缓存。
 * 配合 build.gradle.kts 中移除 jar manifest 的 MixinConfigs 属性(避免 bootstrap
 * 期提前入队),二者缺一不可。
 */
@LateMixin
public class LateMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.projecteteam.json");
    }
}
