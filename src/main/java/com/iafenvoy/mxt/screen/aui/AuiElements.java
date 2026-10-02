package com.iafenvoy.mxt.screen.aui;

import com.sighs.apricityui.init.Element;
import org.jetbrains.annotations.Nullable;

/**
 * The writes a page host makes to its elements: text, an inline style property, a class token and the disabled
 * attribute. This is the only implementation of them - a host, a scroll list and a row binding all come here.
 * <p>
 * Every write compares first: ApricityUI re-runs the page's style pass on any change, and rows are written on every
 * layout. A null element is ignored rather than checked by the caller, because the field behind it is nullable
 * while a host is between binds.
 */
public final class AuiElements {
    private AuiElements() {
    }

    public static void style(@Nullable Element element, String property, String value) {
        if (element == null) return;
        if (value.equals(element.getInlineStylePropertyValue(property))) return;
        element.setInlineStyleProperty(property, value);
    }

    public static void setText(@Nullable Element element, String text) {
        if (element == null) return;
        if (text.equals(element.getTextContent())) return;
        element.setTextContent(text);
    }

    public static void setClass(@Nullable Element element, String token, boolean present) {
        if (element == null) return;
        if (element.getClassList().contains(token) == present) return;
        element.getClassList().toggle(token, present);
    }

    /**
     * The attribute, not a class: the theme dims a button through {@code [disabled]}, and ApricityUI refuses to
     * dispatch a click to an element that carries it, so the look and the behaviour come from the same write.
     */
    public static void setDisabled(@Nullable Element element, boolean disabled) {
        if (element == null) return;
        if (element.hasAttribute("disabled") == disabled) return;
        element.setDisabled(disabled);
    }
}
