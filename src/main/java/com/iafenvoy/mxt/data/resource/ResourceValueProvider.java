package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Constant;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Current;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Maximum;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Missing;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Regen;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.EnvironmentConcentration;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.ActualConcentration;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Resolves a resource-derived value at runtime without exposing attachment mutation to data.
 */
public sealed interface ResourceValueProvider permits Current, Maximum, Regen, Missing, EnvironmentConcentration,
        ActualConcentration, Constant, JsResourceValueProvider {
    Codec<ResourceValueProvider> CODEC = MxtRegistries.RESOURCE_VALUE_PROVIDER_TYPE.byNameCodec().dispatch("type", ResourceValueProvider::codec, Function.identity());

    double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context);

    /**
     * Resolves a value with the owning entity available. Providers that depend on world state
     * override this overload; attachment-backed providers keep the original implementation.
     */
    default double resolve(LivingEntity entity, Holder<Resource> resource, FormulaContext context) {
        return resolve(entity.getData(com.iafenvoy.mxt.registry.MxtAttachments.RESOURCE_HOLDER), resource, context);
    }

    MapCodec<? extends ResourceValueProvider> codec();

    enum Current implements ResourceValueProvider {
        INSTANCE;
        public static final MapCodec<Current> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
            return holder.get(resource);
        }

        @Override
        public MapCodec<Current> codec() {
            return CODEC;
        }
    }

    enum Maximum implements ResourceValueProvider {
        INSTANCE;
        public static final MapCodec<Maximum> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
            return resource.value().max().evaluate(context);
        }

        @Override
        public MapCodec<Maximum> codec() {
            return CODEC;
        }
    }

    enum Regen implements ResourceValueProvider {
        INSTANCE;
        public static final MapCodec<Regen> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
            return resource.value().regen().evaluate(context);
        }

        @Override
        public MapCodec<Regen> codec() {
            return CODEC;
        }
    }

    enum Missing implements ResourceValueProvider {
        INSTANCE;
        public static final MapCodec<Missing> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
            return Math.max(0.0D, resource.value().max().evaluate(context) - holder.get(resource));
        }

        @Override
        public MapCodec<Missing> codec() {
            return CODEC;
        }
    }

    /** Only the environmental template contribution, excluding chunk storage and emitters. */
    enum EnvironmentConcentration implements ResourceValueProvider {
        INSTANCE;
        public static final MapCodec<EnvironmentConcentration> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
            return 0.0D;
        }

        @Override
        public double resolve(LivingEntity entity, Holder<Resource> resource, FormulaContext context) {
            var id = HolderHelper.idOrNull(resource);
            if (id == null) return 0.0D;
            if (entity.level().isClientSide()) {
                return AuraClientState.current().environmentPool(id).amount();
            }
            return AuraService.getSensedAura(entity.level(), entity.blockPosition()).pool(resource).amount();
        }

        @Override
        public MapCodec<EnvironmentConcentration> codec() {
            return CODEC;
        }
    }

    /** The complete resolved concentration, including stored and block/formation contributions. */
    enum ActualConcentration implements ResourceValueProvider {
        INSTANCE;
        public static final MapCodec<ActualConcentration> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
            return 0.0D;
        }

        @Override
        public double resolve(LivingEntity entity, Holder<Resource> resource, FormulaContext context) {
            var id = HolderHelper.idOrNull(resource);
            if (id == null) return 0.0D;
            if (entity.level().isClientSide()) {
                return AuraClientState.current().actualPool(id).amount();
            }
            return AuraService.getPositionAura(entity.level(), entity.blockPosition()).pool(resource).amount();
        }

        @Override
        public MapCodec<ActualConcentration> codec() {
            return CODEC;
        }
    }

    record Constant(NumberProvider value) implements ResourceValueProvider {
        public static final MapCodec<Constant> CODEC = NumberProvider.CODEC.fieldOf("value").xmap(Constant::new, Constant::value);

        @Override
        public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
            return this.value.evaluate(context);
        }

        @Override
        public MapCodec<Constant> codec() {
            return CODEC;
        }
    }
}
