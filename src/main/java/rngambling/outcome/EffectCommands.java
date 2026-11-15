package rngambling.outcome;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import rngambling.luck.Luck;

/**
 * Admin commands to test effects (operators only). The target is any player by name, or the one
 * running the command when left out:
 * <ul>
 * <li>{@code /rngambling effect play <effect> [player] [luck]}: plays one effect (catalog or
 * theme) as if that player broke a lucky block. Luck defaults to the middle of the effect's band.
 * <li>{@code /rngambling effect roll [player] [luck]}: a real draw, as a lucky block of that
 * luck (default 0) broken where the player stands would make, themed by its luck zone.
 * <li>{@code /rngambling effect list [tier]}: the effect ids, by tier.
 * </ul>
 * The "lucky block" is taken to be two blocks in front of the player (where it's empty), so drops
 * and blocks the effect places land beside them, not on them.
 */
public final class EffectCommands {
    private EffectCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var effect = Commands.argument("effect", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Outcomes.everyEffect().keySet(), builder))
                .executes(context -> play(context, self(context), null))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> play(context, EntityArgument.getPlayer(context, "player"), null))
                        .then(luck().executes(context -> play(context, EntityArgument.getPlayer(context, "player"),
                                IntegerArgumentType.getInteger(context, "luck")))));
        var roll = Commands.literal("roll")
                .executes(context -> roll(context, self(context), 0))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> roll(context, EntityArgument.getPlayer(context, "player"), 0))
                        .then(luck().executes(context -> roll(context, EntityArgument.getPlayer(context, "player"),
                                IntegerArgumentType.getInteger(context, "luck")))));
        var list = Commands.literal("list")
                .executes(context -> list(context.getSource(), null))
                .then(Commands.argument("tier", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(Tier.values()).map(t -> t.name().toLowerCase()), builder))
                        .executes(context -> list(context.getSource(), StringArgumentType.getString(context, "tier"))));
        dispatcher.register(Commands.literal("rngambling")
                .then(Commands.literal("effect")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("play").then(effect))
                        .then(roll)
                        .then(list)));
        // /rngambling journal reveal|reset [player]: every effect marked as had, or none (for testing the Lucky Journal).
        for (String action : new String[] {"reveal", "reset"}) {
            boolean all = action.equals("reveal");
            dispatcher.register(Commands.literal("rngambling")
                    .then(Commands.literal("journal")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .then(Commands.literal(action)
                                    .executes(context -> journal(context.getSource(), self(context), all))
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(context -> journal(context.getSource(), EntityArgument.getPlayer(context, "player"), all))))));
        }
    }

    private static int journal(CommandSourceStack source, ServerPlayer player, boolean all) {
        Journal.setAll(player, all);
        source.sendSuccess(() -> Component.translatable(all ? "commands.rngambling.journal.reveal" : "commands.rngambling.journal.reset",
                player.getDisplayName()), true);
        return 1;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> luck() {
        return Commands.argument("luck", IntegerArgumentType.integer(Luck.MIN, Luck.MAX));
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    /** Where the "lucky block" was: two blocks ahead of the player if that's open, else where they stand. */
    static BlockPos blockSpot(ServerPlayer player) {
        BlockPos ahead = player.blockPosition().relative(player.getDirection(), 2);
        return player.level().isEmptyBlock(ahead) ? ahead : player.blockPosition();
    }

    private static int play(CommandContext<CommandSourceStack> context, ServerPlayer player, Integer luck) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "effect");
        Outcome outcome = Outcomes.everyEffect().get(id);
        if (outcome == null) {
            source.sendFailure(Component.translatable("command.rngambling.effect.unknown", id));
            return 0;
        }
        if (outcome.has(Outcome.Flag.ROOM) && !Sandbox.available(source.getServer())) {
            source.sendFailure(Component.translatable("command.rngambling.effect.no_sandbox", id));
            return 0;
        }
        int at = luck != null ? luck : Luck.clamp((outcome.luckMin() + outcome.luckMax()) / 2);
        Outcomes.play(player.level(), blockSpot(player), player, at, outcome);
        source.sendSuccess(() -> Component.translatable("command.rngambling.effect.played", id, player.getDisplayName(), at), true);
        return 1;
    }

    private static int roll(CommandContext<CommandSourceStack> context, ServerPlayer player, int luck) {
        Outcome outcome = Outcomes.roll(player.level(), blockSpot(player), player, luck);
        context.getSource().sendSuccess(() -> Component.translatable("command.rngambling.effect.rolled", outcome.id(),
                player.getDisplayName(), luck), true);
        return 1;
    }

    private static int list(CommandSourceStack source, String tierName) {
        int shown = 0;
        for (Tier tier : Tier.values()) {
            if (tierName != null && !tier.name().equalsIgnoreCase(tierName)) {
                continue;
            }
            List<String> ids = new ArrayList<>();
            for (Outcome outcome : Outcomes.everyEffect().values()) {
                if (outcome.tier() == tier) {
                    ids.add(outcome.id());
                }
            }
            shown += ids.size();
            Component line = Component.translatable("command.rngambling.effect.list_tier", tier.name().toLowerCase(), ids.size(),
                    String.join(", ", ids)).withStyle(tier.colour);
            source.sendSuccess(() -> line, false);
        }
        return shown;
    }
}
