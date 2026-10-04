package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.api.AlchemyWorkstation;
import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.event.AlchemyCraftEvent;
import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.recipe.AlchemyRecipeInput.Slot;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyResolver.Candidate;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyResolver.Evaluated;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyResolver.Gap;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Recipe numbers use the same parameters() path for preview and the frozen batch.
 * A workstation's start and completion callbacks share one reentrancy guard.
 */
public final class AlchemyWorkstationService {
    private static final ThreadLocal<Set<AlchemyWorkstationState>> IN_TRANSACTION = ThreadLocal.withInitial(HashSet::new);

    private AlchemyWorkstationService() {
    }

    public static Optional<Holder<AlchemyFurnaceDefinition>> furnaceDefinition(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        Holder<AlchemyFurnaceDefinition> stored = stack.get(MxtDataComponents.ALCHEMY_FURNACE.get());
        if (stored == null) return Optional.empty();
        Identifier id = HolderHelper.id(stored);
        if (id.equals(HolderHelper.EMPTY)) return Optional.empty();
        return MxtDatapackRegistries.holder(access, MxtResourceKeys.ALCHEMY_FURNACE, id).map(holder -> holder);
    }

    private static Optional<Parameters> parameters(ServerPlayer player, AlchemyWorkstation station, AlchemyRecipe recipe, Evaluated evaluated) {
        if (!evaluated.valid()) return Optional.empty();
        double modifier = inputModifier(player.registryAccess(), station.container(), FormulaContext.of(player));
        double scaled = modifier == QualityService.DEFAULT_MODIFIER ? evaluated.duration() : evaluated.duration() / modifier;
        if (!Double.isFinite(scaled) || scaled <= 0.0D) scaled = evaluated.duration();
        long ticks = Math.max(1L, Math.round(Math.min(scaled, Long.MAX_VALUE)));
        return Optional.of(new Parameters(evaluated.targetTemperature(), evaluated.temperatureTolerance(),
                evaluated.balanceTolerance(), evaluated.catalyst(), ticks, recipe.maxBadTicks(),
                evaluated.main(), evaluated.auxiliary(), evaluated.minimumAura()));
    }

    public static AlchemyPreview preview(ServerPlayer player, AlchemyWorkstation station) {
        FormulaContext context = FormulaContext.of(player);
        AlchemyMixture mixture = AlchemyResolver.mixture(player.registryAccess(), slots(station.container()), context);
        List<Candidate> candidates = AlchemyResolver.candidates(player.level().getServer().getRecipeManager(), mixture, context);
        Candidate chosen = AlchemyResolver.dominating(candidates);
        AlchemyFailure blocker = recipeBlocker(candidates, chosen);
        Optional<Parameters> numbers = Optional.empty();
        List<ItemStack> success = List.of(), failure = List.of();
        if (chosen != null) {
            Optional<RecipeHolder<AlchemyRecipe>> holder = recipe(player.level().getServer().getRecipeManager(), chosen.id());
            if (holder.isPresent()) {
                numbers = parameters(player, station, holder.get().value(), chosen.evaluated());
                List<ItemStack> madeSuccess = create(holder.get().value().successOutputs());
                List<ItemStack> madeFailure = create(holder.get().value().failureOutputs());
                if (madeSuccess == null || madeFailure == null) blocker = AlchemyFailure.INVALID_FORMULA;
                else {
                    success = madeSuccess;
                    failure = madeFailure;
                }
            }
            if (numbers.isEmpty()) blocker = AlchemyFailure.INVALID_FORMULA;
        }
        if (mixture.inputFailure().isPresent()) blocker = mixture.inputFailure().get();
        AlchemyFurnaceDefinition spec = station.furnaceDefinition().map(Holder::value).orElse(null);
        Optional<QualityService.Failure> qualityFailure = Optional.empty();
        if (station.state().busy()) blocker = AlchemyFailure.ACTIVE;
        else if (!station.structureStatus().complete()) blocker = AlchemyFailure.STRUCTURE;
        else if (spec == null) blocker = AlchemyFailure.NO_FURNACE;
        else if (QualityService.find(player.registryAccess(), station.furnaceItem()).isEmpty())
            blocker = AlchemyFailure.FURNACE_QUALITY;
        else {
            qualityFailure = QualityService.check(player, station.furnaceItem());
            AlchemyFailure placed = placement(station.container(), spec);
            if (qualityFailure.isPresent()) blocker = mapQuality(qualityFailure.get());
            else if (placed != null) blocker = placed;
        }
        // Temperature and output capacity never choose a weaker match. They only accept or reject the dominator.
        if (blocker == null && numbers.isPresent()) {
            Parameters process = numbers.get();
            if (!reachable(station, process)) blocker = AlchemyFailure.TEMPERATURE;
            else if (!environment(player.level(), station.getBlockPos(), process)) blocker = AlchemyFailure.ENVIRONMENT;
            else if (!canAccept(station.container(), success)) blocker = AlchemyFailure.OUTPUT_CAPACITY;
        }
        Optional<ResourceKey<Recipe<?>>> resolved = chosen == null ? Optional.empty() : Optional.of(chosen.id());
        return new AlchemyPreview(mixture, candidates, resolved, Optional.ofNullable(blocker), numbers, qualityFailure, success, failure);
    }

