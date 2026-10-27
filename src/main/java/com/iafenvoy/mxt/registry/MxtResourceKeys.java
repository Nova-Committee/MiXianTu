package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.ability.AbilityAffinity;
import com.iafenvoy.mxt.data.ability.render.MountRender;
import com.iafenvoy.mxt.data.alchemy.*;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.aura.*;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.data.cultivation.*;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.iafenvoy.mxt.data.economy.CurrencyValue;
import com.iafenvoy.mxt.data.forging.BlueprintBinding;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.forging.ToolBinding;
import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.FormationShape;
import com.iafenvoy.mxt.data.formation.FormationStroke;
import com.iafenvoy.mxt.data.item.*;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.quality.DefaultQuality;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.rift.RiftPreset;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration;
import com.iafenvoy.mxt.data.talisman.Talisman;
import com.iafenvoy.mxt.data.talisman.TalismanType;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Every registry key MiXianTu owns, kept separate from the registry instances and the registration events so
 * codecs and runtime services can read registry identity without depending on a registration class.
 */
public final class MxtResourceKeys {
    public static final ResourceKey<Registry<RiftPreset>> RIFT = create("rift");
    // Builtin registries
    public static final ResourceKey<Registry<MapCodec<? extends TalismanType>>> TALISMAN_TYPE = create("talisman_type");
    public static final ResourceKey<Registry<MapCodec<? extends CurseType>>> CURSE_TYPE = create("curse_type");
    public static final ResourceKey<Registry<MapCodec<? extends AuraMaximum>>> AURA_MAXIMUM_TYPE = create("aura_maximum_type");
    public static final ResourceKey<Registry<MapCodec<? extends MountRender>>> MOUNT_RENDER_TYPE = create("mount_render_type");
    public static final ResourceKey<Registry<MapCodec<? extends FormationActionType>>> FORMATION_ACTION_TYPE = create("formation_action_type");
    public static final ResourceKey<Registry<MapCodec<? extends FormationStroke>>> FORMATION_STROKE_TYPE = create("formation_stroke_type");
    public static final ResourceKey<Registry<MapCodec<? extends SecretRealmGeneration>>> SECRET_REALM_GENERATION_TYPE = create("secret_realm_generation_type");

    // Datapack registries
    public static final ResourceKey<Registry<Aura>> AURA = create("aura");
    public static final ResourceKey<Registry<RealmStage>> REALM_STAGE = create("realm_stage");
    public static final ResourceKey<Registry<Element>> ELEMENT = create("element");
    public static final ResourceKey<Registry<ElementReaction>> ELEMENT_REACTION = create("element_reaction");
    public static final ResourceKey<Registry<SpiritRoot>> SPIRIT_ROOT = create("spirit_root");
    public static final ResourceKey<Registry<Physique>> PHYSIQUE = create("physique");
    public static final ResourceKey<Registry<Curse>> CURSE = create("curse");
    public static final ResourceKey<Registry<ForgingMethod>> FORGING_METHOD = create("forging_method");
    public static final ResourceKey<Registry<ForgingBlueprint>> FORGING_BLUEPRINT = create("forging_blueprint");
    public static final ResourceKey<Registry<ToolBinding>> TOOL_BINDING = create("tool_binding");
    public static final ResourceKey<Registry<BlueprintBinding>> BLUEPRINT_BINDING = create("blueprint_binding");
    public static final ResourceKey<Registry<Technique>> TECHNIQUE = create("technique");
    public static final ResourceKey<Registry<Progression>> PROGRESSION = create("progression");
    public static final ResourceKey<Registry<Cultivation>> CULTIVATION = create("cultivation");
    public static final ResourceKey<Registry<Artifact>> ARTIFACT = create("artifact");
    public static final ResourceKey<Registry<SpiritHerb>> SPIRIT_HERB = create("spirit_herb");
    public static final ResourceKey<Registry<MedicinalProperty>> MEDICINAL_PROPERTY = create("medicinal_property");
    public static final ResourceKey<Registry<AlchemyFurnaceDefinition>> ALCHEMY_FURNACE = create("alchemy_furnace");
    public static final ResourceKey<Registry<AlchemyWallMaterial>> ALCHEMY_WALL_MATERIAL = create("alchemy_wall_material");
    public static final ResourceKey<Registry<HeatSource>> HEAT_SOURCE = create("heat_source");
    public static final ResourceKey<Registry<Formation>> FORMATION = create("formation");
    public static final ResourceKey<Registry<FormationShape>> FORMATION_SHAPE = create("formation_shape");
    public static final ResourceKey<Registry<Tribulation>> TRIBULATION = create("tribulation");
    public static final ResourceKey<Registry<CreatureProfile>> CREATURE_PROFILE = create("creature_profile");
    public static final ResourceKey<Registry<ContractType>> CONTRACT_TYPE = create("contract_type");
    public static final ResourceKey<Registry<SecretRealm>> SECRET_REALM = create("secret_realm");
    public static final ResourceKey<Registry<CurrencyValue>> CURRENCY = create("currency");
    public static final ResourceKey<Registry<ItemBinding>> ITEM_BINDING = create("item_binding");
    public static final ResourceKey<Registry<WeaponBinding>> WEAPON_BINDING = create("weapon_binding");
    public static final ResourceKey<Registry<Pill>> PILL = create("pill");
    public static final ResourceKey<Registry<PillBinding>> PILL_BINDING = create("pill_binding");
    public static final ResourceKey<Registry<TechniqueBinding>> TECHNIQUE_BINDING = create("technique_binding");
    public static final ResourceKey<Registry<AuraZone>> AURA_ZONE = create("aura_zone");
    public static final ResourceKey<Registry<BlockAura>> BLOCK_AURA = create("block_aura");
    public static final ResourceKey<Registry<ItemAura>> ITEM_AURA = create("item_aura");
    public static final ResourceKey<Registry<ItemQuality>> ITEM_QUALITY = create("quality");
    public static final ResourceKey<Registry<DefaultQuality>> DEFAULT_QUALITY = create("default_quality");
    public static final ResourceKey<Registry<Talisman>> TALISMAN = create("talisman");
    public static final ResourceKey<Registry<AbilityAffinity>> ABILITY_AFFINITY = create("ability_affinity");

    private static <T> ResourceKey<Registry<T>> create(String path) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
    }
}
