package com.projecte.team.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecte.team.ItemNameSearchHelper;

import cpw.mods.fml.common.Loader;
import moze_intel.projecte.utils.ItemSearchHelper;

/**
 * 转换桌搜索在 GTNH 环境失效的修复。
 *
 * <p>
 * ProjectE 检测到 NEI 已加载时,搜索走 ItemSearchHelperNEI:遍历 NEI 旧 API 的
 * {@code SearchField.searchProviders} 列表生成过滤器,列表为空时返回"匹配一切",
 * 输入任何关键字物品环都不变。GTNH 的 NEI fork 已改用 SearchTokenParser 新架构,
 * 从不向该旧列表注册 provider(整包仅声明处引用,恒为空),故 GTNH 中转换桌搜索
 * 必然失效(与团队模式、EMC/知识共享均无关,单人同样复现)。
 *
 * <p>
 * 本 mixin:NEI 已加载但旧 provider 列表为空(GTNH 特征)时,退回名字包含匹配
 * (与 ProjectE 无 NEI 环境的 DefaultSearch 行为一致)。经典 NEI(列表非空)与无 NEI
 * 环境不受影响,走 ProjectE 原逻辑。
 */
@Mixin(ItemSearchHelper.class)
public abstract class ItemSearchHelperMixin {

    @Inject(method = "create", at = @At("HEAD"), cancellable = true, remap = false)
    private static void projecteteam$fallbackWhenNeiHasNoProviders(String searchString,
        CallbackInfoReturnable<ItemSearchHelper> cir) {
        if (Loader.isModLoaded("NotEnoughItems") && neiLegacySearchProvidersEmpty()) {
            cir.setReturnValue(new ItemNameSearchHelper(searchString));
        }
    }

    /**
     * GTNH 的 NEI fork:旧 API 的 {@code SearchField.searchProviders} 恒为空。
     * 反射访问以保持编译期零依赖 NEI。
     */
    private static boolean neiLegacySearchProvidersEmpty() {
        try {
            Class<?> clazz = Class.forName("codechicken.nei.SearchField");
            Object providers = clazz.getField("searchProviders")
                .get(null);
            return providers == null || ((List<?>) providers).isEmpty();
        } catch (Throwable t) {
            // NEI 未装或 API 结构变化:交由 ProjectE 原逻辑(无 NEI 时走 DefaultSearch)
            return false;
        }
    }
}
