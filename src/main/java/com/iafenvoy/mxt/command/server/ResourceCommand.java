package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceBar;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext.Values;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.ActualConcentrationContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.EnvironmentConcentrationContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /resource} command; also reachable as {@code /mxt resource}. Reading and writing one account's value
 * and inspecting the bars a resource declares are the same subject, so they share one node: bare lists every bar,
 * naming a resource reads its value, and {@code bar} or {@code set} go a level deeper. Writing needs operator
 * rights; the rest is open to anyone asking about their own body.
 */
public final class ResourceCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("resource")
                .executes(ctx -> listBars(ctx.getSource(), null, null))
                .then(argument("id", ResourceArgument.resource(context, MxtResourceKeys.RESOURCE))
                        .executes(ctx -> query(ctx.getSource(), resource(ctx, "id")))
                        .then(literal("set").requires(ServerCommandManager::mayChange)
                                .then(argument("value", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> set(ctx.getSource(), resource(ctx, "id"),
                                                DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(literal("bar")
                                .executes(ctx -> listBars(ctx.getSource(), resource(ctx, "id"), null))
                                .then(argument("index", IntegerArgumentType.integer(0, 255))
                                        .executes(ctx -> listBars(ctx.getSource(), resource(ctx, "id"),
                                                IntegerArgumentType.getInteger(ctx, "index"))))));
    }

    private static Reference<Resource> resource(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, name, MxtResourceKeys.RESOURCE);
    }

    private static int query(CommandSourceStack source, Reference<Resource> resource) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        double value = player.getData(MxtAttachments.RESOURCE_HOLDER).get(resource);
        source.sendSuccess(() -> Component.translatable("command.mxt.resource.query", DefinitionText.nameOrId(resource), value), false);
        return 1;
    }

    private static int set(CommandSourceStack source, Reference<Resource> resource, double value) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        player.getData(MxtAttachments.RESOURCE_HOLDER).set(resource, value);
        source.sendSuccess(() -> Component.translatable("command.mxt.resource.set", DefinitionText.nameOrId(resource), value), true);
        return 1;
    }

    private static int listBars(CommandSourceStack source, @Nullable Reference<Resource> selection, Integer index) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<Reference<Resource>> resources = selection != null ? List.of(selection)
                : MxtDatapackRegistries.holders(player.level().registryAccess(), MxtResourceKeys.RESOURCE).toList();
        if (resources.isEmpty()) {
            source.sendFailure(Component.translatable("command.mxt.resourcebar.unknown_resource", "null"));
            return 0;
        }
        int shown = 0;
        for (Reference<Resource> resource : resources) {
            List<ResourceBar> bars = resource.value().bars();
            if (index != null && index >= bars.size()) {
                source.sendFailure(Component.translatable("command.mxt.resourcebar.unknown_index",
                        HolderHelper.id(resource).toString(), index, bars.size()));
                return 0;
            }
            for (int barIndex = 0; barIndex < bars.size(); barIndex++) {
                if (index != null && barIndex != index) continue;
                sendBar(source, player, resource, barIndex, bars.get(barIndex));
                shown++;
            }
        }
        if (shown == 0) {
            source.sendFailure(Component.translatable("command.mxt.resourcebar.empty",
                    selection == null ? "all" : HolderHelper.id(selection).toString()));
            return 0;
        }
        return shown;
    }

    private static void sendBar(CommandSourceStack source, ServerPlayer player, Reference<Resource> resource,
                                int index, ResourceBar bar) {
        String contextId = String.valueOf(MxtRegistries.RESOURCE_BAR_CONTEXT.getKey(bar.context()));
        Component displayName = bar.context().name(resource);
        Optional<Values> extracted = extractValues(player, resource, bar.context());
        if (extracted.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.resourcebar.unavailable",
                    DefinitionText.nameOrId(resource), index, displayName, contextId), false);
            return;
        }
        Values values = extracted.get();
        double maximum = bar.maximum().orElse(values.maximum());
        double percentage = maximum == values.minimum() ? 100.0D
                : (values.current() - values.minimum()) / (maximum - values.minimum()) * 100.0D;
        source.sendSuccess(() -> Component.translatable("command.mxt.resourcebar.entry",
                DefinitionText.nameOrId(resource), index, displayName, contextId,
                formatRaw(values.current()), formatRaw(values.minimum()), formatRaw(maximum), formatRaw(percentage),
                bar.anchor().getSerializedName(), bar.order()), false);
    }

    private static Optional<Values> extractValues(ServerPlayer player, Reference<Resource> resource,
                                                  ResourceBarContext context) {
        // A bar is declared on a value, but the two concentration sources are aura pools: the value names the
        // aura it carries, and a value that carries none has no concentration to report.
        Holder<Aura> aura = AuraLookup.holder(player, resource).orElse(null);
        if (context == ActualConcentrationContext.INSTANCE) {
            if (aura == null) return Optional.empty();
            AuraPool pool = AuraService.getPositionAura(player.level(), player.blockPosition()).pool(aura);
            return Optional.of(new Values(pool.amount(), 0.0D, pool.maximum(), -1L));
        }
        if (context == EnvironmentConcentrationContext.INSTANCE) {
            if (aura == null) return Optional.empty();
            AuraPool pool = AuraService.getSensedAura(player.level(), player.blockPosition()).pool(aura);
            return Optional.of(new Values(pool.amount(), 0.0D, pool.maximum(), -1L));
        }
        return context.extract(player, resource);
    }

    private static String formatRaw(double value) {
        if (Double.isNaN(value)) return "NaN";
        if (value == Double.POSITIVE_INFINITY) return "∞";
        if (value == Double.NEGATIVE_INFINITY) return "-∞";
        return Double.toString(value);
    }
}
