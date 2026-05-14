package com.cosmicproximity.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class Payloads {
    public static void registerTypes() {
        PayloadTypeRegistry.playS2C().register(VoiceSessionInitPayload.TYPE, VoiceSessionInitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VoiceClientReadyPayload.TYPE, VoiceClientReadyPayload.CODEC);
    }

    private Payloads() {}
}
