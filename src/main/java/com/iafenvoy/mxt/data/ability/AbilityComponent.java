package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.ability.AbilityComponent.Charges;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Cooldown;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Empty;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Resource;
import com.iafenvoy.mxt.data.ability.AbilityComponent.TargetLock;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Timer;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Toggle;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.function.Function;

/**
 * Declarative, code-owned state component attached to one granted ability.
 */
public sealed interface AbilityComponent permits Cooldown, Charges,
        Toggle, Timer, Resource, TargetLock,
        Empty {
    Codec<AbilityComponent> CODEC = MxtTypeRegistries.ABILITY_COMPONENT_TYPE.byNameCodec().dispatch("type", AbilityComponent::codec, Function.identity());

    /**
     * Stable component state key. One ability may use at most one component for a key.
     */
    String key();

    MapCodec<? extends AbilityComponent> codec();

    record Cooldown(NumberProvider ticks) implements AbilityComponent {
        public static final MapCodec<Cooldown> CODEC = NumberProvider.CODEC.fieldOf("ticks").xmap(Cooldown::new, Cooldown::ticks);

        @Override
        public String key() {
            return "cooldown";
        }

        @Override
        public MapCodec<Cooldown> codec() {
            return CODEC;
        }
    }

    record Charges(NumberProvider maximum, NumberProvider rechargeTicks) implements AbilityComponent {
        public static final MapCodec<Charges> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                NumberProvider.CODEC.fieldOf("maximum").forGetter(Charges::maximum),
                NumberProvider.CODEC.fieldOf("recharge_ticks").forGetter(Charges::rechargeTicks)
        ).apply(instance, Charges::new));

        @Override
        public String key() {
            return "charges";
        }

        @Override
        public MapCodec<Charges> codec() {
            return CODEC;
        }
    }

    record Toggle(boolean defaultValue) implements AbilityComponent {
        public static final MapCodec<Toggle> CODEC = Codec.BOOL.optionalFieldOf("default", false).xmap(Toggle::new, Toggle::defaultValue);

        @Override
        public String key() {
            return "toggle";
        }

        @Override
        public MapCodec<Toggle> codec() {
            return CODEC;
        }
    }

    record Timer(NumberProvider duration) implements AbilityComponent {
        public static final MapCodec<Timer> CODEC = NumberProvider.CODEC.fieldOf("duration").xmap(Timer::new, Timer::duration);

        @Override
        public String key() {
            return "timer";
        }

        @Override
        public MapCodec<Timer> codec() {
            return CODEC;
        }
    }

    record Resource(Holder<com.iafenvoy.mxt.data.resource.Resource> resource) implements AbilityComponent {
        public static final MapCodec<Resource> CODEC = com.iafenvoy.mxt.data.resource.Resource.CODEC.fieldOf("resource").xmap(Resource::new, Resource::resource);

        @Override
        public String key() {
            return "resource";
        }

        @Override
        public MapCodec<Resource> codec() {
            return CODEC;
        }
    }

    record TargetLock(NumberProvider range) implements AbilityComponent {
        public static final MapCodec<TargetLock> CODEC = NumberProvider.CODEC.fieldOf("range").xmap(TargetLock::new, TargetLock::range);

        @Override
        public String key() {
            return "target_lock";
        }

        @Override
        public MapCodec<TargetLock> codec() {
            return CODEC;
        }
    }

    enum Empty implements AbilityComponent {
        INSTANCE;
        public static final MapCodec<Empty> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public String key() {
            return "empty";
        }

        @Override
        public MapCodec<Empty> codec() {
            return CODEC;
        }
    }
}
