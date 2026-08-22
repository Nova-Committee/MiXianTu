package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.attachment.ForgingSessionComponent;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint.FailureSettlement;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.forging.ForgingService.Failure;
import com.iafenvoy.mxt.runtime.forging.ForgingService.FinishResult;
import com.iafenvoy.mxt.runtime.forging.ForgingService.StartResult;
import com.iafenvoy.mxt.runtime.forging.ForgingService.StrikeResult;
import com.iafenvoy.mxt.runtime.forging.ForgingWorldComponent.StationSession;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map.Entry;
import java.util.Optional;

/**
 * Authoritative station-bound forging transaction boundary.
 */
public final class ForgingWorkstationService {
    private static final double MAX_DISTANCE_SQUARED = 64.0D;

    private ForgingWorkstationService() {
    }

    public static boolean start(ServerPlayer player, BlockPos position, Identifier blueprintId, ForgingBlueprint blueprint) {
        ServerLevel level = player.level();
        if (!canUse(player, position, blueprint)) return false;
        ForgingWorldComponent world = level.getData(MxtAttachments.FORGING_WORLD);
        if (world.get(position).isPresent()) return false;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !BuiltInRegistries.ITEM.getKey(held.getItem()).equals(blueprint.input())) return false;
        StartResult result = ForgingService.start(blueprint);
        if (!result.started()) return false;
        ItemStack input = held.copyWithCount(1);
        held.shrink(1);
        ForgingSessionComponent data = new ForgingSessionComponent();
        data.start(MxtDatapackRegistries.holder(MxtResourceKeys.FORGING_BLUEPRINT, blueprintId).orElseThrow(), blueprint.plan(), result.session(), input, blueprint.result(), blueprint.qualityByExtraSteps(), blueprint.failureSettlement());
        if (!world.put(position, player.getUUID(), data)) {
            if (!player.getInventory().add(input)) player.drop(input, false);
            return false;
        }
        return true;
    }

    public static boolean strike(ServerPlayer player, BlockPos position, Identifier methodId, ForgingMethod method) {
        StationSession station = stationForOwner(player, position).orElse(null);
        if (station == null || station.session().plan().isEmpty() || station.session().session().isEmpty())
            return false;
        ForgingSession session = ForgingSession.restore(station.session().plan().orElseThrow(), station.session().session().orElseThrow());
        FormulaContext context = FormulaContext.of(player);
        boolean conditionsMet = method.condition().test(player, context);
        StrikeResult result = ForgingService.strike(session, methodId, method,
                player.getData(MxtAttachments.RESOURCE_HOLDER), context, () -> conditionsMet);
        if (!result.struck()) return false;
        station.session().update(session);
        return true;
    }

    public static boolean finish(ServerPlayer player, BlockPos position, Identifier blueprintId) {
        StationSession station = stationForOwner(player, position).orElse(null);
        if (station == null || station.session().blueprint().map(HolderHelper::id).filter(blueprintId::equals).isEmpty() || station.session().plan().isEmpty() || station.session().session().isEmpty())
            return false;
        ForgingSessionComponent data = station.session();
        ForgingSession session = ForgingSession.restore(data.plan().orElseThrow(), data.session().orElseThrow());
        FinishResult result = ForgingService.finish(blueprintId, session, data::qualityFor);
        if (!result.finished()) {
            if (result.failure() == Failure.CANCELLED) return false;
            settleFailure(player, position, data);
            return true;
        }
        Identifier resultId = data.result().orElseThrow();
        ItemStack output = BuiltInRegistries.ITEM.getOptional(resultId).map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
        if (output.isEmpty()) return false;
        output.set(MxtDataComponents.FORGING_RESULT.get(), result.result());
        if (!player.getInventory().add(output)) player.drop(output, false);
        MxtDatapackRegistries.get(MxtResourceKeys.FORGING_BLUEPRINT, blueprintId)
                .ifPresent(blueprint -> blueprint.completeAction().execute(player, FormulaContext.of(player)));
        player.level().getData(MxtAttachments.FORGING_WORLD).remove(position);
        return true;
    }

    public static boolean cancel(ServerPlayer player, BlockPos position) {
        StationSession station = stationForOwner(player, position).orElse(null);
        if (station == null) return false;
        ForgingSessionComponent data = station.session();
        if (data.plan().isEmpty() || data.session().isEmpty() || data.input().isEmpty()) return false;
        ForgingSession session = ForgingSession.restore(data.plan().orElseThrow(), data.session().orElseThrow());
        if (!ForgingService.cancel(session)) return false;
        data.blueprint().flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.FORGING_BLUEPRINT, id))
                .ifPresent(blueprint -> blueprint.failAction().execute(player, FormulaContext.of(player)));
        ItemStack input = data.input().orElseThrow();
        if (!player.getInventory().add(input)) player.drop(input, false);
        player.level().getData(MxtAttachments.FORGING_WORLD).remove(position);
        return true;
    }

    /**
     * Death recovery returns every still-locked station input owned by the player, including another dimension.
     */
    public static int cancelAllOwnedOnDeath(ServerPlayer player) {
        int cancelled = 0;
        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            ForgingWorldComponent world = level.getData(MxtAttachments.FORGING_WORLD);
            for (Entry<BlockPos, StationSession> entry : world.sessions().entrySet()) {
                if (!entry.getValue().owner().equals(player.getUUID())) continue;
                ForgingSessionComponent data = entry.getValue().session();
                if (data.input().isEmpty()) continue;
                data.blueprint().flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.FORGING_BLUEPRINT, id))
                        .ifPresent(blueprint -> blueprint.failAction().execute(player, FormulaContext.of(player)));
                player.spawnAtLocation(player.level(), data.input().orElseThrow());
                world.remove(entry.getKey());
                cancelled++;
            }
        }
        return cancelled;
    }

    public static boolean canUse(ServerPlayer player, BlockPos position, ForgingBlueprint blueprint) {
        if (player.distanceToSqr(position.getCenter()) > MAX_DISTANCE_SQUARED) return false;
        Identifier block = BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(position).getBlock());
        return RegistryCodecs.matches(blueprint.workstationBlocks(), BuiltInRegistries.BLOCK,
                Registries.BLOCK, block);
    }

    private static void settleFailure(ServerPlayer player, BlockPos position, ForgingSessionComponent data) {
        FailureSettlement settlement = data.failureSettlement();
        if (player.getRandom().nextDouble() < settlement.inputReturnRatio()) {
            data.input().ifPresent(input -> give(player, input));
        }
        if (player.getRandom().nextDouble() < settlement.failureProductRatio()) {
            settlement.result().flatMap(BuiltInRegistries.ITEM::getOptional).map(ItemStack::new)
                    .ifPresent(output -> give(player, output));
        }
        data.blueprint().flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.FORGING_BLUEPRINT, id))
                .ifPresent(blueprint -> blueprint.failAction().execute(player, FormulaContext.of(player)));
        player.level().getData(MxtAttachments.FORGING_WORLD).remove(position);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static Optional<StationSession> stationForOwner(ServerPlayer player, BlockPos position) {
        StationSession station = player.level().getData(MxtAttachments.FORGING_WORLD).get(position).orElse(null);
        if (station == null || !station.owner().equals(player.getUUID())) return Optional.empty();
        ForgingBlueprint blueprint = station.session().blueprint().map(Holder::value).orElse(null);
        return blueprint != null && canUse(player, position, blueprint) ? Optional.of(station) : Optional.empty();
    }
}
