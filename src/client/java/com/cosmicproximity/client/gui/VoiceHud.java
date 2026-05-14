package com.cosmicproximity.client.gui;

import com.cosmicproximity.client.CosmicProximityClient;
import com.cosmicproximity.client.VoiceClient;
import com.cosmicproximity.config.ClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Two HUD elements:
 *   - bottom-left status pill (connection + mic state)
 *   - top-left "currently speaking" list
 */
public final class VoiceHud {

    private static final int SPEAKING_WINDOW_MS = 300;

    public static void render(GuiGraphics g, DeltaTracker tickCounter) {
        VoiceClient vc = CosmicProximityClient.voiceClient();
        ClientConfig cfg = CosmicProximityClient.clientConfig();
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        Font font = mc.font;
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();

        renderStatusPill(g, font, vc, cfg, screenW, screenH);
        if (vc != null) renderSpeakingList(g, font, vc, mc);
    }

    private static void renderStatusPill(GuiGraphics g, Font font,
                                         VoiceClient vc, ClientConfig cfg,
                                         int screenW, int screenH) {
        boolean connected = vc != null;
        boolean serverMuted = connected && vc.isServerMuted();
        boolean speaking = connected && vc.isTransmitting() && !cfg.muted && !serverMuted;
        boolean muted = cfg.muted;
        boolean deafened = cfg.deafened;

        String icon;
        int iconColor;
        String label;

        if (!connected) {
            icon = "○";
            iconColor = 0xFF808080;
            label = "Voice: off";
        } else if (serverMuted) {
            icon = "⊘";
            iconColor = 0xFFFF3333;
            long secs = vc.serverMuteSecondsLeft();
            if (secs < 0) {
                label = "Muted by admin";
            } else if (secs >= 60) {
                label = "Muted by admin (" + (secs / 60) + "m left)";
            } else {
                label = "Muted by admin (" + secs + "s left)";
            }
        } else if (deafened) {
            icon = "✕";
            iconColor = 0xFFCC4444;
            label = "Deafened";
        } else if (muted) {
            icon = "Ø";
            iconColor = 0xFFCC8844;
            label = "Mic muted";
        } else if (speaking) {
            icon = "●";
            iconColor = 0xFF44CC44;
            label = "Speaking";
        } else {
            icon = "●";
            iconColor = 0xFF44AAFF;
            label = "Voice: on";
        }

        int padding = 4;
        int textW = font.width(label) + font.width(icon) + 6;
        int x = 6;
        int y = screenH - 22;

        g.fill(x, y, x + textW + padding * 2, y + 14, 0x88000000);
        g.drawString(font, Component.literal(icon), x + padding, y + 3, iconColor, false);
        g.drawString(font, Component.literal(label), x + padding + font.width(icon) + 4, y + 3, 0xFFFFFFFF, false);
    }

    private static void renderSpeakingList(GuiGraphics g, Font font, VoiceClient vc, Minecraft mc) {
        Set<UUID> speaking = vc.currentSpeakers(SPEAKING_WINDOW_MS);
        if (speaking.isEmpty() || mc.level == null) return;

        int x = 6;
        int y = 6;
        int row = 0;
        for (UUID id : speaking) {
            Player p = mc.level.getPlayerByUUID(id);
            String name = p != null ? p.getName().getString() : id.toString().substring(0, 8);
            String line = "●  " + name;
            int textW = font.width(line);
            g.fill(x, y + row * 12, x + textW + 6, y + row * 12 + 11, 0x88000000);
            g.drawString(font, Component.literal("●"), x + 3, y + row * 12 + 2, 0xFF44CC44, false);
            g.drawString(font, Component.literal(name), x + 3 + font.width("●") + 4, y + row * 12 + 2, 0xFFFFFFFF, false);
            row++;
            if (row >= 8) break;
        }
    }

    private VoiceHud() {}
}
