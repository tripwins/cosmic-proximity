package com.cosmicproximity.client.gui;

import com.cosmicproximity.client.CosmicProximityClient;
import com.cosmicproximity.client.VoiceClient;
import com.cosmicproximity.client.audio.AudioDevices;
import com.cosmicproximity.config.BundledRelay;
import com.cosmicproximity.config.ClientConfig;
import com.cosmicproximity.config.ClientConfig.VoiceMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class VoiceSettingsScreen extends Screen {

    private static final int BTN_W = 220;
    private static final int BTN_H = 20;
    private static final int METER_W = 220;
    private static final int METER_H = 12;

    private final Screen parent;
    private StandaloneAudioPreview preview;
    private int meterX;
    private int meterY;

    public VoiceSettingsScreen(Screen parent) {
        super(Component.literal("Cosmic Proximity"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        VoiceClient vc = CosmicProximityClient.voiceClient();
        ClientConfig cfg = CosmicProximityClient.clientConfig();

        if (vc != null) {
            vc.setMicTestMode(true);
            preview = null;
        } else {
            preview = new StandaloneAudioPreview(cfg.micDevice, cfg.speakerDevice);
        }

        int cx = width / 2;
        int y = 28;
        int rowGap = 24;

        // ======= RELAY (only when no bundled override) =======
        if (!BundledRelay.ENABLED) {
            addRenderableWidget(CycleButton.onOffBuilder(cfg.useRelay)
                    .create(cx - BTN_W / 2, y, BTN_W, BTN_H,
                            Component.literal("External Relay"),
                            (b, v) -> cfg.useRelay = v));
            y += rowGap;

            EditBox hostBox = new EditBox(font, cx - BTN_W / 2, y, BTN_W * 2 / 3 - 4, BTN_H,
                    Component.literal("Relay Host"));
            hostBox.setMaxLength(64);
            hostBox.setValue(cfg.relayHost);
            hostBox.setHint(Component.literal("relay host or IP"));
            hostBox.setResponder(s -> cfg.relayHost = s);
            addRenderableWidget(hostBox);

            EditBox portBox = new EditBox(font, cx - BTN_W / 2 + BTN_W * 2 / 3, y, BTN_W / 3, BTN_H,
                    Component.literal("Port"));
            portBox.setMaxLength(5);
            portBox.setValue(String.valueOf(cfg.relayPort));
            portBox.setResponder(s -> {
                try { cfg.relayPort = Math.max(1, Math.min(65535, Integer.parseInt(s))); }
                catch (NumberFormatException ignored) {}
            });
            addRenderableWidget(portBox);
            y += rowGap;

            EditBox passwordBox = new EditBox(font, cx - BTN_W / 2, y, BTN_W, BTN_H,
                    Component.literal("Relay Password"));
            passwordBox.setMaxLength(128);
            passwordBox.setValue(cfg.relayPassword);
            passwordBox.setHint(Component.literal("shared room password"));
            passwordBox.setResponder(s -> cfg.relayPassword = s);
            addRenderableWidget(passwordBox);
            y += rowGap + 4;
        }

        // ======= MICROPHONE =======
        addRenderableWidget(cycleDeviceButton(
                cx - BTN_W / 2, y, "Microphone",
                AudioDevices.captureDeviceNames(),
                cfg.micDevice,
                name -> {
                    cfg.micDevice = name;
                    if (vc != null) vc.switchMicDevice(name);
                    else if (preview != null) preview.switchMic(name);
                }));
        y += rowGap;

        addRenderableWidget(CycleButton.<VoiceMode>builder(
                        mode -> Component.literal(mode == VoiceMode.OPEN_MIC ? "Open Mic (VAD)" : "Push to Talk"),
                        cfg.voiceMode)
                .withValues(VoiceMode.values())
                .create(cx - BTN_W / 2, y, BTN_W, BTN_H, Component.literal("Voice Mode"),
                        (b, v) -> cfg.voiceMode = v));
        y += rowGap;

        meterX = cx - METER_W / 2;
        meterY = y + 4;
        y += METER_H + 10;

        addRenderableWidget(new VolumeSlider(
                cx - BTN_W / 2, y, BTN_W, BTN_H, "Mic Boost", cfg.micVolume / 2f,
                v -> cfg.micVolume = v * 2f));
        y += rowGap;

        addRenderableWidget(new VolumeSlider(
                cx - BTN_W / 2, y, BTN_W, BTN_H, "Noise Suppression", cfg.noiseSuppression,
                v -> cfg.noiseSuppression = v));
        y += rowGap;

        addRenderableWidget(new VolumeSlider(
                cx - BTN_W / 2, y, BTN_W, BTN_H, "Voice Activation Threshold", cfg.vadThreshold,
                v -> cfg.vadThreshold = Math.max(0.001f, v)));
        y += rowGap + 4;

        // ======= SPEAKERS =======
        addRenderableWidget(cycleDeviceButton(
                cx - BTN_W / 2, y, "Output",
                AudioDevices.playbackDeviceNames(),
                cfg.speakerDevice,
                name -> {
                    cfg.speakerDevice = name;
                    if (vc != null) vc.switchSpeakerDevice(name);
                    else if (preview != null) preview.switchSpeaker(name);
                }));
        y += rowGap;

        addRenderableWidget(new VolumeSlider(
                cx - BTN_W / 2, y, BTN_W, BTN_H, "Speaker Volume", cfg.speakerVolume,
                v -> { cfg.speakerVolume = v; if (vc != null) vc.setSpeakerVolume(v); }));
        y += rowGap;

        addRenderableWidget(Button.builder(Component.literal("Test Speaker"), b -> {
                    VoiceClient v = CosmicProximityClient.voiceClient();
                    if (v != null) v.playTestTone();
                    else if (preview != null) preview.playTestTone();
                })
                .bounds(cx - BTN_W / 2, y, BTN_W / 2 - 4, BTN_H).build());

        boolean initialLoopback = vc != null ? vc.isMicLoopback()
                : (preview != null && preview.isLoopback());
        addRenderableWidget(CycleButton.onOffBuilder(initialLoopback)
                .create(cx + 4, y, BTN_W / 2 - 4, BTN_H,
                        Component.literal("Hear Myself"),
                        (b, v) -> {
                            VoiceClient cur = CosmicProximityClient.voiceClient();
                            if (cur != null) cur.setMicLoopback(v);
                            else if (preview != null) preview.setLoopback(v);
                        }));
        y += rowGap + 4;

        addRenderableWidget(CycleButton.onOffBuilder(cfg.muted)
                .create(cx - BTN_W / 2, y, BTN_W / 2 - 4, BTN_H,
                        Component.literal("Mute Mic"),
                        (b, v) -> cfg.muted = v));
        addRenderableWidget(CycleButton.onOffBuilder(cfg.deafened)
                .create(cx + 4, y, BTN_W / 2 - 4, BTN_H,
                        Component.literal("Deafen"),
                        (b, v) -> { cfg.deafened = v; if (vc != null) vc.setDeafened(v); }));
        y += rowGap + 6;

        // ======= Per-player mute =======
        if (vc != null && !vc.recentSpeakers().isEmpty()) {
            java.util.UUID localId = Minecraft.getInstance().getUser().getProfileId();
            int shown = 0;
            for (var e : vc.recentSpeakers().entrySet()) {
                if (shown >= 5) break;
                java.util.UUID id = e.getKey();
                if (id.equals(localId)) continue;
                String name = e.getValue();
                addRenderableWidget(CycleButton.onOffBuilder(cfg.mutedPlayers.contains(id))
                        .create(cx - BTN_W / 2, y, BTN_W, BTN_H,
                                Component.literal("Mute " + name),
                                (b, v) -> {
                                    if (v) cfg.mutedPlayers.add(id);
                                    else cfg.mutedPlayers.remove(id);
                                }));
                y += rowGap - 2;
                shown++;
            }
            y += 6;
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 100, y, 200, BTN_H).build());
    }

    private CycleButton<String> cycleDeviceButton(int x, int y, String label,
                                                  List<String> values, String initial,
                                                  Consumer<String> onChange) {
        String selected = values.contains(initial) ? initial : "";
        return CycleButton.<String>builder(
                        name -> Component.literal(name.isEmpty() ? "Default" : trimDeviceName(name)),
                        selected)
                .withValues(values)
                .create(x, y, BTN_W, BTN_H, Component.literal(label), (b, v) -> onChange.accept(v));
    }

    private static String trimDeviceName(String s) {
        return s.length() > 32 ? s.substring(0, 29) + "..." : s;
    }

    private float currentMicRms() {
        VoiceClient vc = CosmicProximityClient.voiceClient();
        if (vc != null) return vc.micRms();
        return preview != null ? preview.micRms() : 0f;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        drawMeter(g);
        drawStatusFooter(g);
    }

    private void drawMeter(GuiGraphics g) {
        ClientConfig cfg = CosmicProximityClient.clientConfig();
        float rms = currentMicRms();
        float clamped = Math.min(1f, rms * 4f);

        g.fill(meterX - 1, meterY - 1, meterX + METER_W + 1, meterY + METER_H + 1, 0xFF202020);
        g.fill(meterX, meterY, meterX + METER_W, meterY + METER_H, 0xFF101010);

        int filled = (int) (clamped * METER_W);
        for (int i = 0; i < filled; i++) {
            float t = (float) i / METER_W;
            g.fill(meterX + i, meterY, meterX + i + 1, meterY + METER_H, colorRamp(t));
        }

        int thresholdX = meterX + (int) (cfg.vadThreshold * METER_W * 4f);
        if (thresholdX < meterX + METER_W) {
            g.fill(thresholdX, meterY - 2, thresholdX + 1, meterY + METER_H + 2, 0xFFFFFF00);
        }

        g.drawString(font, Component.literal("Mic level"), meterX, meterY - 10, 0xFFAAAAAA, false);

        if (preview != null) {
            long bytes = preview.micBytesRead();
            g.drawString(font, Component.literal(bytes + " B"),
                    meterX + METER_W - 36, meterY - 10, bytes > 0 ? 0xFF80FF80 : 0xFFFF8080, false);
        }
    }

    private void drawStatusFooter(GuiGraphics g) {
        String msg;
        int color;
        VoiceClient vc = CosmicProximityClient.voiceClient();

        if (vc != null) {
            msg = "Voice connected.";
            color = 0xFF80FF80;
        } else if (preview == null) {
            msg = "Preview not started.";
            color = 0xFFFFFF80;
        } else if (preview.lastError() != null && !preview.micOpen()) {
            msg = "Mic error: " + preview.lastError();
            color = 0xFFFF6060;
        } else if (preview.lastError() != null && !preview.speakerOpen()) {
            msg = "Speaker error: " + preview.lastError();
            color = 0xFFFF6060;
        } else if (!preview.micOpen() || !preview.speakerOpen()) {
            msg = "Opening audio devices...";
            color = 0xFFFFFF80;
        } else {
            msg = "Connecting to voice server...";
            color = 0xFFFFCC80;
        }
        g.drawCenteredString(font, Component.literal(msg), width / 2, height - 12, color);
    }

    private static int colorRamp(float t) {
        if (t < 0.7f) return 0xFF22CC22;
        if (t < 0.9f) return 0xFFCCCC22;
        return 0xFFCC2222;
    }

    @Override
    public void onClose() {
        if (preview != null) {
            preview.close();
            preview = null;
        }
        VoiceClient vc = CosmicProximityClient.voiceClient();
        if (vc != null) vc.setMicTestMode(false);
        ClientConfig cfg = CosmicProximityClient.clientConfig();
        Path configPath = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("cosmicproximity-client.json");
        cfg.save(configPath);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static final class VolumeSlider extends AbstractSliderButton {
        private final String label;
        private final Consumer<Float> onChange;

        VolumeSlider(int x, int y, int w, int h, String label, float initial, Consumer<Float> onChange) {
            super(x, y, w, h, Component.empty(), Math.max(0, Math.min(1, initial)));
            this.label = label;
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            onChange.accept((float) value);
        }
    }
}
