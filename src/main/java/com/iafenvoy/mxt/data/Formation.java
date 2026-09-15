package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
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
import java.util.Optional;
import java.util.function.Function;

/**
 * A formation's static shape, resource costs, and lifecycle actions.
 *
 * <p>The shape is declared one of two ways, never both:</p>
 * <ul>
 *   <li>{@code structure_template} — a vanilla structure template. Right for a large or intricate
 *       layout, and the only way to require blocks the format cannot name one by one. Its air entries
 *       are ignored: a template saved from a bounding box records its empty cells too, and requiring
 *       them to stay empty would break the formation over a dropped item. A template therefore says
 *       what must be present, never what must be absent.</li>
 *   <li>{@code structure} — an inline list of required blocks at offsets from the controller. Right
 *       for the common case of a handful of positions: the expectation is parsed once into the
 *       definition and is immutable, so validating it is one {@code getBlockState} per block with no
 *       template lookup, NBT round trip or registry parsing. A template, by contrast, has to be
 *       re-serialised and re-parsed on every check.</li>
 * </ul>
 *
 * <p>{@code actions} is what the formation <em>does</em>: a list of functional modules, each carrying the
 * fields its own job needs. What lives here instead is the framework every formation shares — shape,
 * radius, what it costs, and the lifecycle and per-entity hooks a pack can drive by hand. A module may
 * be repeated, so one array can hold two attack modules with different damage, and the hooks stay
 * available next to them as the escape hatch for anything the modules cannot express.</p>
 *
 * <p>{@code hostile} describes what the per-entity actions are for, and it has to be declared because
 * nothing else can supply it for a hand-written action tree: a formation's intent is not recoverable
 * from its actions, since the same effect can be reached through a builtin action, a nested condition or
 * a script. A hostile formation spares whoever its owner recognises as a friend when the server option
 * allows it; a formation that is not hostile — a healing or support array — affects everyone. An
 * {@code mxt:attack} module is hostile by construction and needs no flag; this field is how a definition
 * built out of raw hooks says the same thing. See {@code FormationRelations#affects}.</p>
 */
public record Formation(Optional<Identifier> structureTemplate, List<RequiredBlock> structure,
                        NumberProvider radius, List<ResourceCost> activationCosts,
                        List<ResourceCost> maintenanceCosts, List<FormationActionType> actions,
                        boolean hostile, BlockAction activateAction,
                        BlockAction tickAction, BlockAction deactivateAction,
                        EntityAction entityTickAction, EntityAction entityEnterAction,
                        EntityAction entityExitAction) {
    public static final Codec<Holder<Formation>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.FORMATION);
    public static final Codec<Formation> DIRECT_CODEC = RecordCodecBuilder.<Formation>create(i -> i.group(
            Identifier.CODEC.optionalFieldOf("structure_template").forGetter(Formation::structureTemplate),
            // Strict, unlike the action and cost lists: dropping a required block because its id was
            // mistyped would quietly make the structure easier to satisfy, and a formation standing on
            // half its flags is worse than a definition that refuses to load.
            RequiredBlock.CODEC.listOf().optionalFieldOf("structure", List.of()).forGetter(Formation::structure),
            NumberProvider.CODEC.fieldOf("radius").forGetter(Formation::radius),
            ResourceCost.LIST_CODEC.optionalFieldOf("activation_costs", List.of()).forGetter(Formation::activationCosts),
            ResourceCost.LIST_CODEC.optionalFieldOf("maintenance_costs", List.of()).forGetter(Formation::maintenanceCosts),
            FormationActionType.CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(Formation::actions),
            Codec.BOOL.optionalFieldOf("hostile", false).forGetter(Formation::hostile),
            BlockAction.optionalCodec("activate_action").forGetter(Formation::activateAction),
            BlockAction.optionalCodec("tick_action").forGetter(Formation::tickAction),
            BlockAction.optionalCodec("deactivate_action").forGetter(Formation::deactivateAction),
            EntityAction.optionalCodec("entity_tick_action").forGetter(Formation::entityTickAction),
            EntityAction.optionalCodec("entity_enter_action").forGetter(Formation::entityEnterAction),
            EntityAction.optionalCodec("entity_exit_action").forGetter(Formation::entityExitAction)
    ).apply(i, Formation::new)).flatXmap(Formation::validate, Formation::validate);

    /**
     * The declared shape must be exactly one of the two forms. Reporting it here rather than in the
     * constructor keeps the failure a normal decode error, which is what lets a bad definition be named
     * instead of taken down as an exception.
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
         * Accepts a bare block id, because a layout of plain blocks should read like a list of positions,
         * and falls back to vanilla's {@code {"Name": ..., "Properties": ...}} object only when the state
         * is not the block's default one. The two are interchangeable on read; on write the bare id comes
         * back for anything a plain id can express, so a hand-written layout stays hand-writable.
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
