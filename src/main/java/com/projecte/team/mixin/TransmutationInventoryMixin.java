package com.projecte.team.mixin;

import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecte.team.KnowledgeSortHelper;
import com.projecte.team.TeamManager;

import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;

/**
 * 转化桌 EMC 实时提交:ProjectE 的 addEmc/removeEmc 是玩家 EMC 变化的唯一写入口
 * (容器操作时立即修改内存,关闭 GUI 才提交)。在方法尾部把增量实时转发到团队池,
 * 再由 TeamManager 通过轻量 TeamEmcPKT 网络包即时广播全队,使成员不关闭 GUI
 * 也能立即看到队友的 EMC 变化(绝对覆盖,幂等)。
 * 客户端(EntityPlayerSP)也走这些方法,但通过 worldObj.isRemote 跳过,不重复提交。
 *
 * <p>
 * 另含输出槽重建的性能修复:团队模式下知识是全队并集(GTNH 中上千条),而
 * updateOutputs 每次重建都要按 EMC 排序知识列表(比较器内部每条物品要查 O(log n) 次 EMC),
 * 搜索/买入/卖出时频繁重建会明显卡顿。这里把该排序替换为每条只查一次 EMC 的等价实现。
 */
@Mixin(TransmutationInventory.class)
public abstract class TransmutationInventoryMixin {

    @Shadow(remap = false)
    private EntityPlayer player;

    /**
     * 把 updateOutputs 内的 {@code Collections.sort(knowledge, Comparators.ITEMSTACK_EMC_DESCENDING)}
     * 换成 KnowledgeSortHelper 的等价排序(结果一致,EMC 查询次数 O(n log n) -> O(n))。
     */
    @Redirect(
        method = "updateOutputs(Z)V",
        at = @At(value = "INVOKE", target = "Ljava/util/Collections;sort(Ljava/util/List;Ljava/util/Comparator;)V"),
        remap = false)
    private void projecteteam$fastSortKnowledgeByEmc(List<ItemStack> knowledge, Comparator<ItemStack> comparator) {
        KnowledgeSortHelper.sortByEmcDescending(knowledge);
    }

    /** EMC 增加(存入物品/烧毁):实时转发到团队池 */
    @Inject(method = "addEmc", at = @At("TAIL"), remap = false)
    private void projecteteam$onAddEmc(double value, CallbackInfo ci) {
        if (player != null && !player.worldObj.isRemote) {
            TeamManager.onEmcAdd(player, value);
        }
    }

    /** EMC 减少(合成/取物品):实时转发到团队池 */
    @Inject(method = "removeEmc", at = @At("TAIL"), remap = false)
    private void projecteteam$onRemoveEmc(double value, CallbackInfo ci) {
        if (player != null && !player.worldObj.isRemote) {
            TeamManager.onEmcRemove(player, value);
        }
    }
}
