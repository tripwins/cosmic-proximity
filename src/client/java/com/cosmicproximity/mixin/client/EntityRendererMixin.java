package com.cosmicproximity.mixin.client;

import com.cosmicproximity.client.CosmicProximityClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prepends a small "♪" prefix to the nametag of any player whose UUID is in our
 * relay-participant list — i.e., anyone who currently has the voice mod installed
 * and is connected to the same relay.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void cosmicproximity$markVoiceUsers(Entity entity, CallbackInfoReturnable<Component> cir) {
        Component value = cir.getReturnValue();
        if (value == null) return;
        if (!(entity instanceof Player p)) return;
        if (!CosmicProximityClient.hasMod(p.getUUID())) return;

        MutableComponent prefix = Component.literal("♪ ").withStyle(ChatFormatting.AQUA);
        cir.setReturnValue(prefix.append(value));
    }
}
