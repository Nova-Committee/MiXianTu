package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.attachment.ContractAttachment;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Asks about the contract record a body carries: whether it is bound at all, which contract type it signed and
 * which order it is under. A body without a record answers {@code bound} as false, so a level gated on this stays
 * out of reach for anything nobody has taken as a spirit beast.
 */
public record ContractEntityCondition(boolean bound,
                                      List<Either<Holder<ContractType>, TagKey<ContractType>>> type,
                                      List<Identifier> behavior) implements EntityCondition {
    private static final Codec<List<Identifier>> BEHAVIORS = Codec.either(Identifier.CODEC, Identifier.CODEC.listOf())
            .xmap(either -> either.map(List::of, list -> list),
                    list -> list.size() == 1 ? Either.left(list.getFirst()) : Either.right(list));
    public static final MapCodec<ContractEntityCondition> CODEC = RecordCodecBuilder.<ContractEntityCondition>mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("bound", true).forGetter(ContractEntityCondition::bound),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.CONTRACT_TYPE).optionalFieldOf("type", List.of()).forGetter(ContractEntityCondition::type),
            BEHAVIORS.optionalFieldOf("behavior", List.of()).forGetter(ContractEntityCondition::behavior)
    ).apply(i, ContractEntityCondition::new)).validate(ContractEntityCondition::validate);

    // Asking which contract a body signed, or which order it is under, while also demanding that it signed none
    // has no answer.
    private static DataResult<ContractEntityCondition> validate(ContractEntityCondition condition) {
        if (!condition.bound() && !(condition.type().isEmpty() && condition.behavior().isEmpty()))
            return DataResult.error(() -> "mxt:contract needs bound=true to ask about a contract type or order");
        return DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        ContractAttachment contract = ctx.entity().getExistingData(MxtAttachments.CONTRACT).orElse(null);
        boolean recorded = contract != null && contract.bound();
        if (recorded != this.bound) return false;
        if (!recorded) return true;
        if (!this.type.isEmpty()) {
            Holder<ContractType> signed = contract.contractType().orElse(null);
            if (signed == null || !RegistryCodecs.matches(this.type, signed)) return false;
        }
        return this.behavior.isEmpty() || this.behavior.contains(contract.behavior().id());
    }

    @Override
    public @NonNull MapCodec<ContractEntityCondition> codec() {
        return CODEC;
    }
}
