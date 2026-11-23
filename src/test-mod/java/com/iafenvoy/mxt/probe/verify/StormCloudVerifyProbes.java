package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iafenvoy.mxt.attachment.TribulationAttachment;
import com.iafenvoy.mxt.data.StormCloudOptions;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.entity.StormCloudEntity;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

public final class StormCloudVerifyProbes {
    private StormCloudVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/storm_cloud/options", StormCloudVerifyProbes::options),
                Probe.of("verify/storm_cloud/owner", StormCloudVerifyProbes::owner),
                Probe.of("verify/storm_cloud/tribulation", StormCloudVerifyProbes::tribulation),
                Probe.of("verify/storm_cloud/action", StormCloudVerifyProbes::action));
    }

    private static void options(ProbeContext context) {
        Codec<StormCloudOptions> codec = StormCloudOptions.CODEC;
        StormCloudOptions defaults = codec.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        context.expect("storm cloud default lifetime", defaults.lifetime(), 160);
        JsonObject json = new JsonObject();
        json.addProperty("cloud_color", "#904735AD");
        json.addProperty("lightning_color", "#FFFCB440");
        json.addProperty("radius", 256.0F);
        json.addProperty("thickness", 5.0F);
        json.addProperty("lifetime", 1200);
        StormCloudOptions options = codec.parse(JsonOps.INSTANCE, json).getOrThrow();
        context.expect("storm cloud body color", options.cloudColor(), 0x904735AD);
        context.expect("storm cloud lightning color", options.lightningColor(), 0xFFFCB440);
        context.expect("storm cloud proxy includes the downward bolt", options.bounds(-100, 80, 30),
                new AABB(-356, -50.5, -226, 156, 82.5, 286));
        context.expect("storm cloud registration", BuiltInRegistries.ENTITY_TYPE.getValue(
                Identifier.fromNamespaceAndPath("mxt", "storm_cloud")), MxtEntityTypes.STORM_CLOUD.get());
        context.expect("storm cloud tracking range in chunks", MxtEntityTypes.STORM_CLOUD.get().clientTrackingRange(), 24);

        ServerLevel level = context.level();
        Vec3 position = Vec3.atBottomCenterOf(context.at(5, 4, 5));
        CommandSourceStack source = context.server().createCommandSourceStack().withLevel(level).withPosition(position);
        CommandDispatcher<CommandSourceStack> dispatcher = context.server().getCommands().getDispatcher();
        try {
            context.expect("summon creates one storm cloud", dispatcher.execute(
                    "summon mxt:storm_cloud ~ ~ ~ {cloud:{cloud_color:\"#904735AD\",lightning_color:\"#FFFCB440\",radius:256.0f,thickness:5.0f,lifetime:1200}}",
                    source), 1);
        } catch (CommandSyntaxException failure) {
            throw new IllegalStateException(failure);
        }
        StormCloudEntity cloud = level.getEntitiesOfClass(StormCloudEntity.class, new AABB(position, position).inflate(1)).getFirst();
        try {
            context.expect("summon applies both colors and dimensions", cloud.options(), options);
            context.expect("summon starts a new cloud", cloud.ageTicks(), 0L);
            cloud.setOptions(new StormCloudOptions(options.cloudColor(), options.lightningColor(), options.radius(), options.thickness(), 40));
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, context.registries());
            cloud.saveWithoutId(output);
            CompoundTag stored = output.buildResult();
            long now = level.getGameTime();
            long elapsed = Math.min(35L, now);
            stored.putLong("cloud_created_at", now - elapsed);
            StormCloudEntity restored = new StormCloudEntity(MxtEntityTypes.STORM_CLOUD.get(), level);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, context.registries(), stored));
            context.expect("save and load keep all options", restored.options(), cloud.options());
            context.expect("save and load keep the elapsed world ticks", restored.ageTicks(), elapsed);
            context.expect("visual age keeps sub-tick interpolation", restored.visualAgeTicks(0.5F), elapsed + 0.5F);
            context.expect("a frozen frame stays in the same lightning tick", (long) restored.visualAgeTicks(1.0F), elapsed);

            ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(restored.getId(),
                    Objects.requireNonNull(restored.getEntityData().getNonDefaultValues()));
            StormCloudEntity lateObserver = new StormCloudEntity(MxtEntityTypes.STORM_CLOUD.get(), level);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.registries());
            try {
                ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer, packet);
                ClientboundSetEntityDataPacket initialData = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
                lateObserver.getEntityData().assignValues(initialData.packedItems());
                context.expect("remote spawn metadata keeps appearance", lateObserver.options(), restored.options());
                context.expect("late tracking resumes the current animation age", lateObserver.ageTicks(), elapsed);
                context.expect("storm cloud metadata fully read", buffer.readableBytes(), 0);
            } finally {
                buffer.release();
            }

            restored.setOptions(new StormCloudOptions(options.cloudColor(), options.lightningColor(), options.radius(), options.thickness(), 1));
            context.after(2, () -> {
                long advanced = level.getGameTime() - now;
                context.check("world time advances between observations", advanced >= 2);
                context.expect("tracked animation age advances without a new packet", lateObserver.ageTicks(), elapsed + advanced);
                restored.tick();
                context.check("expired cloud is discarded on the next server tick", restored.isRemoved());
                context.done();
            });
        } finally {
            cloud.discard();
        }

        json.addProperty("cloud_color", "#004735AD");
        StormCloudOptions lightningOnly = codec.parse(JsonOps.INSTANCE, json).getOrThrow();
        context.check("storm cloud zero alpha preserves lightning", (lightningOnly.cloudColor() >>> 24) == 0
                && lightningOnly.lightningColor() == options.lightningColor());
        json.addProperty("cloud_color", "#904735AD");
        json.addProperty("lightning_color", "#00FCB440");
        StormCloudOptions cloudOnly = codec.parse(JsonOps.INSTANCE, json).getOrThrow();
        context.check("storm cloud remains visible with lightning disabled", (cloudOnly.lightningColor() >>> 24) == 0
                && cloudOnly.cloudColor() == options.cloudColor());
        context.expect("disabled lightning needs no extra depth below the cloud", cloudOnly.bounds(-100, 80, 30),
                new AABB(-356, 77.5, -226, 156, 82.5, 286));
        context.check("storm cloud shaders ship their shared GLSL include", StormCloudOptions.class.getResource(
                "/assets/mxt/shaders/include/storm_cloud.glsl") != null);
        json.addProperty("radius", 256.5F);
        context.check("storm cloud rejects oversized radius", codec.parse(JsonOps.INSTANCE, json).error().isPresent());
        json.addProperty("radius", 0.5F);
        json.addProperty("thickness", 0.0F);
        context.check("storm cloud rejects zero thickness", codec.parse(JsonOps.INSTANCE, json).error().isPresent());
        context.note("storm cloud summon, tracking metadata, save and expiry: OK");
    }

    private static void owner(ProbeContext context) {
        ServerLevel level = context.level();
        Vec3 start = Vec3.atBottomCenterOf(context.at(4, 4, 4));
        Marker owner = new Marker(EntityType.MARKER, level);
        owner.setPos(start.add(3, -2, -4));
        context.expect("owner is available in the same level", level.addFreshEntity(owner), true);
        StormCloudEntity cloud = new StormCloudEntity(MxtEntityTypes.STORM_CLOUD.get(), level);
        cloud.snapTo(start);
        try {
            cloud.tick();
            context.expect("unbound cloud stays where summoned", cloud.position(), start);

            cloud.setOwner(owner);
            cloud.tick();
            context.check("bound cloud moves towards the owner by 0.30 blocks in XZ",
                    cloud.position().distanceToSqr(start.add(0.18, 0, -0.24)) < 1e-12);
            Vec3 firstStep = cloud.position();
            owner.setPos(firstStep.add(-0.5, 4, 0));
            cloud.tick();
            Vec3 secondStep = cloud.position();
            context.check("cloud eases towards the owner's new position at short distances",
                    secondStep.distanceToSqr(firstStep.add(-0.06, 0, 0)) < 1e-12);
            context.expect("following preserves the exact Y coordinate", cloud.getY(), start.y);

            owner.setPos(secondStep.add(0.006, 6, -0.004));
            cloud.tick();
            context.expect("cloud settles exactly over a nearby owner", cloud.position(),
                    new Vec3(owner.getX(), start.y, owner.getZ()));

            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, context.registries());
            cloud.saveWithoutId(output);
            CompoundTag saved = output.buildResult();
            StormCloudEntity restored = new StormCloudEntity(MxtEntityTypes.STORM_CLOUD.get(), level);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, context.registries(), saved));
            Vec3 settled = restored.position();
            owner.setPos(settled.add(0, 5, 2));
            restored.tick();
            context.check("restored cloud follows its saved owner",
                    restored.position().distanceToSqr(settled.add(0, 0, 0.24)) < 1e-12);

            CompoundTag commandData = saved.copy();
            commandData.putString("owner", owner.getUUID().toString());
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, context.registries(), commandData));
            restored.tick();
            context.check("cloud accepts UUID text in summon NBT",
                    restored.position().distanceToSqr(settled.add(0, 0, 0.24)) < 1e-12);

            Vec3 beforeRemoval = restored.position();
            owner.discard();
            restored.tick();
            context.expect("a missing owner leaves the cloud in place", restored.position(), beforeRemoval);

            Marker returned = new Marker(EntityType.MARKER, level);
            returned.setUUID(owner.getUUID());
            returned.setPos(beforeRemoval.add(3, -2, 0));
            context.expect("owner can return with the same UUID", level.addFreshEntity(returned), true);
            try {
                restored.tick();
                context.check("cloud resumes following when the owner returns",
                        restored.position().distanceToSqr(beforeRemoval.add(0.30, 0, 0)) < 1e-12);
                Vec3 beforeClear = restored.position();
                restored.setOwner(null);
                restored.tick();
                context.expect("clearing the owner stops movement", restored.position(), beforeClear);
            } finally {
                returned.discard();
            }
            context.note("storm cloud horizontal follow and owner persistence: OK");
        } finally {
            cloud.discard();
            owner.discard();
        }
    }

    private static void tribulation(ProbeContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();
        TribulationAttachment data = player.getData(MxtAttachments.TRIBULATION);
        FormulaContext formula = FormulaContext.of(player);
        Holder<Tribulation> complete = context.require(MxtResourceKeys.TRIBULATION, Identifier.parse("mxt_test:probe_storm_cloud"));
        Holder<Tribulation> fail = context.require(MxtResourceKeys.TRIBULATION, Identifier.parse("mxt_test:probe_storm_cloud_failure"));
        Holder<Tribulation> noCloud = context.require(MxtResourceKeys.TRIBULATION, Identifier.parse("mxt_test:probe_no_darken"));
        long now = level.getGameTime();
        context.check("a tribulation can keep darken disabled while creating a storm cloud", !complete.value().darkenSky());
        context.check("run starts with no automatic cloud when the field is absent",
                TribulationService.start(player, data, noCloud, now, formula).getFailure().isEmpty() && data.stormCloud().isEmpty());
        TribulationService.cancel(data);

        float initialHealth = player.getHealth();
        player.setHealth(20.0F);
        context.check("cloud-enabled tribulation starts", TribulationService.start(player, data, complete, now, formula).getFailure().isEmpty());
        StormCloudEntity cloud = (StormCloudEntity) level.getEntity(data.stormCloud().orElseThrow());
        assert cloud != null;
        context.check("the cloud starts during wind-up", data.windup() > 0 && cloud.isActiveTribulationCloudFor(player));
        context.expect("cloud uses the defined radius and appearance", cloud.options(),
                new StormCloudOptions(0xFF534065, 0xFFFFAA44, 256.0F, 10.0F, StormCloudOptions.DEFAULT.lifetime()));
        context.expect("cloud spawns at the evaluated absolute Y", cloud.getY(), 144.5D);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, context.registries());
        JsonElement saved = TribulationAttachment.CODEC.codec().encodeStart(ops, data).getOrThrow();
        TribulationAttachment restored = TribulationAttachment.CODEC.codec().parse(ops, saved).getOrThrow();
        context.expect("the active run saves its cloud UUID", restored.stormCloud(), data.stormCloud());
        cloud.tick();
        context.check("cloud stays active while the run owns it", cloud.isActiveTribulationCloudFor(player) && !cloud.expired());
        player.setHealth(16.0F);
        TribulationService.tick(player, data, complete, now + 1, formula);
        context.expect("wind-up tick updates the existing cloud's Y", cloud.getY(), 140.5D);
        TribulationService.tick(player, data, complete, now + 2, formula.with("caster_health", 20_000_000.0D));
        context.expect("out-of-range formula result keeps the last valid Y", cloud.getY(), 140.5D);
        player.setHealth(12.0F);
        TribulationService.tick(player, data, complete, now + 3, formula);
        context.expect("timeline tick updates the existing cloud's Y", cloud.getY(), 136.5D);
        context.expect("height updates retain the cloud UUID", data.stormCloud(), restored.stormCloud());
        for (int i = 4; i < 9 && data.tribulation().isPresent(); i++)
            TribulationService.tick(player, data, complete, now + i, formula);
        context.check("success clears the active run", data.tribulation().isEmpty());
        cloud.tick();
        context.check("success starts fading without immediately removing the cloud",
                !cloud.isActiveTribulationCloudFor(player) && !cloud.expired());

        context.check("failure probe starts", TribulationService.start(player, data, fail, now, formula).getFailure().isEmpty());
        StormCloudEntity failedCloud = (StormCloudEntity) level.getEntity(data.stormCloud().orElseThrow());
        assert failedCloud != null;
        context.expect("empty cloud settings use radius 64", failedCloud.options().radius(), 64.0F);
        context.expect("empty cloud settings spawn at Y=160", failedCloud.getY(), 160.0D);
        TribulationService.tick(player, data, fail, now, formula);
        context.check("failed timeline clears its run",
                TribulationService.tick(player, data, fail, now + 1, formula).getFailure().isPresent() && data.tribulation().isEmpty());
        failedCloud.tick();
        context.check("failure starts fading the cloud", !failedCloud.isActiveTribulationCloudFor(player));

        context.check("cancel probe starts", TribulationService.start(player, data, complete, now, formula).getFailure().isEmpty());
        StormCloudEntity cancelled = (StormCloudEntity) level.getEntity(data.stormCloud().orElseThrow());
        assert cancelled != null;
        cancelled.discard();
        TribulationService.tick(player, data, complete, now, formula);
        StormCloudEntity replacement = (StormCloudEntity) level.getEntity(data.stormCloud().orElseThrow());
        assert replacement != null;
        context.check("an active run restores a missing cloud once", replacement != cancelled && replacement.isActiveTribulationCloudFor(player));
        context.expect("a replacement cloud evaluates the current absolute Y", replacement.getY(), 136.5D);
        context.check("cancel clears its run", TribulationService.cancel(data));
        replacement.tick();
        context.check("cancel starts fading the cloud", !replacement.isActiveTribulationCloudFor(player));
        player.setHealth(initialHealth);
        context.after(25, () -> {
            context.check("clouds from success, failure and cancellation all expire after fading",
                    cloud.isRemoved() && failedCloud.isRemoved() && replacement.isRemoved());
            context.note("storm cloud tribulation lifecycle: OK");
            context.done();
        });
    }

    private static void action(ProbeContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();
        Vec3 position = player.position().add(2, 0, 1);
        JsonObject json = new JsonObject();
        json.addProperty("type", "mxt:spawn_storm_cloud");
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, context.registries());
        context.check("storm cloud action requires a lifetime", EntityAction.CODEC.parse(ops, json).error().isPresent());
        json.addProperty("lifetime", 3);
        json.addProperty("radius", 256.0);
        json.addProperty("offset_y", 12);
        json.addProperty("follow", false);
        EntityAction.CODEC.parse(ops, json).getOrThrow().execute(new EntityActionContext(player, FormulaContext.of(player), position));
        Vec3 center = position.add(0, 12, 0);
        StormCloudEntity cloud = level.getEntitiesOfClass(StormCloudEntity.class, new AABB(center, center).inflate(1)).getFirst();
        context.expect("action spawns from the context position plus offset", cloud.position(), center);
        context.expect("action uses the explicitly configured lifetime and radius", cloud.options(), StormCloudOptions.Appearance.DEFAULT.options(256, 3));
        context.check("manually summoned cloud uses timed lifecycle", !cloud.isTribulationCloud());
        cloud.tick();
        context.expect("follow=false keeps the cloud at its assigned location", cloud.position(), center);
        context.after(5, () -> {
            context.check("manually summoned cloud expires on schedule", cloud.isRemoved());
            context.note("storm cloud action: OK");
            context.done();
        });
    }
}
