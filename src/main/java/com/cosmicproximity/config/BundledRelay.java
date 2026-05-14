package com.cosmicproximity.config;

import com.cosmicproximity.CosmicProximity;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads relay coordinates from one of two baked-in resources:
 * <ol>
 *   <li>{@code /cosmicproximity/relay-local.properties} — gitignored override.
 *       Your private build (for a specific friend group) drops a populated
 *       version of this file into {@code src/main/resources/cosmicproximity/}
 *       before building so the resulting jar auto-connects.</li>
 *   <li>{@code /cosmicproximity/relay-defaults.properties} — committed.
 *       Ships with {@code enabled=false}, so an open-source build does
 *       nothing automatic — users configure host/port/password in-game via the
 *       settings menu.</li>
 * </ol>
 */
public final class BundledRelay {
    public static final boolean ENABLED;
    public static final String HOST;
    public static final int PORT;
    public static final String PASSWORD;
    public static final double VOICE_RANGE;

    static {
        Properties p = loadFirst(
                "/cosmicproximity/relay-local.properties",
                "/cosmicproximity/relay-defaults.properties");
        ENABLED      = Boolean.parseBoolean(p.getProperty("enabled", "false"));
        HOST         = p.getProperty("host", "").trim();
        PORT         = parseInt(p.getProperty("port"), 24454);
        PASSWORD     = p.getProperty("password", "").trim();
        VOICE_RANGE  = parseDouble(p.getProperty("voiceRange"), 48.0);
    }

    /** If a bundled override is present and enabled, force the runtime config to use it. */
    public static void apply(ClientConfig cfg) {
        if (!ENABLED) return;
        cfg.useRelay = true;
        cfg.relayHost = HOST;
        cfg.relayPort = PORT;
        cfg.relayPassword = PASSWORD;
        cfg.relayVoiceRange = VOICE_RANGE;
    }

    private static Properties loadFirst(String... resources) {
        Properties p = new Properties();
        for (String r : resources) {
            try (InputStream in = BundledRelay.class.getResourceAsStream(r)) {
                if (in != null) {
                    p.load(in);
                    return p;
                }
            } catch (IOException e) {
                CosmicProximity.LOGGER.warn("Failed loading {}", r, e);
            }
        }
        return p;
    }

    private static int parseInt(String s, int dflt) {
        if (s == null) return dflt;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return dflt; }
    }

    private static double parseDouble(String s, double dflt) {
        if (s == null) return dflt;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return dflt; }
    }

    private BundledRelay() {}
}
