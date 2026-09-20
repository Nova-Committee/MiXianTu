package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityModifierService.ResolvedModifier;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Applies all datapack-defined passive modifiers as removable transient vanilla attributes.
 */
@EventBusSubscriber
public final class PassiveAttributeService {
    private static final String PREFIX = "passive/";
    /**
     * Every attribute this service has ever written to. Vanilla only lists the attributes it syncs to clients
     * when asked for an entity's attributes, and an attribute that is not synced (attack damage, for one, which
     * every client derives from the held item) is left out of that list, so a generated modifier on it would
     * never be found again and would outlive the content that asked for it. This set is the missing half: a
     * generated modifier can only ever live on an attribute some definition named, so those are the only extra
     * ones a cleanup pass has to look at. It grows with content, never with entities, and only the server thread
     * touches it.
     */
    private static final Set<Holder<Attribute>> WRITTEN_ATTRIBUTES = new HashSet<>();

    private PassiveAttributeService() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        reconcile(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(Clone event) {
        reconcile(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerRespawnEvent event) {
        reconcile(event.getEntity());
    }

    /**
     * Reconciles the full generated modifier set. Server thread only.
     */
    public static void reconcile(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        List<Entry> entries = entries(entity);
        removeGenerated(entity, Set.of());
        FormulaContext context = dynamic(entries) ? FormulaContexts.forEntity(entity) : null;
        for (Entry entry : entries) apply(entity, entry, context);
    }

    /**
     * Updates generated attributes without rebuilding unchanged modifiers: dynamic entries are evaluated
     * every tick, static ones are only added when missing. Also repairs attributes lost on player
     * replacement.
     */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        List<Entry> entries = entries(entity);
        Set<Identifier> active = new HashSet<>();
        boolean dynamic = false;
        for (Entry entry : entries) {
            active.add(entry.id());
            dynamic |= entry.definition().value().isPresent();
        }
        removeGenerated(entity, active);
        // A static entry only needs its constant amount, so a tick whose entries are all static
        // never builds a formula context.
        FormulaContext context = dynamic ? FormulaContexts.forEntity(entity) : null;
        for (Entry entry : entries) apply(entity, entry, context);
    }

    private static boolean dynamic(List<Entry> entries) {
        for (Entry entry : entries) if (entry.definition().value().isPresent()) return true;
        return false;
    }

    private static List<Entry> entries(LivingEntity entity) {
        List<Entry> entries = new ArrayList<>();
        Map<Identifier, Integer> abilityIndices = new HashMap<>();
        for (ResolvedModifier value : AbilityModifierService.resolve(entity, entity.getData(MxtAttachments.ABILITY_HOLDER))) {
            int index = abilityIndices.merge(value.ability(), 1, Integer::sum) - 1;
            AttributeEntry definition = value.modifier();
            entries.add(new Entry("ability", value.ability(), index, definition,
                    modifierId("ability", value.ability(), index, definition.modifier())));
        }

        CultivationAttachment cultivation = entity.getData(MxtAttachments.CULTIVATION);
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        for (Holder<RealmStage> realm : cultivation.realmStages().values())
            addAll(entries, "realm", HolderHelper.id(realm), realm.value().passiveModifiers());
        for (Holder<Technique> technique : spirit.learnedTechniques())
            addAll(entries, "technique", HolderHelper.id(technique), technique.value().passiveModifiers());
        for (Holder<Physique> physique : spirit.activePhysiques())
            addAll(entries, "physique", HolderHelper.id(physique), physique.value().attributeModifiers());
        return entries;
    }

    private static void removeGenerated(LivingEntity entity, Set<Identifier> active) {
        for (AttributeInstance instance : modifierHolders(entity)) {
            instance.getModifiers().stream().filter(modifier -> modifier.id().getNamespace().equals(MiXianTu.MOD_ID)
                            && modifier.id().getPath().startsWith(PREFIX)
                            && !active.contains(modifier.id())).map(AttributeModifier::id).toList()
                    .forEach(instance::removeModifier);
        }
    }

    /**
     * Every attribute of this entity a generated modifier could be sitting on: the ones vanilla syncs, plus the
     * ones content has written to before. An attribute the entity's type does not have is skipped rather than
     * created, so asking costs a lookup and never grows the entity's attribute map.
     */
    private static List<AttributeInstance> modifierHolders(LivingEntity entity) {
        List<AttributeInstance> holders = new ArrayList<>(entity.getAttributes().getSyncableAttributes());
        for (Holder<Attribute> attribute : WRITTEN_ATTRIBUTES) {
            if (!entity.getAttributes().hasAttribute(attribute)) continue;
            AttributeInstance instance = entity.getAttributes().getInstance(attribute);
            if (instance != null && !holders.contains(instance)) holders.add(instance);
        }
        return holders;
    }

    private static void addAll(List<Entry> target, String kind, Identifier source, List<AttributeEntry> values) {
        for (int index = 0; index < values.size(); index++) {
            AttributeEntry definition = values.get(index);
            target.add(new Entry(kind, source, index, definition,
                    modifierId(kind, source, index, definition.modifier())));
        }
    }

    private static void apply(LivingEntity entity, Entry entry, @Nullable FormulaContext context) {
        AttributeEntry definition = entry.definition();
        Holder<Attribute> attribute = definition.attribute();
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        final double amount;
        try {
            amount = context == null ? definition.modifier().amount() : definition.amount(context);
        } catch (RuntimeException ignored) {
            return;
        }
        if (!Double.isFinite(amount)) return;
        Identifier id = entry.id();
        AttributeModifier current = instance.getModifier(id);
        if (current == null || Double.compare(current.amount(), amount) != 0
                || current.operation() != definition.modifier().operation()) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, definition.modifier().operation()));
            // Recorded only where a modifier is really written, so the cleanup pass knows where to look for it.
            WRITTEN_ATTRIBUTES.add(attribute);
        }
    }

    /**
     * The generated modifier id. It is computed while the entry is collected, not on every tick.
     */
    private static Identifier modifierId(String kind, Identifier source, int index, AttributeModifier definition) {
        String origin = source.getNamespace() + "/" + source.getPath();
        String original = definition.id().getNamespace() + "/" + definition.id().getPath();
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID,
                PREFIX + kind + "/" + origin + "/" + index + "/" + original);
    }

    private record Entry(String kind, Identifier source, int index, AttributeEntry definition, Identifier id) {
    }
}
