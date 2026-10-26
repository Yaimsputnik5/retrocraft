package com.sammwy.retrocraft.smoke.fabric;

import com.sammwy.retrocraft.smoke.ClientSmoke;

import net.fabricmc.api.ClientModInitializer;

public final class FabricClientSmoke implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientSmoke.install();
    }
}
