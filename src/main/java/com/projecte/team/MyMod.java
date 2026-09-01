package com.projecte.team;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.projecte.team.commands.ProjectETeamCMD;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = MyMod.MODID,
    version = Tags.VERSION,
    name = "ProjectETeam",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:ProjectE")
public class MyMod {

    public static final String MODID = "projecteteam";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.projecte.team.ClientProxy", serverSide = "com.projecte.team.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        // 抢先注册团队代理 props(优先级 HIGHEST,先于 ProjectE 自身的注册)
        MinecraftForge.EVENT_BUS.register(new TeamEvents());
        // 团队数据即时持久化(服务器 tick 内写盘)
        FMLCommonHandler.instance()
            .bus()
            .register(new TeamSaveHandler());
        // 团队数据同步网络频道(S->C)
        com.projecte.team.network.PacketHandler.register();

        proxy.preInit(event);
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new ProjectETeamCMD());
        proxy.serverStarting(event);
    }
}
