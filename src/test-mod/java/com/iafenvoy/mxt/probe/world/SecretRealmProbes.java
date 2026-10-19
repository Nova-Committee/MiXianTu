package com.iafenvoy.mxt.probe.world;

import com.iafenvoy.mxt.data.condition.builtin.entity.InSecretRealmEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.InSecretRealmEntityCondition.Role;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.world.SecretRealmRecord;
import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
import com.iafenvoy.mxt.runtime.world.SecretRealmService;
import com.iafenvoy.mxt.runtime.world.SecretRealmStructurePlacer;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The secret realm machine, one GameTest per step of the old probe: an instance is a dimension, so every part drives
 * the registry and the generation service directly and then inspects the world that came out - its border, its placed
 * structure, its landing spot, the instance cap and the claim rules.
 */
public final class SecretRealmProbes {
    // The structure the fixture places is built here instead of shipping a binary fixture.
    private static final BlockPos TOWER = new BlockPos(20, 1, 20);
    // Where the disposable beings stand: free arena space, clear of the tower scratch and of the player.
    private static final BlockPos BEING = new BlockPos(28, 0, 28);

    private SecretRealmProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("world/secret_realm/open", SecretRealmProbes::open),
                Probe.of("world/secret_realm/border", SecretRealmProbes::border),
                Probe.of("world/secret_realm/structures", SecretRealmProbes::structures),
                Probe.of("world/secret_realm/anchor", SecretRealmProbes::anchor),
                Probe.of("world/secret_realm/instance_cap", SecretRealmProbes::instanceCap),
                Probe.of("world/secret_realm/membership_cap", SecretRealmProbes::membershipCap),
                Probe.of("world/secret_realm/claim_role", SecretRealmProbes::claimRole),
                Probe.of("world/secret_realm/aliases", SecretRealmProbes::aliases),
                Probe.of("world/secret_realm/weighted_entry", SecretRealmProbes::weightedEntry),
                Probe.of("world/secret_realm/existing", SecretRealmProbes::existing),
                Probe.of("world/secret_realm/unresolvable", SecretRealmProbes::unresolvable),
                Probe.of("world/secret_realm/expiry", SecretRealmProbes::expiry),
                Probe.of("world/secret_realm/persistence", SecretRealmProbes::persistence),
                Probe.kitted("world/secret_realm/traveller", SecretRealmProbes::traveller),
                // The pair is one scenario: keep claims an instance and reopen is the restart that finds the record
                // it wrote, so they share a test. Two probes run in separate batches and nothing orders them.
                Probe.of("world/secret_realm/keep_and_reopen", SecretRealmProbes::keepAndReopen));
    }

    private static void keepAndReopen(ProbeContext context) {
        try {
            keep(context);
            reopen(context);
        } finally {
            // The claim outlives the test: the registry keeps the row, so it has to go or the next probe that opens
            // trial_realm index 0 reads this seed instead of its own.
            destroy(context.server(), SecretRealmRegistry.of(
                            context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM)).stream()
                    .map(SecretRealmRecord::dimension).toList());
        }
    }

    // 1. An instance dimension is created on demand, before anybody is allowed in.
    private static void open(ProbeContext context) {
        MinecraftServer server = context.server();
        ServerLevel overworld = server.overworld();
        Holder<SecretRealm> trial = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        List<ResourceKey<Level>> opened = new ArrayList<>();
        boolean towerSaved = saveTower(context, overworld);
        try {
            SecretRealmRecord first = openInstance(server, trial, 0, 12345L, opened);
            ServerLevel dimension = first == null ? null : server.getLevel(first.dimension());
            context.check("secret realm probe: tower_saved=" + towerSaved + " dimension=" + (first != null)
                    + " level=" + (dimension != null), towerSaved && first != null && dimension != null);
        } finally {
            destroy(server, opened);
        }
    }

    // 2. The declared border lands on the instance dimension, which also carries the instance seed.
    private static void border(ProbeContext context) {
        MinecraftServer server = context.server();
        Holder<SecretRealm> trial = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        List<ResourceKey<Level>> opened = new ArrayList<>();
        saveTower(context, server.overworld());
        try {
            SecretRealmRecord first = openInstance(server, trial, 0, 12345L, opened);
            ServerLevel dimension = first == null ? null : server.getLevel(first.dimension());
            if (dimension == null) {
                context.fail("secret realm probe: the trial instance dimension is missing");
                return;
            }
            WorldBorder border = dimension.getWorldBorder();
            context.check("secret realm probe: border size=" + border.getSize() + " center=(" + border.getCenterX()
                            + "," + border.getCenterZ() + ")",
                    close(border.getSize(), 128.0D) && close(border.getCenterX(), 0.0D) && close(border.getCenterZ(), 0.0D));
            context.check("secret realm probe: seed=" + dimension.getSeed(), dimension.getSeed() == 12345L);
        } finally {
            destroy(server, opened);
        }
    }

    // 3. Structures are placed before the landing is chosen, so an arrival can stand on them.
    private static void structures(ProbeContext context) {
        MinecraftServer server = context.server();
        Holder<SecretRealm> trial = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        List<ResourceKey<Level>> opened = new ArrayList<>();
        saveTower(context, server.overworld());
        try {
            SecretRealmRecord first = openInstance(server, trial, 0, 12345L, opened);
            ServerLevel dimension = first == null ? null : server.getLevel(first.dimension());
            if (dimension == null) {
                context.fail("secret realm probe: the trial instance dimension is missing");
                return;
            }
            context.check("secret realm probe: structure block=" + dimension.getBlockState(new BlockPos(8, 64, 8)).getBlock(),
                    dimension.getBlockState(new BlockPos(8, 64, 8)).is(Blocks.GOLD_BLOCK));
        } finally {
            destroy(server, opened);
        }
    }

    // 4. A fixed entry point becomes the instance anchor.
    private static void anchor(ProbeContext context) {
        MinecraftServer server = context.server();
        Holder<SecretRealm> trial = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        List<ResourceKey<Level>> opened = new ArrayList<>();
        saveTower(context, server.overworld());
        try {
            SecretRealmRecord first = openInstance(server, trial, 0, 12345L, opened);
            ServerLevel dimension = first == null ? null : server.getLevel(first.dimension());
            if (dimension == null) {
                context.fail("secret realm probe: the trial instance dimension is missing");
                return;
            }
            Vec3 anchor = first.anchor().orElse(null);
            context.check("secret realm probe: anchor=" + anchor + " prepared=" + first.prepared(),
                    anchor != null && close(anchor.x, 0.5D) && close(anchor.y, 65.0D) && close(anchor.z, 0.5D) && first.prepared());
        } finally {
            destroy(server, opened);
        }
    }

    // 5. The instance cap counts every instance of the definition.
    private static void instanceCap(ProbeContext context) {
        MinecraftServer server = context.server();
        Holder<SecretRealm> trial = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        List<ResourceKey<Level>> opened = new ArrayList<>();
        saveTower(context, server.overworld());
        try {
            openInstance(server, trial, 0, 12345L, opened);
            SecretRealmRecord second = openInstance(server, trial, 1, 999L, opened);
            int count = SecretRealmRegistry.of(trial).size();
            context.check("secret realm probe: instances=" + count + " cap=" + trial.value().maxInstances(),
                    second != null && count >= trial.value().maxInstances());
        } finally {
            destroy(server, opened);
        }
    }

    // 6. Membership is capped per instance and a full instance is not joinable.
    private static void membershipCap(ProbeContext context) {
        MinecraftServer server = context.server();
        Holder<SecretRealm> trial = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        List<ResourceKey<Level>> opened = new ArrayList<>();
        saveTower(context, server.overworld());
        try {
            SecretRealmRecord first = openInstance(server, trial, 0, 12345L, opened);
            SecretRealmRecord second = openInstance(server, trial, 1, 999L, opened);
            if (first == null || second == null) {
                context.fail("secret realm probe: the two trial instances are missing");
                return;
            }
            List<UUID> two = List.of(UUID.randomUUID(), UUID.randomUUID());
            SecretRealmRecord full = second.with(two);
            SecretRealmRegistry.replace(full);
            SecretRealmRegistry.at(first.dimension()).ifPresent(record -> SecretRealmRegistry.replace(record.with(two)));
            boolean joinable = SecretRealmRegistry.joinable(trial, UUID.randomUUID()).isPresent();
            context.check("secret realm probe: full=" + full.full() + " joinable=" + joinable, full.full() && !joinable);
        } finally {
            destroy(server, opened);
        }
    }

    // 7. A claim is recorded, and the condition tells owner from guest.
    private static void claimRole(ProbeContext context) {
        MinecraftServer server = context.server();
        ServerLevel overworld = server.overworld();
        Holder<SecretRealm> trial = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        Holder<SecretRealm> mirror = context.require(MxtResourceKeys.SECRET_REALM, context.id("mirror_realm"));
        List<ResourceKey<Level>> opened = new ArrayList<>();
        List<LivingEntity> probes = new ArrayList<>();
        saveTower(context, overworld);
        try {
            SecretRealmRecord first = openInstance(server, trial, 0, 12345L, opened);
            if (first == null) {
                context.fail("secret realm probe: the trial instance is missing");
                return;
            }
            LivingEntity probe = spawnBeing(context, overworld, context.at(BEING), ProbeDefinitions.PROBE_FIRE_ROOT);
            probes.add(probe);
            boolean ownerSeen = false, guestSeen = true, foreignSeen = true;
            if (probe != null) {
                SecretRealmRegistry.replace(first.withOwner(probe.getUUID()).with(List.of(probe.getUUID())));
                ownerSeen = new InSecretRealmEntityCondition(Optional.of(Either.left(trial)), Role.OWNER).test(probe, FormulaContext.of(probe));
                guestSeen = new InSecretRealmEntityCondition(Optional.of(Either.left(trial)), Role.GUEST).test(probe, FormulaContext.of(probe));
                foreignSeen = new InSecretRealmEntityCondition(Optional.of(Either.left(mirror)), Role.ANY).test(probe, FormulaContext.of(probe));
            }
            context.check("secret realm probe: role owner=" + ownerSeen + " guest=" + guestSeen + " foreign=" + foreignSeen,
                    probe != null && ownerSeen && !guestSeen && !foreignSeen);
        } finally {
            for (LivingEntity probe : probes) if (probe != null) probe.discard();
            destroy(server, opened);
        }
    }

    // 8. A secret realm dimension is reachable by its definition id, a stem realm also by its stem.
    private static void aliases(ProbeContext context) {
        MinecraftServer server = context.server();
        Holder<SecretRealm> trial = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        List<ResourceKey<Level>> opened = new ArrayList<>();
        saveTower(context, server.overworld());
        try {
            SecretRealmRecord first = openInstance(server, trial, 0, 12345L, opened);
            if (first == null) {
                context.fail("secret realm probe: the trial instance is missing");
                return;
            }
            List<Identifier> aliases = SecretRealmRegistry.aliases(first.dimension().identifier()).toList();
            context.check("secret realm probe: aliases=" + aliases, aliases.contains(ProbeDefinitions.TRIAL_REALM));
        } finally {
            destroy(server, opened);
        }
    }

    // 9. A weighted entry list picks by weight: the zero-weight fixed point is never chosen, so a random
    //    landing inside random_radius is what the anchor has to be.
    private static void weightedEntry(ProbeContext context) {
        MinecraftServer server = context.server();
        Holder<SecretRealm> mirror = context.require(MxtResourceKeys.SECRET_REALM, context.id("mirror_realm"));
        List<ResourceKey<Level>> opened = new ArrayList<>();
        saveTower(context, server.overworld());
        try {
            SecretRealmRecord mirrorRecord = openInstance(server, mirror, 0, 777L, opened);
            Vec3 mirrorAnchor = mirrorRecord == null ? null : mirrorRecord.anchor().orElse(null);
            ServerLevel mirrorLevel = mirrorRecord == null ? null : server.getLevel(mirrorRecord.dimension());
            boolean inRadius = mirrorAnchor != null && Math.hypot(mirrorAnchor.x, mirrorAnchor.z) <= 16.0D + 1.0E-6D;
            boolean skipped = mirrorLevel != null && !mirrorLevel.getBlockState(new BlockPos(0, 100, 0)).is(Blocks.GOLD_BLOCK);
            context.check("secret realm probe: random anchor=" + mirrorAnchor + " chance_skipped=" + skipped, inRadius && skipped);
            context.check("secret realm probe: stem alias=" + (mirrorRecord == null ? "-"
                            : SecretRealmRegistry.aliases(mirrorRecord.dimension().identifier()).toList()),
                    mirrorRecord != null && SecretRealmRegistry.aliases(mirrorRecord.dimension().identifier())
                            .anyMatch(alias -> alias.equals(Identifier.fromNamespaceAndPath("minecraft", "the_end"))));
        } finally {
            destroy(server, opened);
        }
    }

    // 10. An existing secret realm creates nothing and reuses the dimension it names.
    private static void existing(ProbeContext context) {
        MinecraftServer server = context.server();
        ServerLevel overworld = server.overworld();
        Holder<SecretRealm> existing = context.require(MxtResourceKeys.SECRET_REALM, context.id("existing_realm"));
        List<ResourceKey<Level>> opened = new ArrayList<>();
        saveTower(context, overworld);
        try {
            SecretRealmRecord existingRecord = openInstance(server, existing, 0, 0L, opened);
            context.check("secret realm probe: existing=" + (existingRecord == null ? "-" : existingRecord.dimension().identifier()),
                    existingRecord != null && existingRecord.dimension().equals(Level.OVERWORLD)
                            && server.getLevel(existingRecord.dimension()) == overworld);
        } finally {
            destroy(server, opened);
        }
    }

    // 11. A definition that names a template or a structure that does not exist creates nothing.
    private static void unresolvable(ProbeContext context) {
        MinecraftServer server = context.server();
        Holder<SecretRealm> absentStructure = context.require(MxtResourceKeys.SECRET_REALM, context.id("missing_structure_realm"));
        Holder<SecretRealm> absentTemplate = context.require(MxtResourceKeys.SECRET_REALM, context.id("template_realm"));
        saveTower(context, server.overworld());
        boolean resolvable = SecretRealmStructurePlacer.resolvable(server.getStructureManager(), absentStructure.value());
        SecretRealmRecord templateAttempt = planned(absentTemplate, 0, 0L);
        boolean templateFailed = SecretRealmService.open(server, templateAttempt).isEmpty();
        context.check("secret realm probe: structure_resolvable=" + resolvable + " template_failed=" + templateFailed
                        + " leftover=" + SecretRealmRegistry.at(templateAttempt.dimension()).isPresent(),
                !resolvable && templateFailed && SecretRealmRegistry.at(templateAttempt.dimension()).isEmpty());
    }

    // 12. An unclaimed instance dies with its clock: the record goes and the dimension is released.
    private static void expiry(ProbeContext context) {
        MinecraftServer server = context.server();
        Holder<SecretRealm> mirror = context.require(MxtResourceKeys.SECRET_REALM, context.id("mirror_realm"));
        List<ResourceKey<Level>> opened = new ArrayList<>();
        saveTower(context, server.overworld());
        try {
            SecretRealmRecord mirrorRecord = openInstance(server, mirror, 0, 777L, opened);
            boolean expired = mirrorRecord != null && SecretRealmService.expire(server, mirrorRecord, server.overworld().getGameTime() + 100L);
            boolean released = mirrorRecord != null && SecretRealmRegistry.at(mirrorRecord.dimension()).isEmpty()
                    && server.getLevel(mirrorRecord.dimension()) == null;
            context.check("secret realm probe: expired=" + expired + " released=" + released, expired && released);
        } finally {
            destroy(server, opened);
        }
    }

    // 13. A claimed secret realm survives its visitors: the policy is what decides keep versus delete.
    private static void persistence(ProbeContext context) {
        MinecraftServer server = context.server();
        ServerLevel overworld = server.overworld();
        Holder<SecretRealm> trial = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        List<ResourceKey<Level>> opened = new ArrayList<>();
        List<LivingEntity> probes = new ArrayList<>();
        saveTower(context, overworld);
        try {
            SecretRealmRecord first = openInstance(server, trial, 0, 12345L, opened);
            if (first == null) {
                context.fail("secret realm probe: the trial instance is missing");
                return;
            }
            // The claim the seventh part writes is reproduced here, because each part is a test of its own.
            LivingEntity probe = spawnBeing(context, overworld, context.at(BEING), ProbeDefinitions.PROBE_FIRE_ROOT);
            probes.add(probe);
            if (probe != null)
                SecretRealmRegistry.replace(first.withOwner(probe.getUUID()).with(List.of(probe.getUUID())));
            SecretRealmRecord claimed = SecretRealmRegistry.at(first.dimension()).orElse(null);
            SecretRealmRecord idled = claimed == null ? null : claimed.idle();
            context.check("secret realm probe: persists=" + (claimed != null && claimed.persists())
                            + " idle_members=" + (idled == null ? -1 : idled.members().size()),
                    claimed != null && claimed.persists() && idled.empty() && probe != null && idled.isOwner(probe.getUUID()));
        } finally {
            for (LivingEntity probe : probes) if (probe != null) probe.discard();
            destroy(server, opened);
        }
    }

    // 14. A traveller that is not a player enters and leaves through the same calls: the record, the
    //     membership row and the teleport are keyed on the entity, not on a connection.
    private static void traveller(ProbeContext context) {
        MinecraftServer server = context.server();
        ServerLevel overworld = server.overworld();
        Holder<SecretRealm> mirror = context.require(MxtResourceKeys.SECRET_REALM, context.id("mirror_realm"));
        List<ResourceKey<Level>> opened = new ArrayList<>();
        List<LivingEntity> probes = new ArrayList<>();
        saveTower(context, overworld);
        try {
            LivingEntity mobTraveller = spawnBeing(context, overworld, context.at(BEING), null);
            probes.add(mobTraveller);
            SecretRealmService.TravelResult mobEntered = mobTraveller == null ? null : SecretRealmService.enter(mobTraveller, server, mirror);
            // A body that is no player is rebuilt by the platform when it crosses a dimension, so the one to read
            // afterwards is the body the call handed back - the one passed in is already gone.
            LivingEntity mobVisitor = mobEntered == null ? null : mobEntered.traveller().orElse(null);
            if (mobVisitor != null && mobVisitor != mobTraveller) probes.add(mobVisitor);
            SecretRealmRecord mobHome = mobVisitor == null ? null : SecretRealmRegistry.ofMember(mobVisitor.getUUID()).orElse(null);
            if (mobHome != null) opened.add(mobHome.dimension());
            boolean mobIn = mobVisitor != null && mobEntered.isSuccess() && mobHome != null
                    && mobVisitor.level().dimension().equals(mobHome.dimension())
                    && mobVisitor.getData(MxtAttachments.SECRET_REALM_TRAVEL).active();
            SecretRealmService.TravelResult mobLeft = mobIn ? SecretRealmService.exit(mobVisitor) : null;
            LivingEntity mobReturned = mobLeft == null ? null : mobLeft.traveller().orElse(null);
            if (mobReturned != null && mobReturned != mobVisitor) probes.add(mobReturned);
            boolean mobOut = mobLeft != null && mobLeft.isSuccess() && mobReturned != null
                    && mobReturned.level().dimension().equals(Level.OVERWORLD)
                    && !mobReturned.getData(MxtAttachments.SECRET_REALM_TRAVEL).active()
                    && SecretRealmRegistry.ofMember(mobReturned.getUUID()).isEmpty();
            context.check("secret realm probe: mob_entered=" + mobIn + " mob_returned=" + mobOut, mobIn && mobOut);
        } finally {
            for (LivingEntity probe : probes) if (probe != null) probe.discard();
            destroy(server, opened);
        }
    }

    // Leaves one claimed instance behind instead of cleaning up, so a restart can be checked for keeping it.
    // The pair only means something when this runs before reopen against the same persistent world.
    private static void keep(ProbeContext context) {
        MinecraftServer server = context.server();
        Holder<SecretRealm> trial = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        // The instance has to be furnished for reopen to have terrain to find, so the tower is saved here too.
        saveTower(context, server.overworld());
        SecretRealmRecord record = SecretRealmService.open(server, planned(trial, 0, 4242L)).orElse(null);
        if (record == null) {
            context.fail("secret realm keep: could not open the instance");
            return;
        }
        UUID owner = UUID.randomUUID();
        SecretRealmRegistry.replace(record.withOwner(owner));
        context.note("secret realm keep: " + record.dimension().identifier() + " owner=" + owner);
        // What keep can assert on its own: the claim it wrote is the record the registry (and the world) holds.
        context.check("secret realm keep: the instance is claimed",
                SecretRealmRegistry.at(record.dimension()).filter(value -> value.isOwner(owner)).isPresent());
    }

    // Reopens a dormant instance the way an entry does, to prove a claimed secret realm comes back with the terrain it
    // had instead of being generated and furnished again. Only means something after keep, against the same world.
    private static void reopen(ProbeContext context) {
        MinecraftServer server = context.server();
        Holder<SecretRealm> trial = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        SecretRealmRecord dormant = SecretRealmRegistry.of(trial).stream()
                .filter(record -> record.index() == 0).findFirst().orElse(null);
        if (dormant == null) {
            context.fail("secret realm reopen: there is no dormant instance");
            return;
        }
        SecretRealmRecord reopened = SecretRealmService.open(server, dormant.restarted(server.overworld().getGameTime())).orElse(null);
        ServerLevel level = reopened == null ? null : server.getLevel(reopened.dimension());
        boolean kept = level != null && level.getBlockState(new BlockPos(8, 64, 8)).is(Blocks.GOLD_BLOCK);
        boolean prepared = reopened != null && reopened.prepared();
        context.check("secret realm reopen: terrain kept=" + kept + " prepared=" + prepared, kept && prepared);
    }

    // The structure the fixture places is built here instead of shipping a binary fixture.
    private static boolean saveTower(ProbeContext context, ServerLevel overworld) {
        Identifier towerId = ProbeDefinitions.id("probe/tower");
        BlockPos scratch = context.at(TOWER);
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                overworld.setBlockAndUpdate(scratch.offset(x, 0, z), Blocks.GOLD_BLOCK.defaultBlockState());
        StructureTemplate tower = overworld.getStructureManager().getOrCreate(towerId);
        tower.fillFromWorld(overworld, scratch, new Vec3i(3, 1, 3), false, List.of());
        boolean towerSaved = overworld.getStructureManager().save(towerId);
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                overworld.setBlockAndUpdate(scratch.offset(x, 0, z), Blocks.AIR.defaultBlockState());
        return towerSaved;
    }

    // One disposable being carrying at most one spirit root, for the legs that need a traveller that is no player.
    private static LivingEntity spawnBeing(ProbeContext context, ServerLevel level, BlockPos pos, @Nullable Identifier root) {
        Pig pig = EntityType.PIG.create(level, EntitySpawnReason.COMMAND);
        if (pig == null) return null;
        pig.setNoAi(true);
        pig.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(pig);
        if (root != null)
            CultivationIdentityService.grantSpiritRoot(pig, context.require(MxtResourceKeys.SPIRIT_ROOT, root));
        return pig;
    }

    private static SecretRealmRecord planned(Holder<SecretRealm> definition, int index, long seed) {
        return SecretRealmService.plan(definition, index, seed, 0L);
    }

    private static SecretRealmRecord openInstance(MinecraftServer server, Holder<SecretRealm> definition, int index,
                                                  long seed, List<ResourceKey<Level>> opened) {
        SecretRealmRecord record = SecretRealmService.open(server, planned(definition, index, seed)).orElse(null);
        if (record != null) opened.add(record.dimension());
        return record;
    }

    // Destroys everything a part opened, so no dimension is shared with the part that runs after it.
    private static void destroy(MinecraftServer server, List<ResourceKey<Level>> opened) {
        for (ResourceKey<Level> key : opened)
            SecretRealmRegistry.at(key).ifPresent(record -> SecretRealmService.destroy(server, record));
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
