package com.projecte.team;

import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    // Override CommonProxy methods here, if you want a different behaviour on the client (e.g. registering renders).
    // Don't forget to call the super methods as well.

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        // 客户端主线程 tick:应用网络包排队的团队数据(EMC/知识)
        FMLCommonHandler.instance()
            .bus()
            .register(new TeamClientHandler());
    }

    // 网络包入口:Netty IO 线程收到,只做 volatile 排队,主线程 tick 应用

    @Override
    public void handleTeamEmc(double emc, String emcBig) {
        TeamClientSync.queueEmc(emc, emcBig);
    }

    @Override
    public void handleTeamKnowledge(NBTTagCompound nbt) {
        TeamClientSync.queueKnowledge(nbt);
    }
}
