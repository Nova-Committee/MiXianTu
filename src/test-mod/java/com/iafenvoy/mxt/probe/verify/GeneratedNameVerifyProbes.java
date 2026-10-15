package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Every name, description and minor-stage name is a key generated from the id the entry was decoded as, so the only
 * way a pack gives one words is translating that key.
 */
public final class GeneratedNameVerifyProbes {
    private GeneratedNameVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/generated_names", GeneratedNameVerifyProbes::generatedNames));
    }

    private static void generatedNames(ProbeContext context) {
        String failure = verifyGeneratedNames(context);
        if (failure != null) context.fail(failure);
    }

    // No definition stores text any more, so every name, description and minor-stage name is a key generated from
    // the id the entry was decoded as. The key is read off the component rather than through getString(): the game
    // test server does have the packs' language loaded, so a translated name would come back as its words.
    private static String verifyGeneratedNames(ProbeContext context) {
        Holder<RealmStage> qiRefining = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING);
        if (qiRefining.value().minorStages() != 9)
            return "the counted qi_refining reads " + qiRefining.value().minorStages() + " minor stages instead of 9";
        String counted = generatedKey(DefinitionText.minorStage(qiRefining, 0));
        if (!counted.equals("mxt.realm_stage.mxt_test.qi_refining.minor_stage.0"))
            return "the counted first minor stage reads " + counted;
        String last = generatedKey(DefinitionText.minorStage(qiRefining, 8));
        if (!last.equals("mxt.realm_stage.mxt_test.qi_refining.minor_stage.8"))
            return "the counted last minor stage reads " + last;
        // A realm whose fixture declared three named minor stages now declares the same count, so the third stage
        // is still the last one it has.
        Holder<RealmStage> coreForming = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.CORE_FORMING);
        if (coreForming.value().minorStages() != 3)
            return "the counted core_forming reads " + coreForming.value().minorStages() + " minor stages instead of 3";
        if (!generatedKey(DefinitionText.minorStage(coreForming, 2))
                .equals("mxt.realm_stage.mxt_test.core_forming.minor_stage.2"))
            return "the counted core_forming last stage reads " + generatedKey(DefinitionText.minorStage(coreForming, 2));
        Holder<ItemQuality> poor = context.require(MxtResourceKeys.ITEM_QUALITY, ProbeDefinitions.POOR_QUALITY);
        if (!generatedKey(DefinitionText.text(poor)).equals("mxt.quality.mxt_test.poor"))
            return "the generated quality name reads " + generatedKey(DefinitionText.text(poor));
        if (!generatedKey(DefinitionText.description(poor)).equals("mxt.quality.mxt_test.poor.description"))
            return "the generated quality description reads " + generatedKey(DefinitionText.description(poor));
        // The fixture that used to write its own words now writes none, so it is generated exactly like the rest:
        // a definition cannot escape the id-derived key.
        Holder<ItemQuality> excellent = context.require(MxtResourceKeys.ITEM_QUALITY, ProbeDefinitions.EXCELLENT_QUALITY);
        if (!generatedKey(DefinitionText.text(excellent)).equals("mxt.quality.mxt_test.excellent"))
            return "the generated excellent name reads " + generatedKey(DefinitionText.text(excellent));
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        if (!generatedKey(DefinitionText.text(qi)).equals("mxt.aura.mxt_test.qi"))
            return "the generated aura name reads " + generatedKey(DefinitionText.text(qi));
        if (!generatedKey(DefinitionText.description(qi)).equals("mxt.aura.mxt_test.qi.description"))
            return "the generated aura description reads " + generatedKey(DefinitionText.description(qi));
        // The test pack's own namespace is part of the key, not the mod's: a definition shipped by a pack is
        // translated under that pack's id.
        Holder<Element> metal = context.require(MxtResourceKeys.ELEMENT, ProbeDefinitions.PROBE_METAL_ELEMENT);
        if (!generatedKey(DefinitionText.text(metal)).equals("mxt.element.mxt_test.metal"))
            return "the generated element name reads " + generatedKey(DefinitionText.text(metal));
        if (!generatedKey(DefinitionText.description(metal)).equals("mxt.element.mxt_test.metal.description"))
            return "the generated element description reads " + generatedKey(DefinitionText.description(metal));
        // Being a translation key is the whole guard: a pack that translates one gets words, a pack that does not
        // gets nothing drawn, and neither can write text of its own. The test pack translates this element, and ships
        // no description for the aura it also defines.
        Optional<MutableComponent> written = DefinitionText.resolved(DefinitionText.description(metal));
        if (written.isEmpty() || written.orElseThrow().getString().equals("mxt.element.mxt_test.metal.description"))
            return "a translated description reads " + written.map(Component::getString).orElse("<nothing>");
        if (DefinitionText.resolved(DefinitionText.description(qi)).isPresent())
            return "an untranslated description would be drawn as " + DefinitionText.description(qi).getString();
        // Nothing else is required to be a key, so a literal and a blank are judged by the same gate.
        if (DefinitionText.resolved(Component.literal("plain words")).isEmpty())
            return "a literal description was dropped";
        if (DefinitionText.resolved(Component.literal("   ")).isPresent())
            return "a blank description was drawn";
        // The sibling suffix a stage's count generates, gated the same way: a translated index reads as words, and
        // one no pack translated is dropped instead of printing its key into the cultivation row.
        Optional<MutableComponent> stage = DefinitionText.resolved(DefinitionText.minorStage(qiRefining, 0));
        if (stage.isEmpty() || stage.orElseThrow().getString().equals("mxt.realm_stage.mxt_test.qi_refining.minor_stage.0"))
            return "a translated minor stage reads " + stage.map(Component::getString).orElse("<nothing>");
        if (DefinitionText.resolved(DefinitionText.minorStage(coreForming, 3)).isPresent())
            return "an untranslated minor stage would be drawn as " + DefinitionText.minorStage(coreForming, 3).getString();
        // A name somewhere that has to show something falls back to the id rather than to the key, so the row still
        // says which entry it is.
        String named = DefinitionText.nameOrId(metal).getString();
        if (!named.equals("Metal Spirit")) return "a translated name reads " + named;
        Identifier absent = Identifier.fromNamespaceAndPath("mxt_test", "no_such_herb");
        String fallback = DefinitionText.nameOrId(MxtResourceKeys.SPIRIT_HERB, absent).getString();
        if (!fallback.equals("mxt_test:no_such_herb")) return "an untranslated name reads " + fallback;
        return null;
    }

    private static String generatedKey(Component text) {
        return text.getContents() instanceof TranslatableContents contents ? contents.getKey() : text.getString();
    }
}
