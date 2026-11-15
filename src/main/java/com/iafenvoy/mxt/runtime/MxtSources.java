package com.iafenvoy.mxt.runtime;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.runtime.Sources;
import net.minecraft.resources.Identifier;

public final class MxtSources {
    private MxtSources() {
    }

    public static final class Grant {
        public static final Sources.Grant SPIRIT_ROOT = new Sources.Grant("spirit_root");
        public static final Sources.Grant PHYSIQUE = new Sources.Grant("physique");
        public static final Sources.Grant TECHNIQUE = new Sources.Grant("technique");
        public static final Sources.Grant REALM_STAGE = new Sources.Grant("realm_stage");
        public static final Sources.Grant CREATURE = new Sources.Grant("creature");
        public static final Sources.Grant CONTRACT = new Sources.Grant("contract");

        private Grant() {
        }
    }

    public static final class Declaration {
        public static final Sources.Declaration REALM = new Sources.Declaration("realm");
        public static final Sources.Declaration TECHNIQUE = new Sources.Declaration("technique");
        public static final Sources.Declaration PHYSIQUE = new Sources.Declaration("physique");

        private Declaration() {
        }
    }

    public static Identifier formation(Identifier definition) {
        // Keep persisted ledger ids stable while ownership moves out of the library.
        return Identifier.fromNamespaceAndPath(Saturn.MOD_ID,
                "formation/" + definition.getNamespace() + "/" + definition.getPath());
    }
}
