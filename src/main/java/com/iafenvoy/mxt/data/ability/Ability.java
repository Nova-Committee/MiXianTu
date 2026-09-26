package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.DataStorageCollector;
import com.iafenvoy.mxt.data.storage.DataStorageDeclaration;
import com.iafenvoy.mxt.data.storage.builtin.ChargesDataStorage;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A named ability; behaviour is selected by the built-in type, which declares the state kinds the ability keeps and
 * carries whatever parameters its own algorithm reads, so this record holds what every type shares: what it costs,
 * when it may run, and how it is named and drawn. {@code element_affinity_mode} says how a cultivator of several
 * matching roots is read: the average of them (the default, and the reading every ability has always had) or the
 * best.
 *
 * <p>Every ability is a registry entry, named by its own id: its registry holder is what the grant ledger, the
 * cooldowns and the wheel hand around. {@code hidden} only drops the entry from an artifact's tooltip; the action
 * fields, the cooldown and the damage condition are declared and read by the types that use them, so this record
 * declares none of them.
 */
public record Ability(Component name, Component description, AbilityType type, List<Cost> costs,
                      NumberProvider castTime, Optional<IconReference> icon,
                      Optional<ChargesDataStorage.Settings> charges, EntityCondition condition,
                      List<Either<Holder<Element>, TagKey<Element>>> elementAffinity, AffinityMode elementAffinityMode,
                      boolean hidden) implements DataStorageDeclaration, NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.ABILITY.identifier());
    public static final Codec<Holder<Ability>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ABILITY);
    public static final Codec<Ability> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Ability::name),
            ContextNameCodec.description(CATEGORY).forGetter(Ability::description),
            AbilityType.CODEC.forGetter(Ability::type),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Ability::costs),
            NumberProvider.CODEC.optionalFieldOf("cast_time", new Constant(0.0D)).forGetter(Ability::castTime),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(Ability::icon),
            ChargesDataStorage.Settings.CODEC.optionalFieldOf("charges").forGetter(Ability::charges),
            EntityCondition.optionalCodec("condition").forGetter(Ability::condition),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element_affinity", List.of()).forGetter(Ability::elementAffinity),
            AffinityMode.CODEC.optionalFieldOf("element_affinity_mode", AffinityMode.AVERAGE).forGetter(Ability::elementAffinityMode),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Ability::hidden)
    ).apply(i, Ability::new));

    // Collected on demand from the type, which is where the declaration lives; the holder only ever stores what was
    // written, so a kind nothing wrote costs nothing.
    @Override
    public List<DataStorage> storages() {
        DataStorageCollector collector = DataStorageCollector.create();
        this.type.createComponents(this, collector);
        return collector.build();
    }

    // How the element_ability_modifier of several matching roots becomes the one element_modifier a cast exposes.
    public enum AffinityMode {
        AVERAGE, MAX;
        public static final Codec<AffinityMode> CODEC = Codec.STRING.xmap(
                value -> valueOf(value.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT));
    }

    // Actions and composite ability types may refer to abilities through holders: keep diagnostic output shallow
    // so logging a cyclic datapack definition cannot recurse through its holder.
    @Override
    public @NonNull String toString() {
        return "Ability[type=" + this.type.getClass().getSimpleName() + ", costs=" + this.costs.size()
                + ", charges=" + this.charges.isPresent()
                + ", elementAffinity=" + this.elementAffinity.size() + "]";
    }
}
