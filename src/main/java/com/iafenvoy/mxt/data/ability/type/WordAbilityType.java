package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityEffect;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.CooldownSource;
import com.iafenvoy.mxt.data.storage.DataStorageCollector;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.Locale;

/**
 * High-tier, code-whitelisted word effects. Datapacks cannot supply an arbitrary command string.
 */
public record WordAbilityType(WordEffect effect, boolean requiresOperator,
                              NumberProvider amount,
                              NumberProvider cooldown) implements AbilityType, AbilityEffect, CooldownSource {
    public static final MapCodec<WordAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WordEffect.CODEC.fieldOf("effect").forGetter(WordAbilityType::effect),
            Codec.BOOL.optionalFieldOf("requires_operator", true).forGetter(WordAbilityType::requiresOperator),
            NumberProvider.CODEC.optionalFieldOf("amount", new Constant(0.0D)).forGetter(WordAbilityType::amount),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(WordAbilityType::cooldown)
    ).apply(i, WordAbilityType::new));

    @Override
    public MapCodec<WordAbilityType> codec() {
        return CODEC;
    }

    // A word has no action fields, so the effect itself is what runs when the cast pipeline reaches it.
    @Override
    public void execute(Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        try {
            if (this.effect == WordEffect.SELF_HEAL && actor instanceof LivingEntity living) {
                living.heal((float) this.amount.evaluate(context));
            } else if (this.effect == WordEffect.PURGE_SELF_CURSES) {
                CurseHolderAttachment holder = actor.getData(MxtAttachments.CURSE_HOLDER);
                new LinkedList<>(holder.instances().keySet()).forEach(curse ->
                        CurseService.remove(actor, curse, Reason.EXPLICIT, -1L, context));
            }
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Ability word effect failed", exception);
        }
    }

    // A word is cast like any other skill, so it can carry a cooldown and a charge pool.
    @Override
    public void createComponents(Ability ability, DataStorageCollector collector) {
        AbilityType.super.createComponents(ability, collector);
        collector.add(CooldownDataStorage.INSTANCE);
        ability.charges().ifPresent(charges -> collector.add(charges.declared()));
    }

    public enum WordEffect {
        SELF_HEAL,
        PURGE_SELF_CURSES;

        public static final Codec<WordEffect> CODEC = Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "Unknown word effect " + value);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
