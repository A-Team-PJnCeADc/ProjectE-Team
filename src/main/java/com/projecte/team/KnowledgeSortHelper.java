package com.projecte.team;

import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;

import moze_intel.projecte.utils.EMCHelper;

/**
 * 转化桌输出槽重建的热点优化。
 *
 * <p>
 * ProjectE 的 {@code TransmutationInventory.updateOutputs(boolean)} 用
 * {@code Collections.sort(knowledge, Comparators.ITEMSTACK_EMC_DESCENDING)} 把知识列表按 EMC 降序排列,
 * 而该比较器每次比较都要调用两次 {@code EMCHelper.getEmcValue}(单次调用自己就要新建并复制
 * SimpleStack、还要分配一个 HashMap 装附魔),即排序一趟的 EMC 查询次数是 O(n log n)。
 * 团队模式下知识是全队并集(GTNH 里上千条很常见),每次 GUI 重建(开桌、翻页、每次键入搜索字符、
 * 每次 EMC/知识同步)都要跑一遍,表现为界面卡顿。
 *
 * <p>
 * 本类先线性扫一遍算出每条知识的 EMC,再按 (EMC 降序, 原下标升序) 排序,与 ProjectE 的稳定排序
 * 结果完全等价(EMC 相同的物品保持原有相对顺序),EMC 查询次数由 O(n log n) 降为 O(n)。
 */
public final class KnowledgeSortHelper {

    private KnowledgeSortHelper() {}

    /**
     * 等价于 {@code Collections.sort(knowledge, Comparators.ITEMSTACK_EMC_DESCENDING)},
     * 但每个物品只查一次 EMC。排序结果写回传入的列表(原地更新元素,不增删)。
     */
    public static void sortByEmcDescending(List<ItemStack> knowledge) {
        int size = knowledge.size();
        if (size < 2) {
            return;
        }
        long[] keys = new long[size];
        for (int i = 0; i < size; i++) {
            // 高 32 位放 EMC(负数比较语义与 ProjectE 比较器一致:EMC 小的排在后面),
            // 低 32 位放 0xFFFFFFFF - i,使 EMC 相同时原下标小的排前面(= 稳定排序)
            keys[i] = ((long) EMCHelper.getEmcValue(knowledge.get(i)) << 32) | (0xFFFFFFFFL - i);
        }
        Arrays.sort(keys);
        ItemStack[] sorted = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            int original = (int) (0xFFFFFFFFL - (keys[size - 1 - i] & 0xFFFFFFFFL));
            sorted[i] = knowledge.get(original);
        }
        for (int i = 0; i < size; i++) {
            knowledge.set(i, sorted[i]);
        }
    }
}
