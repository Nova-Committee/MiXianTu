package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.api.CaptureListener;
import com.iafenvoy.mxt.api.Contractable;
import com.iafenvoy.mxt.api.ContractOperations;
import com.iafenvoy.mxt.api.Perchable;
import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.data.creature.ContractContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A creature that answers every contract interface itself, which is what the framework asks of a content mod.
 * The counters are the probe's evidence: what the framework called, in which order, is the whole assertion.
 */
public final class ProbeBeast extends PathfinderMob implements Contractable, ContractOperations, CaptureListener, Perchable {
    private static final List<String> CALLS = new ArrayList<>();
    private static final Optional<Vec3> DEFAULT_PERCH_ANSWER = Optional.of(new Vec3(0.5D, -0.4D, 0.0D));
    private static boolean refusesContract;
    private static boolean refusesBehavior;
    private static Optional<Vec3> perchAnswer = DEFAULT_PERCH_ANSWER;
    // The probe keeps its owner itself, the way a creature with owner logic of its own would, and saves it next
    // to its other data so a round trip through a carrying item brings the owner back.
    private EntityReference<LivingEntity> owner;

    public ProbeBeast(EntityType<? extends ProbeBeast> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    public static void reset() {
        CALLS.clear();
        refusesContract = false;
        refusesBehavior = false;
        perchAnswer = DEFAULT_PERCH_ANSWER;
    }

    public static List<String> calls() {
        return List.copyOf(CALLS);
    }

    public static void refuseContract() {
        refusesContract = true;
    }

    public static void refuseBehavior(boolean refuse) {
        refusesBehavior = refuse;
    }

    // What the framework is told when it asks where this creature wants to sit; empty is a refusal.
    public static void perchAnswer(Optional<Vec3> answer) {
        perchAnswer = answer;
    }

    @Override
    public Optional<Vec3> perchOffset(Entity vehicle, List<Vec3> claimed) {
        CALLS.add("seat:" + claimed.size());
        return perchAnswer;
    }

    @Override
    public void onPerched(Entity vehicle) {
        CALLS.add("perched");
    }

    @Override
    public void onPerchReleased(Entity vehicle) {
        CALLS.add("unperched");
    }

    @Override
    public EntityReference<LivingEntity> getOwnerReference() {
        return this.owner;
    }

    @Override
    public void setContractOwner(LivingEntity owner) {
        this.owner = EntityReference.of(owner);
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        EntityReference.store(this.owner, output, "Owner");
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.owner = EntityReference.readWithOldOwnerConversion(input, "Owner", this.level());
    }

    @Override
    public boolean acceptsContract(ContractContext context) {
        return !refusesContract && Contractable.super.acceptsContract(context);
    }

    @Override
    public void onContractBound(ContractContext context) {
        CALLS.add("bound");
    }

    @Override
    public void onContractReleased(ContractContext context) {
        CALLS.add("released");
    }

    @Override
    public void onContractDeath(ContractContext context) {
        CALLS.add("death");
    }

    @Override
    public void recall(ContractContext context) {
        CALLS.add("recall");
        ContractOperations.super.recall(context);
    }

    @Override
    public boolean onBehaviorSelected(ContractContext context, ContractBehavior behavior) {
        CALLS.add("order:" + behavior.id().getPath());
        return !refusesBehavior;
    }

    @Override
    public void tick(ContractContext context, ContractBehavior behavior) {
        CALLS.add("tick:" + behavior.id().getPath());
        ContractOperations.super.tick(context, behavior);
    }

    @Override
    public void follow(ContractContext context) {
        CALLS.add("follow");
        ContractOperations.super.follow(context);
    }

    @Override
    public void onDealtDamage(ContractContext context, LivingEntity target, double damage) {
        CALLS.add("damage");
    }

    @Override
    public void onCaptured(Player captor) {
        CALLS.add("captured");
    }

    @Override
    public void onReleased(Player captor) {
        CALLS.add("freed");
    }
}
