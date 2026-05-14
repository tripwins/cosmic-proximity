package com.cosmicproximity.client;

import com.cosmicproximity.config.ClientConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Registers /voicemute and /voiceunmute as client-only chat commands.
 * These mutate {@link ClientConfig#mutedPlayers} — i.e., personal client-side mute,
 * not the relay-side admin mute (which is /mute in Discord).
 */
public final class VoiceMuteCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("voicemute")
                            .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                    .suggests(VoiceMuteCommand::suggestPlayers)
                                    .executes(VoiceMuteCommand::doMute))
            );
            dispatcher.register(
                    ClientCommandManager.literal("voiceunmute")
                            .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                    .suggests(VoiceMuteCommand::suggestPlayers)
                                    .executes(VoiceMuteCommand::doUnmute))
            );
            // Also register a /voicemutes (no arg) to list current personal mutes.
            dispatcher.register(
                    ClientCommandManager.literal("voicemutes").executes(VoiceMuteCommand::doList)
            );
        });
    }

    private static int doMute(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        UUID uuid = resolveUuid(name);
        FabricClientCommandSource src = ctx.getSource();
        if (uuid == null) {
            src.sendFeedback(Component.literal("§cNo nearby/tab-list player named §f" + name + "§c."));
            return 0;
        }
        if (uuid.equals(localUuid())) {
            src.sendFeedback(Component.literal("§eCan't mute yourself."));
            return 0;
        }
        ClientConfig cfg = CosmicProximityClient.clientConfig();
        if (cfg.mutedPlayers.add(uuid)) {
            saveConfig(cfg);
            src.sendFeedback(Component.literal("§6Voice-muted §f" + name + "§6 (personal)."));
        } else {
            src.sendFeedback(Component.literal("§7" + name + " was already muted."));
        }
        return 1;
    }

    private static int doUnmute(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        FabricClientCommandSource src = ctx.getSource();
        ClientConfig cfg = CosmicProximityClient.clientConfig();
        UUID uuid = resolveUuid(name);
        boolean removed = false;
        if (uuid != null) {
            removed = cfg.mutedPlayers.remove(uuid);
        } else {
            // Player not online — try to find in mute list by partial UUID match or skip.
            // For simplicity: only unmute resolvable players. Offer a hint.
            src.sendFeedback(Component.literal(
                    "§cNo nearby/tab-list player named §f" + name + "§c. Use §f/voicemutes§c to see current mutes."));
            return 0;
        }
        if (removed) {
            saveConfig(cfg);
            src.sendFeedback(Component.literal("§aVoice-unmuted §f" + name + "§a."));
            return 1;
        }
        src.sendFeedback(Component.literal("§7" + name + " wasn't muted."));
        return 0;
    }

    private static int doList(CommandContext<FabricClientCommandSource> ctx) {
        ClientConfig cfg = CosmicProximityClient.clientConfig();
        FabricClientCommandSource src = ctx.getSource();
        if (cfg.mutedPlayers.isEmpty()) {
            src.sendFeedback(Component.literal("§7No personal voice mutes."));
            return 0;
        }
        StringBuilder sb = new StringBuilder("§6Personal voice mutes (" + cfg.mutedPlayers.size() + "):\n");
        Minecraft mc = Minecraft.getInstance();
        for (UUID id : cfg.mutedPlayers) {
            String name = resolveName(mc, id);
            sb.append("§7• §f").append(name).append(" §8(").append(id.toString(), 0, 8).append("…)\n");
        }
        src.sendFeedback(Component.literal(sb.toString().stripTrailing()));
        return cfg.mutedPlayers.size();
    }

    // ---- helpers ----

    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        Minecraft mc = Minecraft.getInstance();
        UUID self = localUuid();
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                String name = info.getProfile().name();
                if (name == null) continue;
                if (info.getProfile().id().equals(self)) continue;
                if (name.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    }

    private static UUID resolveUuid(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Player p : mc.level.players()) {
                if (p.getName().getString().equalsIgnoreCase(name)) return p.getUUID();
            }
        }
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                String n = info.getProfile().name();
                if (n != null && n.equalsIgnoreCase(name)) return info.getProfile().id();
            }
        }
        return null;
    }

    private static String resolveName(Minecraft mc, UUID id) {
        if (mc.level != null) {
            Player p = mc.level.getPlayerByUUID(id);
            if (p != null) return p.getName().getString();
        }
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null && info.getProfile().name() != null) return info.getProfile().name();
        }
        return id.toString().substring(0, 8) + "…";
    }

    private static UUID localUuid() {
        return Minecraft.getInstance().getUser().getProfileId();
    }

    private static void saveConfig(ClientConfig cfg) {
        Path configPath = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("cosmicproximity-client.json");
        cfg.save(configPath);
    }

    private VoiceMuteCommand() {}
}
