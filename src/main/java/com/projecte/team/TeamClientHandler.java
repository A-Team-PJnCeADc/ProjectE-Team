package com.projecte.team;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.playerData.Transmutation;

/**
 * 客户端:让打开着的转化桌 GUI 跟随团队 EMC / 知识实时刷新。
 * 背景:转化桌 GUI 显示的 EMC 来自客户端容器(TransmutationInventory.emc),
 * 知识列表是打开时从 props 拷贝的快照(updateOutputs 只在开 GUI / 点槽位时重建);
 * 而服务端广播只更新玩家 props,不会刷新已打开的容器;因此队友操作时,
 * 开着 GUI 的玩家看不到数字/知识变化(关掉重开才更新)。
 * 策略:
 * - EMC:容器 EMC 连续若干 tick 稳定(说明玩家本人没有在操作),且与
 * props(团队权威值)不一致时,把容器显示值对齐到团队值;玩家本人操作时
 * 容器值会持续变化,稳定计数被重置,因此不会干扰本人操作的即时反馈。
 * - 知识:props 的知识列表(服务端同步的权威值)与容器快照长度不一致时,
 * 稳定后调用 updateOutputs() 从 props 重建知识列表与输出槽。
 */
@SideOnly(Side.CLIENT)
public class TeamClientHandler {

    /** 连续稳定多少个 tick 后才对齐(约 500ms,长于服务端广播周期,避免本人操作后短暂回滚) */
    private static final int STABLE_THRESHOLD = 10;

    private int stableTicks = 0;
    private double lastInvEmc = Double.NaN;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        EntityPlayer player = mc.thePlayer;
        if (!(player.openContainer instanceof TransmutationContainer)) {
            stableTicks = 0;
            lastInvEmc = Double.NaN;
            return;
        }
        TransmutationInventory inv = ((TransmutationContainer) player.openContainer).transmutationInventory;
        if (inv == null) {
            return;
        }
        double propsEmc = Transmutation.getEmc(player);

        if (Double.isNaN(lastInvEmc)) {
            lastInvEmc = inv.emc;
            return;
        }
        if (Math.abs(inv.emc - lastInvEmc) > 0.001) {
            // 容器值在变化:玩家本人在操作,重置稳定计数
            stableTicks = 0;
            lastInvEmc = inv.emc;
        } else {
            stableTicks++;
        }
        if (stableTicks >= STABLE_THRESHOLD && Math.abs(inv.emc - propsEmc) > 0.001) {
            // EMC 对齐只在团队共享 EMC 时生效:共享模式下增量已实时入团队池并广播,
            // props(打包团队池)才是权威值;非共享(独立玩家/未开共享)时容器值是
            // 进行中的操作、props 是上次提交值,对齐会把操作中的显示拉回旧值
            // (如存入 300 被拉回 0),必须跳过。
            if (TeamManager.isSharingEMC(player)) {
                inv.emc = propsEmc;
                lastInvEmc = propsEmc;
            }
        }
        // 知识列表刷新:props(服务端权威)与容器快照不一致 = 队友学习/遗忘,
        // 容器稳定(玩家没在操作)后从 props 重建知识列表与输出槽。
        // 仅在团队共享知识时生效(非共享时 props 与快照由本人操作本地同步维护,
        // 且 props 可能滞后于操作,不应重建)。玩家本人学习时客户端本地模拟会
        // 同步更新 props 与快照,长度一致,不会误触发。
        if (TeamManager.isSharingKnowledge(player) && stableTicks >= STABLE_THRESHOLD) {
            List<ItemStack> propsKnowledge = Transmutation.getKnowledge(player);
            if (propsKnowledge != null && inv.knowledge.size() != propsKnowledge.size()) {
                inv.updateOutputs();
                stableTicks = 0;
                lastInvEmc = inv.emc;
            }
        }
    }
}
