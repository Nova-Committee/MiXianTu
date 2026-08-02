package com.iafenvoy.mxt.integration;

import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.data.curse.CurseDefinition;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.Failure;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.runtime.curse.CurseService.ApplyFailure;
import com.iafenvoy.mxt.runtime.curse.CurseService.ApplyResult;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.runtime.world.SoulService;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.AuraWorldData;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Optional-script boundary. It exposes validated operations, never attachment internals.
 */
public final class MxtKubeJsApi {
    private MxtKubeJsApi() {
    }

    public static Optional<AbilityDefinition> ability(@NotNull Identifier id) {
        return MxtDatapackRegistries.get(MxtDatapackRegistries.ABILITY, id);
    }

    public static Optional<CurseDefinition> curse(@NotNull Identifier id) {
        return MxtDatapackRegistries.get(MxtDatapackRegistries.CURSE, id);
    }

    public static UseResult useAbility(@NotNull Entity actor, Identifier id, FormulaContext context) {
        if (actor.level().isClientSide())
            return new UseResult(false, false, AbilityService.Failure.SERVER_ONLY, null, Map.of());
        AbilityDefinition definition = ability(id).orElse(null);
        if (definition == null)
            return new UseResult(false, false, AbilityService.Failure.NOT_GRANTED, null, Map.of());
        return AbilityService.use(id, definition, actor, actor.getData(MxtAttachments.ABILITY_HOLDER), actor.getData(MxtAttachments.RESOURCE_HOLDER), actor.level().getGameTime(), context);
    }

    public static ApplyResult applyCurse(@NotNull Entity target, Identifier id, int stacks, String source, FormulaContext context) {
        if (target.level().isClientSide())
            return new ApplyResult(null, false, ApplyFailure.SERVER_ONLY);
        CurseDefinition definition = curse(id).orElse(null);
        if (definition == null) return new ApplyResult(null, false, ApplyFailure.CONDITION);
        return CurseService.apply(target, id, definition, stacks, target.level().getGameTime(), context, source);
    }

    public static boolean removeCurse(@NotNull Entity target, Identifier id) {
        return !target.level().isClientSide()
                && CurseService.remove(target.getData(MxtAttachments.CURSE_HOLDER), id).isPresent();
    }

    /**
     * Lets a rescue integration complete an explicit, server-authoritative soul recovery.
     */
    public static boolean reclaimSoul(@NotNull Entity entity) {
        return !entity.level().isClientSide() && SoulService.reclaim(entity);
    }

    public static BreakthroughResult tryBreakthrough(@NotNull LivingEntity entity, @NotNull Identifier resource, FormulaContext context) {
        if (entity.level().isClientSide())
            return new BreakthroughResult(false, Failure.SERVER_ONLY, null, Map.of());
        if (MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, resource).isEmpty())
            return new BreakthroughResult(false, Failure.DISABLED, null, Map.of());
        return CultivationService.attempt(entity, entity.getData(MxtAttachments.SPIRIT_DATA), entity.getData(MxtAttachments.RESOURCE_HOLDER), resource, context, () -> true);
    }

    /**
     * Adds non-negative cultivation only; content scripts cannot set arbitrary negative or non-finite state.
     */
    public static boolean addCultivation(LivingEntity entity, double amount) {
        if (entity == null || entity.level().isClientSide() || !Double.isFinite(amount) || amount < 0.0D) return false;
        SpiritData spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        spirit.setCultivationProgress(spirit.cultivationProgress() + amount);
        return true;
    }

    /**
     * Performs the same all-or-nothing resource transaction used by abilities and other server systems.
     */
    public static Result tryConsumeResources(Entity entity, List<ResourceCost> costs, FormulaContext context) {
        if (entity == null || entity.level().isClientSide())
            return new Result(false, null, Map.of());
        try {
            return ResourceTransactions.tryConsume(entity.getData(MxtAttachments.RESOURCE_HOLDER), ResourceTransactions.evaluate(List.copyOf(costs), context));
        } catch (IllegalArgumentException exception) {
            return new Result(false, null, Map.of());
        }
    }

    public static AuraResult aura(Level level, BlockPos position) {
        return AuraService.getPositionAura(level, position);
    }

    public static String addAuraBox(Level level, Identifier zone, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int priority) {
        if (!(level instanceof ServerLevel server) || MxtDatapackRegistries.get(MxtDatapackRegistries.AURA_ZONE, zone).isEmpty())
            throw new IllegalArgumentException("Aura areas require a loaded server aura_zone");
        return server.getData(MxtAttachments.AURA_WORLD).add(new AuraWorldData.Area(zone, new AuraWorldData.Shape(minX, minY, minZ, maxX, maxY, maxZ), priority));
    }

    public static boolean removeAuraArea(Level level, String id) {
        return level instanceof ServerLevel server && server.getData(MxtAttachments.AURA_WORLD).remove(id);
    }
}
