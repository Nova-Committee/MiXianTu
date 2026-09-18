package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.aura.Aura;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

/**
 * A live formation instance: which definition it runs, where it reaches, who pays for it, how much upkeep it
 * has already paid, and how much it has banked. The banked amount is the one field no definition supplies, and
 * persisting it is what lets a lean period be paid for. Being in the level's index {@code is} being active:
 * there is no {@code active} flag, and a hand-written one is ignored while upkeep is charged regardless.
 */
public final class FormationInstance {
    public static final Codec<FormationInstance> CODEC = RecordCodecBuilder.<FormationInstance>create(i -> i.group(
            Identifier.CODEC.fieldOf("formation").forGetter(FormationInstance::formation),
            Codec.DOUBLE.fieldOf("radius").forGetter(FormationInstance::radius),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(FormationInstance::owner),
            Codec.LONG.optionalFieldOf("maintenance_count", 0L).forGetter(FormationInstance::maintenanceCount),
            // Written only when something is actually banked, so a save of the common case does not grow a
            // field per formation. Strict rather than tolerant: this map is written by the mod itself, so a
            // row that does not read back is a bug worth seeing, not a row to drop silently.
            Codec.unboundedMap(Aura.CODEC, Codec.DOUBLE).optionalFieldOf("stored")
                    .forGetter(instance -> instance.stored.isEmpty() ? Optional.empty() : Optional.of(instance.stored))
    ).apply(i, FormationInstance::new)).flatXmap(FormationInstance::validate, FormationInstance::validate);

    /**
     * Checks a decoded value and reports the problem instead of throwing it, so a single malformed row does
     * not fail the whole attachment by escaping the tolerant list decoder that is supposed to skip bad rows.
     */
    private static DataResult<FormationInstance> validate(FormationInstance instance) {
        if (!Double.isFinite(instance.radius) || instance.radius <= 0.0D)
            return DataResult.error(() -> "Formation radius must be finite and positive: " + instance.radius);
        if (instance.maintenanceCount < 0L)
            return DataResult.error(() -> "Formation upkeep count must not be negative: " + instance.maintenanceCount);
        for (Entry<Holder<Aura>, Double> entry : instance.stored.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || !Double.isFinite(entry.getValue()) || entry.getValue() < 0.0D)
                return DataResult.error(() -> "Formation stock must be a finite, non-negative amount of a named aura: "
                        + entry.getKey() + "=" + entry.getValue());
        }
        return DataResult.success(instance);
    }

    private final Identifier formation;
    private final double radius;
    private final Optional<UUID> owner;
    private long maintenanceCount;
    /**
     * Aura the array has banked but not spent, per aura.
     */
    private final Map<Holder<Aura>, Double> stored;

    FormationInstance(Identifier formation, double radius) {
        this(formation, radius, Optional.empty(), 0L, Map.of());
    }

    FormationInstance(Identifier formation, double radius, UUID owner) {
        this(formation, radius, Optional.of(owner), 0L, Map.of());
    }

    private FormationInstance(@NotNull Identifier formation, double radius, @NotNull Optional<UUID> owner,
                              long maintenanceCount, @NotNull Optional<Map<Holder<Aura>, Double>> stored) {
        this(formation, radius, owner, maintenanceCount, stored.orElse(Map.of()));
    }

    private FormationInstance(@NotNull Identifier formation, double radius, @NotNull Optional<UUID> owner,
                              long maintenanceCount, @NotNull Map<Holder<Aura>, Double> stored) {
        this.formation = formation;
        this.radius = radius;
        this.owner = owner;
        this.maintenanceCount = maintenanceCount;
        this.stored = new LinkedHashMap<>(stored);
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

    /**
     * What the array has banked, per resource id, as the live map the upkeep pass reads and writes through
     * {@link #deposit} / {@link #withdraw}. Empty for a formation that declares no storage.
     */
    public Map<Holder<Aura>, Double> stored() {
        return this.stored;
    }

    void maintained() {
        this.maintenanceCount++;
    }

    /**
     * Banks a period's surplus, dropping an entry that rounds to nothing. The capacity is the caller's
     * business: it was already applied when the amount was worked out.
     */
    void deposit(Map<Holder<Aura>, Double> amounts) {
        amounts.forEach((resource, amount) -> {
            if (resource == null || amount == null || !Double.isFinite(amount) || amount <= 0.0D) return;
            double total = this.stored.getOrDefault(resource, 0.0D) + amount;
            if (total <= 0.0D) this.stored.remove(resource);
            else this.stored.put(resource, total);
        });
    }

    /**
     * Spends banked aura, removing an entry that reaches zero. The amount was already clamped to what is on
     * hand when the period was planned, and the removal keeps a drained bank out of the save.
     */
    void withdraw(Map<Holder<Aura>, Double> amounts) {
        amounts.forEach((resource, amount) -> {
            if (resource == null || amount == null || !Double.isFinite(amount) || amount <= 0.0D) return;
            double left = this.stored.getOrDefault(resource, 0.0D) - amount;
            if (left <= 0.0D) this.stored.remove(resource);
            else this.stored.put(resource, left);
        });
    }
}
