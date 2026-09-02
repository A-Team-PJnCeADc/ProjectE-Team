package com.projecte.team;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import com.google.common.collect.Maps;

/**
 * 团队数据的持久化存储(存于主世界的 WorldSavedData)。
 * teams: teamId -> TeamData;playerToTeam: 玩家UUID -> teamId 反向索引;pendingInvites: 被邀请者 -> teamId。
 */
public class TeamDataStore extends WorldSavedData {

    public static final String NAME = "ProjectETeamData";

    private final Map<UUID, TeamData> teams = Maps.newHashMap();
    private final Map<UUID, UUID> playerToTeam = Maps.newHashMap();
    private final Map<UUID, UUID> pendingInvites = Maps.newHashMap(); // invitee -> teamId
    private final Map<UUID, String> kickNotices = Maps.newHashMap(); // 被踢玩家 -> 踢人者名(离线踢出,玩家上线时补发通知)

    public TeamDataStore() {
        super(NAME);
    }

    public TeamDataStore(String name) {
        super(name);
    }

    public static TeamDataStore get(World world) {
        TeamDataStore store = (TeamDataStore) world.loadItemData(TeamDataStore.class, NAME);
        if (store == null) {
            store = new TeamDataStore();
            world.setItemData(NAME, store);
        }
        return store;
    }

    public TeamData getTeam(UUID teamId) {
        return teams.get(teamId);
    }

    /** 玩家所属团队,不在任何团队返回 null */
    public TeamData getTeamForPlayer(UUID playerId) {
        UUID teamId = playerToTeam.get(playerId);
        return teamId == null ? null : teams.get(teamId);
    }

    /** 创建团队(队长自己作为 owner,不占用 members 列表) */
    public TeamData createTeam(UUID ownerId, String name) {
        TeamData data = new TeamData(ownerId, ownerId);
        data.setName(name);
        teams.put(ownerId, data);
        playerToTeam.put(ownerId, ownerId);
        markDirty();
        return data;
    }

    /** 成员加入已有团队 */
    public void addMember(TeamData team, UUID playerId) {
        if (!team.getMembers()
            .contains(playerId)) {
            team.getMembers()
                .add(playerId);
            playerToTeam.put(playerId, team.teamId);
            markDirty();
        }
    }

    /** 成员离开团队(队长不在 members 中,不会被移除) */
    public void removeMember(TeamData team, UUID playerId) {
        team.getMembers()
            .remove(playerId);
        playerToTeam.remove(playerId);
        markDirty();
    }

    /**
     * 转让队长。
     * 
     * @param oldOwnerStays true=旧队长转为普通成员留队(主动转让);false=旧队长离开团队(队长离开时自动转让)
     */
    public void transferOwner(TeamData team, UUID newOwner, boolean oldOwnerStays) {
        UUID oldOwner = team.getOwner();
        team.getMembers()
            .remove(newOwner);
        team.setOwner(newOwner);
        if (oldOwnerStays) {
            if (!oldOwner.equals(newOwner) && !team.getMembers()
                .contains(oldOwner)) {
                team.getMembers()
                    .add(oldOwner);
            }
        } else {
            playerToTeam.remove(oldOwner);
        }
        playerToTeam.put(newOwner, team.teamId);
        markDirty();
    }

    /** 解散团队 */
    public void deleteTeam(TeamData team) {
        for (UUID member : team.getMembers()) {
            playerToTeam.remove(member);
        }
        playerToTeam.remove(team.teamId);
        teams.remove(team.teamId);
        clearPendingInvitesForTeam(team.teamId);
        markDirty();
    }

    // 待处理邀请

    /** 记录邀请(覆盖同目标已有邀请) */
    public void addPendingInvite(UUID invitee, UUID teamId) {
        pendingInvites.put(invitee, teamId);
        markDirty();
    }

