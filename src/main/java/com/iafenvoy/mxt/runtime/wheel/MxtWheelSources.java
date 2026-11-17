package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.runtime.creature.ContractBells;
import com.iafenvoy.mxt.runtime.wheel.builtin.AuraKind;
import com.iafenvoy.mxt.runtime.wheel.builtin.BehaviorKind;
import com.iafenvoy.saturn.api.wheel.WheelEntryKind;
import com.iafenvoy.saturn.api.wheel.WheelSource;
import com.iafenvoy.saturn.runtime.wheel.WheelEntryKinds;
import com.iafenvoy.saturn.runtime.wheel.WheelSourceTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class MxtWheelSources {
    public static final WheelEntryKind AURA = new AuraKind();
    public static final WheelEntryKind BEHAVIOR = new BehaviorKind();
    public static final WheelSource CONTRACT = new WheelSource() {
        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("mxt", "contract");
        }

        @Override
        public Component displayName() {
            return Component.translatable("wheel.mxt.source.contract");
        }

        @Override
        public boolean configured() {
            return false;
        }

        @Override
        public List<Identifier> grantSources(LivingEntity entity) {
            return List.of();
        }

        @Override
        public List<ItemStack> equipment(LivingEntity entity) {
            return List.of(entity.getMainHandItem(), entity.getOffhandItem());
        }

        @Override
        public boolean offers(LivingEntity entity, WheelEntryKind kind, Identifier id) {
            return kind.is(BEHAVIOR) && ContractBells.selection(entity).map(selection -> selection.offers(id)).orElse(false);
        }
    };

    private MxtWheelSources() {
    }

    public static void register() {
        WheelEntryKinds.register(AURA);
        WheelEntryKinds.register(BEHAVIOR);
        WheelSourceTypes.register(CONTRACT);
    }
}
