package com.projecte.team;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    // Override CommonProxy methods here, if you want a different behaviour on the client (e.g. registering renders).
    // Don't forget to call the super methods as well.

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        // 客户端 tick:让打开着的转化桌 GUI 跟随团队 EMC 实时刷新
        FMLCommonHandler.instance()
            .bus()
            .register(new TeamClientHandler());
    }
}
