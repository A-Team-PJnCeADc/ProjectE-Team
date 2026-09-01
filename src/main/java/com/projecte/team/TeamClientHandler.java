package com.projecte.team;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 客户端主线程 tick:应用网络包排队的团队数据(EMC/知识)。
 * 不做任何判断/轮询启发式——只是把服务端推来的权威值写到 props 和
 * 打开着的转化桌容器上(见 TeamClientSync),多人同时开 GUI 也即时同步。
 */
@SideOnly(Side.CLIENT)
public class TeamClientHandler {

    /** 上一个 tick 的玩家实体:实体变化(登出/进服/换维度)时清空排队数据 */
    private EntityPlayer lastPlayer;

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
        if (player.worldObj == null) {
            return;
        }
        if (player != lastPlayer) {
            lastPlayer = player;
            TeamClientSync.clearPending();
            return;
        }
        TeamClientSync.applyPending();
    }
}
