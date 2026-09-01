package com.projecte.team;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import moze_intel.projecte.playerData.TransmutationProps;
import moze_intel.projecte.utils.ItemHelper;

/**
 * ProjectE TransmutationProps 的团队代理子类。
 * 原理:ProjectE 所有 EMC/知识读写都通过 TransmutationProps.getDataFor(player)
 * 返回的实例上的虚方法完成(Transmutation.getEmc/setEmc/getKnowledge 等)。
 * 本类在玩家构造时抢先注册(事件优先级 HIGHEST),覆写这些方法:
 * - 服务端且玩家在队并开启共享 -> 读写团队共享池
 * - 否则(客户端 / 非团队成员 / 共享关闭)-> 走父类(个人数据)
 * 玩家个人的字段数据在加入团队时保持原样(冻结),离开团队后自动还原。
 *
 * 关键坑(持久化键冲突):ProjectE 的 PlayerEvents.onConstruct 也会无条件 register
 * 一个普通 TransmutationProps,其键被 Forge remap 成 "ProjectETransmutation1",
 * 但两个实例的 saveNBTData 都往同一个顶层键 "ProjectETransmutation" 写数据,
 * 普通实例(字段恒 0)后写会覆盖本类(字段=个人 EMC),导致独立玩家 EMC 存档丢失。
 * 故本类覆写 saveNBTData/loadNBTData,读写独立键 "ProjectETeamTransmutation"。
 */
public class TeamTransmutationProps extends TransmutationProps {

    private static final String SAVE_KEY = "ProjectETeamTransmutation";

    private final EntityPlayer player;

    public TeamTransmutationProps(EntityPlayer player) {
        super(player);
        this.player = player;
    }

    @Override
    protected double getTransmutationEmc() {
        if (!player.worldObj.isRemote && TeamManager.isSharingEMC(player)) {
            return TeamManager.getTeamEmc(player);
        }
        return super.getTransmutationEmc();
    }

    @Override
    protected void setTransmutationEmc(double transmutationEmc) {
        if (!player.worldObj.isRemote && TeamManager.isSharingEMC(player)) {
            TeamManager.setTeamEmc(player, transmutationEmc);
            return;
        }
        super.setTransmutationEmc(transmutationEmc);
    }

    @Override
    protected List<ItemStack> getKnowledge() {
        if (!player.worldObj.isRemote && TeamManager.isSharingKnowledge(player)) {
            return TeamManager.getTeamKnowledge(player);
        }
        return super.getKnowledge();
    }

    @Override
    protected NBTTagCompound saveForPacket() {
        if (!player.worldObj.isRemote && (TeamManager.isSharingEMC(player) || TeamManager.isSharingKnowledge(player))) {
            NBTTagCompound compound = new NBTTagCompound();
            compound.setDouble(
                "transmutationEmc",
                TeamManager.isSharingEMC(player) ? TeamManager.getTeamEmc(player) : super.getTransmutationEmc());

            NBTTagList knowledgeWrite = new NBTTagList();
            List<ItemStack> knowledge = TeamManager.isSharingKnowledge(player) ? TeamManager.getTeamKnowledge(player)
                : super.getKnowledge();
            for (ItemStack i : knowledge) {
                NBTTagCompound tag = i.writeToNBT(new NBTTagCompound());
                knowledgeWrite.appendTag(tag);
            }
            compound.setTag("knowledge", knowledgeWrite);

            // 输入锁槽是个人 UI 状态,不共享
            compound.setTag("inputlocks", ItemHelper.toIndexedNBTList(getInputLocks()));
            return compound;
        }
        return super.saveForPacket();
    }

    // 客户端专用写入(只应用服务端推来的权威值,不触发团队代理判断)

    /** 客户端:写入个人 EMC 字段(绕过团队代理判断,super 直写) */
    public void applyClientEmc(double emc) {
        super.setTransmutationEmc(emc);
    }

    /** 客户端:写入个人知识列表(绕过团队代理判断,super 直写) */
    public void applyClientKnowledge(List<ItemStack> knowledge) {
        List<ItemStack> list = super.getKnowledge();
        list.clear();
        list.addAll(knowledge);
    }

    /**
     * 持久化到独立键,避免与 ProjectE 普通 TransmutationProps 写同一个键互相覆盖。
     * 只保存个人字段(super 方法直接读写 private 字段,不受本类覆写影响)。
     */
    @Override
    public void saveNBTData(NBTTagCompound compound) {
        NBTTagCompound properties = new NBTTagCompound();
        properties.setDouble("transmutationEmc", super.getTransmutationEmc());

        NBTTagList knowledgeWrite = new NBTTagList();
        for (ItemStack i : super.getKnowledge()) {
            knowledgeWrite.appendTag(i.writeToNBT(new NBTTagCompound()));
        }
        properties.setTag("knowledge", knowledgeWrite);
        properties.setTag("inputlock", ItemHelper.toIndexedNBTList(getInputLocks()));
        compound.setTag(SAVE_KEY, properties);
    }

    @Override
    public void loadNBTData(NBTTagCompound compound) {
        NBTTagCompound properties = compound.getCompoundTag(SAVE_KEY);
        if (properties.hasNoTags()) {
            // 兼容旧存档(数据曾在 "ProjectETransmutation" 键下)
            properties = compound.getCompoundTag(TransmutationProps.PROP_NAME);
        }

        super.setTransmutationEmc(properties.getDouble("transmutationEmc"));

        List<ItemStack> knowledge = super.getKnowledge();
        knowledge.clear();
        NBTTagList list = properties.getTagList("knowledge", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack item = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
            if (item != null) {
                knowledge.add(item);
            }
        }
        ItemHelper.compactItemListNoStacksize(knowledge);

        NBTTagList list2 = properties.getTagList("inputlock", Constants.NBT.TAG_COMPOUND);
        setInputLocks(ItemHelper.copyIndexedNBTToArray(list2, new ItemStack[9]));
    }
}
