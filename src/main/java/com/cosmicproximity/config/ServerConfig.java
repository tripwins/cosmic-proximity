package com.cosmicproximity.config;

import com.cosmicproximity.CosmicProximity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerConfig {
    /** UDP port the voice server binds. 0 = OS-assigned ephemeral. */
    public int voicePort = 24454;

    /** Max audible distance in blocks. */
    public double voiceRange = 48.0;

    /** Per-player rate limit (packets/sec) before drop. ~50 packets/sec is normal speech (20ms frames). */
    public int maxPacketsPerSecond = 100;

    /** If false, players in different dimensions can never hear each other. */
    public boolean crossDimensionVoice = false;

    /** Public hostname/IP advertised to clients. Empty = derive from MC server bind. */
    public String publicHost = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ServerConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                return GSON.fromJson(Files.readString(file), ServerConfig.class);
            }
            ServerConfig cfg = new ServerConfig();
            cfg.save(file);
            return cfg;
        } catch (IOException e) {
            CosmicProximity.LOGGER.warn("Failed to load server config, using defaults", e);
            return new ServerConfig();
        }
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            CosmicProximity.LOGGER.warn("Failed to save server config", e);
        }
    }
}
