package com.iafenvoy.mxt.data.formation.builtin;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.FormationIntervals;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.formation.FormationActionContext;
import com.iafenvoy.mxt.runtime.formation.FormationGrants;
import com.iafenvoy.mxt.runtime.formation.FormationOwners;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * The benefit module: what the array hands to the entities it covers. No field for attribute modifiers, because a
 * granted {@link Ability} already carries its own, and none for the aura, because replacing the zone is
 * {@link AuraFormationAction}. Under {@link TargetMode#ALLIES} an unidentifiable entity is not given the benefit.
 */
public record BuffFormationAction(List<Holder<Ability>> abilities, TargetMode target,
                                  int intervalTicks) implements FormationActionType {
    public static final MapCodec<BuffFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(BuffFormationAction::abilities),
            TargetMode.CODEC.optionalFieldOf("target", TargetMode.ALL).forGetter(BuffFormationAction::target),
            FormationIntervals.CODEC.optionalFieldOf("interval_ticks", FormationIntervals.PERIOD).forGetter(BuffFormationAction::intervalTicks)
    ).apply(i, BuffFormationAction::new));

    @Override
    public MapCodec<BuffFormationAction> codec() {
        return CODEC;
    }

    // Grants under the source convention, which is also what releases them; nothing here takes a grant back,
    // because the ticker drops every ability of that source for an entity that leaves.
    @Override
    public void interval(FormationActionContext context) {
        Entity entity = context.entity();
        if (entity == null) return;
        Identifier source = Sources.formation(context.carrier().id());
        if (!targets(this.target, entity, context.carrier().owners())) {
            // An entity the module no longer has anything for must not keep the formation's grant, or losing
            // friend status while standing still leaves the gift behind. Releasing an unused source is cheap.
            FormationGrants.release(entity, source);
            return;
        }
        for (Holder<Ability> ability : this.abilities) {
            AbilityAttachment granted = entity.getData(MxtAttachments.ABILITY_HOLDER);
            if (granted.grant(ability, source) && entity instanceof LivingEntity living)
                AbilityEventBridge.rebuildTriggerSubscriptions(living);
        }
    }

    // ALLIES asks the friend system and leaves out an unidentifiable entity, because handing a stranger the
    // owner's bonus is the failure this avoids.
    private static boolean targets(TargetMode mode, Entity entity, FormationOwners owners) {
        return switch (mode) {
            case ALL -> true;
            case OWNER -> owners.contains(entity);
            case ALLIES -> owners.identify(entity.level(), entity) == TriState.TRUE;
        };
    }

    public enum TargetMode implements StringRepresentable {
        ALL,
        ALLIES,
        OWNER;

        public static final Codec<TargetMode> CODEC = StringRepresentable.fromEnum(TargetMode::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
