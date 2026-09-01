package com.projecte.team.network;

import com.projecte.team.MyMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * S->C:团队 EMC 生效值(服务端计算,绝对覆盖,幂等)。
 * 每次 addEmc/removeEmc(mixin 转发到团队池)后即时发送,客户端直接应用,
 * 打开着的转化桌 GUI 数字立即刷新,无需任何客户端轮询/稳定性判断。
 */
public class TeamEmcPKT implements IMessage {

    public double emc;

    public TeamEmcPKT() {}

    public TeamEmcPKT(double emc) {
        this.emc = emc;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        emc = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(emc);
    }

    public static class Handler implements IMessageHandler<TeamEmcPKT, IMessage> {

        @Override
        public IMessage onMessage(TeamEmcPKT message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                // 包处理器运行在 Netty IO 线程,客户端状态必须回主线程应用
                MyMod.proxy.handleTeamEmc(message.emc);
            }
            return null;
        }
    }
}
