package com.iafenvoy.mxt.util.chain;

import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * One walked line, low to high. A line carries no name of its own: whatever the domain knows a line by is the
 * domain's to keep, since only the domain can say what a line is called.
 */
public record Chain<T>(List<Holder<T>> nodes) {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static <T> Chain<T> empty() {
        return new Chain<>(List.of());
    }

    public int size() {
        return this.nodes.size();
    }

    public Holder<T> first() {
        return this.nodes.getFirst();
    }

    public Holder<T> last() {
        return this.nodes.getLast();
    }

    public Optional<Holder<T>> at(int index) {
        return index < 0 || index >= this.nodes.size() ? Optional.empty() : Optional.of(this.nodes.get(index));
    }

    // -1 when the entry is not on this line.
    public int indexOf(Holder<T> node) {
        for (int index = 0; index < this.nodes.size(); index++)
            if (Objects.equals(this.nodes.get(index), node))
                return index;
        return -1;
    }

    public boolean contains(Holder<T> node) {
        return this.indexOf(node) >= 0;
    }

    // The step above, empty at the top of the line and for an entry this line does not hold.
    public Optional<Holder<T>> next(Holder<T> node) {
        int index = this.indexOf(node);
        return index < 0 ? Optional.empty() : this.at(index + 1);
    }

    // The same step downwards, empty at the head of the line.
    public Optional<Holder<T>> previous(Holder<T> node) {
        int index = this.indexOf(node);
        return index <= 0 ? Optional.empty() : this.at(index - 1);
    }

    public List<Identifier> ids() {
        return this.nodes.stream().map(HolderHelper::id).toList();
    }

    public void validate(BiPredicate<T, T> predicate) {
        for (int i = 1; i < this.nodes.size(); i++)
            if (!predicate.test(this.nodes.get(i - 1).value(), this.nodes.get(i).value()))
                LOGGER.error("Chain {} is failed validation at {} and {}.", this.ids(), HolderHelper.id(this.nodes.get(i - 1)), HolderHelper.id(this.nodes.get(i)));
    }

    public Component buildText(Holder<T> current) {
        List<? extends Component> entries = this.nodes().stream().map(DefinitionText::text).toList();
        int currentIndex = this.indexOf(current);
        MutableComponent text = Component.empty();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) text.append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY));
            text.append(entries.get(i).copy().withStyle(i == currentIndex ? ChatFormatting.GREEN : i < currentIndex ? ChatFormatting.GRAY : ChatFormatting.WHITE));
        }
        return text;
    }
}
