package com.cosmicproximity.client;

import com.cosmicproximity.CosmicProximity;
import com.cosmicproximity.client.audio.AudioDevices;
import com.cosmicproximity.client.gui.PlayerSpeakingIndicator;
import com.cosmicproximity.client.gui.VoiceHud;
import com.cosmicproximity.client.gui.VoiceSettingsScreen;
import com.cosmicproximity.config.BundledRelay;
import com.cosmicproximity.config.ClientConfig;
import com.cosmicproximity.net.VoiceClientReadyPayload;
import com.cosmicproximity.net.VoiceSessionInitPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.player.LocalPlayer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.UUID;

public class CosmicProximityClient implements ClientModInitializer {
    private static volatile VoiceClient voiceClient;
    private static volatile ClientConfig clientConfig;

    public static VoiceClient voiceClient() { return voiceClient; }
    public static ClientConfig clientConfig() { return clientConfig; }

    /** True if the given Mojang UUID is currently known to have the voice mod installed. */
    public static boolean hasMod(UUID uuid) {
        if (uuid == null) return false;
        try {
            if (uuid.equals(Minecraft.getInstance().getUser().getProfileId())) return true;
        } catch (Throwable ignored) {}
        VoiceClient vc = voiceClient;
        return vc != null && vc.participants().contains(uuid);
    }

    @Override
    public void onInitializeClient() {
        Path configPath = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("cosmicproximity-client.json");
        clientConfig = ClientConfig.load(configPath);
        BundledRelay.apply(clientConfig);

        KeyBinds.register();
        VoiceMuteCommand.register();
        AudioDevices.logInventory();

        // MC server-integrated path: server with the mod sends us a VoiceSessionInitPayload.
        ClientPlayNetworking.registerGlobalReceiver(VoiceSessionInitPayload.TYPE,
                (payload, context) -> context.client().execute(() -> onMcServerSessionInit(payload, context.player())));

        // Connect on world join — picks up the current Mojang account each time.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            if (voiceClient == null && relayConfigured()) {
                connectToRelay();
            }
        }));

        // Disconnect on world leave — always teardown so the next join picks up fresh identity.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> teardown());

        ClientTickEvents.END_CLIENT_TICK.register(CosmicProximityClient::onClientTick);

        HudRenderCallback.EVENT.register(VoiceHud::render);
        PlayerSpeakingIndicator.register();
    }

    private static boolean relayConfigured() {
        return clientConfig.useRelay
                && !clientConfig.relayHost.isEmpty()
                && !clientConfig.relayPassword.isEmpty();
    }

    private static void onMcServerSessionInit(VoiceSessionInitPayload p, LocalPlayer player) {
        teardown();
        SocketAddress remote = player.connection.getConnection().getRemoteAddress();
        if (!(remote instanceof InetSocketAddress tcpAddr)) {
            CosmicProximity.LOGGER.warn("Server connection is not InetSocketAddress: {}", remote);
            return;
        }
        InetSocketAddress voiceAddr = new InetSocketAddress(tcpAddr.getAddress(), p.udpPort());
        try {
            voiceClient = new VoiceClient(voiceAddr, p.sessionId(), p.aesKey(),
                    p.voiceRange(), clientConfig, null);
            ClientPlayNetworking.send(VoiceClientReadyPayload.INSTANCE);
            CosmicProximity.LOGGER.info("Voice connected to MC server-integrated voice at {}", voiceAddr);
        } catch (Exception e) {
            CosmicProximity.LOGGER.error("Failed to start voice client (MC server mode)", e);
        }
    }

    /** Connect to the external relay using the local Mojang account's identity. */
    private static void connectToRelay() {
        ClientConfig cfg = clientConfig;
        InetSocketAddress relayAddr = new InetSocketAddress(cfg.relayHost, cfg.relayPort);
        UUID sessionId = UUID.randomUUID();
        byte[] aesKey = com.cosmicproximity.net.CryptoUtil
                .deriveKeyFromPassword(cfg.relayPassword).getEncoded();

        User user = Minecraft.getInstance().getUser();
        UUID playerUuid = user.getProfileId();
        String playerName = user.getName();

        try {
            VoiceClient.RelayContext ctx = new VoiceClient.RelayContext(playerUuid, playerName);
            voiceClient = new VoiceClient(relayAddr, sessionId, aesKey,
                    cfg.relayVoiceRange, cfg, ctx);
            CosmicProximity.LOGGER.info("Voice connected to relay {} as {} ({})",
                    relayAddr, playerName, playerUuid);
        } catch (Exception e) {
            CosmicProximity.LOGGER.error("Failed to start voice client (relay mode)", e);
        }
    }

    /**
     * roomHash = hash of the MC server's remote address (per-server room).
     * 0 = lobby / single-player / not-yet-connected. Lobby is currently unreachable
     * because we only connect on JOIN, but kept as a safe fallback.
     */
    private static int computeRoomHash(Minecraft mc) {
        try {
            if (mc.getConnection() != null) {
                SocketAddress addr = mc.getConnection().getConnection().getRemoteAddress();
                if (addr instanceof InetSocketAddress isa) {
                    return (isa.getHostString() + ":" + isa.getPort()).hashCode();
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static void teardown() {
        VoiceClient vc = voiceClient;
        if (vc != null) {
            vc.close();
            voiceClient = null;
            CosmicProximity.LOGGER.info("Voice client disconnected");
        }
    }

    private static void onClientTick(Minecraft mc) {
        if (KeyBinds.OPEN_SETTINGS.consumeClick()) {
            mc.setScreen(new VoiceSettingsScreen(mc.screen));
        }

        VoiceClient vc = voiceClient;
        LocalPlayer p = mc.player;

        // Defensive: if account changed silently (e.g. some launchers swap accounts without
        // closing the MC connection), reconnect so the relay sees the right identity.
        if (vc != null && p != null && vc.identityUuid() != null) {
            UUID liveUuid = mc.getUser().getProfileId();
            if (!liveUuid.equals(vc.identityUuid())) {
                CosmicProximity.LOGGER.info("Mojang account changed ({} → {}), reconnecting voice",
                        vc.identityUuid(), liveUuid);
                teardown();
                if (relayConfigured()) connectToRelay();
                return;
            }
        }

        if (vc == null || p == null) return;

        int roomHash = computeRoomHash(mc);
        int dimHash = p.level().dimension().identifier().toString().hashCode();
        vc.updateListener(p.position(), p.getLookAngle(), dimHash, roomHash);
        vc.setPttKeyDown(KeyBinds.PUSH_TO_TALK.isDown());
        vc.tick();

        if (KeyBinds.TOGGLE_MUTE.consumeClick()) {
            clientConfig.muted = !clientConfig.muted;
        }
        if (KeyBinds.TOGGLE_DEAFEN.consumeClick()) {
            clientConfig.deafened = !clientConfig.deafened;
            vc.setDeafened(clientConfig.deafened);
        }
    }
}
