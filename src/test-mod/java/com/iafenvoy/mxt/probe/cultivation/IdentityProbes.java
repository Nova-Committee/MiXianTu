package com.iafenvoy.mxt.probe.cultivation;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Physique;
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
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;

import java.util.List;

/**
 * The cultivation identity surface: the script API, the tier a definition now reports, and the reading of a physique
 * written as if it were elemental. One test per leg, so one disagreement no longer hides the rest.
 *
 * <p>The guarantees pinned: a switched-off root or physique is still held while contributing nothing (so the reads
 * answer both questions separately); a tier is a reference with a reader; and a field belonging to another registry
 * is ignored while the definition still loads.
 */
public final class IdentityProbes {
    private IdentityProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("cultivation/identity/grants", IdentityProbes::grants),
                Probe.of("cultivation/identity/quality", IdentityProbes::quality),
                Probe.of("cultivation/identity/foreign_fields", IdentityProbes::foreignFields),
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

    // A tier is a reference rather than content's own word, so it is read back as the entry it names. The free-text
    // rarity the two used to carry is gone; the fixtures now name real quality entries.
    private static void quality(ProbeContext context) {
        Holder<Physique> physique = context.require(MxtResourceKeys.PHYSIQUE, ProbeDefinitions.PROBE_PHYSIQUE);
        boolean quality = physique.value().quality()
                .map(holder -> HolderHelper.id(holder).equals(context.id("spirit_iron"))).orElse(false)
                && context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.PROBE_FIRE_ROOT).value().quality()
                .map(holder -> HolderHelper.id(holder).equals(context.id("normal"))).orElse(false);
        report(context, "identity probe: quality=" + quality, quality);
    }

    // A physique that also names an element, an element relation or a spirit-root field keeps loading and those keys
    // are ignored - the record codec's own reading of a key it was not told about, which now includes the rarity key
    // this shape used to read. A written number is still checked while the pack loads, so a broken multiplier is not.
    private static void foreignFields(ProbeContext context) {
        boolean foreignIgnored = ignoresForeignFields();
        boolean negative = Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, named(single("damage_dealt_multiplier", -1.0D))).isError();
        boolean plain = Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, named(single("rarity", "probe"))).result().isPresent();
        boolean loose = foreignIgnored && negative && plain;
        report(context, "identity probe: foreign_fields_ignored=" + foreignIgnored
                + " negative_refused=" + negative + " plain_accepted=" + plain, loose);
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

    // Ignoring a field is only a guarantee if it leaves no trace, so the two decoded definitions are compared. The
    // rarity key is one of them: it is what this shape read before a tier became a reference.
    private static boolean ignoresForeignFields() {
        JsonObject foreign = new JsonObject();
        foreign.addProperty("element", "mxt_test:fire");
        foreign.addProperty("overcomes", "mxt_test:water");
        foreign.addProperty("damage_types", "mxt_test:fire");
        foreign.addProperty("rarity", "probe");
        return Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, named(foreign)).result()
                .equals(Physique.DIRECT_CODEC.parse(JsonOps.INSTANCE, named(new JsonObject())).result());
    }

    // A definition with no name or description would fail on those fields alone, which would make every leg below
    // vacuous: both are only derivable from an entry id, and bare JsonOps carries none.
    private static JsonObject named(JsonObject object) {
        object.addProperty("name", "probe");
        object.addProperty("description", "probe");
        return object;
    }

    // One-field JSON object, for decode checks that ask whether a single key is enough to break a definition.
    private static JsonObject single(String key, Object value) {
        JsonObject object = new JsonObject();
        if (value instanceof Number number) object.addProperty(key, number);
        else if (value instanceof Boolean flag) object.addProperty(key, flag);
        else object.addProperty(key, String.valueOf(value));
        return object;
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
