package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.SpiritAttachment;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.Title;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityModifierService.ResolvedModifier;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.HolderHelper;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies all datapack-defined passive modifiers as removable transient vanilla attributes.
 */
@EventBusSubscriber
public final class PassiveAttributeService {
    private static final String PREFIX = "passive/";

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
     * Reconciles the full generated modifier set. Call only on the server thread.
     */
    public static void reconcile(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        List<Entry> entries = entries(entity);
        removeGenerated(entity, Set.of());
        FormulaContext context = FormulaContexts.forEntity(entity);
        for (Entry entry : entries) apply(entity, entry, context);
    }

    /**
     * Updates generated attributes without rebuilding unchanged modifiers. Dynamic
     * entries are evaluated on every server tick; static entries are only added
     * when missing. This also repairs attributes lost during player replacement.
     */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        List<Entry> entries = entries(entity);
        Set<Identifier> active = new HashSet<>();
        for (Entry entry : entries) active.add(modifierId(entry));
        removeGenerated(entity, active);
        FormulaContext context = FormulaContexts.forEntity(entity);
        for (Entry entry : entries) apply(entity, entry, context);
    }

    private static List<Entry> entries(LivingEntity entity) {
        List<Entry> entries = new ArrayList<>();
        Map<Identifier, Integer> abilityIndices = new HashMap<>();
        for (ResolvedModifier value : AbilityModifierService.resolve(entity.getData(MxtAttachments.ABILITY_HOLDER))) {
            int index = abilityIndices.merge(value.ability(), 1, Integer::sum) - 1;
            entries.add(new Entry("ability", value.ability(), index, value.modifier()));
        }

        SpiritAttachment spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        spirit.realmStage().ifPresent(realm -> addAll(entries, "realm", HolderHelper.id(realm), realm.value().passiveModifiers()));
        spirit.activeTechnique().ifPresent(technique -> addAll(entries, "technique", HolderHelper.id(technique), technique.value().passiveModifiers()));
        for (Holder<Physique> physique : spirit.physiques())
            addAll(entries, "physique", HolderHelper.id(physique), physique.value().attributeModifiers());
        for (Holder<Title> title : spirit.titles())
            addAll(entries, "title", HolderHelper.id(title), title.value().passiveModifiers());
        return entries;
    }

    private static void removeGenerated(LivingEntity entity, Set<Identifier> active) {
        for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes()) {
            instance.getModifiers().stream().filter(modifier -> modifier.id().getNamespace().equals(MiXianTu.MOD_ID)
                            && modifier.id().getPath().startsWith(PREFIX)
                            && !active.contains(modifier.id())).map(AttributeModifier::id).toList()
                    .forEach(instance::removeModifier);
        }
    }

    private static void addAll(List<Entry> target, String kind, Identifier source, List<AttributeEntry> values) {
        for (int index = 0; index < values.size(); index++)
            target.add(new Entry(kind, source, index, values.get(index)));
    }

    private static void apply(LivingEntity entity, Entry entry, FormulaContext context) {
        AttributeEntry definition = entry.definition();
        Holder<Attribute> attribute = definition.attribute();
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        final double amount;
        try {
            amount = definition.amount(context);
        } catch (RuntimeException ignored) {
            return;
        }
        if (!Double.isFinite(amount)) return;
        Identifier id = modifierId(entry);
        AttributeModifier current = instance.getModifier(id);
        if (current == null || Double.compare(current.amount(), amount) != 0
                || current.operation() != definition.modifier().operation())
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, definition.modifier().operation()));
    }

    private static Identifier modifierId(Entry entry) {
        AttributeModifier definition = entry.definition().modifier();
        String source = entry.source().getNamespace() + "/" + entry.source().getPath();
        String original = definition.id().getNamespace() + "/" + definition.id().getPath();
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID,
                PREFIX + entry.kind() + "/" + source + "/" + entry.index() + "/" + original);
    }

    private record Entry(String kind, Identifier source, int index, AttributeEntry definition) {
    }
}
