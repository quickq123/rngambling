package rngambling.outcome;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin commands for the Sandbox (operators only):
 * <ul>
 * <li>{@code /rngambling sandbox list}: everyone with a visit on record: whether they're in the
 * Sandbox now, where they'll be brought back to, and how many item stacks their saved inventory copy holds.
 * <li>{@code /rngambling sandbox return <player>}: brings a player out to their exact return point,
 * ending the visit (what's left in their box comes back with them).
 * <li>{@code /rngambling sandbox restore <player>}: replaces a player's inventory with the copy
 * taken when their last visit started. It replaces rather than adds, so it can't duplicate items.
 * </ul>
 */
public final class SandboxCommands {
    private SandboxCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rngambling")
                .then(Commands.literal("sandbox")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("list").executes(context -> list(context.getSource())))
                        .then(Commands.literal("return")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> bringBack(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> restore(context.getSource(), EntityArgument.getPlayer(context, "player")))))));
    }

    private static int list(CommandSourceStack source) {
        Map<UUID, SandboxRecords.Visit> visits = SandboxRecords.get(source.getServer()).all();
        if (visits.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.rngambling.sandbox.none"), false);
            return 0;
        }
        long now = System.currentTimeMillis();
        visits.values().stream().sorted((a, b) -> Long.compare(b.startedAt(), a.startedAt())).forEach(visit -> {
            long minutes = Math.max(0, (now - visit.startedAt()) / 60_000);
            Component line = Component.translatable(visit.active() ? "command.rngambling.sandbox.line_active" : "command.rngambling.sandbox.line_done",
                    visit.name(), String.format("%s %.1f %.1f %.1f", visit.level(), visit.x(), visit.y(), visit.z()),
                    visit.itemCount(), minutes);
            source.sendSuccess(() -> line, false);
        });
        return visits.size();
    }

    private static int bringBack(CommandSourceStack source, ServerPlayer player) {
        if (!Sandbox.inSandbox(player)) {
            source.sendFailure(Component.translatable("command.rngambling.sandbox.not_there", player.getDisplayName()));
            return 0;
        }
        String where = Sandbox.bringBack(player);
        source.sendSuccess(() -> Component.translatable("command.rngambling.sandbox.returned", player.getDisplayName(), where), true);
        return 1;
    }

    private static int restore(CommandSourceStack source, ServerPlayer player) {
        SandboxRecords records = SandboxRecords.get(source.getServer());
        if (!records.restore(player)) {
            source.sendFailure(Component.translatable("command.rngambling.sandbox.no_copy", player.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.rngambling.sandbox.restored", player.getDisplayName(),
                records.get(player.getUUID()).itemCount()), true);
        return 1;
    }
}
