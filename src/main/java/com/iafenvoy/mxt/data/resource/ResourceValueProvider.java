package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Constant;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Current;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Maximum;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Missing;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Regen;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;

import java.util.function.Function;

/**
 * Resolves a resource-derived value at runtime without exposing attachment mutation to data.
 */
public sealed interface ResourceValueProvider permits Current, Maximum, Regen, Missing, Constant, JsResourceValueProvider {
    Codec<ResourceValueProvider> CODEC = MxtRegistries.RESOURCE_VALUE_PROVIDER_TYPE.byNameCodec().dispatch("type", ResourceValueProvider::codec, Function.identity());

    double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context);

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
