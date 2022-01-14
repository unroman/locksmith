package gg.moonflower.locksmith.common.lock;

import gg.moonflower.locksmith.api.lock.AbstractLock;
import gg.moonflower.locksmith.api.lock.LockManager;
import gg.moonflower.locksmith.core.Locksmith;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LockInteractionManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Component LOCKED = new TranslatableComponent("lock." + Locksmith.MOD_ID + ".locked");

    public static InteractionResult onRightClickBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        BlockPos pos = hitResult.getBlockPos();
        AbstractLock lock = LockManager.getLock(level, pos);
        if (lock == null || player.isCreative())
            return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        if (Locksmith.CONFIG.enableLockpicking.get() && lock.pick(player, level, pos, stack, hand)) {
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (lock.onRightClick(player, level, stack, hitResult)) {
            return InteractionResult.PASS;
        } else {
            player.displayClientMessage(LOCKED, true);
            if (level.isClientSide()) {
                player.playNotifySound(SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        }
    }

    public static InteractionResult onLeftClickBlock(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
        AbstractLock lock = LockManager.getLock(level, pos);
        if (lock == null || player.isCreative())
            return InteractionResult.PASS;

        if (lock.onLeftClick(player, level, hand, pos, direction))
            return InteractionResult.PASS;
        else {
            player.displayClientMessage(LOCKED, true);
            return InteractionResult.FAIL;
        }
    }

    public static void onBreakBlock(Level level, BlockPos pos, BlockState state) {
        AbstractLock lock = LockManager.getLock(level, pos);
        if (lock == null)
            return;

        if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            Tag tag = AbstractLock.CODEC.encodeStart(NbtOps.INSTANCE, lock).getOrThrow(false, LOGGER::error);
            if (!(tag instanceof CompoundTag))
                return;

            CompoundTag lockTag = (CompoundTag) tag;
            lockTag.put("pos", BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, lock.getPos().relative(ChestBlock.getConnectedDirection(state))).getOrThrow(false, LOGGER::error));
            AbstractLock newLock = AbstractLock.CODEC.parse(NbtOps.INSTANCE, lockTag).getOrThrow(false, LOGGER::error);
            LockManager.get(level).addLock(newLock);
            return;
        }

        LockManager.get(level).removeLock(lock.getPos(), pos, false);
    }
}
