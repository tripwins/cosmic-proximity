package com.cosmicproximity;

import com.cosmicproximity.config.ServerConfig;
import com.cosmicproximity.net.Payloads;
import com.cosmicproximity.net.VoiceSessionInitPayload;
import com.cosmicproximity.server.VoiceServer;
import com.cosmicproximity.server.VoiceSession;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.nio.file.Path;

public class CosmicProximity implements ModInitializer {
    public static final String MOD_ID = "cosmicproximity";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile VoiceServer voiceServer;
    private static volatile ServerConfig serverConfig;

    @Override
    public void onInitialize() {
        LOGGER.info("Cosmic Proximity initialising");
        Payloads.registerTypes();

        ServerLifecycleEvents.SERVER_STARTING.register(CosmicProximity::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(CosmicProximity::onServerStopping);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                onPlayerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                onPlayerLeave(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(CosmicProximity::onServerTick);
    }

    private static void onServerStarting(MinecraftServer server) {
        Path configPath = server.getServerDirectory().resolve("config").resolve("cosmicproximity-server.json");
        serverConfig = ServerConfig.load(configPath);
        try {
            voiceServer = new VoiceServer(server, serverConfig);
        } catch (SocketException e) {
            LOGGER.error("Failed to bind voice UDP port {}", serverConfig.voicePort, e);
        }
    }

    private static void onServerStopping(MinecraftServer server) {
        if (voiceServer != null) {
            voiceServer.close();
            voiceServer = null;
        }
    }

    private static void onPlayerJoin(ServerPlayer player) {
        if (voiceServer == null) return;
        VoiceSession session = voiceServer.registerPlayer(player);
        VoiceSessionInitPayload payload = new VoiceSessionInitPayload(
                voiceServer.port(),
                session.sessionId,
                session.aesKey.getEncoded(),
                serverConfig.voiceRange);
        ServerPlayNetworking.send(player, payload);
        LOGGER.info("Sent voice session init to {} (session {})",
                player.getName().getString(), session.sessionId);
    }

    private static void onPlayerLeave(ServerPlayer player) {
        if (voiceServer == null) return;
        voiceServer.unregisterPlayer(player.getUUID());
    }

    private static void onServerTick(MinecraftServer server) {
        if (voiceServer == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            voiceServer.updateSnapshot(p);
        }
    }
}
