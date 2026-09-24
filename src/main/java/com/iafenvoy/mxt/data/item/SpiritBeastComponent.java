package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The one creature a spirit beast bag holds: its saved data, the type it has to be built back from, and the
 * contract it was under when it went in.
 *
 * <p>The type is stored beside the data rather than inside it, so the saved data keeps exactly what the creature
 * wrote and a release knows what to build; the name, contract type and owner are snapshots, because a tooltip is
 * drawn where nothing may be decoded or looked up.</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public record SpiritBeastComponent(Optional<Identifier> entityType, Optional<CompoundTag> entity,
                                   Optional<Component> name, Optional<Holder<ContractType>> contractType,
                                   Optional<UUID> owner, String ownerName) implements TooltipProvider {
    public static final SpiritBeastComponent EMPTY = new SpiritBeastComponent(Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), "");
    public static final Codec<SpiritBeastComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.optionalFieldOf("entity_type").forGetter(SpiritBeastComponent::entityType),
            CompoundTag.CODEC.optionalFieldOf("entity").forGetter(SpiritBeastComponent::entity),
            ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(SpiritBeastComponent::name),
            RegistryFixedCodec.create(MxtResourceKeys.CONTRACT_TYPE).optionalFieldOf("contract_type").forGetter(SpiritBeastComponent::contractType),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(SpiritBeastComponent::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(SpiritBeastComponent::ownerName)
    ).apply(i, SpiritBeastComponent::new));

    // The data and the type are written together or not at all, so one of them missing is an empty bag as well.
    public boolean stored() {
        return this.entityType.isPresent() && this.entity.isPresent();
    }

    // Attachments ride along in the saved data, which is what carries the creature's contract and its profile
    // through the bag; the name written down here is whatever the creature was called at that moment.
    public static SpiritBeastComponent capture(Mob beast, Holder<ContractType> type, ServerPlayer owner) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, beast.level().registryAccess());
        beast.saveWithoutId(output);
        return new SpiritBeastComponent(Optional.of(BuiltInRegistries.ENTITY_TYPE.getKey(beast.getType())),
                Optional.of(output.buildResult()), Optional.of(beast.getDisplayName()), Optional.of(type),
                Optional.of(owner.getUUID()), owner.getGameProfile().name());
    }

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.SPIRIT_BEAST, TooltipAppender.createComponentAppender(MxtDataComponents.SPIRIT_BEAST.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.spirit_beast_bag.state",
                this.stored() ? Component.translatable("tooltip.mxt.filled") : Component.translatable("tooltip.mxt.empty")));
        if (!this.stored()) return;
        this.name.ifPresent(name -> consumer.accept(Component.translatable("tooltip.mxt.spirit_beast_bag.beast", name).withStyle(ChatFormatting.GRAY)));
        this.contractType.ifPresent(type -> consumer.accept(Component.translatable("tooltip.mxt.spirit_beast_bag.contract",
                DefinitionText.name(type, "contract_type")).withStyle(ChatFormatting.GRAY)));
        // A nameless owner is a name nobody could answer, which is why nothing is shown rather than a blank line.
        if (!this.ownerName.isBlank())
            consumer.accept(Component.translatable("tooltip.mxt.spirit_beast_bag.owner", this.ownerName).withStyle(ChatFormatting.GRAY));
    }
}
