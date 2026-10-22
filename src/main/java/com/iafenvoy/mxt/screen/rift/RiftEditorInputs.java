package com.iafenvoy.mxt.screen.rift;

import com.sighs.apricityui.init.Element;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class RiftEditorInputs {
    private final Map<String, Element> fields = new HashMap<>();
    private final Map<String, NumberValue> numbers = new HashMap<>();

    public void bind(String name, Element field) {
        this.fields.put(name, field);
        if ("number".equals(field.getType())) {
            this.format(name);
            field.addEventListener("input", _ -> this.numbers.remove(name));
            field.addEventListener("blur", _ -> this.format(name));
        }
    }

    private void format(String name) {
        Element field = this.fields.get(name);
        if ("1".equals(field.getAttribute("step")) || this.value(name).isEmpty()) return;
        try {
            this.number(name, this.number(name));
        } catch (IllegalArgumentException ignored) {
            // Keep incomplete input visible so the user can finish or correct it.
        }
    }

    public String value(String name) {
        return this.fields.get(name).getValue().trim();
    }

    public void value(String name, String value) {
        Element field = this.fields.get(name);
        if (!field.getValue().equals(value)) field.setValue(value);
        this.numbers.remove(name);
    }

    public double number(String name) {
        String text = this.value(name);
        NumberValue stored = this.numbers.get(name);
        // Rounded display text must not quantize untouched coordinates, basis vectors or snap spacing.
        if (stored != null && stored.text.equals(text)) return stored.exact;
        double value = Double.parseDouble(text);
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name);
        return value;
    }

    public void number(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name);
        String text = String.format(Locale.ROOT, "%.3f", value == 0 || Math.abs(value) < 0.0005 ? 0 : value);
        this.value(name, text);
        this.numbers.put(name, new NumberValue(text, value));
    }

    public boolean checked(String name) {
        return this.fields.get(name).isChecked();
    }

    public void checked(String name, boolean value) {
        Element field = this.fields.get(name);
        if (field.isChecked() != value) field.setChecked(value);
    }

    public Vec3 vector(String name) {
        return new Vec3(this.number(name + "_x"), this.number(name + "_y"), this.number(name + "_z"));
    }

    public void vector(String name, Vec3 value) {
        this.number(name + "_x", value.x);
        this.number(name + "_y", value.y);
        this.number(name + "_z", value.z);
    }

    public boolean isEmpty() {
        return this.fields.isEmpty();
    }

    public void clear() {
        this.fields.clear();
        this.numbers.clear();
    }

    private record NumberValue(String text, double exact) {
    }
}
