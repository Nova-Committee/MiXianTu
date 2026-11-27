package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.AuraHandler;
import com.iafenvoy.mxt.runtime.spirit.ItemAuraHandler;
import com.iafenvoy.mxt.runtime.world.AuraService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber
public final class MxtCapabilities {
    private static final Identifier AURA_HANDLER = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "aura_handler");
    public static final BlockCapability<AuraHandler, @Nullable BlockContext> BLOCK_AURA = BlockCapability.create(AURA_HANDLER, AuraHandler.class, BlockContext.class);
    public static final ItemCapability<AuraHandler, ItemContext> ITEM_AURA = ItemCapability.create(AURA_HANDLER, AuraHandler.class, ItemContext.class);

    private MxtCapabilities() {
    }

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(BLOCK_AURA, MxtBlockEntities.SPIRIT_CRAFTING_TABLE.get(), (table, _) -> table);
        event.registerBlockEntity(BLOCK_AURA, MxtBlockEntities.DISPLAY_STAND.get(), (stand, context) -> stand.auraHandler(context == null ? null : context.actor()));
        event.registerItem(ITEM_AURA, ItemAuraHandler::new,
                MxtItems.SPIRIT_STONE.get(), MxtItems.MEDIUM_SPIRIT_STONE.get(), MxtItems.HIGH_SPIRIT_STONE.get(),
                MxtItems.SUPREME_SPIRIT_STONE.get(), MxtItems.TALISMAN.get(), MxtItems.SPIRIT_VESSEL.get());
    }

    public static @Nullable AuraHandler findBlock(ServerLevel level, BlockPos pos, @Nullable Direction side, @Nullable LivingEntity actor) {
        LevelChunk chunk = AuraService.loadedChunk(level, pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        BlockState state = chunk.getBlockState(pos);
        BlockEntity entity = state.hasBlockEntity() ? chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE) : null;
        return level.getCapability(BLOCK_AURA, pos, state, entity, new BlockContext(side, actor));
    }

    public static @Nullable AuraHandler findItem(ItemStack stack, ItemContext context) {
        return stack.getCapability(ITEM_AURA, context);
    }

    public record BlockContext(@Nullable Direction side, @Nullable LivingEntity actor) {
    }

    // Identity names the physical slot. Detached stack access should be reused within a transaction.
    public record ItemContext(ItemAccess access, Level level, @Nullable LivingEntity actor, Object storageIdentity) {
        public HolderLookup.Provider registries() {
            return this.level.registryAccess();
        }

        public static ItemContext forStack(ItemStack stack, LivingEntity actor) {
            return new ItemContext(ItemAccess.forStack(stack), actor.level(), actor, stack);
        }

        public static ItemContext forHand(Player player, InteractionHand hand) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
            return new ItemContext(ItemAccess.forHandlerIndexStrict(PlayerInventoryWrapper.of(player), slot), player.level(), player, new HandSlot(player, slot));
        }
    }

    private record HandSlot(Player player, int slot) {
    }
}
