package com.iafenvoy.mxt.screen.aui;

import com.iafenvoy.mxt.config.MxtClientConfig;
import com.sighs.apricityui.init.Document;

public enum AuiTheme {
    PARCHMENT("parchment", false),
    JADE("jade", false),
    INK_GOLD("ink-gold", true);

    private final String id;
    private final boolean dark;

    AuiTheme(String id, boolean dark) {
        this.id = id;
        this.dark = dark;
    }

    public String id() {
        return this.id;
    }

    public boolean dark() {
        return this.dark;
    }

    public static AuiTheme current() {
        return MxtClientConfig.INSTANCE.appearance.theme.getValue();
    }

    public void apply(Document document) {
        if (document.body != null && !this.id.equals(document.body.getAttribute("data-mxt-theme"))) {
            document.body.setAttribute("data-mxt-theme", this.id);
            // Bindings resolve data colours immediately, before AUI's next style tick.
            document.flushPendingStyleUpdates();
        }
    }
}
