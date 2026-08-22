package com.iafenvoy.mxt.util.matcher.builtin;

import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Pattern;

public record WildcardEntry(String pattern) implements Entry {
    public static final MapCodec<WildcardEntry> CODEC = Codec.STRING.fieldOf("pattern").xmap(WildcardEntry::new, WildcardEntry::pattern);

    public WildcardEntry {
        if (pattern.isEmpty()) throw new IllegalArgumentException("Item matcher wildcard must not be empty");
    }

    @Override
    public boolean matches(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        StringBuilder expression = new StringBuilder("^");
        for (int i = 0; i < this.pattern.length(); i++) {
            char c = this.pattern.charAt(i);
            if (c == '*') expression.append(".*");
            else if (c == '?') expression.append('.');
            else expression.append(Pattern.quote(String.valueOf(c)));
        }
        return Pattern.matches(expression.append('$').toString(), id);
    }

    @Override
    public MapCodec<WildcardEntry> codec() {
        return CODEC;
    }
}
