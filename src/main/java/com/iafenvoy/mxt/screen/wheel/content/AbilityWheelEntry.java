package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKind;
import com.iafenvoy.mxt.screen.wheel.WheelDuration;
import com.iafenvoy.mxt.screen.wheel.WheelSelection;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One active ability as a wheel entry: the wheel sees a name, icon, cooldown and trigger, while the grant the
 * server checks before honouring that trigger stays in this definition.
 */
public record AbilityWheelEntry(Identifier id, Ability definition) implements WheelMenuEntry {
    private static final int ACCENT = 0xFFFFD24A;

    @Override
    public WheelEntryKind kind() {
        return WheelEntryKind.ABILITY;
    }

    @Override
    public Component title() {
        return DefinitionText.name(this.id, "ability");
    }

    @Override
    public Optional<IconReference> icon() {
        return this.definition.icon();
    }

    @Override
    public int accentColor() {
        return ACCENT;
    }

    /** Ticks of cooldown left, or 0 when the player no longer holds the ability and so has none to report. */
    @Override
    public long cooldownTicks(Player player) {
        if (player == null) return 0L;
        AbilityAttachment holder = player.getExistingData(MxtAttachments.ABILITY_HOLDER).orElse(null);
        if (holder == null) return 0L;
        Holder<Ability> ability = holder.sources().keys().stream()
                .filter(value -> HolderHelper.id(value).equals(this.id)).findFirst().orElse(null);
        if (ability == null) return 0L;
        return Math.max(0L, holder.cooldowns().getOrDefault(ability, 0L) - player.level().getGameTime());
    }

    /** Kind, name and the definition's numbers; lines whose value is zero are left out. */
    @Override
    public List<Component> tooltip(Player player) {
        List<Component> lines = new ArrayList<>(5);
        lines.add(this.kind().displayName().copy().withStyle(ChatFormatting.GRAY));
        lines.add(this.title().copy().withStyle(ChatFormatting.WHITE));
        if (player == null) return lines;
        FormulaContext context = FormulaContext.of(player);
        if (!this.definition.costs().isEmpty())
            lines.add(Component.translatable("wheel.mxt.tooltip.cost", WheelTooltips.costs(this.definition.costs(), player)));
        double cooldown = this.definition.cooldown().evaluate(context);
        if (cooldown > 0.0D)
            lines.add(Component.translatable("wheel.mxt.tooltip.cooldown", WheelDuration.seconds(cooldown)));
        double castTime = this.definition.castTime().evaluate(context);
        if (castTime > 0.0D)
            lines.add(Component.translatable("wheel.mxt.tooltip.cast_time", WheelDuration.seconds(castTime)));
        if (!this.definition.elementAffinity().isEmpty())
            lines.add(Component.translatable("wheel.mxt.tooltip.element", WheelTooltips.elements(this.definition.elementAffinity())));
        return lines;
    }

    @Override
    public void onSelected(WheelSelection selection) {
        WheelTrigger.send(this, selection.source());
    }
}
