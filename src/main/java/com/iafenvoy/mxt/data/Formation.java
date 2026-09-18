package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A formation's static shape, resource costs and lifecycle actions. The shape is declared one of two ways,
 * never both: {@code structure_template}, whose air entries are ignored so a template says what must be
 * present and never what must be absent; or {@code structure}, an inline list of required blocks at offsets
 * from the controller. {@code spare_friends} is the friend-or-foe switch: it decides whether the per-entity
 * work goes to everyone the array covers or only to those its owner does not recognise, and it says nothing
 * about what the array is for — an attacking array is one whose actions attack.
 */
public record Formation(Optional<Identifier> structureTemplate, List<RequiredBlock> structure,
                        NumberProvider radius, List<ResourceCost> activationCosts,
                        List<ResourceCost> maintenanceCosts, Optional<Storage> storage,
                        List<FormationActionType> actions,
                        boolean spareFriends, BlockAction activateAction,
                        BlockAction tickAction, BlockAction deactivateAction,
                        EntityAction entityTickAction, EntityAction entityEnterAction,
                        EntityAction entityExitAction) {
    public static final Codec<Holder<Formation>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.FORMATION);
    public static final Codec<Formation> DIRECT_CODEC = RecordCodecBuilder.<Formation>create(i -> i.group(
            Identifier.CODEC.optionalFieldOf("structure_template").forGetter(Formation::structureTemplate),
            // Strict, unlike the action and cost lists: dropping a mistyped required block would quietly make
            // the structure easier to satisfy, and a formation standing on half its flags is worse.
            RequiredBlock.CODEC.listOf().optionalFieldOf("structure", List.of()).forGetter(Formation::structure),
            NumberProvider.CODEC.fieldOf("radius").forGetter(Formation::radius),
            ResourceCost.LIST_CODEC.optionalFieldOf("activation_costs", List.of()).forGetter(Formation::activationCosts),
            ResourceCost.LIST_CODEC.optionalFieldOf("maintenance_costs", List.of()).forGetter(Formation::maintenanceCosts),
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
    ).apply(i, Formation::new)).flatXmap(Formation::validate, Formation::validate);

    /**
     * What the array may keep of the aura its own ground supplies; without it the formation is a pass-through.
     * {@code capacity} is per resource and required, so a {@code storage} with no {@code capacity} does not
     * decode, and stocked aura only offsets a bill of the same resource (see {@code FormationService.MaintainRule}).
     */
    public record Storage(Map<Holder<Aura>, NumberProvider> capacity) {
        public static final Codec<Storage> CODEC = RecordCodecBuilder.create(i -> i.group(
                CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).fieldOf("capacity").forGetter(Storage::capacity)
        ).apply(i, Storage::new));
    }

    /**
     * Reporting the shape error here rather than in the constructor keeps it a normal decode error, so a bad
     * definition can be named instead of taken down as an exception.
     */
    private static DataResult<Formation> validate(Formation formation) {
        boolean template = formation.structureTemplate().isPresent();
        boolean inline = !formation.structure().isEmpty();
        if (template && inline)
            return DataResult.error(() -> "A formation declares both structure_template and structure; keep exactly one");
        if (!template && !inline)
            return DataResult.error(() -> "A formation needs either structure_template or a non-empty structure");
        return DataResult.success(formation);
    }

    /**
     * One block an inline structure requires, at an offset from the controller.
     */
    public record RequiredBlock(BlockPos offset, BlockState state) {
        /**
         * Accepts a bare block id, falling back to vanilla's {@code {"Name": ..., "Properties": ...}} object
         * only when the state is not the default; on write the bare id comes back wherever it can.
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
