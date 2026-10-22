package com.iafenvoy.mxt.screen.rift;

import com.iafenvoy.mxt.data.rift.RiftTemplate;

import java.util.*;

public final class RiftEditSession {
    private static final int MAX_HISTORY = 64;
    private final Deque<RiftTemplate> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    public final Set<Integer> selected = new LinkedHashSet<>();
    private RiftTemplate draft;
    private long version;

    public RiftEditSession(RiftTemplate initial) { this.draft = initial; }
    public RiftTemplate draft() { return this.draft; }
    public long version() { return this.version; }

    public void apply(RiftTemplate next) {
        if (this.draft.equals(next)) return;
        this.undo.addLast(this.draft);
        if (this.undo.size() > MAX_HISTORY) this.undo.removeFirst();
        this.redo.clear(); this.draft = next; this.version++;
        this.selected.removeIf(index -> index >= next.shape().vertices().size());
    }

    public void transientDraft(RiftTemplate draft) { this.draft = draft; this.version++; }
    public void finishDrag(RiftTemplate before) {
        if (before.equals(this.draft)) return;
        this.undo.addLast(before); if (this.undo.size() > MAX_HISTORY) this.undo.removeFirst(); this.redo.clear();
    }

    public void undo() {
        if (this.undo.isEmpty()) return;
        this.redo.addLast(this.draft); this.draft = this.undo.removeLast(); this.selected.clear(); this.version++;
    }
    public void redo() {
        if (this.redo.isEmpty()) return;
        this.undo.addLast(this.draft); this.draft = this.redo.removeLast(); this.selected.clear(); this.version++;
    }
}
