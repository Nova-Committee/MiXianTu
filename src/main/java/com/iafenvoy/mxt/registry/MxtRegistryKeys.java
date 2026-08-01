package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.data.alchemy.AlchemyRecipeDefinition;
import com.iafenvoy.mxt.data.alchemy.PillDefinition;
import com.iafenvoy.mxt.data.alchemy.SpiritHerbDefinition;
import com.iafenvoy.mxt.data.artifact.ItemArchetypeDefinition;
import com.iafenvoy.mxt.data.creature.ContractTypeDefinition;
import com.iafenvoy.mxt.data.creature.CreatureProfileDefinition;
import com.iafenvoy.mxt.data.cultivation.*;
import com.iafenvoy.mxt.data.curse.CurseDefinition;
import com.iafenvoy.mxt.data.economy.CurrencyValueDefinition;
import com.iafenvoy.mxt.data.forging.ForgingBlueprintDefinition;
import com.iafenvoy.mxt.data.forging.ForgingMethodDefinition;
import com.iafenvoy.mxt.data.formation.FormationDefinition;
import com.iafenvoy.mxt.data.material.MaterialGradeDefinition;
import com.iafenvoy.mxt.data.resource.ResourceBarDefinition;
import com.iafenvoy.mxt.data.resource.ResourceDefinition;
import com.iafenvoy.mxt.data.sect.SectDefinition;
import com.iafenvoy.mxt.data.title.TitleDefinition;
import com.iafenvoy.mxt.data.tribulation.TribulationDefinition;
import com.iafenvoy.mxt.data.item.ItemBindingDefinition;
import com.iafenvoy.mxt.data.item.ItemDefinition;
import com.iafenvoy.mxt.data.item.ItemEffectDefinition;
import com.iafenvoy.mxt.data.world.RealmInstanceDefinition;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Keys for every custom datapack registry.
 */
public final class MxtRegistryKeys {
    public static final ResourceKey<Registry<ResourceDefinition>> RESOURCE = create("resource");
    public static final ResourceKey<Registry<ResourceBarDefinition>> RESOURCE_BAR = create("resource_bar");
    public static final ResourceKey<Registry<RealmStageDefinition>> REALM_STAGE = create("realm_stage");
    public static final ResourceKey<Registry<ElementDefinition>> ELEMENT = create("element");
    public static final ResourceKey<Registry<SpiritRootDefinition>> SPIRIT_ROOT = create("spirit_root");
    public static final ResourceKey<Registry<PhysiqueDefinition>> PHYSIQUE = create("physique");
    public static final ResourceKey<Registry<AbilityDefinition>> ABILITY = create("ability");
    public static final ResourceKey<Registry<CurseDefinition>> CURSE = create("curse");
    public static final ResourceKey<Registry<ForgingMethodDefinition>> FORGING_METHOD = create("forging_method");
    public static final ResourceKey<Registry<ForgingBlueprintDefinition>> FORGING_BLUEPRINT = create("forging_blueprint");
    public static final ResourceKey<Registry<CultivationTechniqueDefinition>> CULTIVATION_TECHNIQUE = create("cultivation_technique");
    public static final ResourceKey<Registry<CultivateActionDefinition>> CULTIVATE_ACTION = create("cultivate_action");
    public static final ResourceKey<Registry<ItemArchetypeDefinition>> ITEM_ARCHETYPE = create("item_archetype");
    public static final ResourceKey<Registry<SpiritHerbDefinition>> SPIRIT_HERB = create("spirit_herb");
    public static final ResourceKey<Registry<AlchemyRecipeDefinition>> ALCHEMY_RECIPE = create("alchemy_recipe");
    public static final ResourceKey<Registry<FormationDefinition>> FORMATION = create("formation");
    public static final ResourceKey<Registry<TribulationDefinition>> TRIBULATION = create("tribulation");
    public static final ResourceKey<Registry<CreatureProfileDefinition>> CREATURE_PROFILE = create("creature_profile");
    public static final ResourceKey<Registry<ContractTypeDefinition>> CONTRACT_TYPE = create("contract_type");
    public static final ResourceKey<Registry<TitleDefinition>> TITLE = create("title");
    public static final ResourceKey<Registry<MaterialGradeDefinition>> MATERIAL_GRADE = create("material_grade");
    public static final ResourceKey<Registry<SectDefinition>> SECT = create("sect");
    public static final ResourceKey<Registry<RealmInstanceDefinition>> REALM_INSTANCE = create("realm_instance");
    public static final ResourceKey<Registry<CurrencyValueDefinition>> CURRENCY = create("currency");
    public static final ResourceKey<Registry<ItemDefinition>> ITEM = create("item");
    public static final ResourceKey<Registry<ItemDefinition>> PILL = create("pill");
    public static final ResourceKey<Registry<ItemDefinition>> WEAPON = create("weapon");
    public static final ResourceKey<Registry<ItemEffectDefinition>> ITEM_EFFECT = create("item_effect");
    public static final ResourceKey<Registry<ItemBindingDefinition>> ITEM_BINDING = create("item_binding");

    private MxtRegistryKeys() {
    }

    private static <T> ResourceKey<Registry<T>> create(String path) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
    }
}
