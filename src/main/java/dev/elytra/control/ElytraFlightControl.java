package dev.elytra.control;

import net.fabricmc.api.ModInitializer;


import dev.elytra.control.probe.m0d.M0DServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElytraFlightControl implements ModInitializer {
    public static final String MOD_ID = "elytra_flight_control";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("3790's Elytra Flight Control {}，运动协议{}；当前支持单人及单控制玩家的本机回环专服。",
            net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString(),
            dev.elytra.control.probe.m0d.M0DProtocol.VERSION);
        M0DServer.initialize();
        dev.elytra.control.visual.VisualServer.initialize();
    }
}
