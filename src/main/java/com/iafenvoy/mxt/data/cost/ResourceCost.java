package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceUseService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * Consumes a datapack resource from the player's resource attachment.
 */
public record ResourceCost(Holder<Resource> resource, NumberProvider amount) implements Cost {
    public static final MapCodec<ResourceCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(ResourceCost::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ResourceCost::amount)
    ).apply(i, ResourceCost::new));

    @Override
    public boolean check(Player player) {
        double value = this.evaluate(player);
        return ResourceUseService.canUse(player, this.resource) && Double.isFinite(value) && value > 0.0D
                && player.getData(MxtAttachments.RESOURCE_HOLDER).get(this.resource) >= value;
    }

    public Identifier id() {
        return HolderHelper.id(this.resource);
    }

    public double evaluate(FormulaContext context) {
        double value = this.amount.evaluate(context);
        if (!Double.isFinite(value) || value <= 0.0D)
            throw new IllegalStateException("Resource cost " + this.id() + " must evaluate to a finite positive value");
        return value;
    }

    @Override
    public void consume(Player player) {
        double value = this.evaluate(player);
        if (!Double.isFinite(value) || value <= 0.0D) return;
        ResourceTransactions.tryConsume(player, player.getData(MxtAttachments.RESOURCE_HOLDER),
                new Evaluation(Map.of(HolderHelper.id(this.resource), value)));
    }

    private double evaluate(Player player) {
        FormulaContext context = ResourceService.formulaContext(player, HolderHelper.id(this.resource),
                this.resource.value(), FormulaContext.of(player));
        return this.amount.evaluate(context);
    }

    @Override
    public MapCodec<ResourceCost> codec() {
        return CODEC;
    }
}
