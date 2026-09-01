package com.projecte.team.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 团队数据同步网络频道(自定义包,服务端计算权威值,客户端只接收并应用)。
 * 取代旧方案的 ProjectE Transmutation.sync 广播 + 客户端轮询对齐:
 * - TeamEmcPKT(0):EMC 变化时实时推送全队,轻量(8 字节)
 * - TeamKnowledgePKT(1):知识变化时(PlayerKnowledgeChangeEvent 驱动)推送全队
 * 两个包都是 S->C;客户端操作仍走原版/ProjectE 既有包,无 C->S 自定义包。
 */
public final class PacketHandler {

    public static final String CHANNEL_NAME = "PETEAM";

    private static final SimpleNetworkWrapper HANDLER = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);

    private PacketHandler() {}

    /** 在 preInit 注册消息(仅 S->C 方向) */
    public static void register() {
        HANDLER.registerMessage(TeamEmcPKT.Handler.class, TeamEmcPKT.class, 0, Side.CLIENT);
        HANDLER.registerMessage(TeamKnowledgePKT.Handler.class, TeamKnowledgePKT.class, 1, Side.CLIENT);
    }

    /** 向单个玩家发送团队 EMC(服务端计算好的生效值) */
    public static void sendTo(EntityPlayerMP player, IMessage message) {
        if (player != null && player.playerNetServerHandler != null) {
            HANDLER.sendTo(message, player);
        }
    }
}
