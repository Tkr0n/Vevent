package com.verkku.title;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerkkuTitleMod implements ClientModInitializer {
    public static final String MOD_ID = "verkku-title";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final String SERVER_ADDRESS = System.getProperty("verkku.serverAddress", "verkku.taild0c659.ts.net");
    public static final int SERVER_PORT = Integer.getInteger("verkku.server.port", 25565);

    @Override
    public void onInitializeClient() {
        LOGGER.info("VerkkuCraft Title Screen loaded");
    }
}
