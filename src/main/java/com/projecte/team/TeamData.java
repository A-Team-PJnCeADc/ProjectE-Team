package com.projecte.team;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.google.common.collect.Lists;

import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;

/**
 * 团队数据。teamId 为创建团队时的队长 UUID,转让队长后保持不变。
 * EMC 与知识是团队共享池;成员的个人数据保存在各自的 TransmutationProps 中(加入团队时冻结)。
 */
public class TeamData {

    public final UUID teamId;
    private String name;
    private UUID owner;
    private final List<UUID> members = Lists.newArrayList();
    private boolean shareEMC = true;
    private boolean shareKnowledge = true;
    private double emc;
    private final List<ItemStack> knowledge = Lists.newArrayList();

    public TeamData(UUID teamId, UUID owner) {
        this.teamId = teamId;
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** 显示用团队名:未命名(旧存档)时回退为 Team + 短 ID */
    public String getDisplayName() {
        return name != null && !name.isEmpty() ? name
            : "Team " + teamId.toString()
                .substring(0, 4);
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public List<UUID> getMembers() {
        return members;
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean isShareEMC() {
        return shareEMC;
    }

    public void setShareEMC(boolean shareEMC) {
        this.shareEMC = shareEMC;
    }

    public boolean isShareKnowledge() {
        return shareKnowledge;
    }

    public void setShareKnowledge(boolean shareKnowledge) {
        this.shareKnowledge = shareKnowledge;
    }

    public double getEmc() {
        return emc;
    }

    public void setEmc(double emc) {
        this.emc = emc;
    }

    public List<ItemStack> getKnowledge() {
        return knowledge;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setString("owner", owner.toString());
        if (name != null) {
            compound.setString("name", name);
        }
        compound.setBoolean("shareEMC", shareEMC);
        compound.setBoolean("shareKnowledge", shareKnowledge);
        compound.setDouble("emc", emc);

        NBTTagList memberList = new NBTTagList();
        for (UUID uuid : members) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("uuid", uuid.toString());
            memberList.appendTag(tag);
        }
        compound.setTag("members", memberList);

        NBTTagList knowledgeList = new NBTTagList();
        for (ItemStack stack : knowledge) {
            NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
            knowledgeList.appendTag(tag);
        }
        compound.setTag("knowledge", knowledgeList);
        return compound;
    }

    public static TeamData readFromNBT(NBTTagCompound compound) {
        TeamData data = new TeamData(
            UUID.fromString(compound.getString("teamId")),
            UUID.fromString(compound.getString("owner")));
        data.name = compound.getString("name");
        data.shareEMC = compound.getBoolean("shareEMC");
        data.shareKnowledge = compound.getBoolean("shareKnowledge");
        data.emc = compound.getDouble("emc");

        NBTTagList memberList = compound.getTagList("members", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < memberList.tagCount(); i++) {
            NBTTagCompound tag = memberList.getCompoundTagAt(i);
            data.members.add(UUID.fromString(tag.getString("uuid")));
        }

        NBTTagList knowledgeList = compound.getTagList("knowledge", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < knowledgeList.tagCount(); i++) {
            ItemStack stack = ItemStack.loadItemStackFromNBT(knowledgeList.getCompoundTagAt(i));
            if (stack != null) {
                data.knowledge.add(stack);
            }
        }
        return data;
    }

    /** 清理没有 EMC 的过期知识(与 TransmutationProps 行为一致) */
    public void pruneStaleKnowledge() {
        Iterator<ItemStack> iter = knowledge.iterator();
        while (iter.hasNext()) {
            if (!EMCHelper.doesItemHaveEmc(iter.next())) {
                iter.remove();
            }
        }
    }

    /** 紧凑 + 去重(与 TransmutationProps 行为一致) */
    public void pruneDuplicateKnowledge() {
        ItemHelper.compactItemListNoStacksize(knowledge);
        for (ItemStack s : knowledge) {
            if (s.stackSize > 1) {
                s.stackSize = 1;
            }
        }
    }
}
