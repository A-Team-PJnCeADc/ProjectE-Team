package com.projecte.team;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import moze_intel.projecte.playerData.TransmutationProps;

/**
 * 玩家实体构造时抢先注册 TeamTransmutationProps(优先级 HIGHEST,先于 ProjectE 自己的注册),
 * 使 ProjectE 的 TransmutationProps.register 因名称已被占用而被 FML 重命名(仅日志警告,无副作用),
 * 玩家身上的 props 实例即为我们覆写了团队代理逻辑的子类。
 */
public class TeamEvents {

    private static final Logger LOG = LogManager.getLogger("ProjectETeam");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onConstruct(EntityEvent.EntityConstructing evt) {
        if (evt.entity instanceof EntityPlayer && !(evt.entity instanceof FakePlayer)) {
            EntityPlayer player = (EntityPlayer) evt.entity;
            IExtendedEntityProperties existing = player.getExtendedProperties(TransmutationProps.PROP_NAME);
            if (existing == null) {
                player.registerExtendedProperties(TransmutationProps.PROP_NAME, new TeamTransmutationProps(player));
                // 注意:EntityConstructing 触发时 GameProfile 尚未初始化,不能调用 getCommandSenderName()
                LOG.info(
                    "Team proxy registered for {} ({})",
                    player.getUniqueID(),
                    player.worldObj.isRemote ? "client" : "server");
            } else {
                LOG.warn(
                    "ProjectETransmutation already taken by {} - team proxy NOT active!",
                    existing.getClass()
                        .getName());
            }
        }
    }
}
