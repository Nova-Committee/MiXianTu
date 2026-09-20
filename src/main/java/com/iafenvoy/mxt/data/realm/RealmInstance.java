package com.iafenvoy.mxt.data.realm;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.codec.CombinedCodecs;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
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
 * Datapack policy for a secret realm. A realm is not a fixed dimension: every instance of this definition
 * is a dimension created on demand from {@link RealmGeneration}, so the fields below decide how that
 * dimension is generated, bounded and furnished, where travellers arrive, and who may claim it.
 *
 * <p>An instance stops being simulated when its last member leaves. A claimed ({@code owned}) realm only
 * unloads, keeping its generated terrain for the next visit, while an unclaimed one is destroyed with its
 * region data.
 */
public record RealmInstance(RealmGeneration generation, long seed, Optional<Border> border, int maxInstances,
                            Optional<Integer> maxMembers, boolean owned, long durationTicks,
                            List<StructurePlacement> structures, List<EntryPoint> entry,
                            EntityCondition enterCondition, EntityCondition exitCondition,
                            Optional<Component> enterDeniedMessage, Optional<Component> exitDeniedMessage,
                            EntityAction enterAction, EntityAction exitAction) {
    /**
     * The vanilla border diameter. A definition without a border is explicitly reset to this instead of
     * inheriting the overworld border that derived level data would otherwise hand to a runtime dimension.
     */
    public static final double DEFAULT_BORDER_SIZE = 29999984.0D;
    public static final int MAX_INSTANCES = 256;
    public static final int MAX_MEMBERS = 100_000;

    public static final Codec<RealmInstance> CODEC = RecordCodecBuilder.<RealmInstance>create(i -> i.group(
            RealmGeneration.CODEC.fieldOf("generation").forGetter(RealmInstance::generation),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(RealmInstance::seed),
            Border.CODEC.optionalFieldOf("border").forGetter(RealmInstance::border),
            Codec.intRange(1, MAX_INSTANCES).optionalFieldOf("max_instances", 1).forGetter(RealmInstance::maxInstances),
            Codec.intRange(1, MAX_MEMBERS).optionalFieldOf("max_members").forGetter(RealmInstance::maxMembers),
            Codec.BOOL.optionalFieldOf("owned", false).forGetter(RealmInstance::owned),
            MiscCodecs.longRange(0L, Long.MAX_VALUE).optionalFieldOf("duration_ticks", 0L).forGetter(RealmInstance::durationTicks),
            StructurePlacement.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(RealmInstance::structures),
            EntryPoint.LIST_CODEC.optionalFieldOf("entry", List.of()).forGetter(RealmInstance::entry),
            EntityCondition.optionalCodec("enter_condition").forGetter(RealmInstance::enterCondition),
            EntityCondition.optionalCodec("exit_condition").forGetter(RealmInstance::exitCondition),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("enter_denied_message").forGetter(RealmInstance::enterDeniedMessage),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("exit_denied_message").forGetter(RealmInstance::exitDeniedMessage),
            EntityAction.optionalCodec("enter_action").forGetter(RealmInstance::enterAction),
            EntityAction.optionalCodec("exit_action").forGetter(RealmInstance::exitAction)
    ).apply(i, RealmInstance::new)).validate(RealmInstance::validate);

    /**
     * The border to write onto an instance dimension. A definition without one still receives the vanilla
     * default so that a realm never inherits a shrunken overworld border.
     */
    public Border effectiveBorder() {
        return this.border.orElse(Border.VANILLA_DEFAULT);
    }

    public int memberLimit() {
        return this.maxMembers.orElse(MAX_MEMBERS);
    }

    /**
     * Picks one entry point by weight, or {@code null} when the definition asks for a random landing.
     */
    public EntryPoint pickEntry(RandomSource random) {
        return EntryPoint.select(this.entry, random);
    }

    private static DataResult<RealmInstance> validate(RealmInstance value) {
        String failure = null;
        if (value.border().isPresent()) {
            Border border = value.border().get();
            if (!Double.isFinite(border.size()) || border.size() <= 0.0D)
                failure = "Realm border size must be a positive finite number";
            else if (border.warningBlocks() < 0 || border.warningTime() < 0)
                failure = "Realm border warning numbers must not be negative";
            else if (!Double.isFinite(border.damagePerBlock()) || border.damagePerBlock() < 0.0D
                    || !Double.isFinite(border.safeZone()) || border.safeZone() < 0.0D)
                failure = "Realm border damage numbers must be finite and non-negative";
            else
                for (StructurePlacement placement : value.structures())
                    if (!placement.relativeToEntry() && !inside(border, placement.pos())) {
                        failure = "Structure " + placement.nbt() + " at " + placement.pos().toShortString() + " lies outside the realm border";
                        break;
                    }
        }
        if (failure == null)
            for (EntryPoint point : value.entry()) {
                if (point.randomRadius().isPresent() && !(point.randomRadius().get() > 0.0D)) {
                    failure = "Realm entry random_radius must be positive";
                    break;
                }
                if (!Double.isFinite(point.spread()) || point.spread() < 0.0D) {
                    failure = "Realm entry spread must be a finite non-negative number";
                    break;
                }
                if (point.weight() < 0) {
                    failure = "Realm entry weight must not be negative";
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

    /**
     * How an instance dimension is bounded. An omitted border means "the vanilla default", never "whatever
     * the overworld uses".
     */
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

    /**
     * One structure template placed into a fresh instance. {@code chance} is rolled per instance, so the
     * same definition can furnish a realm differently on every visit.
     */
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

    /**
     * One weighted landing option. The field accepts a single object or an array of them, so a realm with
     * one fixed entrance keeps the short form while a larger one can spread arrivals over several points.
     */
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

        /**
         * Weighted choice with the same fallback as {@link com.iafenvoy.mxt.data.action.WeightedActionEntry}:
         * a non-positive total falls back to a uniform pick instead of failing.
         */
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
