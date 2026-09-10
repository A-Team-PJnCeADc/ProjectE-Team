package com.projecte.team;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import moze_intel.projecte.utils.Comparators;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;

/**
 * 团队数据。teamId 为创建团队时的队长 UUID,转让队长后保持不变。
 * EMC 与知识是团队共享池;成员的个人数据保存在各自的 TransmutationProps 中(加入团队时冻结)。
 */
public class TeamData {

    /** 过期知识清理的最小间隔(毫秒):ProjectE 每次读知识都会全表扫一遍,团队知识上千条时不划算 */
    private static final long STALE_PRUNE_INTERVAL_MS = 1000;

    public final UUID teamId;
    private String name;
    private UUID owner;
    private final List<UUID> members = Lists.newArrayList();
    private boolean shareEMC = true;
    private boolean shareKnowledge = true;
    private double emc;
    private final List<ItemStack> knowledge = Lists.newArrayList();
    private long lastStalePruneAt;

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

    /**
     * 带节流的过期知识清理:距上次清理不足 {@link #STALE_PRUNE_INTERVAL_MS} 时直接跳过。
     * 该清理本质是全表扫描(每条一次 EMC 查询),而 ProjectE 每次读知识都会调一次,
     * 团队知识上千条时会成为常态开销;过期知识只可能在 EMC 映射变化后出现,秒级延迟无影响。
     *
     * @return 是否真的删掉了条目(调用方据此标记脏数据)
     */
    public boolean pruneStaleKnowledgeIfDue(long nowMillis) {
        if (nowMillis - lastStalePruneAt < STALE_PRUNE_INTERVAL_MS) {
            return false;
        }
        lastStalePruneAt = nowMillis;
        int before = knowledge.size();
        pruneStaleKnowledge();
        return knowledge.size() != before;
    }

    /**
     * 紧凑 + 去重(与 TransmutationProps 私有的 pruneDuplicateKnowledge 行为一致:相同物品(含 NBT)
     * 的堆叠合并到首次出现处、其余置 0 后移除,再按物品排序,最后把堆叠数压成 1)。
     *
     * <p>
     * 注意不要退回 {@code ItemHelper.compactItemListNoStacksize}:它是 O(n²) 双层循环,且每次比较都要
     * copy 两个 ItemStack。ProjectE 每学一个新物品都会走到这里(add 路径),团队知识上千条时单次
     * 学习就要做上万次比较 + 数万次分配,是"卖出/学习新物品"卡顿的来源之一。这里用哈希索引
     * 把重复检测降到 O(n)。
     */
    public void pruneDuplicateKnowledge() {
        Map<StackKey, ItemStack> firstSeen = Maps.newHashMapWithExpectedSize(knowledge.size());
        for (int i = 0; i < knowledge.size(); i++) {
            ItemStack stack = knowledge.get(i);
            StackKey key = new StackKey(stack);
            ItemStack first = firstSeen.get(key);
            if (first == null) {
                firstSeen.put(key, stack);
                continue;
            }
            first.stackSize += stack.stackSize;
            stack.stackSize = 0;
        }
        Collections.sort(knowledge, Comparators.ITEMSTACK_ASCENDING);
        ItemHelper.trimItemList(knowledge);
        for (ItemStack s : knowledge) {
            if (s.stackSize > 1) {
                s.stackSize = 1;
            }
        }
    }

    /**
     * ItemStack 的哈希键,等价语义为 {@code ItemStack.areItemStacksEqual} 归一化后的比较
     * (物品 + 损伤值 + NBT 深比较)。
     */
    private static final class StackKey {

        private final Item item;
        private final int damage;
        private final NBTTagCompound tag;

        StackKey(ItemStack stack) {
            this.item = stack.getItem();
            this.damage = stack.getItemDamage();
            this.tag = stack.getTagCompound();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StackKey)) {
                return false;
            }
            StackKey other = (StackKey) obj;
            if (item != other.item || damage != other.damage) {
                return false;
            }
            return tag == null ? other.tag == null : tag.equals(other.tag);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(item);
            result = 31 * result + damage;
            result = 31 * result + (tag == null ? 0 : tag.hashCode());
            return result;
        }
    }
}
