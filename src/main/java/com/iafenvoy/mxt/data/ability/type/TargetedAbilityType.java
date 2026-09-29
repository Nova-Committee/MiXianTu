package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.*;
import com.iafenvoy.mxt.data.storage.DataStorageCollector;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.runtime.ability.AbilityActivationService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.stream.Stream;

/**
 * A skill whose own part is the reach and nothing else: it picks entities with its {@code target_selector} - an area,
 * a line or a sector, which is where the distance is bounded - and runs one payload ability on each of them. Which
 * entities qualify is the payload's own {@code target_condition}, and what happens to them is its one-target half
 * ({@code bi_entity_action} with this caster as the actor); the payload's costs, cast time, cooldown, charges,
 * condition and element affinity are not read, because the press pays as this ability, once.
 */
public record TargetedAbilityType(TargetSelector targetSelector, Holder<Ability> ability,
                                  NumberProvider cooldown) implements AbilityType, AbilityApplier, Togglable, CooldownSource {
    public static final MapCodec<TargetedAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TargetSelector.CODEC.fieldOf("target_selector").forGetter(TargetedAbilityType::targetSelector),
            Ability.CODEC.fieldOf("ability").forGetter(TargetedAbilityType::ability),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(TargetedAbilityType::cooldown)
    ).apply(i, TargetedAbilityType::new));

    @Override
    public MapCodec<TargetedAbilityType> codec() {
        return CODEC;
    }

    // A press pays inside its own transaction, like mxt:active, so it keeps the payment state and not the four action
    // kinds: a payload's actions write those under the ability that declares them, never under this one.
    @Override
    public void createComponents(Ability ability, DataStorageCollector collector) {
        AbilityType.super.createComponents(ability, collector);
        collector.add(CooldownDataStorage.INSTANCE);
        ability.charges().ifPresent(charges -> collector.add(charges.declared()));
    }

    @Override
    public boolean gated(ToggleContext context) {
        return false;
    }

    @Override
    public Result activate(ToggleContext context) {
        return AbilityActivationService.cast(context);
    }

    @Override
    public Holder<Ability> payload() {
        return this.ability;
    }

    // A payload with no one-target half reaches nobody however wide the selector is; the runtime refuses that as
    // NOT_APPLICABLE before this is asked, so an empty stream here means the selector found nobody or the payload's
    // own condition kept nobody.
    @Override
    public Stream<Entity> reach(Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        if (!(this.ability.value().type() instanceof ActionCarrier carrier)) return Stream.empty();
        return this.targetSelector.select(actor, context, origin)
                .filter(target -> carrier.targetCondition().test(actor, target,
                        ActionCarrier.targetContext(actor, target, context)));
    }
}
