package com.projecte.team.network;

import net.minecraft.nbt.NBTTagCompound;

import com.projecte.team.MyMod;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * S->C:团队知识全量列表(服务端计算,绝对覆盖,幂等)。
 * 知识变更由 PlayerKnowledgeChangeEvent 驱动即时发送全队;
 * 客户端写入 props 并重建打开着的转化桌知识列表/输出槽(updateOutputs)。
 * 载荷为 {"knowledge": NBTTagList[ItemStack NBT]},与 ProjectE saveForPacket 同构。
 */
public class TeamKnowledgePKT implements IMessage {

    public NBTTagCompound nbt;

    public TeamKnowledgePKT() {}

    public TeamKnowledgePKT(NBTTagCompound nbt) {
        this.nbt = nbt;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        nbt = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, nbt);
    }

    public static class Handler implements IMessageHandler<TeamKnowledgePKT, IMessage> {

        @Override
        public IMessage onMessage(TeamKnowledgePKT message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                // 包处理器运行在 Netty IO 线程,客户端状态必须回主线程应用
                MyMod.proxy.handleTeamKnowledge(message.nbt);
            }
            return null;
        }
    }
}
