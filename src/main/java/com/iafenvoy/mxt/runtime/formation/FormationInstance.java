package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.event.FormationEvent.Deactivate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * A live formation instance: which definition it runs, where it reaches, who pays for it, and how much
 * upkeep it has already paid.
 *
 * <p>Being in the level's index <em>is</em> being active. There used to be an {@code active} flag
 * alongside it, but nothing could make it false while the instance was still reachable: teardown
 * removes the entry from {@link FormationWorldAttachment} and marks the instance in the same call, and
 * every other reader treats "in {@code formations()}" as the answer anyway. A persisted field whose
 * value is always {@code true} invites a later reader to trust it as a switch — and a hand-edited save
 * saying {@code "active": false} would be silently ignored and charged upkeep regardless — so the flag
 * is gone and entry membership is the single source of truth. Teardown is announced by
 * {@link Deactivate}, which does not need a flag to be
 * meaningful.</p>
 *
 * <p>This class carries its own {@link #CODEC} rather than delegating to a separate snapshot record.
 * {@code RecordCodecBuilder} only needs a full-argument constructor and a getter per component, both of
 * which this class has, and every field here is a plain value — so there is nothing a stored form would
 * need to drop. The snapshot pattern is still right where the runtime object holds something the stored
 * form must not, as in {@code ForgingSession}, whose snapshot omits its resolved plan.</p>
 *
 * <p>The attachment stores these instances directly, which is what makes mutation in place the only way
 * to change one: there is no write-back step to forget, and a change cannot be lost because a later
 * branch decided not to save. State changes are package-private, so outside this package an instance is
 * read-only.</p>
 */
public final class FormationInstance {
    public static final Codec<FormationInstance> CODEC = RecordCodecBuilder.<FormationInstance>create(i -> i.group(
            Identifier.CODEC.fieldOf("formation").forGetter(FormationInstance::formation),
            Codec.DOUBLE.fieldOf("radius").forGetter(FormationInstance::radius),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(FormationInstance::owner),
            Codec.LONG.optionalFieldOf("maintenance_count", 0L).forGetter(FormationInstance::maintenanceCount)
    ).apply(i, FormationInstance::new)).flatXmap(FormationInstance::validate, FormationInstance::validate);

    /**
     * Checks a decoded value and reports the problem instead of throwing it.
     *
     * <p>Validation used to live in the constructor. That made a single malformed row take the whole save
     * down, because a throwing codec escapes the tolerant list decoder that is supposed to skip bad rows
     * and instead fails the attachment outright. Reporting it keeps the failure a normal decode error, so
     * {@code CollectionCodecs.list} can drop the row and name the reason.</p>
     *
     * <p>Runtime construction has nothing to fear from the move: the only caller validates the radius
     * before building an instance, and the upkeep counter starts at zero.</p>
     */
    private static DataResult<FormationInstance> validate(FormationInstance instance) {
        if (!Double.isFinite(instance.radius) || instance.radius <= 0.0D)
            return DataResult.error(() -> "Formation radius must be finite and positive: " + instance.radius);
        if (instance.maintenanceCount < 0L)
            return DataResult.error(() -> "Formation upkeep count must not be negative: " + instance.maintenanceCount);
        return DataResult.success(instance);
    }

    private final Identifier formation;
    private final double radius;
    private final Optional<UUID> owner;
    private long maintenanceCount;

    FormationInstance(Identifier formation, double radius) {
        this(formation, radius, Optional.empty(), 0L);
    }

    FormationInstance(Identifier formation, double radius, UUID owner) {
        this(formation, radius, Optional.of(owner), 0L);
    }

    private FormationInstance(@NotNull Identifier formation, double radius, @NotNull Optional<UUID> owner, long maintenanceCount) {
        this.formation = formation;
        this.radius = radius;
        this.owner = owner;
        this.maintenanceCount = maintenanceCount;
    }

    public Identifier formation() {
        return this.formation;
    }

    public double radius() {
        return this.radius;
    }

    public Optional<UUID> owner() {
        return this.owner;
    }

    public long maintenanceCount() {
        return this.maintenanceCount;
    }

    void maintained() {
        this.maintenanceCount++;
    }
}
