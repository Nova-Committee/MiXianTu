package com.iafenvoy.mxt.runtime.artifact;

import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Which artifact capability a wheel cell names: the artifact definition's own id plus the capability's key inside
 * it.
 *
 * <p>One artifact may offer several capabilities - a flying sword that also holds things offers two - so the
 * artifact alone cannot be the identity of a cell. The pair is written as one id, {@code ns:path/key}, because the
 * whole wheel speaks ids: the saved layout stores them, the trigger carries them and the server re-reads the page
 * by them. The key is code-owned and never contains a slash, so the last slash in a path is always the split.</p>
 */
public record ArtifactCapability(Identifier artifact, String key) {
    /** The id this capability is addressed by; also the id a cell of the main wheel stores. */
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(this.artifact.getNamespace(), this.artifact.getPath() + "/" + this.key);
    }

    /** Reads one back; empty for an id that is not shaped like a capability, which is not an error. */
    public static Optional<ArtifactCapability> parse(Identifier id) {
        if (id == null) return Optional.empty();
        String path = id.getPath();
        int cut = path.lastIndexOf('/');
        if (cut <= 0 || cut == path.length() - 1) return Optional.empty();
        return Optional.of(new ArtifactCapability(
                Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring(0, cut)), path.substring(cut + 1)));
    }
}
