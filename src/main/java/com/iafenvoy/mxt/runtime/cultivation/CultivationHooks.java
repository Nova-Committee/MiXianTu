package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.MinorStageAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.attachment.TechniqueAttachment;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.progression.ProgressionHolder;
import com.iafenvoy.mxt.event.dispatcher.CollectAbilityGrantsEvent;
import com.iafenvoy.mxt.event.dispatcher.CollectPassiveAttributesEvent;
import com.iafenvoy.mxt.event.dispatcher.CollectProgressionOwnersEvent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.runtime.progression.ProgressionOwners;
import com.iafenvoy.mxt.runtime.progression.ProgressionService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;

/**
 * Everything a body's cultivation identity contributes: what its spirit roots, physiques, techniques and minor
 * stages grant, the passive modifiers they declare, and the techniques it owns a progression chain for. Each of
 * those lives in the attachment of its own module, so this reads three of them.
 */
@EventBusSubscriber
public final class CultivationHooks {
    private CultivationHooks() {
    }

    @SubscribeEvent
    public static void onCollectGrants(CollectAbilityGrantsEvent event) {
        LivingEntity entity = event.getEntity();
        Provider access = entity.level().registryAccess();
        SpiritIdentityAttachment roots = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (roots != null) {
            for (Holder<SpiritRoot> root : roots.activeSpiritRoots())
                event.want(Sources.granted(Sources.Grant.SPIRIT_ROOT, HolderHelper.id(root)), root.value().grantedAbilities());
            for (Holder<Physique> physique : roots.activePhysiques())
                event.want(Sources.granted(Sources.Grant.PHYSIQUE, HolderHelper.id(physique)), physique.value().grantedAbilities());
        }
        TechniqueAttachment techniques = entity.getExistingData(MxtAttachments.TECHNIQUE).orElse(null);
        if (techniques != null)
            for (Holder<Technique> technique : techniques.learnedTechniques()) {
                Identifier id = HolderHelper.id(technique);
                Identifier source = Sources.granted(Sources.Grant.TECHNIQUE, id);
                event.want(source, technique.value().grantedAbilities());
                // Mastery adds to the same source, so a promotion only has to change the level: a technique's grants
                // are one contribution, not one per level it passed.
                techniques.techniqueLevels().current(technique).ifPresent(current -> event.wantResolved(source, ProgressionService.grantedAbilities(technique.value(), current, access)));
            }
        // A realm stage's unlocks are keyed by how far the body got inside it, so the record is the whole answer:
        // no record means the body never stood in that realm, and a realm it has left keeps granting.
        MinorStageAttachment stages = entity.getExistingData(MxtAttachments.MINOR_STAGES).orElse(null);
        if (stages != null)
            for (Map.Entry<Holder<RealmStage>, Integer> record : stages.minorStageRecords().entrySet())
                event.wantResolved(Sources.granted(Sources.Grant.REALM_STAGE, HolderHelper.id(record.getKey())), MinorStageService.unlockedAbilities(record.getKey(), record.getValue(), access));
    }

    // Every attachment is read without being created, so a body that never cultivated costs nothing here.
    @SubscribeEvent
    public static void onCollectAttributes(CollectPassiveAttributesEvent event) {
        LivingEntity entity = event.getEntity();
        CultivationAttachment cultivation = entity.getExistingData(MxtAttachments.CULTIVATION).orElse(null);
        if (cultivation != null)
            for (Holder<RealmStage> realm : cultivation.realmStages().values())
                event.add(Sources.Declaration.REALM, HolderHelper.id(realm), realm.value().passiveModifiers());
        TechniqueAttachment techniques = entity.getExistingData(MxtAttachments.TECHNIQUE).orElse(null);
        if (techniques != null)
            for (Holder<Technique> technique : techniques.learnedTechniques())
                event.add(Sources.Declaration.TECHNIQUE, HolderHelper.id(technique), technique.value().passiveModifiers());
        SpiritIdentityAttachment roots = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (roots != null)
            for (Holder<Physique> physique : roots.activePhysiques())
                event.add(Sources.Declaration.PHYSIQUE, HolderHelper.id(physique), physique.value().attributeModifiers());
    }

    // Every learned technique owns its own chain, and the technique attachment holds the levels of exactly those.
    @SubscribeEvent
    public static void onCollectOwners(CollectProgressionOwnersEvent event) {
        TechniqueAttachment techniques = event.getEntity().getExistingData(MxtAttachments.TECHNIQUE).orElse(null);
        if (techniques == null) return;
        ProgressionHolder<Technique> levels = techniques.techniqueLevels();
        for (Holder<Technique> technique : techniques.learnedTechniques())
            event.add(new ProgressionOwners.Owner<>(technique, levels));
    }
}
