package com.iafenvoy.mxt.runtime.failure;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.resources.Identifier;

/**
 * Every source a refusal can name, in one place: the datapack registry whose definitions can refuse, so a module enum
 * points at the same constant the key {@code failure.<namespace>.<path>.<name>} is built from. {@link #COMMON} is the
 * one for a refusal any module can answer with, and it is what the interface defaults to.
 */
public final class FailureSources {
    public static final FailureSource COMMON = new FailureSource(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "common"));
    public static final FailureSource ABILITY = FailureSource.of(MxtResourceKeys.ABILITY);
    public static final FailureSource ALCHEMY_FURNACE = FailureSource.of(MxtResourceKeys.ALCHEMY_FURNACE);
    public static final FailureSource ARTIFACT = FailureSource.of(MxtResourceKeys.ARTIFACT);
    public static final FailureSource CONTRACT_TYPE = FailureSource.of(MxtResourceKeys.CONTRACT_TYPE);
    public static final FailureSource CULTIVATION = FailureSource.of(MxtResourceKeys.CULTIVATION);
    public static final FailureSource CURSE = FailureSource.of(MxtResourceKeys.CURSE);
    public static final FailureSource FORMATION = FailureSource.of(MxtResourceKeys.FORMATION);
    public static final FailureSource FORGING_BLUEPRINT = FailureSource.of(MxtResourceKeys.FORGING_BLUEPRINT);
    public static final FailureSource ITEM_QUALITY = FailureSource.of(MxtResourceKeys.ITEM_QUALITY);
    public static final FailureSource PROGRESSION = FailureSource.of(MxtResourceKeys.PROGRESSION);
    public static final FailureSource REALM_STAGE = FailureSource.of(MxtResourceKeys.REALM_STAGE);
    public static final FailureSource SECRET_REALM = FailureSource.of(MxtResourceKeys.SECRET_REALM);
    public static final FailureSource TECHNIQUE = FailureSource.of(MxtResourceKeys.TECHNIQUE);
    public static final FailureSource TRIBULATION = FailureSource.of(MxtResourceKeys.TRIBULATION);

    private FailureSources() {
    }
}
