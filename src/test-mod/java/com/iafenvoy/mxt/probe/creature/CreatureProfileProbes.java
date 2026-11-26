package com.iafenvoy.mxt.probe.creature;

import com.iafenvoy.mxt.attachment.CreatureSpiritAttachment;
import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.creature.CreatureProfileService;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Objects;

public final class CreatureProfileProbes {
    private CreatureProfileProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("creature/profile/fallback", CreatureProfileProbes::fallback),
                Probe.of("verify/external_api/creature_profile_priority", CreatureProfileProbes::priority));
    }

    private static void fallback(ProbeContext context) {
        Holder<CreatureProfile> expected = context.require(MxtResourceKeys.CREATURE_PROFILE,
                Identifier.fromNamespaceAndPath("mxt_test", "fallback/03_first_valid"));
        Holder<Resource> counter = context.require(SaturnResourceKeys.RESOURCE,
                Identifier.fromNamespaceAndPath("mxt_test", "trigger_probe"));
        Mob eligible = create(context, EntityType.SILVERFISH, 3);
        Mob rejected = create(context, EntityType.ENDERMITE, 5);
        try {
            context.check("profile probe: eligible entity joined", context.level().addFreshEntity(eligible));
            context.check("profile probe: ineligible entity joined", context.level().addFreshEntity(rejected));
            CreatureSpiritAttachment data = eligible.getData(MxtAttachments.CREATURE_SPIRIT);
            context.check("profile probe: first eligible ID selected", data.profile().filter(expected::equals).isPresent());
            context.check("profile probe: persisted winning values", close(data.intelligence(), 12.5D)
                    && data.innerCore().is(Items.AMETHYST_SHARD) && data.innerCore().getCount() == 2);
            context.check("profile probe: selected action ran once",
                    !CreatureProfileService.applySelected(eligible)
                            && close(eligible.getData(SaturnAttachments.RESOURCE_HOLDER).get(counter), 3.0D));
            context.check("profile probe: every candidate refused",
                    rejected.getExistingData(MxtAttachments.CREATURE_SPIRIT).flatMap(CreatureSpiritAttachment::profile).isEmpty()
                            && close(rejected.getData(SaturnAttachments.RESOURCE_HOLDER).get(counter), 0.0D));
        } finally {
            eligible.discard();
            rejected.discard();
        }
    }

    private static Mob create(ProbeContext context, EntityType<? extends Mob> type, int offset) {
        Mob creature = Objects.requireNonNull(type.create(context.level(), EntitySpawnReason.COMMAND));
        creature.setNoAi(true);
        creature.setPos(context.player().position().add(offset, 0, 0));
        return creature;
    }

    private static void priority(ProbeContext context) {
        Holder<CreatureProfile> expected = context.require(MxtResourceKeys.CREATURE_PROFILE, "priority/02_winner");
        Mob creature = create(context, EntityType.CAVE_SPIDER, 3);
        try {
            context.check("mob joined", context.level().addFreshEntity(creature));
            CreatureSpiritAttachment data = creature.getData(MxtAttachments.CREATURE_SPIRIT);
            context.expect("valid higher priority wins over earlier default", data.profile().orElse(null), expected);
            context.check("higher ineligible profile falls through", close(data.intelligence(), 8.5D));
        } finally {
            creature.discard();
        }
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