    /** 被邀请者待接受的团队,无邀请返回 null */
    public TeamData getPendingInviteFor(UUID invitee) {
        UUID teamId = pendingInvites.get(invitee);
        return teamId == null ? null : teams.get(teamId);
    }

    /** 清除被邀请者的待处理邀请 */
    public void removePendingInvite(UUID invitee) {
        if (pendingInvites.remove(invitee) != null) {
            markDirty();
        }
    }

    /** 清除发给某团队的所有待处理邀请(团队解散时调用) */
    public void clearPendingInvitesForTeam(UUID teamId) {
        Iterator<Map.Entry<UUID, UUID>> iter = pendingInvites.entrySet()
            .iterator();
        boolean changed = false;
        while (iter.hasNext()) {
            if (iter.next()
                .getValue()
                .equals(teamId)) {
                iter.remove();
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    // 离线踢出通知(目标不在线时记录,玩家下次登录由 TeamManager.notifyOnLogin 补发)

    /** 记录离线踢出通知:玩家下次登录时提示\"你被踢出了团队\" */
    public void addKickNotice(UUID kicked, String kickerName) {
        kickNotices.put(kicked, kickerName);
        markDirty();
    }

    /** 取出并清除该玩家的离线踢出通知,没有则返回 null */
    public String takeKickNotice(UUID kicked) {
        String kickerName = kickNotices.remove(kicked);
        if (kickerName != null) {
            markDirty();
        }
        return kickerName;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        teams.clear();
        playerToTeam.clear();
        pendingInvites.clear();
        kickNotices.clear();

        NBTTagList teamList = nbt.getTagList("teams", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < teamList.tagCount(); i++) {
            NBTTagCompound tag = teamList.getCompoundTagAt(i);
            TeamData data = TeamData.readFromNBT(tag);
            teams.put(data.teamId, data);
            // 只映射当前 owner 与 members(teamId 是创建时的队长 UUID,转让后不等于 owner,
            // 不能再无条件映射——否则转让后离队的旧队长重进服会被错误恢复成团队成员)
            playerToTeam.put(data.getOwner(), data.teamId);
            for (UUID member : data.getMembers()) {
                playerToTeam.put(member, data.teamId);
            }
        }

        NBTTagList inviteList = nbt.getTagList("pendingInvites", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < inviteList.tagCount(); i++) {
            NBTTagCompound tag = inviteList.getCompoundTagAt(i);
            UUID invitee = UUID.fromString(tag.getString("invitee"));
            UUID teamId = UUID.fromString(tag.getString("teamId"));
            if (teams.containsKey(teamId)) {
                pendingInvites.put(invitee, teamId);
            }
        }

        NBTTagList noticeList = nbt.getTagList("kickNotices", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < noticeList.tagCount(); i++) {
            NBTTagCompound tag = noticeList.getCompoundTagAt(i);
            kickNotices.put(UUID.fromString(tag.getString("uuid")), tag.getString("kicker"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList teamList = new NBTTagList();
        for (Map.Entry<UUID, TeamData> entry : teams.entrySet()) {
            NBTTagCompound tag = entry.getValue()
                .writeToNBT(new NBTTagCompound());
            tag.setString(
                "teamId",
                entry.getKey()
                    .toString());
            teamList.appendTag(tag);
        }
        nbt.setTag("teams", teamList);

        NBTTagList inviteList = new NBTTagList();
        for (Map.Entry<UUID, UUID> entry : pendingInvites.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(
                "invitee",
                entry.getKey()
                    .toString());
            tag.setString(
                "teamId",
                entry.getValue()
                    .toString());
            inviteList.appendTag(tag);
        }
        nbt.setTag("pendingInvites", inviteList);

        NBTTagList noticeList = new NBTTagList();
        for (Map.Entry<UUID, String> entry : kickNotices.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(
                "uuid",
                entry.getKey()
                    .toString());
            tag.setString("kicker", entry.getValue());
            noticeList.appendTag(tag);
        }
        nbt.setTag("kickNotices", noticeList);
    }
}
