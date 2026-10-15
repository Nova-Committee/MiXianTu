package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.storage.builtin.ChargesDataStorage;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityStorage;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A weapon's skill is granted by the item in hand, so putting the item away takes the whole grant with it. What must
 * not go with it is the cooldown, or the skill could be fired again by swapping the item out and back.
 */
public final class AbilityCooldownVerifyProbes {
    private AbilityCooldownVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/ability_cooldown", AbilityCooldownVerifyProbes::abilityCooldown));
    }

    private static void abilityCooldown(ProbeContext context) {
        String failure = verifyAbilityCooldown(context);
        if (failure != null) context.fail(failure);
    }

    // This drives the same sequence an equipment change does - grant, cooldown, revoke, grant - on an actor of its
    // own; every other kind of state still goes, so a re-granted skill does not come back with its charges.
    private static String verifyAbilityCooldown(ProbeContext context) {
        ServerLevel level = context.level();
        // The old leg read a heightmap position out in the open world; the actor only needs a spot clear of the
        // probe's own player, so it stands at arena relative (10, 0, 10).
        BlockPos base = context.player().blockPosition().offset(6, 0, 6);
        LivingEntity actor = spawnProbe(context, base, null);
        if (actor == null) return "could not create the probe being";
        try {
            AbilityAttachment abilities = actor.getData(MxtAttachments.ABILITY_HOLDER);
            Holder<Ability> ability = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.id("firebolt"));
            Identifier source = ProbeDefinitions.id("grant/cooldown_probe");
            long gameTime = level.getGameTime();
            abilities.grant(ability, source);
            AbilityStorage.startCooldown(abilities, ability, 200.0D, gameTime);
            AbilityStorage.charges(abilities, ability, ChargesDataStorage.INSTANCE, gameTime).setRemaining(1.0D, gameTime);
            if (!AbilityStorage.onCooldown(abilities, ability, gameTime))
                return "the probe could not be put on cooldown at all";
            abilities.revoke(ability, source);
            if (abilities.has(ability)) return "revoking the only source left the ability granted";
            long kept = AbilityStorage.cooldownRemaining(abilities, ability, gameTime);
            if (kept <= 0L) return "the cooldown was dropped together with the item that granted the ability";
            if (AbilityStorage.get(abilities, ability, ChargesDataStorage.class).isPresent())
                return "the charges outlived the source they belonged to";
            abilities.grant(ability, source);
            boolean cooling = AbilityStorage.onCooldown(abilities, ability, gameTime);
            abilities.revoke(ability, source);
            return cooling ? null : "the re-granted ability was ready again with " + kept + " ticks left";
        } finally {
            actor.discard();
        }
    }

    // One disposable probe entity carrying exactly one spirit root, so the element it strikes or is struck with is
    // never a question. A pig has no armour and no innate resistance; a null root leaves it carrying none.
    private static LivingEntity spawnProbe(ProbeContext context, BlockPos pos, @Nullable Identifier root) {
        ServerLevel level = context.level();
        Pig pig = EntityType.PIG.create(level, EntitySpawnReason.COMMAND);
        if (pig == null) return null;
        pig.setNoAi(true);
        pig.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(pig);
        if (root != null)
            CultivationIdentityService.grantSpiritRoot(pig, context.require(MxtResourceKeys.SPIRIT_ROOT, root));
        return pig;
    }
}
