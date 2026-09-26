package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.runtime.wheel.WheelEntryKinds;
import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilitySources;
import com.iafenvoy.mxt.runtime.ability.AbilityStorage;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.api.WheelEntryKind;
import com.iafenvoy.mxt.runtime.wheel.WheelSources;
import com.iafenvoy.mxt.screen.wheel.WheelDuration;
import com.iafenvoy.mxt.screen.wheel.WheelSelection;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One pressable ability as a wheel entry: the wheel sees a name, icon, cooldown and trigger, and an ability that
 * acts on an item carries the stack it came from. A state is drawn when the ability reports one, which is how a
 * switch and a one-shot look different without the wheel knowing what either of them is.
 */
public record AbilityWheelEntry(Holder<Ability> ability, @Nullable ItemStack carrier,
                                Optional<Boolean> state) implements WheelMenuEntry {
    private static final int ACCENT_ON = 0xFF7BD37B;
    private static final int ACCENT_OFF = 0xFF8A8F9A;
    private static final int ACCENT_READY = 0xFFB08CE8;
    private static final int ACCENT_PLAIN = 0xFFFFD24A;

    @Override
    public WheelEntryKind kind() {
        return WheelEntryKinds.ABILITY;
    }

    @Override
    public Identifier id() {
        return HolderHelper.id(this.ability);
    }

    @Override
    public Component title() {
        return this.ability.value().name();
    }

    @Override
    public Optional<IconReference> icon() {
        return this.ability.value().icon();
    }

    @Override
    public int accentColor() {
        if (!(this.ability.value().type() instanceof Togglable)) return ACCENT_PLAIN;
        return this.state.map(on -> on ? ACCENT_ON : ACCENT_OFF).orElse(ACCENT_READY);
    }

    @Override
    public long cooldownTicks(@Nullable Player player) {
        if (player == null) return 0L;
        AbilityAttachment holder = player.getExistingData(MxtAttachments.ABILITY_HOLDER).orElse(null);
        if (holder == null) return 0L;
        return AbilityStorage.remaining(holder, HolderHelper.id(this.ability), player.level().getGameTime());
    }

    // The length the last use actually got: the declared cooldown field can be overridden by a stored
    // mxt:cooldown, and only the payment knows the number the display has to draw the sheet against.
    @Override
    public long cooldownLength(@Nullable Player player) {
        if (player == null) return 0L;
        AbilityAttachment holder = player.getExistingData(MxtAttachments.ABILITY_HOLDER).orElse(null);
        if (holder == null) return 0L;
        return AbilityStorage.get(holder, HolderHelper.id(this.ability), CooldownDataStorage.class)
                .flatMap(CooldownDataStorage::duration)
                .map(Math::round)
                .orElse(0L);
    }

    @Override
    public List<Component> tooltip(@Nullable Player player) {
        List<Component> lines = new ArrayList<>(6);
        lines.add(this.kind().displayName().copy().withStyle(ChatFormatting.GRAY));
        lines.add(this.title().copy().withStyle(ChatFormatting.WHITE));
        List<Cost> costs = this.ability.value().costs();
        if (player != null && !costs.isEmpty())
            lines.add(Component.translatable("wheel.mxt.tooltip.cost", WheelTooltips.costs(costs, player)));
        this.state.ifPresent(on -> lines.add(Component.translatable(
                on ? "wheel.mxt.tooltip.state_on" : "wheel.mxt.tooltip.state_off")));
        if (player == null) return lines;
        FormulaContext context = FormulaContext.of(player);
        double cooldown = this.ability.value().cooldown().evaluate(context);
        if (cooldown > 0.0D)
            lines.add(Component.translatable("wheel.mxt.tooltip.cooldown", WheelDuration.seconds(cooldown)));
        double castTime = this.ability.value().castTime().evaluate(context);
        if (castTime > 0.0D)
            lines.add(Component.translatable("wheel.mxt.tooltip.cast_time", WheelDuration.seconds(castTime)));
        if (!this.ability.value().elementAffinity().isEmpty())
            lines.add(Component.translatable("wheel.mxt.tooltip.element",
                    WheelTooltips.elements(this.ability.value().elementAffinity())));
        // Last, because it answers "where did this come from": the thing that keeps it, not the thing that happens
        // to declare it as well.
        Component origin = this.origin(player);
        if (origin != null) lines.add(origin);
        return lines;
    }

    // A taught skill says so even when a carried item declares it too, since learning is what survives putting the
    // item down. An item's own page then reads as that item, and anything else stays unclaimed rather than guessed.
    private @Nullable Component origin(Player player) {
        Set<Identifier> sources = WheelSources.sources(player, HolderHelper.id(this.ability));
        if (sources.stream().anyMatch(AbilitySources::isLearned))
            return Component.translatable("wheel.mxt.tooltip.source.learned");
        ItemStack carrier = this.carrier;
        if (carrier != null && !carrier.isEmpty())
            return Component.translatable("wheel.mxt.tooltip.source.item", itemName(player, carrier));
        return sources.isEmpty() ? null : Component.translatable("wheel.mxt.tooltip.source.other");
    }

    // The artifact's own name when the stack declares one, so the line names the artifact rather than the item it
    // happens to be made of. It carries the tier's colour when the pack gave one, and gold otherwise, because a
    // name that is not tinted reads as part of the sentence rather than as the name.
    private static Component itemName(Player player, ItemStack carrier) {
        RegistryAccess registries = player.level().registryAccess();
        Holder<Artifact> definition = ArtifactService.definition(registries, carrier).orElse(null);
        Component name = definition == null ? carrier.getHoverName() : DefinitionText.name(definition);
        Holder<ItemQuality> quality = ItemQualityService.find(registries, carrier)
                .filter(holder -> holder.value().color().isPresent()).orElse(null);
        return quality == null ? name.copy().withStyle(ChatFormatting.GOLD) : ItemQualityService.coloredName(quality, name);
    }

    @Override
    public void onSelected(WheelSelection selection) {
        WheelTrigger.send(this, selection.source());
    }
}