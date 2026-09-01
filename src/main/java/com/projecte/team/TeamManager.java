package com.projecte.team;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.IExtendedEntityProperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Lists;
import com.projecte.team.network.PacketHandler;
import com.projecte.team.network.TeamEmcPKT;
import com.projecte.team.network.TeamKnowledgePKT;

import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.playerData.TransmutationProps;
import moze_intel.projecte.utils.Constants;

/**
 * 团队系统核心:团队 CRUD、EMC/知识共享代理、数据同步。
 * 设计:玩家加入(创建)团队后,EMC 与知识读写通过 TeamTransmutationProps 的多态覆写
 * 重定向到团队共享池;玩家个人的 TransmutationProps 字段保持加入时的值(冻结),
 * 离开团队后自动还原。
 */
public final class TeamManager {

    private static final Logger LOG = LogManager.getLogger("ProjectETeam");

    private TeamManager() {}

    // 基础查询

    private static TeamDataStore getStore() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers.length == 0) {
            return null;
        }
        WorldServer overworld = server.worldServers[0];
        return TeamDataStore.get(overworld);
    }

    public static TeamData getTeam(EntityPlayer player) {
        TeamDataStore store = getStore();
        if (store == null) {
            return null;
        }
        return store.getTeamForPlayer(player.getUniqueID());
    }

    public static boolean isInTeam(EntityPlayer player) {
        return getTeam(player) != null;
    }

    public static boolean isOwner(EntityPlayer player) {
        TeamData team = getTeam(player);
        return team != null && team.isOwner(player.getUniqueID());
    }

    // 共享代理判断

    /** 玩家是否正在共享团队 EMC */
    public static boolean isSharingEMC(EntityPlayer player) {
        TeamData team = getTeam(player);
        return team != null && team.isShareEMC();
    }

    /** 玩家是否正在共享团队知识 */
    public static boolean isSharingKnowledge(EntityPlayer player) {
        TeamData team = getTeam(player);
        return team != null && team.isShareKnowledge();
    }

    // EMC 代理

    public static double getTeamEmc(EntityPlayer player) {
        TeamData team = getTeam(player);
        return team == null ? 0 : team.getEmc();
    }

    /**
     * Mixin 钩子(TransmutationInventoryMixin):转化桌 EMC 增加时实时转发到团队池。
     * 仅在服务端被调用(客户端已由 Mixin 内 isRemote 判断跳过)。
     * 上限与 ProjectE 容器一致钳制在 TILE_MAX_EMC:否则团队池可超上限,
     * 对齐成员容器后 hasMaxedEmc() 为真会阻止 shift-click 存入。
     */
    public static void onEmcAdd(EntityPlayer player, double value) {
        TeamData team = getTeam(player);
        if (team != null && team.isShareEMC()) {
            team.setEmc(Math.min(Constants.TILE_MAX_EMC, Math.max(0, team.getEmc() + value)));
            markDirty();
            syncTeamEmc(team);
        }
    }

    /**
     * Mixin 钩子(TransmutationInventoryMixin):转化桌 EMC 减少时实时转发到团队池。
     * 团队 EMC 下限钳制为 0,避免并发消耗导致负值。
     */
    public static void onEmcRemove(EntityPlayer player, double value) {
        TeamData team = getTeam(player);
        if (team != null && team.isShareEMC()) {
            team.setEmc(Math.max(0, team.getEmc() - value));
            markDirty();
            syncTeamEmc(team);
        }
    }

    /**
     * closeInventory 的提交入口。团队共享模式下 EMC 增量已由 Mixin(addEmc/removeEmc)
     * 实时提交,容器快照提交会覆盖团队池,这里直接忽略并顺带同步一次;
     * 非共享模式(理论上不经过此方法)兜底为绝对覆盖。
     */
    public static void setTeamEmc(EntityPlayer player, double emc) {
        TeamData team = getTeam(player);
        if (team == null) {
            return;
        }
        if (team.isShareEMC()) {
            syncTeamEmc(team);
            return;
        }
        team.setEmc(Math.max(0, emc));
        markDirty();
        syncTeamEmc(team);
    }

    // 知识代理

    /**
     * 返回团队共享知识列表。调用方(ProjectE 的 Transmutation)会直接对该列表
     * 执行 add/remove/clear,因此每次读取时标记脏数据,保证修改能被持久化。
     */
    public static List<ItemStack> getTeamKnowledge(EntityPlayer player) {
        TeamData team = getTeam(player);
        if (team == null) {
            return Lists.newArrayList();
        }
        int sizeBefore = team.getKnowledge()
            .size();
        team.pruneStaleKnowledge();
        if (team.getKnowledge()
            .size() != sizeBefore) {
            markDirty();
        }
        // 活视图:写操作(add/remove/clear)会立即标记脏数据,由 TeamSaveHandler 即时落盘
        return new TeamKnowledgeView(team);
    }

    /** 标记团队数据为脏(下个保存检查周期内写盘) */
    static void markDirty() {
        TeamDataStore store = getStore();
        if (store != null) {
            store.markDirty();
        }
    }

    /**
     * 非共享模式玩家开着转化桌时,定期把容器 EMC 提交到个人 props。
     * 背景:ProjectE 只在关闭 GUI(onContainerClosed -> setEmc)时提交容器 EMC;
     * 玩家不关 GUI 直接退出游戏时容器未提交,EMC 丢失(重进为 0)。
     * 团队共享玩家由 mixin 实时写入团队池,退出不丢,不受影响;
     * 此处只补非共享玩家(独立玩家 / 团队未开共享)。
     */
    public static void flushOpenTransmutationContainers() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP) {
                commitContainerEmc((EntityPlayerMP) o);
            }
        }
    }

    /**
     * 把单个玩家开着的转化桌容器 EMC 提交到个人 props(共享玩家跳过,由 mixin 维护团队池)。
     * 幂等:仅在容器值与 props 不一致时提交,并打日志便于排查"EMC 丢失"问题。
     */
    public static void commitContainerEmc(EntityPlayerMP player) {
        if (isSharingEMC(player)) {
            return;
        }
        if (player.openContainer instanceof TransmutationContainer) {
            TransmutationInventory inv = ((TransmutationContainer) player.openContainer).transmutationInventory;
            if (inv != null) {
                double propsEmc = Transmutation.getEmc(player);
                if (Math.abs(inv.emc - propsEmc) > 0.001) {
                    Transmutation.setEmc(player, inv.emc);
                    LOG.info(
                        "commit container EMC: {} container={} props(prev)={}",
                        player.getCommandSenderName(),
                        inv.emc,
                        propsEmc);
                }
            }
        }
    }

    // 数据同步(自定义网络包,全服务端计算,客户端只接收应用)

    /** 玩家生效的 EMC:共享则团队池,否则个人字段 */
    private static double getEffectiveEmc(EntityPlayer player) {
        if (isSharingEMC(player)) {
            TeamData team = getTeam(player);
            return team == null ? 0 : team.getEmc();
        }
        return Transmutation.getEmc(player);
    }

    /** 玩家生效的知识:共享则团队池,否则个人字段 */
    private static List<ItemStack> getEffectiveKnowledge(EntityPlayer player) {
        if (isSharingKnowledge(player)) {
            return getTeamKnowledge(player);
        }
        return Transmutation.getKnowledge(player);
    }

    /** 把生效知识打包成网络载荷(与 ProjectE saveForPacket 的 knowledge 段同构) */
    private static NBTTagCompound buildKnowledgePayload(List<ItemStack> knowledge) {
        NBTTagCompound compound = new NBTTagCompound();
        NBTTagList knowledgeWrite = new NBTTagList();
        for (ItemStack i : knowledge) {
            NBTTagCompound tag = i.writeToNBT(new NBTTagCompound());
            knowledgeWrite.appendTag(tag);
        }
        compound.setTag("knowledge", knowledgeWrite);
        return compound;
    }

    /** EMC 变化时向全队推送(轻量包,绝对覆盖,幂等;addEmc/removeEmc 每笔操作即时触发) */
    public static void syncTeamEmc(TeamData team) {
        if (team == null || MinecraftServer.getServer() == null) {
            return;
        }
        syncEmcToMember(team, team.getOwner());
        for (UUID member : team.getMembers()) {
            syncEmcToMember(team, member);
        }
    }

    /**
     * 向单个在线成员推送 EMC(离线成员跳过,登录时 ProjectE 的登录同步会补齐),并把它开着的
     * 服务端容器 emc 对齐到团队池:否则队友烧毁的 EMC 进入团队池后,其他成员已开着的容器 emc
     * 仍是打开时的旧快照,服务端 writeIntoOutputSlot/canTakeStack 校验会因 emc 不足拒绝取物品
     * (表现为"只有放入者能取出")。共享 EMC 时容器 emc 的权威值就是团队池。
     */
    private static void syncEmcToMember(TeamData team, UUID uuid) {
        EntityPlayerMP player = getOnlinePlayer(uuid);
        if (player != null) {
            PacketHandler.sendTo(player, new TeamEmcPKT(getEffectiveEmc(player)));
            if (team.isShareEMC() && player.openContainer instanceof TransmutationContainer) {
                TransmutationInventory inv = ((TransmutationContainer) player.openContainer).transmutationInventory;
                if (inv != null) {
                    inv.emc = team.getEmc();
                }
            }
        }
    }

    /** 知识变化时向全队推送全量列表(由 PlayerKnowledgeChangeEvent 驱动,事件在变更完成后触发) */
    public static void syncTeamKnowledge(TeamData team) {
        if (team == null || MinecraftServer.getServer() == null) {
            return;
        }
        syncKnowledgeToMember(team, team.getOwner());
        for (UUID member : team.getMembers()) {
            syncKnowledgeToMember(team, member);
        }
    }

    private static void syncKnowledgeToMember(TeamData team, UUID uuid) {
        EntityPlayerMP player = getOnlinePlayer(uuid);
        if (player != null) {
            PacketHandler.sendTo(player, new TeamKnowledgePKT(buildKnowledgePayload(getEffectiveKnowledge(player))));
        }
    }

    /** 全量同步(模式切换/设置变更等低频事件):EMC + 知识一起推送 */
    public static void syncTeam(TeamData team) {
        syncTeamEmc(team);
        syncTeamKnowledge(team);
    }

    /** 向单个玩家推送完整生效状态(加入/离开/创建等个人↔团队模式切换时用,替代旧 Transmutation.sync) */
    private static void syncPlayerState(EntityPlayerMP player) {
        PacketHandler.sendTo(player, new TeamEmcPKT(getEffectiveEmc(player)));
        PacketHandler.sendTo(player, new TeamKnowledgePKT(buildKnowledgePayload(getEffectiveKnowledge(player))));
    }

    /**
     * 玩家登录后主动推送一次当前生效状态(团队共享则团队池,否则个人字段)。
     * 背景:ProjectE 的登录同步(onEntityJoinWorld → Transmutation.sync → saveForPacket)依赖
     * 那一刻 isSharingEMC 的判断,任何时序偏差都会让客户端收到个人值;这里在 PlayerLoggedInEvent
     * (实体入世界之后)再推一次,保证重进后显示的一定是团队池(或正确的个人值)。
     */
    public static void syncOnLogin(EntityPlayerMP player) {
        syncPlayerState(player);
    }

    // 指令操作

    /**
     * 验证团队代理是否生效:玩家身上的 ProjectETransmutation props 必须是 TeamTransmutationProps 子类。
     * 若 ProjectE 抢先注册导致代理失效,团队共享将静默不生效,这里给出明确警告。
     */
    private static void verifyProxy(EntityPlayerMP player) {
        IExtendedEntityProperties props = player.getExtendedProperties(TransmutationProps.PROP_NAME);
        if (!(props instanceof TeamTransmutationProps)) {
            LOG.warn(
                "Team proxy NOT active for {}: props = {} - team sharing will not work!",
                player.getCommandSenderName(),
                props == null ? "null"
                    : props.getClass()
                        .getName());
        }
    }

    /** 创建团队(name 为空时默认 "玩家名 team")。返回错误消息 key,成功返回 null。 */
    public static String create(EntityPlayerMP player, String name) {
        verifyProxy(player);
        TeamDataStore store = getStore();
        if (store == null) {
            return "pe.team.error.noworld";
        }
        if (store.getTeamForPlayer(player.getUniqueID()) != null) {
            return "pe.team.create.already";
        }
        if (name == null || name.trim()
            .isEmpty()) {
            name = defaultTeamName(player);
        }
        TeamData team = store.createTeam(player.getUniqueID(), name.trim());
        // 队长从个人模式切换到团队模式,立即推送团队生效状态刷新客户端显示
        syncPlayerState(player);
        sendMessage(player, "pe.team.create.success", team.getDisplayName());
        return null;
    }

    /** 邀请玩家加入(队长无团队时自动创建团队;目标需通过 accept 确认)。返回错误消息 key,成功返回 null。 */
    public static String invite(EntityPlayerMP sender, String targetName) {
        verifyProxy(sender);
        EntityPlayerMP target = getPlayerByName(targetName);
        if (target == null) {
            return "pe.team.invite.notfound";
        }
        if (target.getUniqueID()
            .equals(sender.getUniqueID())) {
            return "pe.team.invite.self";
        }
        if (getTeam(target) != null) {
            return "pe.team.invite.already";
        }

        TeamDataStore store = getStore();
        if (store == null) {
            return "pe.team.error.noworld";
        }

        TeamData team = store.getTeamForPlayer(sender.getUniqueID());
        if (team == null) {
            team = store.createTeam(sender.getUniqueID(), defaultTeamName(sender));
            // 自动创建团队后推送队长生效状态(从个人模式切换到团队模式)
            syncPlayerState(sender);
            sendMessage(sender, "pe.team.invite.created", team.getDisplayName());
        } else if (!team.isOwner(sender.getUniqueID())) {
            return "pe.team.error.notowner";
        }

        store.addPendingInvite(target.getUniqueID(), team.teamId);
        sendMessage(sender, "pe.team.invite.sent", target.getCommandSenderName());
        sendMessage(target, "pe.team.invite.notify", sender.getCommandSenderName());
        return null;
    }

    /** 接受待处理邀请并加入团队。返回错误消息 key,成功返回 null。 */
    public static String accept(EntityPlayerMP player) {
        TeamDataStore store = getStore();
        if (store == null) {
            return "pe.team.error.noworld";
        }
        if (store.getTeamForPlayer(player.getUniqueID()) != null) {
            return "pe.team.invite.already";
        }
        TeamData team = store.getPendingInviteFor(player.getUniqueID());
        if (team == null) {
            return "pe.team.accept.noinvite";
        }

        store.removePendingInvite(player.getUniqueID());
        store.addMember(team, player.getUniqueID());
        syncPlayerState(player);
        sendMessage(player, "pe.team.notify.joined", getNameForUUID(team.getOwner()));
        sendMessageToTeam(team, "pe.team.notify.accepted", player.getCommandSenderName());
        return null;
    }

    /** 拒绝待处理邀请。返回错误消息 key,成功返回 null。 */
    public static String decline(EntityPlayerMP player) {
        TeamDataStore store = getStore();
        if (store == null) {
            return "pe.team.error.noworld";
        }
        TeamData team = store.getPendingInviteFor(player.getUniqueID());
        if (team == null) {
            return "pe.team.decline.noinvite";
        }

        store.removePendingInvite(player.getUniqueID());
        sendMessage(player, "pe.team.decline.declined");
        sendMessageToMember(team, team.getOwner(), "pe.team.notify.declined", player.getCommandSenderName());
        return null;
    }

    /** 踢出成员(仅队长)。返回错误消息 key,成功返回 null。 */
    public static String kick(EntityPlayerMP sender, String targetName) {
        TeamDataStore store = getStore();
        if (store == null) {
            return "pe.team.error.noworld";
        }
        TeamData team = store.getTeamForPlayer(sender.getUniqueID());
        if (team == null) {
            return "pe.team.error.noteam";
        }
        if (!team.isOwner(sender.getUniqueID())) {
            return "pe.team.error.notowner";
        }

        EntityPlayerMP target = getPlayerByName(targetName);
        if (target == null) {
            return "pe.team.kick.notonline";
        }
        if (target.getUniqueID()
            .equals(sender.getUniqueID())) {
            return "pe.team.kick.self";
        }
        if (!team.isMember(target.getUniqueID())) {
            return "pe.team.kick.notmember";
        }

        store.removeMember(team, target.getUniqueID());
        store.removePendingInvite(target.getUniqueID());
        syncPlayerState(target);
        sendMessage(target, "pe.team.notify.kicked", sender.getCommandSenderName());
        return null;
    }

    /** 离开团队。队长离开时:有成员则自动转让,无成员则解散。返回错误消息 key,成功返回 null。 */
    public static String leave(EntityPlayerMP player) {
        TeamDataStore store = getStore();
        if (store == null) {
            return "pe.team.error.noworld";
        }
        TeamData team = store.getTeamForPlayer(player.getUniqueID());
        if (team == null) {
            return "pe.team.error.noteam";
        }

        if (team.isOwner(player.getUniqueID())) {
            if (team.getMembers()
                .isEmpty()) {
                store.deleteTeam(team);
                syncPlayerState(player);
                sendMessage(player, "pe.team.leave.disbanded");
            } else {
                UUID newOwner = team.getMembers()
                    .get(0);
                // 旧队长(玩家本人)正在离开,不转为成员
                store.transferOwner(team, newOwner, false);
                syncTeam(team);
                sendMessage(player, "pe.team.leave.transferred");
                sendMessageToMember(team, newOwner, "pe.team.notify.newowner", player.getCommandSenderName());
            }
        } else {
            store.removeMember(team, player.getUniqueID());
            syncPlayerState(player);
            sendMessageToTeam(team, "pe.team.notify.left", player.getCommandSenderName());
        }
        return null;
    }

    /** 转让队长(仅队长,目标须为在线成员)。返回错误消息 key,成功返回 null。 */
    public static String transferOwner(EntityPlayerMP sender, String targetName) {
        TeamDataStore store = getStore();
        if (store == null) {
            return "pe.team.error.noworld";
        }
        TeamData team = store.getTeamForPlayer(sender.getUniqueID());
        if (team == null) {
            return "pe.team.error.noteam";
        }
        if (!team.isOwner(sender.getUniqueID())) {
            return "pe.team.error.notowner";
        }

        EntityPlayerMP target = getPlayerByName(targetName);
        if (target == null) {
            return "pe.team.transfer.notonline";
        }
        if (target.getUniqueID()
            .equals(sender.getUniqueID())) {
            return "pe.team.transfer.self";
        }
        if (!team.isMember(target.getUniqueID())) {
            return "pe.team.transfer.notmember";
        }

        // 主动转让:旧队长转为普通成员留队,继续接收团队广播
        store.transferOwner(team, target.getUniqueID(), true);
        syncTeam(team);
        sendMessage(target, "pe.team.notify.newowner", sender.getCommandSenderName());
        return null;
    }

    /** 设置团队共享选项(仅队长)。返回错误消息 key,成功返回 null。 */
    public static String setSetting(EntityPlayerMP sender, String setting, boolean value) {
        TeamDataStore store = getStore();
        if (store == null) {
            return "pe.team.error.noworld";
        }
        TeamData team = store.getTeamForPlayer(sender.getUniqueID());
        if (team == null) {
            return "pe.team.error.noteam";
        }
        if (!team.isOwner(sender.getUniqueID())) {
            return "pe.team.error.notowner";
        }

        if ("share_emc".equalsIgnoreCase(setting)) {
            team.setShareEMC(value);
        } else if ("share_knowledge".equalsIgnoreCase(setting)) {
            team.setShareKnowledge(value);
        } else {
            return "pe.team.settings.invalid";
        }

        store.markDirty();
        syncTeam(team);
        sendMessage(sender, value ? "pe.team.settings.enabled" : "pe.team.settings.disabled", setting);
        return null;
    }

    /** 生成成员列表文本(供 members 指令使用),无团队返回 null */
    public static String buildMembersList(EntityPlayerMP player) {
        TeamData team = getTeam(player);
        if (team == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        appendMember(sb, team, team.getOwner());
        for (UUID member : team.getMembers()) {
            appendMember(sb, team, member);
        }
        return sb.toString();
    }

    private static void appendMember(StringBuilder sb, TeamData team, UUID uuid) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(getNameForUUID(uuid));
        if (team.isOwner(uuid)) {
            sb.append(" (owner)");
        }
        if (getOnlinePlayer(uuid) == null) {
            sb.append(" (offline)");
        }
    }

    // 工具

    /** 默认团队名:"玩家名 team" */
    private static String defaultTeamName(EntityPlayerMP player) {
        return player.getCommandSenderName() + " team";
    }

    /** 按名字查找在线玩家(忽略大小写) */
    public static EntityPlayerMP getPlayerByName(String name) {
        if (MinecraftServer.getServer() == null) {
            return null;
        }
        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) o;
                if (player.getCommandSenderName()
                    .equalsIgnoreCase(name)) {
                    return player;
                }
            }
        }
        return null;
    }

    /** 按 UUID 查找在线玩家 */
    public static EntityPlayerMP getOnlinePlayer(UUID uuid) {
        if (MinecraftServer.getServer() == null) {
            return null;
        }
        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) o;
                if (player.getUniqueID()
                    .equals(uuid)) {
                    return player;
                }
            }
        }
        return null;
    }

    /** 显示名:优先在线玩家名,否则取 UUID 前 8 位 */
    private static String getNameForUUID(UUID uuid) {
        EntityPlayerMP player = getOnlinePlayer(uuid);
        return player != null ? player.getCommandSenderName()
            : uuid.toString()
                .substring(0, 8);
    }

    private static void sendMessage(EntityPlayerMP player, String key, Object... args) {
        player.addChatMessage(new ChatComponentTranslation(key, args));
    }

    private static void sendMessageToMember(TeamData team, UUID uuid, String key, Object... args) {
        EntityPlayerMP player = getOnlinePlayer(uuid);
        if (player != null) {
            sendMessage(player, key, args);
        }
    }

    private static void sendMessageToTeam(TeamData team, String key, Object... args) {
        sendMessageToMember(team, team.getOwner(), key, args);
        for (UUID member : team.getMembers()) {
            sendMessageToMember(team, member, key, args);
        }
    }
}
