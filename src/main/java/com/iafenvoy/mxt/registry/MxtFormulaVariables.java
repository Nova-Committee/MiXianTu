package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.cultivation.CultivationProfile;
import com.iafenvoy.mxt.runtime.cultivation.CultivationProfiles;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
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
 * The built-in formula variables.
 *
 * <p>Every entry decomposes a number out of the objects a {@link FormulaContext} carries.
 * Values that belong to no object — damage, a block position, an event payload — are not
 * variables; callers keep those in the context's explicit value map.</p>
 *
 * <p>This is a code registry, so data packs cannot add variables. The two entity families claim
 * a prefix ({@code caster_} and {@code target_}) and receive only the remainder of the name, so a
 * name such as {@code caster_mxt_common} arrives here as {@code mxt_common}. Flattened resource
 * and attribute names are indexed by {@link FormulaNames}.</p>
 */
@SuppressWarnings("unused")
public final class MxtFormulaVariables {
    public static final DeferredRegister<FormulaVariable> REGISTRY = DeferredRegister.create(MxtRegistries.FORMULA_VARIABLE, MiXianTu.MOD_ID);

    public static final DeferredHolder<FormulaVariable, FormulaVariable> ZERO = REGISTRY.register("zero", ZeroVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> RANDOM = REGISTRY.register("random", RandomVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> CASTER = REGISTRY.register("caster", () -> new EntityVariable("caster_"));
    public static final DeferredHolder<FormulaVariable, FormulaVariable> TARGET = REGISTRY.register("target", () -> new EntityVariable("target_"));
    public static final DeferredHolder<FormulaVariable, FormulaVariable> REALM = REGISTRY.register("realm", RealmVariable::new);

    /**
     * A plain zero, for formulas that must switch a term off without editing the expression.
     */
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

    /**
     * The authoritative random source of the current evaluation.
     */
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

    /**
     * The {@code caster_} and {@code target_} families: health, vanilla experience level, and one
     * name per resource and per attribute the entity has.
     */
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
                case "max_health" -> entity instanceof LivingEntity living ? (double) living.getMaxHealth() : Double.NaN;
                case "level" -> entity instanceof ServerPlayer player ? (double) player.experienceLevel : 0.0D;
                default -> state(entity, suffix);
            };
        }

        /**
         * Reads one resource or attribute of the entity. Attributes are asked first because the
         * context used to expose resources before attributes, which let an attribute shadow a
         * resource with the same flattened name.
         */
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
            return resource == null ? Double.NaN : living.getData(MxtAttachments.RESOURCE_HOLDER).get(resource);
        }

        /**
         * The client only holds the attributes it needs, so a formula must not read a hidden
         * attribute as if it were up to date. Which attributes those are is a property of the
         * entity type, so the set is collected once per type instead of once per lookup.
         */
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

    /**
     * The cultivation state of the resource the formula is evaluated for. These names exist only
     * in a resource context, which is why {@code level} means a realm rank here while
     * {@code caster_level} stays the vanilla experience level.
     */
    private static final class RealmVariable implements FormulaVariable {
        private static final Set<String> NAMES = Set.of("realm", "realm_rank", "level", "absorbed_aura", "cultivation_progress");

        @Override
        public Set<String> names() {
            return NAMES;
        }

        @Override
        public double value(String key, String suffix, FormulaContext context) {
            if (!suffix.isEmpty()) return Double.NaN;
            ResourceSubject subject = context.resource();
            if (subject == null) return Double.NaN;
            Holder<CultivationProfile> cultivation = CultivationProfiles
                    .holder(CultivationProfiles.access(context), subject.resource()).orElse(null);
            int rank = cultivation == null ? -1 : ResourceService.realmRank(subject.cultivation(), cultivation);
            if (key.equals("absorbed_aura") || key.equals("cultivation_progress"))
                return rank < 0 || cultivation == null ? 0.0D : subject.cultivation().cultivationProgress(cultivation);
            return Math.max(0, rank);
        }
    }
}
