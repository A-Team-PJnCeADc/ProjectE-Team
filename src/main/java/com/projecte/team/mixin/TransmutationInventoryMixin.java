package com.projecte.team.mixin;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecte.team.TeamManager;

import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;

/**
 * 转化桌 EMC 实时提交:ProjectE 的 addEmc/removeEmc 是玩家 EMC 变化的唯一写入口
 * (容器操作时立即修改内存,关闭 GUI 才提交)。在方法尾部把增量实时转发到团队池,
 * 再由 TeamManager 通过轻量 TeamEmcPKT 网络包即时广播全队,使成员不关闭 GUI
 * 也能立即看到队友的 EMC 变化(绝对覆盖,幂等)。
 * 客户端(EntityPlayerSP)也走这些方法,但通过 worldObj.isRemote 跳过,不重复提交。
 */
@Mixin(TransmutationInventory.class)
public abstract class TransmutationInventoryMixin {

    @Shadow(remap = false)
    private EntityPlayer player;

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