    public static StartResult start(ServerPlayer player, AlchemyWorkstation station) {
        Set<AlchemyWorkstationState> starting = IN_TRANSACTION.get();
        if (!starting.add(station.state())) return StartResult.rejected(AlchemyFailure.ACTIVE);
        try {
            return startGuarded(player, station);
        } finally {
            starting.remove(station.state());
        }
    }

    private static StartResult startGuarded(ServerPlayer player, AlchemyWorkstation station) {
        BlockPos pos = station.getBlockPos();
        if (station.state().busy()) return StartResult.rejected(AlchemyFailure.ACTIVE);
        AlchemyPreview preview = preview(player, station);
        if (preview.blocker().isPresent())
            return new StartResult(false, preview.blocker().get(), preview.qualityFailure());
        if (preview.parameters().isEmpty() || preview.resolvedRecipe().isEmpty())
            return StartResult.rejected(AlchemyFailure.INVALID_FORMULA);
        Optional<RecipeHolder<AlchemyRecipe>> holder = recipe(player.level().getServer().getRecipeManager(), preview.resolvedRecipe().get());
        if (holder.isEmpty()) return StartResult.rejected(AlchemyFailure.DISABLED);
        Parameters numbers = preview.parameters().get();
        List<ItemStack> success = preview.successOutputs(), failure = preview.failureOutputs();
        ItemStack[] before = copyInputs(station.container());
        if (NeoForge.EVENT_BUS.post(new AlchemyCraftEvent.Pre(holder.get(), pos, player, inputCopies(station.container()))).isCanceled())
            return StartResult.rejected(AlchemyFailure.CANCELLED);
        // A listener may move an item elsewhere. Restoring that external mutation would duplicate it.
        if (!sameInputs(before, station.container())) return StartResult.rejected(AlchemyFailure.CANCELLED);
        if (!station.structureStatus().complete()) return StartResult.rejected(AlchemyFailure.STRUCTURE);
        if (!reachable(station, numbers)) return StartResult.rejected(AlchemyFailure.TEMPERATURE);
        if (!canAccept(station.container(), success)) return StartResult.rejected(AlchemyFailure.OUTPUT_CAPACITY);
        clearInputs(station.container());
        station.state().begin(AlchemySession.start(holder.get().id().identifier(), holder.get().value(), numbers,
                success, failure, player.getUUID(), preview.mixture()));
        station.setChanged();
        return StartResult.success();
    }

    public static void abort(ServerLevel level, BlockPos pos, AlchemyWorkstation station) {
        abort(level, pos, station, AlchemyFailure.CANCELLED);
    }

    public static void abort(ServerLevel level, BlockPos pos, AlchemyWorkstation station, AlchemyFailure reason) {
        AlchemySession session = station.state().session().orElse(null);
        if (session == null || session.phase() == AlchemyPhase.READY || session.settled()) return;
        session.scrap(reason);
        station.setChanged();
    }

