package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.ability.AbilityService.ChannelResult;
import com.iafenvoy.mxt.runtime.ability.AbilityService.State;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * Development-only assertion for the channelled ability lifecycle. It drives the production
 * service entry points so the check fails if a channelled ability stops applying its effect on
 * activation or on each due upkeep pulse.
 *
 * <p>The check only needs a {@link LivingEntity}, so it can run at server start on a temporary
 * entity as well as from a command against a real player.</p>
 */
public final class ChannelProbe {
    private static final Identifier PARENT = id("channel_parent");
    private static final Identifier PULSE = id("channel_pulse");
    private static final Identifier PROBE_RESOURCE = id("channel_probe");
    private static final Identifier SOURCE = id("grant/test_kit");

    private ChannelProbe() {
    }

    /**
     * Runs the assertion and returns a description of the first failure, or {@code null} when the
     * expected behaviour is observed. The probe resource starts empty, so the delta at each step is
     * unambiguous.
     */
    public static String verify(LivingEntity actor) {
        Holder<Ability> parent = MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, PARENT).orElse(null);
        Holder<Ability> pulse = MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, PULSE).orElse(null);
        Holder<Resource> probe = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, PROBE_RESOURCE).orElse(null);
        if (parent == null || pulse == null || probe == null) return "missing mxt_test channel definitions";

        AbilityAttachment abilities = actor.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderAttachment resources = actor.getData(MxtAttachments.RESOURCE_HOLDER);
        FormulaContext context = FormulaContext.of(actor);
        // Start from a known empty probe so every later comparison is an exact delta.
        resources.remove(probe);
        ResourceService.initialize(resources, probe, context);
        double start = resources.get(probe);
        if (start != 0.0D) return "the channel probe did not start empty";

        long now = actor.level().getGameTime();
        abilities.grant(parent, SOURCE);
        abilities.grant(pulse, SOURCE);
        abilities.setChannelledAbility(null);

        UseResult started = AbilityService.use(parent, parent.value(), actor, abilities, resources, now, context);
        if (!started.committed()) return "composite activation was rejected: " + started.failure();
        if (abilities.channelledAbility().filter(pulse::equals).isEmpty())
            return "the channelled child did not become the active channel";
        // The upkeep cost is paid per pulse, so the probe must not change while the channel is idle.
        if (Double.compare(resources.get(probe), start) != 0)
            return "the channel applied an effect before its first upkeep pulse";

        ChannelResult early = AbilityService.tickChannel(pulse, pulse.value(), actor, abilities, resources, now, context);
        if (early.state() != State.WAITING)
            return "an upkeep pulse fired before its interval: " + early.state();
        if (Double.compare(resources.get(probe), start) != 0)
            return "an early tick changed the channel probe";

        long due = abilities.componentState(pulse, "channel_next_tick")
                .map(state -> (long) state.value()).orElse(now);
        ChannelResult fired = AbilityService.tickChannel(pulse, pulse.value(), actor, abilities, resources, due, context);
        if (fired.state() != State.PULSED)
            return "no upkeep pulse at the scheduled tick: " + fired.state();
        if (Double.compare(resources.get(probe), start + 1.0D) != 0)
            return "the upkeep pulse did not apply its effect exactly once (probe is " + resources.get(probe) + ")";

        // The channel must still be active here, otherwise the release assertions below are vacuous.
        if (abilities.channelledAbility().filter(pulse::equals).isEmpty())
            return "the channel was not active before the release check";
        if (!AbilityService.stopChannel(abilities) || abilities.channelledAbility().isPresent())
            return "stopChannel did not release the channel";
        if (AbilityService.stopChannel(abilities))
            return "stopChannel reported a change on an already idle channel";
        return null;
    }

    /**
     * Releases the grants this probe installed so repeated runs stay independent.
     */
    public static void clear(LivingEntity actor) {
        AbilityAttachment abilities = actor.getData(MxtAttachments.ABILITY_HOLDER);
        MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, PARENT)
                .ifPresent(ability -> abilities.revoke(ability, SOURCE));
        MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, PULSE)
                .ifPresent(ability -> abilities.revoke(ability, SOURCE));
        abilities.setChannelledAbility(null);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path);
    }
}
