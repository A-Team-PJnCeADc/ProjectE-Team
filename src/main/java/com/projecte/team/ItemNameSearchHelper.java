package com.projecte.team;

import java.util.Locale;

import net.minecraft.item.ItemStack;

import moze_intel.projecte.utils.ItemSearchHelper;

/**
 * 名字包含匹配过滤器(ProjectE 无 NEI 环境的 DefaultSearch 的公共等价实现)。
 * ItemSearchHelper 的嵌套 DefaultSearch 是 private,无法直接复用,故在此实现同语义版本
 * 供 ItemSearchHelperMixin 在 GTNH(NEI 旧 provider 列表为空)时兜底返回。
 *
 * <p>
 * 注意:本类必须放在 mixin 包(com.projecte.team.mixin)之外——mixin 配置声明的包内
 * 所有类都被 Mixin 框架视为 mixin 类,禁止被注入代码直接引用(IllegalClassLoadError)。
 */
public class ItemNameSearchHelper extends ItemSearchHelper {

    public ItemNameSearchHelper(String searchString) {
        super(searchString);
    }

    @Override
    protected boolean doesItemMatchFilter_(ItemStack stack) {
        // 无搜索词时直接放行:默认实现也会先算显示名(StatCollector 翻译 + 字符串分配),
        // 而 GUI 每次重建(EMC/知识同步、翻页、开桌)都会对整张知识表跑一遍过滤
        if (searchString.isEmpty()) {
            return true;
        }
        String displayName;
        try {
            displayName = stack.getDisplayName()
                .toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            // 与原 DefaultSearch 一致:显示名解析异常时不移除该物品
            return true;
        }
        if (displayName == null) {
            return false;
        }
        return displayName.contains(searchString);
    }
}
