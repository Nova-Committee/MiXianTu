package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.EntitySources;
import com.iafenvoy.mxt.runtime.ModuleHooks;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.runtime.progression.ProgressionService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;

/**
 * Everything a body's cultivation identity contributes: what its spirit roots, physiques, techniques and minor
 * stages grant, the passive modifiers they declare, and the techniques it owns a progression chain for.
 *
 * <p>One object answers all three, so the cultivation side is one registration rather than one per question.
 */
public final class CultivationHooks implements EntitySources.Grants, EntitySources.Attributes, EntitySources.Owns {
    private CultivationHooks() {
    }

    public static void register() {
        CultivationHooks hooks = new CultivationHooks();
        ModuleHooks.register(EntitySources.Grants.class, hooks);
        ModuleHooks.register(EntitySources.Attributes.class, hooks);
        ModuleHooks.register(EntitySources.Owns.class, hooks);
    }

    @Override
    public void collect(LivingEntity entity, ProgressionAttachment progress, EntitySources.GrantSink sink) {
        SpiritIdentityAttachment spirit = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (spirit == null) return;
        for (Holder<SpiritRoot> root : spirit.activeSpiritRoots())
            sink.want(Sources.granted(Sources.Grant.SPIRIT_ROOT, HolderHelper.id(root)), root.value().grantedAbilities());
        for (Holder<Physique> physique : spirit.activePhysiques())
            sink.want(Sources.granted(Sources.Grant.PHYSIQUE, HolderHelper.id(physique)), physique.value().grantedAbilities());
        for (Holder<Technique> technique : spirit.learnedTechniques()) {
            Identifier id = HolderHelper.id(technique);
            Identifier source = Sources.granted(Sources.Grant.TECHNIQUE, id);
            sink.want(source, technique.value().grantedAbilities());
            // Mastery adds to the same source, so a promotion only has to change the level: a technique's grants
            // are one contribution, not one per level it passed.
            ProgressionService.currentLevel(progress, id, technique.value()).ifPresent(current ->
                    sink.wantResolved(source, ProgressionService.grantedAbilities(technique.value(), current)));
        }
        // A realm stage's unlocks are keyed by how far the body got inside it, so the record is the whole answer:
        // no record means the body never stood in that realm, and a realm it has left keeps granting.
        for (Map.Entry<Holder<RealmStage>, Integer> record : spirit.minorStageRecords().entrySet())
            sink.wantResolved(Sources.granted(Sources.Grant.REALM_STAGE, HolderHelper.id(record.getKey())),
                    MinorStageService.unlockedAbilities(record.getKey(), record.getValue()));
    }

    // Both attachments are read without being created, so a body that never cultivated costs nothing here.
    @Override
    public void collect(LivingEntity entity, EntitySources.AttributeSink sink) {
        CultivationAttachment cultivation = entity.getExistingData(MxtAttachments.CULTIVATION).orElse(null);
        if (cultivation != null)
            for (Holder<RealmStage> realm : cultivation.realmStages().values())
                sink.add(Sources.Declaration.REALM, HolderHelper.id(realm), realm.value().passiveModifiers());
        SpiritIdentityAttachment spirit = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (spirit == null) return;
        for (Holder<Technique> technique : spirit.learnedTechniques())
            sink.add(Sources.Declaration.TECHNIQUE, HolderHelper.id(technique), technique.value().passiveModifiers());
        for (Holder<Physique> physique : spirit.activePhysiques())
            sink.add(Sources.Declaration.PHYSIQUE, HolderHelper.id(physique), physique.value().attributeModifiers());
    }

    // Every learned technique owns its own chain; the level it stands on is the key its record is stored under.
    @Override
    public List<EntitySources.Owner> held(Entity entity) {
        SpiritIdentityAttachment spirit = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (spirit == null) return List.of();
        return spirit.learnedTechniques().stream()
                .map(technique -> new EntitySources.Owner(HolderHelper.id(technique), technique.value()))
                .toList();
    }
}
