package com.iafenvoy.mxt.runtime;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.data.ability.AbilityAffinity;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationAffinity;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.runtime.progression.ProgressionDamageMultiplier;
import com.iafenvoy.saturn.api.AbilityHooks;
import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Saturn's questions about the world, answered by the content that owns them: which element an ability belongs to and
 * what the body's roots are worth for it, whether a quality gate lets a stack be used, which abilities a carried or
 * equipped stack grants, and where the body's Curios sit. Everything the mod does not decide keeps the default.
 */
public final class MxtAbilityHooks implements AbilityHooks {
    private static final MxtAbilityHooks INSTANCE = new MxtAbilityHooks();

    private MxtAbilityHooks() {
    }

    public static void install() {
        AbilityHooks.set(INSTANCE);
    }

    /**
     * The outgoing multiplier of one use: the ability's element affinity times the level of any progression chain that
     * grants it. A body with no matching root gets zero from the affinity, which is the same refusal the gate makes.
     */
    @Override
    public double damageMultiplier(LivingEntity actor, Holder<Ability> ability) {
        double progression = ProgressionDamageMultiplier.of(actor, ability);
        AbilityAffinity affinity = RegistryCaches.ABILITY_AFFINITY.of(ability);
        if (affinity == null || affinity.isEmpty()) return progression;
        double value = CultivationAffinity.abilityMultiplier(actor.getData(MxtAttachments.SPIRIT_IDENTITY), affinity.elements(), FormulaContext.of(actor), affinity.mode());
        return Double.isFinite(value) && value >= 0.0D ? progression * value : 0.0D;
    }

    /**
     * A body whose roots match none of the ability's elements cannot pay the cast at all: the affinity is a gate and a
     * multiplier, and a zero multiplier is the same refusal one step later.
     */
    @Override
    public boolean validate(Ability definition, Entity actor, FormulaContext context) {
        if (!(actor instanceof LivingEntity living)) return true;
        AbilityAffinity affinity = RegistryCaches.ABILITY_AFFINITY.of(definition, living.level().registryAccess());
        if (affinity == null || affinity.isEmpty()) return true;
        SpiritIdentityAttachment spirit = living.getData(MxtAttachments.SPIRIT_IDENTITY);
        return CultivationAffinity.abilityMultiplier(spirit, affinity.elements(), context, affinity.mode()) > 0.0D;
    }

    @Override
    public void onCommitted(ServerPlayer player, Holder<Ability> ability) {
        MxtCriteriaTriggers.ABILITY.get().trigger(player, HolderHelper.id(ability));
    }

    @Override
    public boolean canUseItem(LivingEntity entity, ItemStack stack) {
        return QualityService.canUse(entity, stack);
    }

    @Override
    public List<Holder<Ability>> itemAbilities(LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        return ArtifactService.abilities(entity.level().registryAccess(), stack);
    }

    @Override
    public List<ItemStack> equippedItems(LivingEntity entity) {
        return CuriosIntegration.equipped(entity);
    }
}
