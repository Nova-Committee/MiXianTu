package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.attachment.CreatureSpiritAttachment;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyHeatService;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.saturn.util.HolderHelper;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RegistryMatchVerifyProbes {
    private RegistryMatchVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/external_api/match_resolution", RegistryMatchVerifyProbes::resolution));
    }

    private static void resolution(ProbeContext context) {
        CaveSpider mob = Objects.requireNonNull(EntityType.CAVE_SPIDER.create(context.level(), EntitySpawnReason.COMMAND));
        mob.setPos(context.at(5, 2, 5).getCenter());
        try {
            context.check("mob joined", context.level().addFreshEntity(mob));
            Optional<Holder<CreatureProfile>> saved = mob.getExistingData(MxtAttachments.CREATURE_SPIRIT).flatMap(CreatureSpiritAttachment::profile);
            List<TranslatableContents> creature = run(context, "creature " + mob.getUUID());
            context.expect("creature diagnosis lists rejection followed by winner", creature.size(), 2);
            if (creature.size() == 2) {
                context.expect("creature rejection identifies the failed higher priority definition", id(creature.getFirst()),
                        "mxt_test:priority/01_ineligible");
                context.expect("creature rejection identifies invalid intelligence",
                        ((Component) creature.getFirst().getArgs()[2]).getContents() instanceof TranslatableContents reason
                                ? reason.getKey() : "", "command.mxt.match.creature.intelligence");
                context.expect("creature diagnosis agrees with spawn selection", id(creature.getLast()),
                        saved.map(HolderHelper::id).orElseThrow().toString());
            }
        } finally {
            mob.discard();
        }

        context.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
        expectWinner(context, "artifact", "mxt_test:match/b_winner", 31);

        BlockPos pos = context.at(9, 1, 9);
        context.level().setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
        String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        expectWinner(context, "heat_source " + coords, "mxt_test:match/b_winner", 31);
        context.expect("production heat lookup uses that winner", AlchemyHeatService.maxTemperature(context.level(), pos), 75.0D);

        Holder<AuraZone> zone = Objects.requireNonNull(AuraService.getPositionAura(context.level(), pos).source());
        expectWinner(context, "aura_zone " + coords, HolderHelper.id(zone).toString(), zone.value().priority());
        context.note("OK registry priority and diagnostics for creature, artifact, heat and aura");
    }

    private static String id(TranslatableContents message) {
        return message.getArgs()[0].toString();
    }

    private static void expectWinner(ProbeContext context, String command, String id, int priority) {
        List<TranslatableContents> messages = run(context, command);
        if (!context.check(command + " reports one selected definition", messages.size() == 1)) return;
        TranslatableContents message = messages.getFirst();
        context.expect(command + " winner", id(message), id);
        context.expect(command + " priority", message.getArgs()[1], priority);
    }

    private static List<TranslatableContents> run(ProbeContext context, String command) {
        List<TranslatableContents> messages = new ArrayList<>();
        CommandSourceStack source = context.server().createCommandSourceStack()
                .withLevel(context.level()).withEntity(context.player()).withSource(new CommandSource() {
                    @Override
                    public void sendSystemMessage(@NotNull Component message) {
                        if (message.getContents() instanceof TranslatableContents contents) messages.add(contents);
                    }

                    @Override
                    public boolean acceptsSuccess() {
                        return true;
                    }

                    @Override
                    public boolean acceptsFailure() {
                        return true;
                    }

                    @Override
                    public boolean shouldInformAdmins() {
                        return false;
                    }
                });
        context.server().getCommands().performPrefixedCommand(source, "mxt match " + command);
        return messages;
    }
}
