package com.projecte.team;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.common.util.Constants;

import com.google.common.collect.Lists;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.playerData.TransmutationProps;
import moze_intel.projecte.utils.ItemHelper;

/**
 * 客户端数据应用器。设计原则:全服务端计算,客户端只接收并应用权威值,不做任何判断/轮询。
 * 线程模型:网络包处理器在 Netty IO 线程收到数据,只做 volatile 排队(queueEmc/queueKnowledge,
 * 绝对覆盖、幂等,last-write-wins);TeamClientHandler 在客户端主线程 tick 统一应用
 * (applyPending),避免 1.7.10 中 addScheduledTask 未映射的问题,同时天然合并同 tick 内
 * 的连续包(如 shift-click 批量存入的多次 EMC 广播)。
 * 应用内容:
 * - EMC:写 props + 打开着的转化桌容器 emc(GUI 每帧直接读 inv.emc,立即刷新)
 * - 知识:写 props + 打开着的转化桌 updateOutputs()(从 props 重建知识列表与输出槽)
 * 无论玩家是否开着 GUI、是否正在操作,收到即应用——不再有旧方案的
 * "稳定 N tick 才对齐"启发式,多人同时开 GUI 也即时同步。
 */
@SideOnly(Side.CLIENT)
public final class TeamClientSync {

    // 待应用状态(Netty 线程写,主线程 tick 读+清;volatile 保证可见性)
    private static volatile double pendingEmc;
    private static volatile boolean hasPendingEmc;
    private static volatile NBTTagCompound pendingKnowledge;
    private static volatile boolean hasPendingKnowledge;

    private TeamClientSync() {}

    /** 网络包入口(任意线程):排队最新 EMC 生效值 */
    public static void queueEmc(double emc) {
        pendingEmc = emc;
        hasPendingEmc = true;
    }

    /** 网络包入口(任意线程):排队最新知识全量列表 */
    public static void queueKnowledge(NBTTagCompound nbt) {
        pendingKnowledge = nbt;
        hasPendingKnowledge = true;
    }

    /** 主线程 tick 调用:应用所有排队数据(绝对覆盖,重复应用无害) */
    public static void applyPending() {
        if (hasPendingEmc) {
            hasPendingEmc = false;
            applyEmc(pendingEmc);
        }
        if (hasPendingKnowledge) {
            hasPendingKnowledge = false;
            applyKnowledge(pendingKnowledge);
        }
    }

    /** 玩家实体变化(登出/进服/换维度)时清空排队数据,避免旧会话的过期值覆盖新实体上已同步的 props */
    public static void clearPending() {
        hasPendingEmc = false;
        hasPendingKnowledge = false;
    }

    private static void applyEmc(double emc) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        writeEmc(player, emc);

        if (player.openContainer instanceof TransmutationContainer) {
            TransmutationInventory inv = ((TransmutationContainer) player.openContainer).transmutationInventory;
            if (inv != null) {
                inv.emc = emc;
                // 团队 EMC 变化后重建输出槽:EMC 价值 > 剩余 EMC 的物品从 GUI 显示中暂时隐藏
                // (updateOutputs 只做显示层过滤,不动 props/团队知识——隐藏不是忘记;
                // 无锁时按容器 emc 过滤,有锁时按锁价值过滤,锁价值 > emc 时输出清空,
                // 均为 ProjectE vanilla 语义;EMC 回升后再次重建即恢复显示)
                inv.updateOutputs();
            }
        }
    }

    private static void applyKnowledge(NBTTagCompound nbt) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null || nbt == null) {
            return;
        }
        List<ItemStack> knowledge = Lists.newArrayList();
        NBTTagList list = nbt.getTagList("knowledge", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack item = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
            if (item != null) {
                knowledge.add(item);
            }
        }
        writeKnowledge(player, knowledge);

        if (player.openContainer instanceof TransmutationContainer) {
            TransmutationInventory inv = ((TransmutationContainer) player.openContainer).transmutationInventory;
            if (inv != null) {
                // updateOutputs 从 props 重新读取知识并重建输出槽(仅客户端有效)
                inv.updateOutputs();
            }
        }
    }

    // props 写入(团队代理子类直写个人字段;兜底组装 NBT 走 readFromPacket)

    private static void writeEmc(EntityPlayer player, double emc) {
        TransmutationProps props = getProps(player);
        if (props == null) {
            return;
        }
        if (props instanceof TeamTransmutationProps) {
            ((TeamTransmutationProps) props).applyClientEmc(emc);
            return;
        }
        // 兜底(正常情况下 props 必为团队代理子类):组装完整 NBT 覆盖 emc,保留现有知识/锁槽
        NBTTagCompound compound = new NBTTagCompound();
        compound.setDouble("transmutationEmc", emc);
        compound.setTag("knowledge", toKnowledgeNbt(Transmutation.getKnowledge(player)));
        compound.setTag("inputlocks", ItemHelper.toIndexedNBTList(Transmutation.getInputsAndLock(player)));
        props.readFromPacket(compound);
    }

    private static void writeKnowledge(EntityPlayer player, List<ItemStack> knowledge) {
        TransmutationProps props = getProps(player);
        if (props == null) {
            return;
        }
        if (props instanceof TeamTransmutationProps) {
            ((TeamTransmutationProps) props).applyClientKnowledge(knowledge);
            return;
        }
        // 兜底:组装完整 NBT 覆盖知识,保留现有 emc/锁槽
        NBTTagCompound compound = new NBTTagCompound();
        compound.setDouble("transmutationEmc", Transmutation.getEmc(player));
        compound.setTag("knowledge", toKnowledgeNbt(knowledge));
        compound.setTag("inputlocks", ItemHelper.toIndexedNBTList(Transmutation.getInputsAndLock(player)));
        props.readFromPacket(compound);
    }

    private static TransmutationProps getProps(EntityPlayer player) {
        IExtendedEntityProperties props = player.getExtendedProperties(TransmutationProps.PROP_NAME);
        return props instanceof TransmutationProps ? (TransmutationProps) props : null;
    }

    private static NBTTagList toKnowledgeNbt(List<ItemStack> knowledge) {
        NBTTagList list = new NBTTagList();
        for (ItemStack stack : knowledge) {
            list.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        return list;
    }
}
