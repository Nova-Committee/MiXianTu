package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Constant;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Current;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Maximum;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Missing;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Regen;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/**
 * Resolves a resource-derived value at runtime without exposing attachment mutation to data.
 */
public sealed interface ResourceValueProvider permits Current, Maximum, Regen, Missing, Constant {
    Codec<ResourceValueProvider> CODEC = MxtTypeRegistries.RESOURCE_VALUE_PROVIDER_TYPE.byNameCodec().dispatch("type", ResourceValueProvider::codec, Function.identity());

    double resolve(ResourceHolderData holder, Identifier resource, Resource definition, FormulaContext context);

    MapCodec<? extends ResourceValueProvider> codec();

    enum Current implements ResourceValueProvider {
        INSTANCE;
        public static final MapCodec<Current> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public double resolve(ResourceHolderData holder, Identifier resource, Resource definition, FormulaContext context) {
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
        public double resolve(ResourceHolderData holder, Identifier resource, Resource definition, FormulaContext context) {
            return definition.max().evaluate(context);
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
        public double resolve(ResourceHolderData holder, Identifier resource, Resource definition, FormulaContext context) {
            return definition.regen().evaluate(context);
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
        public double resolve(ResourceHolderData holder, Identifier resource, Resource definition, FormulaContext context) {
            return Math.max(0.0D, definition.max().evaluate(context) - holder.get(resource));
        }

        @Override
        public MapCodec<Missing> codec() {
            return CODEC;
        }
    }

    record Constant(NumberProvider value) implements ResourceValueProvider {
        public static final MapCodec<Constant> CODEC = NumberProvider.CODEC.fieldOf("value").xmap(Constant::new, Constant::value);

        @Override
        public double resolve(ResourceHolderData holder, Identifier resource, Resource definition, FormulaContext context) {
            return this.value.evaluate(context);
        }

        @Override
        public MapCodec<Constant> codec() {
            return CODEC;
        }
    }
}
