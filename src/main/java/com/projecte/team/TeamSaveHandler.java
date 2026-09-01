package com.projecte.team;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 服务器 tick 周期内检查团队数据是否有改动,有则立即写盘(即时持久化,
 * 不再依赖原版约 30 秒的保存周期;多次连续修改合并为一次写盘)。
 */
public class TeamSaveHandler {

    /** 每 5 tick(约 250ms)检查一次 */
    private static final int CHECK_INTERVAL = 5;

    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter % CHECK_INTERVAL != 0) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers.length == 0) {
            return;
        }
        TeamDataStore store = TeamDataStore.get(server.worldServers[0]);
        if (store.isDirty()) {
            server.worldServers[0].mapStorage.saveAllData();
        }
        // 合并广播待同步团队的数据(知识等批量写操作延迟到此处统一发送)
        TeamManager.flushPendingSyncs();
        // 非共享玩家开着转化桌:容器 EMC 定期提交到个人 props
        // (防止不关 GUI 直接退出游戏导致 EMC 丢失;共享玩家由 mixin 实时入团队池)
        TeamManager.flushOpenTransmutationContainers();
    }

    /** 玩家登出时兜底提交其开着的转化桌容器 EMC(覆盖"没关 GUI 就登出"的边界) */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            TeamManager.commitContainerEmc((EntityPlayerMP) event.player);
        }
    }
}
