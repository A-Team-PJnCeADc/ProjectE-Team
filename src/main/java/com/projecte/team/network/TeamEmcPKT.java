package com.projecte.team.network;

import java.nio.charset.StandardCharsets;

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
 *
 * <p>
 * 装了 PE-E-GTNH 时额外带一个**精确十进制字符串**(BigInteger 团队池),客户端存进
 * {@code TeamClientSync.teamEmcBig} 缓存:double 字段超过 2^53 后只是近似值,而缓存可以逐位精确
 * (PE-E-GTNH 的转化桌 GUI 文本会优先读这个缓存)。
 */
public class TeamEmcPKT implements IMessage {

    public double emc;
    /** big 模式下的精确值(十进制字符串);非 big 模式为 null。 */
    public String emcBig;

    public TeamEmcPKT() {}

    public TeamEmcPKT(double emc) {
        this(emc, null);
    }

    public TeamEmcPKT(double emc, String emcBig) {
        this.emc = emc;
        this.emcBig = emcBig;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        emc = buf.readDouble();
        if (buf.readBoolean()) {
            int length = buf.readInt();
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            emcBig = new String(bytes, StandardCharsets.UTF_8);
        } else {
            emcBig = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(emc);
        buf.writeBoolean(emcBig != null);
        if (emcBig != null) {
            byte[] bytes = emcBig.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    public static class Handler implements IMessageHandler<TeamEmcPKT, IMessage> {

        @Override
        public IMessage onMessage(TeamEmcPKT message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                // 包处理器运行在 Netty IO 线程,客户端状态必须回主线程应用
                MyMod.proxy.handleTeamEmc(message.emc, message.emcBig);
            }
            return null;
        }
    }
}
