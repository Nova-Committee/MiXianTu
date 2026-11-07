package com.iafenvoy.mxt.runtime.world;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.*;

// Only data already visible to tracking clients belongs on this wire format; the full snapshot stays server-side.
public record SoulAppearance(Identifier entityType, Optional<ResolvableProfile> profile, List<DataValue<?>> metadata,
                             Map<EquipmentSlot, ItemStack> equipment, float width, float height, float scale) {
    public static final StreamCodec<RegistryFriendlyByteBuf, SoulAppearance> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SoulAppearance decode(RegistryFriendlyByteBuf input) {
            Identifier type = input.readIdentifier();
            Optional<ResolvableProfile> profile = input.readBoolean() ? Optional.of(ResolvableProfile.STREAM_CODEC.decode(input)) : Optional.empty();
            int count = input.readVarInt();
            if (count < 0 || count > 255) throw new DecoderException("Invalid soul metadata size: " + count);
            List<DataValue<?>> values = new ArrayList<>(count);
            for (int i = 0; i < count; i++) values.add(DataValue.read(input, input.readUnsignedByte()));
            Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : EquipmentSlot.VALUES)
                equipment.put(slot, ItemStack.OPTIONAL_STREAM_CODEC.decode(input));
            float width = input.readFloat(), height = input.readFloat(), scale = input.readFloat();
            if (!validDimension(width) || !validDimension(height) || !validDimension(scale))
                throw new DecoderException("Invalid soul dimensions");
            return new SoulAppearance(type, profile, values, equipment, width, height, scale);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf output, SoulAppearance value) {
            output.writeIdentifier(value.entityType);
            output.writeBoolean(value.profile.isPresent());
            value.profile.ifPresent(profile -> ResolvableProfile.STREAM_CODEC.encode(output, profile));
            output.writeVarInt(value.metadata.size());
            value.metadata.forEach(data -> data.write(output));
            for (EquipmentSlot slot : EquipmentSlot.VALUES)
                ItemStack.OPTIONAL_STREAM_CODEC.encode(output, value.equipment.getOrDefault(slot, ItemStack.EMPTY));
            output.writeFloat(value.width);
            output.writeFloat(value.height);
            output.writeFloat(value.scale);
        }
    };

    public SoulAppearance {
        metadata = metadata.stream().<DataValue<?>>map(SoulAppearance::copyValue).toList();
        Map<EquipmentSlot, ItemStack> copied = new EnumMap<>(EquipmentSlot.class);
        equipment.forEach((slot, stack) -> copied.put(slot, stack.copy()));
        equipment = Map.copyOf(copied);
    }

    @Override
    public List<DataValue<?>> metadata() {
        return this.metadata.stream().<DataValue<?>>map(SoulAppearance::copyValue).toList();
    }

    @Override
    public Map<EquipmentSlot, ItemStack> equipment() {
        Map<EquipmentSlot, ItemStack> copied = new EnumMap<>(EquipmentSlot.class);
        this.equipment.forEach((slot, stack) -> copied.put(slot, stack.copy()));
        return copied;
    }

    public static SoulAppearance capture(LivingEntity entity, SoulSnapshot snapshot) {
        List<DataValue<?>> values = entity.getEntityData().getNonDefaultValues();
        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.VALUES) equipment.put(slot, entity.getItemBySlot(slot));
        return new SoulAppearance(snapshot.entityType(), snapshot.profile(), values == null ? List.of() : values,
                equipment, snapshot.width(), snapshot.height(), snapshot.scale());
    }

    public static SoulAppearance fallback(SoulSnapshot snapshot) {
        return new SoulAppearance(snapshot.entityType(), snapshot.profile(), List.of(), Map.of(),
                snapshot.width(), snapshot.height(), snapshot.scale());
    }

    private static <T> DataValue<T> copyValue(DataValue<T> value) {
        return new SynchedEntityData.DataValue<>(value.id(), value.serializer(), value.serializer().copy(value.value()));
    }

    private static boolean validDimension(float value) {
        return Float.isFinite(value) && value >= 0.01F && value <= 1024.0F;
    }
}
