package com.iafenvoy.mxt.util.matcher.builtin;

import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public record RegexEntry(String pattern) implements Entry {
    public static final MapCodec<RegexEntry> CODEC = Codec.STRING.fieldOf("pattern").xmap(RegexEntry::new, RegexEntry::pattern);
    private static final Map<String, Pattern> PATTERN_CACHE = new LinkedHashMap<>();

    public RegexEntry {
        if (pattern.isEmpty()) throw new IllegalArgumentException("Item matcher regex must not be empty");
        PATTERN_CACHE.put(pattern, Pattern.compile(pattern));
    }

    @Override
    public boolean matches(ItemStack stack) {
        return PATTERN_CACHE.computeIfAbsent(this.pattern, Pattern::compile).matcher(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).matches();
    }

    @Override
    public MapCodec<RegexEntry> codec() {
        return CODEC;
    }
}
