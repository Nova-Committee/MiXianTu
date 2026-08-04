package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.alchemy.AlchemyRecipe;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.artifact.ItemArchetype;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.data.cultivation.*;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.CurrencyValue;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.MaterialGrade;
import com.iafenvoy.mxt.data.resource.ResourceBar;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.Sect;
import com.iafenvoy.mxt.data.Title;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.data.RealmInstance;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.BlockAura;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Keys for every custom datapack registry.
 */
public final class MxtRegistryKeys {
    public static final ResourceKey<Registry<Resource>> RESOURCE = create("resource");
    public static final ResourceKey<Registry<ResourceBar>> RESOURCE_BAR = create("resource_bar");
    public static final ResourceKey<Registry<RealmStage>> REALM_STAGE = create("realm_stage");
    public static final ResourceKey<Registry<Element>> ELEMENT = create("element");
    public static final ResourceKey<Registry<SpiritRoot>> SPIRIT_ROOT = create("spirit_root");
    public static final ResourceKey<Registry<Physique>> PHYSIQUE = create("physique");
    public static final ResourceKey<Registry<Ability>> ABILITY = create("ability");
    public static final ResourceKey<Registry<Curse>> CURSE = create("curse");
    public static final ResourceKey<Registry<ForgingMethod>> FORGING_METHOD = create("forging_method");
    public static final ResourceKey<Registry<ForgingBlueprint>> FORGING_BLUEPRINT = create("forging_blueprint");
    public static final ResourceKey<Registry<CultivationTechnique>> CULTIVATION_TECHNIQUE = create("cultivation_technique");
    public static final ResourceKey<Registry<CultivateAction>> CULTIVATE_ACTION = create("cultivate_action");
    public static final ResourceKey<Registry<ItemArchetype>> ITEM_ARCHETYPE = create("item_archetype");
    public static final ResourceKey<Registry<SpiritHerb>> SPIRIT_HERB = create("spirit_herb");
    public static final ResourceKey<Registry<AlchemyRecipe>> ALCHEMY_RECIPE = create("alchemy_recipe");
    public static final ResourceKey<Registry<Formation>> FORMATION = create("formation");
    public static final ResourceKey<Registry<Tribulation>> TRIBULATION = create("tribulation");
    public static final ResourceKey<Registry<CreatureProfile>> CREATURE_PROFILE = create("creature_profile");
    public static final ResourceKey<Registry<ContractType>> CONTRACT_TYPE = create("contract_type");
    public static final ResourceKey<Registry<Title>> TITLE = create("title");
    public static final ResourceKey<Registry<MaterialGrade>> MATERIAL_GRADE = create("material_grade");
    public static final ResourceKey<Registry<Sect>> SECT = create("sect");
    public static final ResourceKey<Registry<RealmInstance>> REALM_INSTANCE = create("realm_instance");
    public static final ResourceKey<Registry<CurrencyValue>> CURRENCY = create("currency");
    public static final ResourceKey<Registry<ItemBinding>> ITEM_BINDING = create("item_binding");
    public static final ResourceKey<Registry<WeaponBinding>> WEAPON_BINDING = create("weapon_binding");
    public static final ResourceKey<Registry<PillBinding>> PILL_BINDING = create("pill_binding");
    /**
     * Templates used by the world aura resolver and runtime aura areas.
     */
    public static final ResourceKey<Registry<AuraZone>> AURA_ZONE = create("aura_zone");
    /**
     * Per-block aura emitters used by the chunk aura cache.
     */
    public static final ResourceKey<Registry<BlockAura>> BLOCK_AURA = create("block_aura");

    private MxtRegistryKeys() {
    }

    private static <T> ResourceKey<Registry<T>> create(String path) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
    }
}
