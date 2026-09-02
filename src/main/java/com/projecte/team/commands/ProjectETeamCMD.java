package com.projecte.team.commands;

import java.util.List;
import java.util.Locale;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.projecte.team.TeamManager;

import moze_intel.projecte.network.commands.ProjectEBaseCMD;

public class ProjectETeamCMD extends ProjectEBaseCMD {

    private static final List<String> commands = Lists.newArrayList(
        "create",
        "invite",
        "members",
        "kick",
        "leave",
        "transfer_owner",
        "settings",
        "accept",
        "decline");
    private static final List<String> settings = Lists.newArrayList("share_emc", "share_knowledge");
    private static final List<String> booleans = Lists.newArrayList("true", "false");

    @Override
    public String getCommandName() {
        return "projecte_team";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "pe.team.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] params) {
        if (params.length == 1) {
            return Lists.newArrayList(Iterables.filter(commands, new LowerCasePrefixPredicate(params[0])));
        }
        if (params.length == 2 && ("invite".equalsIgnoreCase(params[0]) || "kick".equalsIgnoreCase(params[0]))) {
            // 离线成员管理:补全范围包含曾在本服务器登录过的离线玩家
            return TeamManager.getKnownPlayerNames();
        }
        if (params.length == 2 && "transfer_owner".equalsIgnoreCase(params[0])) {
            return getOnlinePlayerNames();
        }
        if (params.length == 3 && "settings".equalsIgnoreCase(params[0])) {
            return Lists.newArrayList(Iterables.filter(settings, new LowerCasePrefixPredicate(params[2])));
        }
        if (params.length == 4 && "settings".equalsIgnoreCase(params[0])) {
            return Lists.newArrayList(Iterables.filter(booleans, new LowerCasePrefixPredicate(params[3])));
        }
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] params) {
        if (params.length == 0) {
            sendMessage(sender, new ChatComponentTranslation("pe.team.usage"));
            return;
        }

        if (!(sender instanceof EntityPlayerMP)) {
            sendError(sender, new ChatComponentTranslation("pe.team.error.ingame"));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        String subName = params[0].toLowerCase(Locale.ROOT);
        String error = null;

        if ("create".equals(subName)) {
            String name = params.length >= 2 ? params[1] : null;
            error = TeamManager.create(player, name);
        } else if ("invite".equals(subName)) {
            if (params.length < 2) {
                sendMessage(sender, new ChatComponentTranslation("pe.team.invite.usage"));
                return;
            }
            error = TeamManager.invite(player, params[1]);
        } else if ("accept".equals(subName)) {
            error = TeamManager.accept(player);
        } else if ("decline".equals(subName)) {
            error = TeamManager.decline(player);
        } else if ("members".equals(subName)) {
            String members = TeamManager.buildMembersList(player);
            if (members == null) {
                error = "pe.team.error.noteam";
            } else {
                sendSuccess(
                    sender,
                    new ChatComponentTranslation(
                        "pe.team.members.header",
                        TeamManager.getTeam(player)
                            .getDisplayName()));
                sendMessage(sender, new ChatComponentText(members));
            }
        } else if ("kick".equals(subName)) {
            if (params.length < 2) {
                sendMessage(sender, new ChatComponentTranslation("pe.team.kick.usage"));
                return;
            }
            error = TeamManager.kick(player, params[1]);
        } else if ("leave".equals(subName)) {
            error = TeamManager.leave(player);
        } else if ("transfer_owner".equals(subName)) {
            if (params.length < 2) {
                sendMessage(sender, new ChatComponentTranslation("pe.team.transfer.usage"));
                return;
            }
            error = TeamManager.transferOwner(player, params[1]);
        } else if ("settings".equals(subName)) {
            if (params.length < 3) {
                sendMessage(sender, new ChatComponentTranslation("pe.team.settings.usage"));
                return;
            }
            Boolean value = parseBoolean(params[2]);
            if (value == null) {
                sendError(sender, new ChatComponentTranslation("pe.team.settings.badvalue"));
                return;
            }
            error = TeamManager.setSetting(player, params[1], value);
        } else {
            sendMessage(sender, new ChatComponentTranslation("pe.team.usage"));
            return;
        }

        if (error != null) {
            sendError(sender, new ChatComponentTranslation(error));
        }
    }

    private static Boolean parseBoolean(String s) {
        if ("true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static List<String> getOnlinePlayerNames() {
        List<String> names = Lists.newArrayList();
        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP) {
                names.add(((EntityPlayerMP) o).getCommandSenderName());
            }
        }
        return names;
    }

    private static class LowerCasePrefixPredicate implements Predicate<String> {

        private final String prefix;

        public LowerCasePrefixPredicate(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public boolean apply(String input) {
            return input.toLowerCase(Locale.ROOT)
                .startsWith(prefix.toLowerCase(Locale.ROOT));
        }
    }
}
