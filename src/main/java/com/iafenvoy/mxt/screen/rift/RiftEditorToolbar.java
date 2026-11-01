package com.iafenvoy.mxt.screen.rift;

import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.List;

public final class RiftEditorToolbar {
    private static final List<String> ACTIONS = List.of("tab_shape", "tab_placement", "tab_target", "tab_appearance",
            "toggle_properties", "preview", "camera");

    private RiftEditorToolbar() {
    }

    public static boolean layout(Document document) {
        Element anchor = document.getElementById("properties_menu_anchor");
        Element shortcuts = document.getElementById("property_shortcuts");
        Element overflow = document.getElementById("menu_properties");
        for (String id : ACTIONS) {
            Element action = document.getElementById(id);
            if (action.parentElement != shortcuts) document.createRelation(action, shortcuts, false);
        }
        AuiElements.style(shortcuts, "display", "flex");
        AuiElements.style(anchor, "display", "none");
        AuiElements.style(overflow, "display", "none");
        document.flushPendingStyleUpdates();
        document.commitRenderState();
        Element header = document.getElementById("header"), footer = document.getElementById("footer");
        if (footer.getBoundingClientRect().right <= header.getBoundingClientRect().right + 0.01) return false;
        AuiElements.style(anchor, "display", "flex");
        document.flushPendingStyleUpdates();
        document.commitRenderState();
        double excess = footer.getBoundingClientRect().right - header.getBoundingClientRect().right;
        Element.DOMRect first = document.getElementById(ACTIONS.getFirst()).getBoundingClientRect();
        double gap = document.getElementById(ACTIONS.get(1)).getBoundingClientRect().left - first.right;
        int visible = ACTIONS.size();
        // Move the existing nodes so selected state and click listeners survive overflow and resizing.
        while (visible > 0 && excess > 0.01) {
            Element action = document.getElementById(ACTIONS.get(--visible));
            excess -= action.getBoundingClientRect().width + gap;
        }
        for (int i = visible; i < ACTIONS.size(); i++)
            document.createRelation(document.getElementById(ACTIONS.get(i)), overflow, false);
        AuiElements.style(shortcuts, "display", visible == 0 ? "none" : "flex");
        return true;
    }
}
