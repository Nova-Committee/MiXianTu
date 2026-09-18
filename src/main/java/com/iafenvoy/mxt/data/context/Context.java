package com.iafenvoy.mxt.data.context;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class Context {
    /**
     * The extension key an activation's own place travels under, when it is not the acting entity's position.
     * Read it through {@link #origin()}, never directly: the extension map is untyped.
     */
    public static final String ORIGIN = "mxt:origin";

    private final Map<String, Object> data;

    /**
     * Creates an empty context. Extension values are added through {@link #set(String, Object)}.
     */
    public Context() {
        this.data = new LinkedHashMap<>();
    }

    /**
     * Formula values associated with this context. Specialized contexts override this.
     */
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

    /**
     * Returns the mutable extension data owned by this runtime context.
     * Fixed context fields remain exposed by the specialised context type.
     */
    public Map<String, Object> data() {
        return this.data;
    }

    /**
     * Copies extension values into a newly-created child context without exposing a map constructor.
     */
    public <T extends Context> T copyTo(T target) {
        this.data.forEach(target::set);
        return target;
    }

    /**
     * The place this context was told to happen at, or empty when it is the acting entity's own. A subclass is
     * what turns this into a position, because only it knows which entity to fall back to.
     */
    public Optional<Vec3> origin() {
        return this.get(ORIGIN);
    }

    /**
     * States the place this context happens at. It travels as extension data, so a nested action - a sequence,
     * a composite, anything that hands its own context on - inherits it without being told again.
     */
    public Context origin(@Nullable Vec3 value) {
        this.set(ORIGIN, value);
        return this;
    }
}
