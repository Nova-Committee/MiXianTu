package com.iafenvoy.mxt.util.formula.variable;

import com.iafenvoy.mxt.runtime.world.SecretRealmRecord;
import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import net.minecraft.world.entity.Entity;

import java.util.Set;

// The state of the secret realm the subject is inside. The names carry the {@code secret_realm_} prefix
// because {@code realm} already means a cultivation stage and both can be read in one expression; every name
// answers NaN outside a secret realm, so a condition can tell "not in a secret realm" from "in an empty one".
public final class SecretRealmVariable implements FormulaVariable {
    private static final Set<String> NAMES = Set.of("secret_realm_members", "secret_realm_limit",
            "secret_realm_elapsed", "secret_realm_duration", "secret_realm_index", "secret_realm_is_owner");

    @Override
    public Set<String> names() {
        return NAMES;
    }

    @Override
    public double value(String key, String suffix, FormulaContext context) {
        if (!suffix.isEmpty()) return Double.NaN;
        Entity entity = context.caster() != null ? context.caster() : context.player();
        if (entity == null) return Double.NaN;
        SecretRealmRecord record = SecretRealmRegistry.ofMember(entity.getUUID()).orElse(null);
        if (record == null) return Double.NaN;
        return switch (key) {
            case "secret_realm_members" -> record.members().size();
            case "secret_realm_limit" -> record.instance().maxMembers();
            case "secret_realm_elapsed" ->
                    record.startedAt() < 0L ? 0.0D : Math.max(0.0D, entity.level().getGameTime() - record.startedAt());
            case "secret_realm_duration" -> record.instance().durationTicks();
            case "secret_realm_index" -> record.index();
            case "secret_realm_is_owner" -> record.isOwner(entity.getUUID()) ? 1.0D : 0.0D;
            default -> Double.NaN;
        };
    }
}
