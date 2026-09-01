package com.projecte.team;

import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        MyMod.LOG.info(Config.greeting);
        MyMod.LOG.info("I am MyMod at version " + Tags.VERSION);
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {}

    // 网络包入口(S->C 团队数据,客户端覆写为回主线程应用)

    /** 服务端收到的是 TeamEmcPKT 的发送侧,不会走到这里;客户端由 ClientProxy 覆写 */
    public void handleTeamEmc(double emc) {}

    /** 服务端收到的是 TeamKnowledgePKT 的发送侧,不会走到这里;客户端由 ClientProxy 覆写 */
    public void handleTeamKnowledge(NBTTagCompound nbt) {}
}
