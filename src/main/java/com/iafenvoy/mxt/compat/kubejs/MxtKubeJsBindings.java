package com.iafenvoy.mxt.compat.kubejs;

import com.iafenvoy.mxt.compat.kubejs.binding.MxtKubeJsActionBindings;
import com.iafenvoy.mxt.compat.kubejs.binding.MxtKubeJsConditionBindings;
import com.iafenvoy.mxt.compat.kubejs.binding.MxtKubeJsValueBindings;

/**
 * Root object exposed as {@code Mxt} to KubeJS scripts.
 */
public final class MxtKubeJsBindings {
    public static final MxtKubeJsBindings INSTANCE = new MxtKubeJsBindings();
    private final MxtKubeJsApiFacade api = new MxtKubeJsApiFacade();
    private final MxtKubeJsActionBindings actions = new MxtKubeJsActionBindings();
    private final MxtKubeJsConditionBindings conditions = new MxtKubeJsConditionBindings();
    private final MxtKubeJsValueBindings values = new MxtKubeJsValueBindings();

    private MxtKubeJsBindings() {
    }

    /**
     * Query and mutation operations that use the same server validation as core gameplay.
     */
    public MxtKubeJsApiFacade api() {
        return this.api;
    }

    /**
     * Registers callbacks used by data-driven action objects.
     */
    public MxtKubeJsActionBindings actions() {
        return this.actions;
    }

    /**
     * Registers callbacks used by data-driven condition objects.
     */
    public MxtKubeJsConditionBindings conditions() {
        return this.conditions;
    }

    /**
     * Registers callbacks used by number and resource value providers.
     */
    public MxtKubeJsValueBindings values() {
        return this.values;
    }

}
