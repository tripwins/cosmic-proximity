package com.cosmicproximity.client.gui;

import com.cosmicproximity.client.CosmicProximityClient;
import com.cosmicproximity.client.VoiceClient;
import com.cosmicproximity.mixin.client.CameraAccessor;
import com.cosmicproximity.mixin.client.GameRendererAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Set;
import java.util.UUID;

/**
 * Draws a billboarded "♪" above any player we've heard a frame from in the
 * last 300ms. Uses the same world rendering pattern vanilla uses for name tags.
 */
public final class PlayerSpeakingIndicator {
    private static final Component ICON = Component.literal("♪");
    private static final int COLOR = 0xFF44CC44;
    private static final int FULL_LIGHT = 0xF000F0;
    private static final long SPEAKING_WINDOW_MS = 300;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(PlayerSpeakingIndicator::render);
    }

    private static void render(WorldRenderContext ctx) {
        VoiceClient vc = CosmicProximityClient.voiceClient();
        if (vc == null) return;
        Set<UUID> speakers = vc.currentSpeakers(SPEAKING_WINDOW_MS);
        if (speakers.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        MultiBufferSource consumers = ctx.consumers();
        if (consumers == null) return;

        Camera cam = ((GameRendererAccessor) mc.gameRenderer).cosmicproximity$getMainCamera();
        if (cam == null) return;
        Vec3 camPos = ((CameraAccessor) cam).cosmicproximity$getPosition();
        if (camPos == null) return;
        // The matrix stack at AFTER_ENTITIES is camera-relative; we translate by (world - camera).

        PoseStack stack = ctx.matrices();
        Font font = mc.font;
        float halfWidth = font.width(ICON) / -2f;

        Entity localPlayer = mc.player;

        for (Player p : level.players()) {
            if (!speakers.contains(p.getUUID())) continue;
            if (p == localPlayer) continue;

            double y = p.getY() + p.getBbHeight() + 0.5;
            stack.pushPose();
            stack.translate(p.getX() - camPos.x, y - camPos.y, p.getZ() - camPos.z);
            stack.mulPose(cam.rotation());
            stack.scale(-0.025f, -0.025f, 0.025f);

            Matrix4f matrix = stack.last().pose();
            font.drawInBatch(ICON, halfWidth, 0,
                    COLOR, false, matrix, consumers, Font.DisplayMode.NORMAL, 0, FULL_LIGHT);

            stack.popPose();
        }
    }

    private PlayerSpeakingIndicator() {}
}
