package com.sammwy.retrocraft.smoke.forge;

import com.sammwy.retrocraft.smoke.ClientSmoke;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("retrocraft_smoke")
public final class ForgeClientSmoke {
    public ForgeClientSmoke() {
        FMLJavaModLoadingContext.get()
                .getModEventBus()
                .addListener(
                        (FMLClientSetupEvent event) -> event.enqueueWork(ClientSmoke::install));
    }
}
