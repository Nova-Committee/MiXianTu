package com.iafenvoy.mxt.data.context;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class Context {
    // Read it through origin(), never directly: the extension map is untyped.
    public static final String ORIGIN = "mxt:origin";

    private final Map<String, Object> data;

    public Context() {
        this.data = new LinkedHashMap<>();
    }

    public FormulaContext formula() {
        return FormulaContext.EMPTY;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        try {
            return Optional.ofNullable((T) this.data.get(key));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public <T> T getOrDefault(String key, T defaultValue) {
        return this.<T>get(key).orElse(defaultValue);
    }

    public <T> void set(String key, T value) {
        this.data.put(key, value);
    }

    public boolean has(String key) {
        return this.data.containsKey(key);
    }

    public Map<String, Object> data() {
        return this.data;
    }

    // Copies without exposing a map constructor.
    public <T extends Context> T copyTo(T target) {
        this.data.forEach(target::set);
        return target;
    }

    // A subclass is what turns this into a position, because only it knows which entity to fall back to.
    public Optional<Vec3> origin() {
        return this.get(ORIGIN);
    }

    // Travels as extension data, so a nested action (a sequence, a composite) inherits it without being told again.
    public Context origin(@Nullable Vec3 value) {
        this.set(ORIGIN, value);
        return this;
    }
}
