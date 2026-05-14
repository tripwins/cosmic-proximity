package com.cosmicproximity.client;

import com.cosmicproximity.CosmicProximity;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class KeyBinds {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(CosmicProximity.MOD_ID, "main"));

    public static final KeyMapping PUSH_TO_TALK = new KeyMapping(
            "key.cosmicproximity.push_to_talk",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    public static final KeyMapping TOGGLE_MUTE = new KeyMapping(
            "key.cosmicproximity.toggle_mute",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);

    public static final KeyMapping TOGGLE_DEAFEN = new KeyMapping(
            "key.cosmicproximity.toggle_deafen",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);

    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.cosmicproximity.open_settings",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    public static void register() {
        KeyBindingHelper.registerKeyBinding(PUSH_TO_TALK);
        KeyBindingHelper.registerKeyBinding(TOGGLE_MUTE);
        KeyBindingHelper.registerKeyBinding(TOGGLE_DEAFEN);
        KeyBindingHelper.registerKeyBinding(OPEN_SETTINGS);
    }

    private KeyBinds() {}
}
