package com.iafenvoy.mxt.data.talisman.type;

import com.iafenvoy.mxt.data.ability.ActionCarrier;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.iafenvoy.mxt.data.talisman.TalismanType;
import com.iafenvoy.mxt.data.talisman.TalismanUse;
import com.iafenvoy.mxt.runtime.talisman.TalismanProjectileEntity;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * The carrier itself is thrown, so this is the only type with no {@code max_use} - one use spends the paper whatever
 * its definitions say. The caster's own action runs at the throw and the other two halves wait for the
 * landing, which is why the projectile only has to remember the definition's id: an entity hit runs the target half,
 * a block hit the block half. The block half exists because a landing in the world reaches no entity, so it gates and
 * acts on the block that was struck instead.
 */
public record ThrownTalismanType(NumberProvider speed, NumberProvider gravity, BiEntityCondition targetCondition,
                                 EntityAction entityAction, BiEntityAction biEntityAction,
                                 BlockCondition blockCondition,
                                 BlockAction blockAction) implements TalismanType {
    // A throw far past a hand's own reach would only make the level walk every entity it holds.
    private static final double MAX_SPEED = 4.0D;

    public static final MapCodec<ThrownTalismanType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("speed", new Constant(1.25D)).forGetter(ThrownTalismanType::speed),
            NumberProvider.CODEC.optionalFieldOf("gravity", new Constant(DEFAULT_GRAVITY)).forGetter(ThrownTalismanType::gravity),
            BiEntityCondition.optionalCodec("target_condition").forGetter(ThrownTalismanType::targetCondition),
            EntityAction.optionalCodec("entity_action").forGetter(ThrownTalismanType::entityAction),
            BiEntityAction.optionalCodec("bi_entity_action").forGetter(ThrownTalismanType::biEntityAction),
            BlockCondition.optionalCodec("block_condition").forGetter(ThrownTalismanType::blockCondition),
            BlockAction.optionalCodec("block_action").forGetter(ThrownTalismanType::blockAction)
    ).apply(i, ThrownTalismanType::new));

    @Override
    public Plan plan(TalismanUse use) {
        // Nothing to aim at yet: the throw is always possible, and where it lands is the projectile's business.
        return Plan.of(List.of());
    }

    @Override
    public void apply(TalismanUse use, Plan plan) {
        TalismanType.runActions(this.entityAction, this.biEntityAction, use, List.of());
        double velocity = this.speed.evaluate(use.formula());
        if (!Double.isFinite(velocity) || velocity <= 0.0D) return;
        if (use.user().level() instanceof ServerLevel server)
            server.addFreshEntity(new TalismanProjectileEntity(server, use.user(), use.definition(),
                    Math.min(velocity, MAX_SPEED)));
    }

    // The landing half: the caster's action already happened at the throw, so only the one bi-entity action is left,
    // admitted by the same condition every other target goes through.
    @Override
    public void landOn(TalismanUse use, Entity target) {
        FormulaContext targetContext = ActionCarrier.targetContext(use.user(), target, use.formula());
        if (!this.targetCondition.test(use.user(), target, targetContext)) return;
        this.biEntityAction.execute(use.user(), target,
                new BiEntityActionContext(use.user(), target, targetContext, use.origin()));
    }

    // The block half runs where the carrier struck: its own condition admits its own action, and the face travels so
    // an action can tell which side was hit. Nothing here reads an entity, because there is none.
    @Override
    public void landOnBlock(TalismanUse use, BlockPos pos, Direction direction) {
        Level level = use.user().level();
        if (!this.blockCondition.test(level, pos, use.formula())) return;
        this.blockAction.execute(new BlockActionContext(level, pos, Optional.of(direction), use.formula()));
    }

    @Override
    public double gravity(FormulaContext context) {
        double value = this.gravity.evaluate(context);
        return Double.isFinite(value) && value >= 0.0D ? value : DEFAULT_GRAVITY;
    }

    @Override
    public MapCodec<ThrownTalismanType> codec() {
        return CODEC;
    }
}
