package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A formation's static shape, resource costs and lifecycle actions. The shape is declared one of two ways,
 * never both: {@code structure_template}, whose air entries are ignored so a template says what must be present
 * and never what must be absent, or {@code structure}, an inline list of required blocks at offsets from the
 * controller. {@code spare_friends} decides whether per-entity work goes to everyone the array covers or only to
 * those its owner does not recognise; what the array is for is its actions, not this field.
 */
public record Formation(Component name, Component description, Optional<Identifier> structureTemplate,
                        StructureCheck structureCheck,
                        List<RequiredBlock> structure,
                        NumberProvider radius, List<Cost> activationCosts,
                        List<Cost> maintenanceCosts, Optional<Storage> storage,
                        List<FormationActionType> actions,
                        boolean spareFriends, BlockAction activateAction,
                        BlockAction tickAction, BlockAction deactivateAction,
                        EntityAction entityTickAction, EntityAction entityEnterAction,
                        EntityAction entityExitAction) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.FORMATION.identifier());
    public static final Codec<Holder<Formation>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.FORMATION);
    // The template and the check policy share one group slot, which keeps the group inside RecordCodecBuilder's
    // component limit; the JSON keys are unchanged by it.
    public static final Codec<Formation> DIRECT_CODEC = RecordCodecBuilder.<Formation>create(i -> i.group(
                    ContextNameCodec.name(CATEGORY).forGetter(Formation::name),
                    ContextNameCodec.description(CATEGORY).forGetter(Formation::description),
                    MiscCodecs.pair(
                                    Identifier.CODEC.optionalFieldOf("structure_template"),
                                    StructureCheck.CODEC.optionalFieldOf("structure_check", StructureCheck.STRUCTURE))
                            .forGetter(formation -> Pair.of(formation.structureTemplate(), formation.structureCheck())),
                    // Strict, unlike the action and cost lists: dropping a mistyped required block would quietly make
                    // the structure easier to satisfy, and a formation standing on half its flags is worse.
                    RequiredBlock.CODEC.listOf().optionalFieldOf("structure", List.of()).forGetter(Formation::structure),
                    NumberProvider.CODEC.fieldOf("radius").forGetter(Formation::radius),
                    Cost.LIST_CODEC.optionalFieldOf("activation_costs", List.of()).forGetter(Formation::activationCosts),
                    Cost.LIST_CODEC.optionalFieldOf("maintenance_costs", List.of()).forGetter(Formation::maintenanceCosts),
                    // A framework field rather than a module: what an array keeps is a question about its own upkeep,
                    // and absent is the answer most arrays give, so this is one field rather than a module.
                    Storage.CODEC.optionalFieldOf("storage").forGetter(Formation::storage),
                    FormationActionType.CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(Formation::actions),
                    Codec.BOOL.optionalFieldOf("spare_friends", false).forGetter(Formation::spareFriends),
                    BlockAction.optionalCodec("activate_action").forGetter(Formation::activateAction),
                    BlockAction.optionalCodec("tick_action").forGetter(Formation::tickAction),
                    BlockAction.optionalCodec("deactivate_action").forGetter(Formation::deactivateAction),
                    EntityAction.optionalCodec("entity_tick_action").forGetter(Formation::entityTickAction),
                    EntityAction.optionalCodec("entity_enter_action").forGetter(Formation::entityEnterAction),
                    EntityAction.optionalCodec("entity_exit_action").forGetter(Formation::entityExitAction)
            ).apply(i, (name, description, structure, blocks, radius, activationCosts, maintenanceCosts, storage, actions,
                        spareFriends, activateAction, tickAction, deactivateAction, entityTickAction, entityEnterAction,
                        entityExitAction) ->
                    new Formation(name, description, structure.getFirst(), structure.getSecond(), blocks, radius,
                            activationCosts, maintenanceCosts, storage, actions, spareFriends, activateAction, tickAction,
                            deactivateAction, entityTickAction, entityEnterAction, entityExitAction)))
            .flatXmap(Formation::validate, Formation::validate);

    /**
     * Whether standing on the right blocks is part of raising this array at all. {@code always} is for an array
     * that is meant to stand anywhere, and it refuses a declared structure rather than ignoring one.
     */
    public enum StructureCheck {
        STRUCTURE,
        ALWAYS;

        public static final Codec<StructureCheck> CODEC = Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "Unknown structure check " + value);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }

    /**
     * What the array may keep of the aura its own ground supplies; without it the formation is a pass-through.
     * {@code capacity} is required, and stocked aura only offsets a bill of the same resource
     * (see {@code FormationService.MaintainRule}).
     */
    public record Storage(Map<Holder<Aura>, NumberProvider> capacity) {
        public static final Codec<Storage> CODEC = RecordCodecBuilder.create(i -> i.group(
                CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).fieldOf("capacity").forGetter(Storage::capacity)
        ).apply(i, Storage::new));
    }

    // Reported as a decode error rather than thrown from the constructor, so a bad definition can be named.
    private static DataResult<Formation> validate(Formation formation) {
        boolean template = formation.structureTemplate().isPresent();
        boolean inline = !formation.structure().isEmpty();
        if (formation.structureCheck() == StructureCheck.ALWAYS) {
            if (template || inline)
                return DataResult.error(() -> "structure_check always means no structure is checked, so a formation "
                        + "must not declare structure_template or structure");
            return DataResult.success(formation);
        }
        if (template && inline)
            return DataResult.error(() -> "A formation declares both structure_template and structure; keep exactly one");
        if (!template && !inline)
            return DataResult.error(() -> "A formation needs either structure_template or a non-empty structure, "
                    + "or structure_check always");
        return DataResult.success(formation);
    }

    /**
     * One block an inline structure requires, at an offset from the controller.
     */
    public record RequiredBlock(BlockPos offset, BlockState state) {
        /**
         * Accepts a bare block id, falling back to vanilla's {@code {"Name": ..., "Properties": ...}} object only
         * for a non-default state; on write the bare id comes back wherever it can.
         */
        private static final Codec<BlockState> STATE = Codec.either(BuiltInRegistries.BLOCK.byNameCodec(), BlockState.CODEC)
                .xmap(choice -> choice.map(Block::defaultBlockState, Function.identity()),
                        state -> state.equals(state.getBlock().defaultBlockState())
                                ? Either.left(state.getBlock()) : Either.right(state));

        public static final Codec<RequiredBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("offset").forGetter(RequiredBlock::offset),
                STATE.fieldOf("state").forGetter(RequiredBlock::state)
        ).apply(i, RequiredBlock::new));
    }
}
