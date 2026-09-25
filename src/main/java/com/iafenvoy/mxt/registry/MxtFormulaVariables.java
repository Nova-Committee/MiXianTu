package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.cultivation.LifeSpanService;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.world.SecretRealmRecord;
import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContext.ResourceSubject;
import com.iafenvoy.mxt.util.formula.FormulaNames;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The built-in formula variables: every entry decomposes a number out of the objects a {@link FormulaContext}
 * carries, while values that belong to no object (damage, a block position, an event payload) stay in the
 * context's explicit value map. A code registry, so data packs cannot add variables.
 */
@SuppressWarnings("unused")
public final class MxtFormulaVariables {
    public static final DeferredRegister<FormulaVariable> REGISTRY = DeferredRegister.create(MxtRegistries.FORMULA_VARIABLE, MiXianTu.MOD_ID);

    public static final DeferredHolder<FormulaVariable, FormulaVariable> ZERO = REGISTRY.register("zero", ZeroVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> RANDOM = REGISTRY.register("random", RandomVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> CASTER = REGISTRY.register("caster", () -> new EntityVariable("caster_"));
    public static final DeferredHolder<FormulaVariable, FormulaVariable> TARGET = REGISTRY.register("target", () -> new EntityVariable("target_"));
    public static final DeferredHolder<FormulaVariable, FormulaVariable> REALM = REGISTRY.register("realm", RealmVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> SECRET_REALM = REGISTRY.register("secret_realm", SecretRealmVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> LIFESPAN = REGISTRY.register("lifespan", LifespanVariable::new);

    // For formulas that must switch a term off without editing the expression.
    private record ZeroVariable() implements FormulaVariable {
        private static final Set<String> NAMES = Set.of("zero");

        @Override
        public Set<String> names() {
            return NAMES;
        }

        @Override
        public double value(String key, String suffix, FormulaContext context) {
            return suffix.isEmpty() ? 0.0D : Double.NaN;
        }
    }

    private record RandomVariable() implements FormulaVariable {
        private static final Set<String> NAMES = Set.of("random");

        @Override
        public Set<String> names() {
            return NAMES;
        }

        @Override
        public double value(String key, String suffix, FormulaContext context) {
            return suffix.isEmpty() ? context.random().nextDouble() : Double.NaN;
        }
    }

    // Health, vanilla experience level, one name per resource and per attribute the entity has, and one name per
    // element answering 1 or 0: all a formula may ask about an element is whether this entity is one of its
    // cultivators - the relations themselves are the damage pipeline's business, read the same way there.
    private static final class EntityVariable implements FormulaVariable {
        private static final Map<EntityType<?>, Set<Holder<Attribute>>> SYNCABLE_ATTRIBUTES = new ConcurrentHashMap<>();

        private final String prefix;
        private final Set<String> prefixes;

        private EntityVariable(String prefix) {
            this.prefix = prefix;
            this.prefixes = Set.of(prefix);
        }

        @Override
        public Set<String> names() {
            return Set.of();
        }

        @Override
        public Set<String> prefixes() {
            return this.prefixes;
        }

        @Override
        public double value(String key, String suffix, FormulaContext context) {
            Entity entity = this.prefix.equals(key) ? context.caster() : context.target();
            if (entity == null) return Double.NaN;
            return switch (suffix) {
                case "health" -> entity instanceof LivingEntity living ? (double) living.getHealth() : Double.NaN;
                case "max_health" ->
                        entity instanceof LivingEntity living ? (double) living.getMaxHealth() : Double.NaN;
                case "level" -> entity instanceof ServerPlayer player ? (double) player.experienceLevel : 0.0D;
                case "element_count" -> (double) Elements.of(entity).size();
                default -> state(entity, suffix);
            };
        }

        // Attributes are asked first, because the context used to expose resources before attributes.
        private static double state(Entity entity, String field) {
            if (!(entity instanceof LivingEntity living)) return Double.NaN;
            Holder<Attribute> attribute = FormulaNames.attribute(field);
            if (attribute != null) {
                AttributeInstance instance = living.getAttributes().getInstance(attribute);
                if (instance == null) return 0.0D;
                if (living.level().isClientSide() && !synchronised(living, attribute)) return 0.0D;
                return instance.getValue();
            }
            Holder<Resource> resource = FormulaNames.resource(living.level().registryAccess(), field);
            if (resource != null) return living.getData(MxtAttachments.RESOURCE_HOLDER).get(resource);
            Holder<Element> element = FormulaNames.element(living.level().registryAccess(), field);
            if (element == null) return Double.NaN;
            return Elements.of(living).contains(element) ? 1.0D : 0.0D;
        }

        // The client only holds the attributes it needs, so a formula must not read a hidden attribute as if it
        // were up to date.
        private static boolean synchronised(LivingEntity entity, Holder<Attribute> attribute) {
            Set<Holder<Attribute>> syncable = SYNCABLE_ATTRIBUTES.get(entity.getType());
            if (syncable == null) {
                Set<Holder<Attribute>> collected = new HashSet<>();
                for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes())
                    collected.add(instance.getAttribute());
                syncable = Set.copyOf(collected);
                SYNCABLE_ATTRIBUTES.put(entity.getType(), syncable);
            }
            return syncable.contains(attribute);
        }
    }

    // The cultivation state of the resource the formula is evaluated for. These names exist only in a resource
    // context, which is why {@code level} means a realm rank here while {@code caster_level} stays the vanilla
    // experience level. {@code minor_stage} is the 0-based position inside the current stage's own minor stages,
    // and unlike {@code realm} it answers NaN for a mortal, who stands on no stage there is to cut.
    private static final class RealmVariable implements FormulaVariable {
        private static final Set<String> NAMES = Set.of("realm", "realm_rank", "level", "absorbed_aura", "cultivation_progress", "minor_stage");

        @Override
        public Set<String> names() {
            return NAMES;
        }

        @Override
        public double value(String key, String suffix, FormulaContext context) {
            if (!suffix.isEmpty()) return Double.NaN;
            ResourceSubject subject = context.resource();
            if (subject == null) return Double.NaN;
            Holder<Aura> aura = AuraLookup
                    .holder(AuraLookup.access(context), subject.resource()).orElse(null);
            if (key.equals("minor_stage"))
                return aura == null ? Double.NaN : CultivationService.minorStage(aura, subject.cultivation(), context);
            int rank = aura == null ? -1 : ResourceService.realmRank(subject.cultivation(), aura);
            if (key.equals("absorbed_aura") || key.equals("cultivation_progress"))
                return rank < 0 ? 0.0D : subject.cultivation().cultivationProgress(aura);
            return Math.max(0, rank);
        }
    }

    // The state of the secret realm the subject is inside. The names carry the {@code secret_realm_} prefix
    // because {@code realm} already means a cultivation stage and both can be read in one expression; every name
    // answers NaN outside a secret realm, so a condition can tell "not in a secret realm" from "in an empty one".
    private static final class SecretRealmVariable implements FormulaVariable {        private static final Set<String> NAMES = Set.of("secret_realm_members", "secret_realm_limit",
                "secret_realm_elapsed", "secret_realm_duration", "secret_realm_index", "secret_realm_is_owner");

        @Override
        public Set<String> names() {
            return NAMES;
        }

        @Override
        public double value(String key, String suffix, FormulaContext context) {
            if (!suffix.isEmpty()) return Double.NaN;
            Entity entity = context.caster() != null ? context.caster() : context.player();
            if (entity == null) return Double.NaN;
            SecretRealmRecord record = SecretRealmRegistry.ofMember(entity.getUUID()).orElse(null);
            if (record == null) return Double.NaN;
            return switch (key) {
                case "secret_realm_members" -> record.members().size();
                case "secret_realm_limit" -> record.instance().maxMembers().orElse(-1);
                case "secret_realm_elapsed" -> record.startedAt() < 0L ? 0.0D
                        : Math.max(0.0D, entity.level().getGameTime() - record.startedAt());
                case "secret_realm_duration" -> record.instance().durationTicks();
                case "secret_realm_index" -> record.index();
                case "secret_realm_is_owner" -> record.isOwner(entity.getUUID()) ? 1.0D : 0.0D;
                default -> Double.NaN;
            };
        }
    }

    // How much life the caster has left and what its ceiling is, in ticks. Both answer NaN for a body with no
    // ledger, so a condition can tell "never accounted for" from "accounted for and spent".
    private static final class LifespanVariable implements FormulaVariable {
        private static final Set<String> NAMES = Set.of("lifespan_remaining", "lifespan_total");

        @Override
        public Set<String> names() {
            return NAMES;
        }

        @Override
        public double value(String key, String suffix, FormulaContext context) {
            if (!suffix.isEmpty()) return Double.NaN;
            Entity entity = context.caster() != null ? context.caster() : context.player();
            if (entity == null) return Double.NaN;
            long value = key.equals("lifespan_remaining")
                    ? LifeSpanService.remaining(entity) : LifeSpanService.total(entity);
            return value < 0L ? Double.NaN : value;
        }
    }
}
