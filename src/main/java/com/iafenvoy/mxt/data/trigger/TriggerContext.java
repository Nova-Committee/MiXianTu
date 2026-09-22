package com.iafenvoy.mxt.data.trigger;

import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime values made available to a trigger: fixed fields for common Minecraft events, plus extension values
 * stored by {@link Context#set}.
 */
public final class TriggerContext extends Context {
    @Nullable
    private Entity actor;
    @Nullable
    private Entity target;
    @Nullable
    private Level level;
    @Nullable
    private BlockPos position;
    @Nullable
    private ItemStack item;
    @Nullable
    private BlockState block;
    @Nullable
    private DamageSource damageSource;
    private FormulaContext formula = FormulaContext.EMPTY;

    public @Nullable Entity actor() {
        return this.actor;
    }

    public @Nullable Entity target() {
        return this.target;
    }

    public @Nullable Level level() {
        return this.level;
    }

    public @Nullable BlockPos position() {
        return this.position;
    }

    public @Nullable ItemStack item() {
        return this.item;
    }

    public @Nullable BlockState block() {
        return this.block;
    }

    public @Nullable DamageSource damageSource() {
        return this.damageSource;
    }

    @Override
    public FormulaContext formula() {
        return this.formula;
    }

    public TriggerContext actor(@Nullable Entity value) {
        this.actor = value;
        return this;
    }

    public TriggerContext target(@Nullable Entity value) {
        this.target = value;
        return this;
    }

    public TriggerContext level(@Nullable Level value) {
        this.level = value;
        return this;
    }

    public TriggerContext position(@Nullable BlockPos value) {
        this.position = value;
        return this;
    }

    public TriggerContext item(@Nullable ItemStack value) {
        this.item = value;
        return this;
    }

    public TriggerContext block(@Nullable BlockState value) {
        this.block = value;
        return this;
    }

    public TriggerContext damageSource(@Nullable DamageSource value) {
        this.damageSource = value;
        return this;
    }

    public TriggerContext formula(@Nullable FormulaContext value) {
        this.formula = value == null ? FormulaContext.EMPTY : value;
        return this;
    }

    // A payload value a formula cannot hold; numbers belong in the formula context because a formula reads them.
    public <T> TriggerContext payload(String key, @Nullable T value) {
        this.set(key, value);
        return this;
    }

    // Keyed payloads are type-erased by design: a reader must be the matcher the publisher was written for,
    // and TriggerPayload owns the keys.
    public <T> @Nullable T payload(String key) {
        return this.<T>get(key).orElse(null);
    }
}
