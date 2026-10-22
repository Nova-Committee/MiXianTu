package com.iafenvoy.mxt.probe.cultivation;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.probe.ProbeKit;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;

import java.util.List;

/**
 * The cultivation identity surface: the script API and the burst a creature can fire without a player.
 *
 * <p>The guarantees pinned: a switched-off root or physique is still held while contributing nothing (so the reads
 * answer both questions separately), and a burst is a spend plus a projectile that no player is needed for.
 */
public final class IdentityProbes {
    private IdentityProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("cultivation/identity/grants", IdentityProbes::grants),
                Probe.kitted("cultivation/identity/mob_burst", IdentityProbes::mobBurst));
    }

    private static void grants(ProbeContext context) {
        LivingEntity probe = spawnProbe(context, context.playerSpot().above());
        try {
            if (probe == null) {
                context.fail("identity probe: could not create the probe entity");
                return;
            }
            boolean granted = MxtKubeJsApi.grantSpiritRoot(probe, ProbeDefinitions.PROBE_FIRE_ROOT).failure() == null
                    && MxtKubeJsApi.grantPhysique(probe, ProbeDefinitions.PROBE_PHYSIQUE).failure() == null;
            boolean listed = MxtKubeJsApi.spiritRoots(probe).equals(List.of(ProbeDefinitions.PROBE_FIRE_ROOT.toString()))
                    && MxtKubeJsApi.physiques(probe).equals(List.of(ProbeDefinitions.PROBE_PHYSIQUE.toString()))
                    && MxtKubeJsApi.activeSpiritRoots(probe).equals(List.of(ProbeDefinitions.PROBE_FIRE_ROOT.toString()))
                    && MxtKubeJsApi.activePhysiques(probe).equals(List.of(ProbeDefinitions.PROBE_PHYSIQUE.toString()))
                    && MxtKubeJsApi.hasSpiritRoot(probe, ProbeDefinitions.PROBE_FIRE_ROOT)
                    && MxtKubeJsApi.hasPhysique(probe, ProbeDefinitions.PROBE_PHYSIQUE)
                    && MxtKubeJsApi.isSpiritRootEnabled(probe, ProbeDefinitions.PROBE_FIRE_ROOT)
                    && MxtKubeJsApi.isPhysiqueEnabled(probe, ProbeDefinitions.PROBE_PHYSIQUE);
            // Off is not gone: the body still holds both, nothing of either counts, and switching again is a
            // change that did not happen rather than a second toggle.
            boolean switchedOff = MxtKubeJsApi.setSpiritRootEnabled(probe, ProbeDefinitions.PROBE_FIRE_ROOT, false).changed()
                    && MxtKubeJsApi.setPhysiqueEnabled(probe, ProbeDefinitions.PROBE_PHYSIQUE, false).changed()
                    && MxtKubeJsApi.hasSpiritRoot(probe, ProbeDefinitions.PROBE_FIRE_ROOT)
                    && MxtKubeJsApi.hasPhysique(probe, ProbeDefinitions.PROBE_PHYSIQUE)
                    && !MxtKubeJsApi.isSpiritRootEnabled(probe, ProbeDefinitions.PROBE_FIRE_ROOT)
                    && MxtKubeJsApi.activeSpiritRoots(probe).isEmpty()
                    && MxtKubeJsApi.activePhysiques(probe).isEmpty()
                    && close(DamageCalculationService.physiqueMultiplier(probe, true), 1.0D)
                    && !MxtKubeJsApi.setPhysiqueEnabled(probe, ProbeDefinitions.PROBE_PHYSIQUE, false).changed();
            boolean removed = MxtKubeJsApi.removeSpiritRoot(probe, ProbeDefinitions.PROBE_FIRE_ROOT)
                    && MxtKubeJsApi.removePhysique(probe, ProbeDefinitions.PROBE_PHYSIQUE)
                    && MxtKubeJsApi.spiritRoots(probe).isEmpty() && MxtKubeJsApi.physiques(probe).isEmpty()
                    && !MxtKubeJsApi.hasSpiritRoot(probe, ProbeDefinitions.PROBE_FIRE_ROOT)
                    && !MxtKubeJsApi.setSpiritRootEnabled(probe, ProbeDefinitions.PROBE_FIRE_ROOT, true).changed();
            boolean identity = granted && listed && switchedOff && removed;
            report(context, "identity probe: granted=" + granted + " listed=" + listed
                    + " switched_off=" + switchedOff + " removed=" + removed, identity);
        } finally {
            if (probe != null) probe.discard();
        }
    }

    // A burst is a resource spend plus a projectile, and neither half asks for a player: the wheel is only how a
    // player requests one. The probe stands at the realm the aura's own use gate names.
    private static void mobBurst(ProbeContext context) {
        LivingEntity probe = spawnProbe(context, context.playerSpot().above());
        try {
            if (probe == null) {
                context.fail("identity probe: could not create the probe entity");
                return;
            }
            ResourceHolderAttachment burstResources = probe.getData(MxtAttachments.RESOURCE_HOLDER);
            Holder<Aura> burstAura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SPIRIT_POWER);
            Holder<Resource> burstResource = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.SPIRIT_POWER);
            long burstAt = context.level().getGameTime();
            Holder<RealmStage> burstRealm = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.SPIRIT_POWER_REFINING);
            // The realm comes first: a resource ceiling is a formula over the body's own realm, so a body that never
            // entered one reads a ceiling of nothing and the fill below would hold none of what it asked for.
            boolean realmSet = CultivationService.setRealm(probe.getData(MxtAttachments.CULTIVATION), burstRealm);
            ProbeKit.ensureResource(probe, burstResources, burstResource, 20.0D);
            // The pack's own default is what the account starts at once the ceiling admits it, so the price is read as
            // what left the account rather than as a value the burst was supposed to leave behind.
            double beforeBurst = burstResources.get(burstResource);
            boolean burst = realmSet
                    && SpiritBurstService.fireOnce(probe, ProbeDefinitions.SPIRIT_POWER)
                    && close(beforeBurst - burstResources.get(burstResource), 10.0D)
                    && probe.getData(MxtAttachments.SPIRIT_BURST_COOLDOWNS).isOnCooldown(burstAura, burstAt)
                    && !SpiritBurstService.fireOnce(probe, ProbeDefinitions.SPIRIT_POWER);
            report(context, "identity probe: mob_burst=" + burst + " spent=" + (beforeBurst - burstResources.get(burstResource)), burst);
        } finally {
            if (probe != null) probe.discard();
        }
    }

    // The source's per-leg line: it goes to the run log with its own verdict, and the check carries it to the ledger.
    private static void report(ProbeContext context, String line, boolean value) {
        context.note(line + (value ? " OK" : " MISMATCH"));
        context.check(line, value);
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }

    // One disposable probe entity carrying no spirit root; a pig has no armour and no innate resistance, which keeps
    // what the pipeline hands over equal to what it loses. A null return means the type refused to create one.
    private static LivingEntity spawnProbe(ProbeContext context, BlockPos pos) {
        Pig pig = EntityType.PIG.create(context.level(), EntitySpawnReason.COMMAND);
        if (pig == null) return null;
        pig.setNoAi(true);
        pig.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        context.level().addFreshEntity(pig);
        return pig;
    }
}
