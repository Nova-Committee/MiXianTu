package com.iafenvoy.mxt.registry.cache;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.ElementReaction;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.chain.Chain;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.level.block.Block;

import java.util.*;

public final class RegistryCaches {
    public static final RealmChainCache REALM_CHAIN = new RealmChainCache().validateOrder(RealmStage::aura, Objects::equals)
            .validateChain(ladder -> ladder.nodes().stream().map(node -> node.value().aura()).distinct().count() == 1);
    public static final ChainCache<Progression> PROGRESSION_CHAIN = new ChainCache<>(MxtResourceKeys.PROGRESSION).callback(RegistryCaches::validateTechniqueLevels);
    // A ladder is allowed to name itself only once and inherit that name downwards, so two tiers on one line may
    // not write different names; the link check only catches the conflicting pair, and the whole-line rule below
    // is what actually keeps a misnamed ladder out of the index.
    public static final ChainCache<ItemQuality> QUALITY_CHAIN = new ChainCache<>(MxtResourceKeys.ITEM_QUALITY)
            .validateOrder(ItemQuality::quality, RegistryCaches::inheritsName)
            .validateChain(RegistryCaches::oneLadderName);
    public static final HeatSourceCache HEAT_SOURCE = new HeatSourceCache();
    public static final ReverseMappingListCache<Identifier, TriggerRule> TRIGGER_RULES_BY_SIGNAL = new ReverseMappingListCache<>(MxtResourceKeys.TRIGGER, rule -> List.of(rule.value().trigger().signalType()));
    public static final ReverseMappingListCache<Block, BlockAura> BLOCK_AURA = new ReverseMappingListCache<>(MxtResourceKeys.BLOCK_AURA, (aura) -> RegistryCodecs.resolve(aura.value().blocks(), BuiltInRegistries.BLOCK).map(Holder::value).toList());
    public static final ReverseMappingCache<Holder<Resource>, Aura> RESOURCE_AURA = new ReverseMappingCache<>(MxtResourceKeys.AURA, aura -> aura.value().resource());
    public static final FormulaNameCache<Resource> RESOURCE_NAMES = new FormulaNameCache<>(MxtResourceKeys.RESOURCE, "resource");
    public static final FormulaNameCache<Element> ELEMENT_NAMES = new FormulaNameCache<>(MxtResourceKeys.ELEMENT, "element");
    public static final FormulaNameCache<Attribute> ATTRIBUTE_NAMES = new FormulaNameCache<>(Registries.ATTRIBUTE, "attribute");
    public static final OrderCache<ElementReaction> ELEMENT_REACTION_ORDER = new OrderCache<>(MxtResourceKeys.ELEMENT_REACTION, Comparator.<Holder.Reference<ElementReaction>>comparingInt(h -> h.value().priority()).reversed().thenComparing(h -> h.key().identifier()));
    public static final DamageElementsCache DAMAGE_ELEMENTS = new DamageElementsCache();
    // Change is made greedily, so the set it is made from is checked when the pack loads.
    public static final CurrencyChangeCache CURRENCY_CHANGE = CurrencyChangeCache.of();
    // The four binding tables a stack may claim; each answers "could anything in here match" without a scan.
    public static final ItemMatcherCache<ItemBinding> ITEM_BINDINGS = new ItemMatcherCache<>(MxtResourceKeys.ITEM_BINDING);
    public static final ItemMatcherCache<WeaponBinding> WEAPON_BINDINGS = new ItemMatcherCache<>(MxtResourceKeys.WEAPON_BINDING);
    public static final ItemMatcherCache<PillBinding> PILL_BINDINGS = new ItemMatcherCache<>(MxtResourceKeys.PILL_BINDING);
    public static final ItemMatcherCache<TechniqueBinding> TECHNIQUE_BINDINGS = new ItemMatcherCache<>(MxtResourceKeys.TECHNIQUE_BINDING);

    // Constructs every cache above into RegistryCache's rebuild list. That event only rebuilds what is already
    // registered there, and nothing else touches this class before a world loads, so a run without this call leaves
    // all of them empty for the whole session.
    public static void init() {
    }

    // One link of a ladder may leave the name out and inherit it from the tier below, so the rule only refuses a
    // pair that both write a name and disagree about it.
    private static boolean inheritsName(Optional<Identifier> lower, Optional<Identifier> upper) {
        return upper.isEmpty() || lower.isEmpty() || lower.get().equals(upper.get());
    }

    // The same rule for the whole line, since the walk can reach a line whose names disagree far apart. A line with
    // one name, or with none at all, is a ladder; a line that names itself twice differently is not one ladder.
    private static boolean oneLadderName(Chain<ItemQuality> ladder) {
        Set<Identifier> names = new HashSet<>();
        for (Holder<ItemQuality> tier : ladder.nodes()) tier.value().quality().ifPresent(names::add);
        return names.size() <= 1;
    }

    // A technique annotates one chain: a holder starts at its entry level and may be configured for every level
    // after it. A partially annotated chain is reported, so nobody reaches a level nothing describes.
    private static void validateTechniqueLevels(RegistryAccess access, ChainCache<Progression> chains) {
        access.lookupOrThrow(MxtResourceKeys.TECHNIQUE).listElements().forEach(holder -> {
            Technique technique = holder.value();
            Holder<Progression> entry = technique.defaultLevel().orElse(null);
            if (entry == null) return;
            Identifier techniqueId = HolderHelper.id(holder);
            Chain<Progression> chain = chains.chainOf(entry).orElse(null);
            if (chain == null) return;
            Set<Holder<Progression>> configured = technique.configuration().keySet();
            // Check all progression configured
            for (int index = chain.indexOf(entry) + 1; index < chain.size(); index++) {
                Holder<Progression> level = chain.at(index).orElseThrow();
                if (configured.contains(level)) continue;
                RegistryCache.LOGGER.error("{} does not configure the progression level {}", techniqueId, level);
                return;
            }
            // Check configuration and print warning
            for (Holder<Progression> configuredStage : configured)
                if (!chain.contains(configuredStage) || chain.indexOf(configuredStage) < chain.indexOf(entry))
                    RegistryCache.LOGGER.warn("{} configures progression level {}, which it can never reach from {}", techniqueId, configuredStage, entry);
        });
    }
}