    public static void tick(ServerLevel level, BlockPos pos, AlchemyWorkstation station) {
        AlchemyWorkstationState state = station.state();
        AlchemySession session = state.session().orElse(null);
        double temperature = state.temperature();
        AlchemyPhase phase = state.phase();
        long remaining = session == null ? 0 : session.remainingTicks();
        int badTicks = session == null ? 0 : session.badTicks();
        if (state.active()) {
            AlchemyFurnaceStructure.Status structure = station.structureStatus();
            if (!structure.missing().isEmpty() || !structure.conflicts().isEmpty())
                abort(level, pos, station, AlchemyFailure.STRUCTURE);
            else if (!structure.unloaded().isEmpty()) return;
        }
        Optional<Holder<AlchemyFurnaceDefinition>> furnace = station.furnaceDefinition();
        if (state.active()) {
            if (furnace.isEmpty()) abort(level, pos, station, AlchemyFailure.DISABLED);
            else {
                heat(level, station, furnace.get().value());
                if (session.phase() == AlchemyPhase.WARMING && session.inRange(state.temperature()))
                    session.enterRunning();
                if (session.phase() == AlchemyPhase.RUNNING) session.tickRunning(state.temperature());
                if (session.due()) session.generatePending();
            }
        } else
            furnace.ifPresent(alchemyFurnaceDefinitionHolder -> cool(state, alchemyFurnaceDefinitionHolder.value().coolingPerTick(), 0.0D));
        if (session != null && session.phase() == AlchemyPhase.READY && !session.settled())
            settle(level, pos, station, session);
        if (temperature != state.temperature() || phase != state.phase()
                || session != state.session().orElse(null)
                || session != null && (remaining != session.remainingTicks() || badTicks != session.badTicks()))
            station.setChanged();
    }

    private static void heat(ServerLevel level, AlchemyWorkstation station, AlchemyFurnaceDefinition spec) {
        double heating = heatingPerTick(level, station);
        double limit = station.maximumTemperature();
        if (heating > 0.0D && limit > 0.0D)
            approach(station.state(), heating, spec.coolingPerTick(), Math.min(station.targetTemperature(), limit));
        else cool(station.state(), spec.coolingPerTick(), 0.0D);
    }

    private static double heatingPerTick(ServerLevel level, AlchemyWorkstation station) {
        return AlchemyHeatService.heatingPerTick(level, station.heatSourcePos());
    }

    private static void approach(AlchemyWorkstationState state, double heating, double cooling, double target) {
        double temperature = state.temperature();
        if (temperature < target) temperature = Math.min(target, temperature + heating);
        else if (temperature > target) temperature = Math.max(target, temperature - cooling);
        state.setTemperature(temperature);
    }

    private static void cool(AlchemyWorkstationState state, double cooling, double floor) {
        state.setTemperature(Math.max(floor, state.temperature() - cooling));
    }

