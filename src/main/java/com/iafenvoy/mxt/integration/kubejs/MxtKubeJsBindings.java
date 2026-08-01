package com.iafenvoy.mxt.integration.kubejs;

/**
 * Root object exposed as {@code Mxt} to KubeJS scripts.
 */
public final class MxtKubeJsBindings {
    public static final MxtKubeJsBindings INSTANCE = new MxtKubeJsBindings();
    private final MxtKubeJsApiFacade api = new MxtKubeJsApiFacade();

    private MxtKubeJsBindings() {
    }

    /**
     * Query and mutation operations that use the same server validation as core gameplay.
     */
    public MxtKubeJsApiFacade api() {
        return this.api;
    }
}
