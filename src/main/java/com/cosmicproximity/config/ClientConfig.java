package com.cosmicproximity.config;

import com.cosmicproximity.CosmicProximity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ClientConfig {

    public enum VoiceMode { PUSH_TO_TALK, OPEN_MIC }

    public VoiceMode voiceMode = VoiceMode.PUSH_TO_TALK;

    public float micVolume = 1.0f;
    public float speakerVolume = 1.0f;
    public boolean muted = false;
    public boolean deafened = false;
    public String micDevice = "";
    public String speakerDevice = "";

    /** Voice-activity threshold (post-suppression RMS, 0..1). Used when voiceMode == OPEN_MIC. */
    public float vadThreshold = 0.04f;

    /** 0 = off, 1 = aggressive. Adaptive noise gate strength. */
    public float noiseSuppression = 0.5f;

    // ------- External relay mode (no server-side mod required) -------
    /** If true, connect to an external relay instead of waiting for an MC server-side handshake. */
    public boolean useRelay = false;
    public String relayHost = "";
    public int relayPort = 24454;
    /** Shared room password. Everyone using the same relay+password is in the same voice room. */
    public String relayPassword = "";
    /** Voice range in blocks. Should match the relay's --range setting. */
    public double relayVoiceRange = 48.0;

    /** UUIDs of players this client has muted (won't hear their voice). */
    public Set<UUID> mutedPlayers = new HashSet<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ClientConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                ClientConfig cfg = GSON.fromJson(Files.readString(file), ClientConfig.class);
                if (cfg.voiceMode == null) cfg.voiceMode = VoiceMode.PUSH_TO_TALK;
                if (cfg.mutedPlayers == null) cfg.mutedPlayers = new HashSet<>();
                return cfg;
            }
            ClientConfig cfg = new ClientConfig();
            cfg.save(file);
            return cfg;
        } catch (IOException e) {
            CosmicProximity.LOGGER.warn("Failed to load client config, using defaults", e);
            return new ClientConfig();
        }
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            CosmicProximity.LOGGER.warn("Failed to save client config", e);
        }
    }
}