    private static void settle(ServerLevel level, BlockPos pos, AlchemyWorkstation station, AlchemySession session) {
        Set<AlchemyWorkstationState> transactions = IN_TRANSACTION.get();
        if (!transactions.add(station.state())) return;
        AlchemyCraftEvent.Post event;
        try {
            List<ItemStack> pending = session.pendingItems();
            if (!canAccept(station.container(), pending)) return;
            insert(station.container(), pending);
            ServerPlayer operator = level.getServer().getPlayerList().getPlayer(session.operator());
            RecipeHolder<AlchemyRecipe> frozen = new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, session.recipeId()), session.recipe());
            event = new AlchemyCraftEvent.Post(frozen, pos, session.operator(), operator,
                    !session.failed(), session.failure().orElse(null), pending);
            session.clearPending();
            session.markSettled();
            station.state().clearSession();
            station.setChanged();
            session.blockAction().execute(level, pos, FormulaContext.of(level));
            if (operator != null) {
                session.action().execute(operator, FormulaContext.of(operator));
                if (!session.failed()) MxtCriteriaTriggers.ALCHEMY.get().trigger(operator, session.recipeId());
            }
        } finally {
            transactions.remove(station.state());
        }
        // Only Post may start the next batch, after every action belonging to the completed one.
        NeoForge.EVENT_BUS.post(event);
    }


    private static boolean reachable(AlchemyWorkstation station, Parameters numbers) {
        double cap = station.maximumTemperature();
        double low = Math.max(0.0D, numbers.targetTemperature() - numbers.temperatureTolerance());
        double high = numbers.targetTemperature() + numbers.temperatureTolerance();
        double target = station.targetTemperature();
        return cap > 0.0D && low <= cap && high >= 0.0D && low <= high && target >= low && target <= high;
    }

    private static boolean environment(Level level, BlockPos pos, Parameters numbers) {
        AuraResult aura = AuraService.getPositionAura(level, pos);
        if (aura.rules().alchemyEnvBonus()) return true;
        for (Map.Entry<Identifier, Double> entry : numbers.minimumAura().entrySet()) {
            Optional<Holder<Aura>> holder = MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.AURA, entry.getKey()).map(found -> found);
            if (holder.isEmpty() || aura.pool(holder.get()).amount() + 1.0E-6D < entry.getValue()) return false;
        }
        return true;
    }

    private static AlchemyFailure placement(Container container, AlchemyFurnaceDefinition spec) {
        int count = 0;
        for (int index = 0; index < AlchemySlots.OUTPUT_START; index++) {
            ItemStack stack = container.getItem(index);
            if (stack.isEmpty()) continue;
            if (!AlchemySlots.enabled(index, spec)) return AlchemyFailure.SLOTS;
            count += stack.getCount();
        }
        return count > spec.capacity() ? AlchemyFailure.CAPACITY : null;
    }

    private static double inputModifier(Provider access, Container container, FormulaContext context) {
        double modifier = QualityService.DEFAULT_MODIFIER;
        boolean graded = false;
        for (int index = 0; index < AlchemySlots.OUTPUT_START; index++) {
            ItemStack stack = container.getItem(index);
            if (stack.isEmpty()) continue;
            Optional<Holder<ItemQuality>> quality = QualityService.find(access, stack);
            if (quality.isEmpty()) continue;
            double value = QualityService.modifier(quality.get(), ItemQuality::alchemyModifier, context);
            modifier = graded ? Math.min(modifier, value) : value;
            graded = true;
        }
        return modifier;
    }

    private static List<Slot> slots(Container container) {
        List<Slot> slots = new ArrayList<>(AlchemySlots.OUTPUT_START);
        for (int index = 0; index < AlchemySlots.OUTPUT_START; index++)
            slots.add(new Slot(AlchemySlots.role(index), container.getItem(index)));
        return slots;
    }

    private static List<AlchemyCraftEvent.InputCopy> inputCopies(Container container) {
        List<AlchemyCraftEvent.InputCopy> copies = new ArrayList<>();
        for (int index = 0; index < AlchemySlots.OUTPUT_START; index++) {
            ItemStack stack = container.getItem(index);
            if (!stack.isEmpty()) copies.add(new AlchemyCraftEvent.InputCopy(AlchemySlots.role(index), stack.copy()));
        }
        return copies;
    }

    private static void clearInputs(Container container) {
        for (int index = 0; index < AlchemySlots.OUTPUT_START; index++) container.setItem(index, ItemStack.EMPTY);
    }

    private static ItemStack[] copyInputs(Container container) {
        ItemStack[] copy = new ItemStack[AlchemySlots.OUTPUT_START];
        for (int index = 0; index < copy.length; index++) copy[index] = container.getItem(index).copy();
        return copy;
    }

    private static boolean sameInputs(ItemStack[] before, Container container) {
        for (int index = 0; index < AlchemySlots.OUTPUT_START; index++)
            if (!ItemStack.isSameItemSameComponents(before[index], container.getItem(index)) || before[index].getCount() != container.getItem(index).getCount())
                return false;
        return true;
    }


    private static boolean canAccept(Container container, List<ItemStack> stacks) {
        if (container instanceof AlchemyAggregateContainer aggregate) {
            for (int index = 0; index < AlchemySlots.OUTPUT_COUNT; index++)
                if (!aggregate.present(AlchemySlots.OUTPUT_START + index)) return false;
        }
        return plan(container, stacks) != null;
    }

    private static void insert(Container container, List<ItemStack> stacks) {
        int[] counts = plan(container, stacks);
        if (counts == null) throw new IllegalStateException("Alchemy outputs changed after capacity check");
        ItemStack[] placed = plannedStacks(container, stacks);
        for (int index = 0; index < AlchemySlots.OUTPUT_COUNT; index++) {
            int slot = AlchemySlots.OUTPUT_START + index;
            ItemStack existing = container.getItem(slot);
            if (counts[index] == 0) {
                if (!existing.isEmpty()) container.setItem(slot, ItemStack.EMPTY);
                continue;
            }
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, placed[index]))
                container.setItem(slot, placed[index].copyWithCount(counts[index]));
            else if (existing.getCount() != counts[index]) existing.setCount(counts[index]);
        }
    }

    /**
     * Remaining room per output slot after merging identical pending stacks. Null when they do not fit.
     */
    private static int @Nullable [] plan(Container container, List<ItemStack> stacks) {
        int[] counts = new int[AlchemySlots.OUTPUT_COUNT];
        ItemStack[] kinds = new ItemStack[AlchemySlots.OUTPUT_COUNT];
        for (int index = 0; index < AlchemySlots.OUTPUT_COUNT; index++) {
            ItemStack existing = container.getItem(AlchemySlots.OUTPUT_START + index);
            kinds[index] = existing.isEmpty() ? ItemStack.EMPTY : existing;
            counts[index] = existing.getCount();
        }
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            int max = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
            if (stack.getCount() <= 0 || stack.getCount() > max) return null;
            int left = stack.getCount();
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int index = 0; index < AlchemySlots.OUTPUT_COUNT && left > 0; index++) {
                    boolean empty = counts[index] == 0;
                    if (pass == 0) {
                        if (empty || !ItemStack.isSameItemSameComponents(kinds[index], stack)) continue;
                    } else if (!empty) continue;
                    int room = max - counts[index];
                    if (room <= 0) continue;
                    int take = Math.min(room, left);
                    if (empty) kinds[index] = stack;
                    counts[index] += take;
                    left -= take;
                }
            }
            if (left > 0) return null;
        }
        return counts;
    }

    private static ItemStack[] plannedStacks(Container container, List<ItemStack> stacks) {
        ItemStack[] kinds = new ItemStack[AlchemySlots.OUTPUT_COUNT];
        int[] counts = new int[AlchemySlots.OUTPUT_COUNT];
        for (int index = 0; index < AlchemySlots.OUTPUT_COUNT; index++) {
            ItemStack existing = container.getItem(AlchemySlots.OUTPUT_START + index);
            kinds[index] = existing;
            counts[index] = existing.getCount();
        }
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            int max = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
            int left = stack.getCount();
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int index = 0; index < AlchemySlots.OUTPUT_COUNT && left > 0; index++) {
                    boolean empty = counts[index] == 0;
                    if (pass == 0) {
                        if (empty || !ItemStack.isSameItemSameComponents(kinds[index], stack)) continue;
                    } else if (!empty) continue;
                    int room = max - counts[index];
                    if (room <= 0) continue;
                    int take = Math.min(room, left);
                    if (empty) kinds[index] = stack;
                    counts[index] += take;
                    left -= take;
                }
            }
        }
        return kinds;
    }

    private static @Nullable List<ItemStack> create(List<ItemStackTemplate> templates) {
        List<ItemStack> stacks = new ArrayList<>(templates.size());
        for (ItemStackTemplate template : templates) {
            ItemStack stack = template.create();
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) return null;
            stacks.add(stack);
        }
        return stacks;
    }

    private static Optional<RecipeHolder<AlchemyRecipe>> recipe(RecipeManager manager, ResourceKey<Recipe<?>> id) {
        return manager.byKey(id).filter(holder -> holder.value() instanceof AlchemyRecipe).map(holder -> {
            @SuppressWarnings("unchecked")
            RecipeHolder<AlchemyRecipe> cast = (RecipeHolder<AlchemyRecipe>) holder;
            return cast;
        });
    }

    private static @Nullable AlchemyFailure recipeBlocker(List<Candidate> candidates, @Nullable Candidate chosen) {
        if (chosen != null) return null;
        for (Candidate candidate : candidates) if (candidate.matched()) return AlchemyFailure.AMBIGUOUS;
        return candidates.isEmpty() ? AlchemyFailure.INSUFFICIENT : reason(candidates.getFirst().gaps());
    }

    private static AlchemyFailure reason(List<Gap> gaps) {
        for (Gap gap : gaps) if (gap.kind() == Gap.Kind.EXTRA) return AlchemyFailure.CONFLICT;
        for (Gap gap : gaps) if (gap.kind() == Gap.Kind.BALANCE) return AlchemyFailure.IMBALANCE;
        return AlchemyFailure.INSUFFICIENT;
    }

    private static AlchemyFailure mapQuality(QualityService.Failure failure) {
        return switch (failure) {
            case BINDING_CONDITIONS -> AlchemyFailure.BINDING_CONDITIONS;
            case QUALITY_CONDITIONS -> AlchemyFailure.QUALITY_CONDITIONS;
            case UNBOUND -> AlchemyFailure.UNBOUND;
            case MAX_USES -> AlchemyFailure.MAX_USES;
            case COOLDOWN -> AlchemyFailure.COOLDOWN;
        };
    }

    public record Parameters(double targetTemperature, double temperatureTolerance, double balanceTolerance,
                             double catalystRequirement, long durationTicks, int maxBadTicks,
                             Map<Identifier, Double> mainRequirements, Map<Identifier, Double> auxiliaryRequirements,
                             Map<Identifier, Double> minimumAura) {
        public static final Codec<Parameters> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("target_temperature").forGetter(Parameters::targetTemperature),
                Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("temperature_tolerance").forGetter(Parameters::temperatureTolerance),
                Codec.doubleRange(0, 1).fieldOf("balance_tolerance").forGetter(Parameters::balanceTolerance),
                Codec.doubleRange(Double.MIN_VALUE, Double.MAX_VALUE).fieldOf("catalyst_requirement").forGetter(Parameters::catalystRequirement),
                Codec.LONG.validate(value -> value > 0 ? DataResult.success(value) : DataResult.error(() -> "Alchemy duration must be positive"))
                        .fieldOf("duration").forGetter(Parameters::durationTicks),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("max_bad_ticks").forGetter(Parameters::maxBadTicks),
                Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).fieldOf("main_requirements").forGetter(Parameters::mainRequirements),
                Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).fieldOf("auxiliary_requirements").forGetter(Parameters::auxiliaryRequirements),
                Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).fieldOf("minimum_aura").forGetter(Parameters::minimumAura)
        ).apply(i, Parameters::new));
    }

    public record AlchemyPreview(AlchemyMixture mixture, List<Candidate> candidates,
                                 Optional<ResourceKey<Recipe<?>>> resolvedRecipe,
                                 Optional<AlchemyFailure> blocker, Optional<Parameters> parameters,
                                 Optional<QualityService.Failure> qualityFailure, List<ItemStack> successOutputs,
                                 List<ItemStack> failureOutputs) {
    }

    public record StartResult(boolean started, @Nullable AlchemyFailure failure,
                              Optional<QualityService.Failure> qualityFailure) {
        public static StartResult success() {
            return new StartResult(true, null, Optional.empty());
        }

        public static StartResult rejected(AlchemyFailure failure) {
            return new StartResult(false, failure, Optional.empty());
        }

        public static StartResult rejected(AlchemyFailure failure, QualityService.Failure quality) {
            return new StartResult(false, failure, Optional.of(quality));
        }
    }
}
