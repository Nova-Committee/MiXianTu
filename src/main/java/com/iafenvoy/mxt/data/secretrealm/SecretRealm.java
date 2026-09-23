package com.iafenvoy.mxt.data.secretrealm;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.CombinedCodecs;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Datapack policy for a secret realm. A secret realm is not a fixed dimension: every instance is a dimension created on
 * demand from {@link SecretRealmGeneration}. A claimed ({@code owned}) secret realm only unloads when its last member leaves,
 * keeping its terrain, while an unclaimed one is destroyed with its region data.
 */
public record SecretRealm(Component name, Component description, SecretRealmGeneration generation, long seed,
                            Optional<Border> border, int maxInstances,
                            Optional<Integer> maxMembers, boolean owned, long durationTicks,
                            List<StructurePlacement> structures, List<EntryPoint> entry,
                            EntityCondition enterCondition, EntityCondition exitCondition,
                            Optional<Component> enterDeniedMessage, Optional<Component> exitDeniedMessage,
                            EntityAction enterAction, EntityAction exitAction) implements NamedDefinition {
    // Written explicitly onto an instance without a border instead of inheriting the overworld border that derived
    // level data would otherwise hand to a runtime dimension.
    public static final double DEFAULT_BORDER_SIZE = 29999984.0D;
    public static final int MAX_INSTANCES = 256;
    public static final int MAX_MEMBERS = 100_000;

    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.SECRET_REALM.identifier());
    public static final Codec<SecretRealm> CODEC = RecordCodecBuilder.<SecretRealm>create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(SecretRealm::name),
            ContextNameCodec.description(CATEGORY).forGetter(SecretRealm::description),
            SecretRealmGeneration.CODEC.fieldOf("generation").forGetter(SecretRealm::generation),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(SecretRealm::seed),
            Border.CODEC.optionalFieldOf("border").forGetter(SecretRealm::border),
            Codec.intRange(1, MAX_INSTANCES).optionalFieldOf("max_instances", 1).forGetter(SecretRealm::maxInstances),
            Codec.intRange(1, MAX_MEMBERS).optionalFieldOf("max_members").forGetter(SecretRealm::maxMembers),
            Codec.BOOL.optionalFieldOf("owned", false).forGetter(SecretRealm::owned),
            MiscCodecs.longRange(0L, Long.MAX_VALUE).optionalFieldOf("duration_ticks", 0L).forGetter(SecretRealm::durationTicks),
            StructurePlacement.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(SecretRealm::structures),
            EntryPoint.LIST_CODEC.optionalFieldOf("entry", List.of()).forGetter(SecretRealm::entry),
            // Seventeen components; one pair keeps the group at sixteen.
            MiscCodecs.pair(
                    EntityCondition.optionalCodec("enter_condition"),
                    EntityCondition.optionalCodec("exit_condition"))
                    .forGetter(realm -> Pair.of(realm.enterCondition(), realm.exitCondition())),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("enter_denied_message").forGetter(SecretRealm::enterDeniedMessage),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("exit_denied_message").forGetter(SecretRealm::exitDeniedMessage),
            EntityAction.optionalCodec("enter_action").forGetter(SecretRealm::enterAction),
            EntityAction.optionalCodec("exit_action").forGetter(SecretRealm::exitAction)
    ).apply(i, (name, description, generation, seed, border, maxInstances, maxMembers, owned, durationTicks,
                structures, entry, conditions, enterDeniedMessage, exitDeniedMessage, enterAction, exitAction) ->
            new SecretRealm(name, description, generation, seed, border, maxInstances, maxMembers, owned,
                    durationTicks, structures, entry, conditions.getFirst(), conditions.getSecond(),
                    enterDeniedMessage, exitDeniedMessage, enterAction, exitAction))).validate(SecretRealm::validate);

    // Never "whatever the overworld uses": an omitted definition border still gets the vanilla default.
    public Border effectiveBorder() {
        return this.border.orElse(Border.VANILLA_DEFAULT);
    }

    public int memberLimit() {
        return this.maxMembers.orElse(MAX_MEMBERS);
    }

    public EntryPoint pickEntry(RandomSource random) {
        return EntryPoint.select(this.entry, random);
    }

    private static DataResult<SecretRealm> validate(SecretRealm value) {
        String failure = null;
        if (value.border().isPresent()) {
            Border border = value.border().get();
            if (!Double.isFinite(border.size()) || border.size() <= 0.0D)
                failure = "Secret realm border size must be a positive finite number";
            else if (border.warningBlocks() < 0 || border.warningTime() < 0)
                failure = "Secret realm border warning numbers must not be negative";
            else if (!Double.isFinite(border.damagePerBlock()) || border.damagePerBlock() < 0.0D
                    || !Double.isFinite(border.safeZone()) || border.safeZone() < 0.0D)
                failure = "Secret realm border damage numbers must be finite and non-negative";
            else
                for (StructurePlacement placement : value.structures())
                    if (!placement.relativeToEntry() && !inside(border, placement.pos())) {
                        failure = "Structure " + placement.nbt() + " at " + placement.pos().toShortString() + " lies outside the secret realm border";
                        break;
                    }
        }
        if (failure == null)
            for (EntryPoint point : value.entry()) {
                if (point.randomRadius().isPresent() && !(point.randomRadius().get() > 0.0D)) {
                    failure = "Secret realm entry random_radius must be positive";
                    break;
                }
                if (!Double.isFinite(point.spread()) || point.spread() < 0.0D) {
                    failure = "Secret realm entry spread must be a finite non-negative number";
                    break;
                }
                if (point.weight() < 0) {
                    failure = "Secret realm entry weight must not be negative";
                    break;
                }
            }
        String message = failure;
        return message == null ? DataResult.success(value) : DataResult.error(() -> message);
    }

    private static boolean inside(Border border, BlockPos pos) {
        double half = border.size() / 2.0D;
        return Math.abs(pos.getX() + 0.5D - border.center().x) <= half && Math.abs(pos.getZ() + 0.5D - border.center().y) <= half;
    }

    /** How an instance dimension is bounded; empty means the vanilla default, never the overworld's border. */
    public record Border(Vec2 center, double size, int warningBlocks, int warningTime, double damagePerBlock,
                         double safeZone) {
        public static final Border VANILLA_DEFAULT = new Border(new Vec2(0.0F, 0.0F), DEFAULT_BORDER_SIZE, 5, 15, 0.2D, 5.0D);
        public static final Codec<Border> CODEC = RecordCodecBuilder.create(i -> i.group(
                MiscCodecs.HORIZONTAL_PAIR.optionalFieldOf("center", new Vec2(0.0F, 0.0F)).forGetter(Border::center),
                Codec.DOUBLE.optionalFieldOf("size", DEFAULT_BORDER_SIZE).forGetter(Border::size),
                Codec.INT.optionalFieldOf("warning_blocks", 5).forGetter(Border::warningBlocks),
                Codec.INT.optionalFieldOf("warning_time", 15).forGetter(Border::warningTime),
                Codec.DOUBLE.optionalFieldOf("damage_per_block", 0.2D).forGetter(Border::damagePerBlock),
                Codec.DOUBLE.optionalFieldOf("safe_zone", 5.0D).forGetter(Border::safeZone)
        ).apply(i, Border::new));
    }

    // chance is rolled per instance, so the same definition can furnish a secret realm differently on every visit.
    public record StructurePlacement(Identifier nbt, BlockPos pos, Rotation rotation, Mirror mirror, double integrity,
                                     double chance, boolean relativeToEntry, boolean ignoreEntities, boolean keepLiquids) {
        public static final Codec<StructurePlacement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("nbt").forGetter(StructurePlacement::nbt),
                BlockPos.CODEC.fieldOf("pos").forGetter(StructurePlacement::pos),
                Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(StructurePlacement::rotation),
                Mirror.CODEC.optionalFieldOf("mirror", Mirror.NONE).forGetter(StructurePlacement::mirror),
                Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("integrity", 1.0D).forGetter(StructurePlacement::integrity),
                Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("chance", 1.0D).forGetter(StructurePlacement::chance),
                Codec.BOOL.optionalFieldOf("relative_to_entry", false).forGetter(StructurePlacement::relativeToEntry),
                Codec.BOOL.optionalFieldOf("ignore_entities", false).forGetter(StructurePlacement::ignoreEntities),
                Codec.BOOL.optionalFieldOf("keep_liquids", true).forGetter(StructurePlacement::keepLiquids)
        ).apply(i, StructurePlacement::new));
    }

    public enum Rotation {
        NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90;
        public static final Codec<Rotation> CODEC = Codec.STRING.xmap(
                value -> valueOf(value.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT));
    }

    public enum Mirror {
        NONE, LEFT_RIGHT, FRONT_BACK;
        public static final Codec<Mirror> CODEC = Codec.STRING.xmap(
                value -> valueOf(value.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT));
    }

    // The field accepts a single object or an array of them, so one fixed entrance keeps the short form.
    public record EntryPoint(Optional<Vec3> pos, Optional<Float> yaw, Optional<Float> pitch,
                             Optional<Vec2> randomCenter, Optional<Double> randomRadius, double spread, int weight) {
        public static final Codec<EntryPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                Vec3.CODEC.optionalFieldOf("pos").forGetter(EntryPoint::pos),
                Codec.FLOAT.optionalFieldOf("yaw").forGetter(EntryPoint::yaw),
                Codec.FLOAT.optionalFieldOf("pitch").forGetter(EntryPoint::pitch),
                MiscCodecs.HORIZONTAL_PAIR.optionalFieldOf("random_center").forGetter(EntryPoint::randomCenter),
                Codec.DOUBLE.optionalFieldOf("random_radius").forGetter(EntryPoint::randomRadius),
                Codec.DOUBLE.optionalFieldOf("spread", 3.0D).forGetter(EntryPoint::spread),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(EntryPoint::weight)
        ).apply(i, EntryPoint::new));
        public static final Codec<List<EntryPoint>> LIST_CODEC = CombinedCodecs.combineCodec(CODEC);

        // Same fallback as WeightedActionEntry: a non-positive total picks uniformly instead of failing.
        public static EntryPoint select(List<EntryPoint> entries, RandomSource random) {
            if (entries.isEmpty()) return null;
            long total = entries.stream().mapToLong(entry -> Math.max(0, entry.weight())).sum();
            if (total <= 0L) return entries.get(random.nextInt(entries.size()));
            long selected = (long) (random.nextDouble() * total);
            for (EntryPoint entry : entries) {
                selected -= Math.max(0, entry.weight());
                if (selected < 0L) return entry;
            }
            return entries.getLast();
        }
    }
}
