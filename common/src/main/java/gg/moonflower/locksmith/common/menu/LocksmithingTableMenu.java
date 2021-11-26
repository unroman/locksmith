package gg.moonflower.locksmith.common.menu;

import com.mojang.datafixers.util.Pair;
import gg.moonflower.locksmith.common.item.KeyItem;
import gg.moonflower.locksmith.core.Locksmith;
import gg.moonflower.locksmith.core.registry.LocksmithBlocks;
import gg.moonflower.locksmith.core.registry.LocksmithItems;
import gg.moonflower.locksmith.core.registry.LocksmithMenus;
import gg.moonflower.locksmith.core.registry.LocksmithSounds;
import gg.moonflower.pollen.api.util.QuickMoveHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class LocksmithingTableMenu extends AbstractContainerMenu {
    public static final ResourceLocation EMPTY_SLOT_KEY = new ResourceLocation(Locksmith.MOD_ID, "item/empty_locksmithing_table_slot_key");
    private static final QuickMoveHelper MOVE_HELPER = new QuickMoveHelper().
            add(0, 4, 4, 36, false). // to Inventory
                    add(4, 36, 0, 2, false); // from Inventory

    private final ContainerLevelAccess access;
    private final Slot keyInputSlot;
    private final Container inputSlots = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            LocksmithingTableMenu.this.slotsChanged(this);
        }

        // TODO: make result
    };
    private final Slot inputSlot;    private final Container resultSlots = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            LocksmithingTableMenu.this.slotsChanged(this);
        }
    };
    private long lastSoundTime;
    private boolean pendingResult;
    public LocksmithingTableMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }
    public LocksmithingTableMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(LocksmithMenus.LOCKSMITHING_TABLE_MENU.get(), containerId);
        this.access = access;

        this.keyInputSlot = this.addSlot(new Slot(this.inputSlots, 0, 21, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == LocksmithItems.KEY.get() || stack.getItem() == LocksmithItems.BLANK_KEY.get();
            }

            @Override
            public ItemStack onTake(Player player, ItemStack stack) {
                LocksmithingTableMenu.this.pendingResult = false;
                return super.onTake(player, stack);
            }

            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY_SLOT_KEY);
            }
        });
        this.inputSlot = this.addSlot(new Slot(this.inputSlots, 1, 21, 54) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == LocksmithItems.BLANK_KEY.get() || stack.getItem() == LocksmithItems.BLANK_LOCK.get();
            }

            @Override
            public ItemStack onTake(Player player, ItemStack stack) {
                LocksmithingTableMenu.this.pendingResult = false;
                return super.onTake(player, stack);
            }
        });

        this.addSlot(new ResultSlot(0, 101, 35));
        this.addSlot(new ResultSlot(1, 131, 35));

        for (int x = 0; x < 3; ++x) {
            for (int y = 0; y < 9; ++y) {
                this.addSlot(new Slot(inventory, y + x * 9 + 9, 8 + y * 18, 84 + x * 18));
            }
        }

        for (int hotbarIndex = 0; hotbarIndex < 9; ++hotbarIndex) {
            this.addSlot(new Slot(inventory, hotbarIndex, 8 + hotbarIndex * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, LocksmithBlocks.LOCKSMITHING_TABLE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, blockPos) -> this.clearContainer(player, level, this.inputSlots));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return MOVE_HELPER.performAction(this, slot);
    }

    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (inventory == this.inputSlots) {
            this.createResult();
        }
    }

    private void createResult() {
        if (this.pendingResult)
            return;

        this.resultSlots.setItem(0, ItemStack.EMPTY);
        this.resultSlots.setItem(1, ItemStack.EMPTY);
        if (!this.isValid())
            return;

        ItemStack keyStack = this.keyInputSlot.getItem();
        ItemStack inputStack = this.inputSlot.getItem();
        boolean blank = this.keyInputSlot.getItem().getItem() == LocksmithItems.BLANK_KEY.get();

        ItemStack result;
        if (inputStack.getItem() == LocksmithItems.BLANK_LOCK.get()) {
            result = new ItemStack(LocksmithItems.LOCK.get());
        } else if (inputStack.getItem() == LocksmithItems.BLANK_KEY.get()) {
            result = new ItemStack(LocksmithItems.KEY.get());
        } else return;

        if (inputStack.hasCustomHoverName())
            result.setHoverName(keyStack.getHoverName());

        if (blank && this.isValidInputItem(inputStack)) {
            UUID id = UUID.randomUUID();

            ItemStack newKey = new ItemStack(LocksmithItems.KEY.get());
            if (keyStack.hasCustomHoverName())
                newKey.setHoverName(keyStack.getHoverName());
            KeyItem.setOriginal(newKey, true);
            KeyItem.setLockId(newKey, id);
            KeyItem.setLockId(result, id);

            this.resultSlots.setItem(0, newKey);
            this.resultSlots.setItem(1, result);
        } else if (!blank && this.isValidInputItem(inputStack)) {
            UUID id = KeyItem.getLockId(keyStack);
            if (id == null)
                return;

            KeyItem.setLockId(result, id);
            this.resultSlots.setItem(0, keyStack.copy());
            this.resultSlots.setItem(1, result);
        }

        this.pendingResult = true;
    }

    private boolean isValid() {
        if (!this.keyInputSlot.hasItem() && !this.inputSlot.hasItem())
            return false;

        Item key = this.keyInputSlot.getItem().getItem();
        if (key != LocksmithItems.KEY.get() && key != LocksmithItems.BLANK_KEY.get())
            return false;
        if (!this.isValidInputItem(this.inputSlot.getItem()))
            return false;

        return key != LocksmithItems.KEY.get() || (KeyItem.isOriginal(this.keyInputSlot.getItem()) && KeyItem.getLockId(this.keyInputSlot.getItem()) != null);
    }

    private boolean isValidInputItem(ItemStack stack) {
        return stack.getItem() == LocksmithItems.BLANK_LOCK.get() || stack.getItem() == LocksmithItems.BLANK_KEY.get();
    }

    class ResultSlot extends Slot {
        public ResultSlot(int index, int x, int y) {
            super(LocksmithingTableMenu.this.resultSlots, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack onTake(Player player, ItemStack stack) {
            LocksmithingTableMenu.this.keyInputSlot.remove(1);
            LocksmithingTableMenu.this.inputSlot.remove(1);

            super.onTake(player, stack);

            LocksmithingTableMenu.this.pendingResult = !LocksmithingTableMenu.this.resultSlots.getItem(0).isEmpty() || !LocksmithingTableMenu.this.resultSlots.getItem(1).isEmpty();

            LocksmithingTableMenu.this.createResult();
            LocksmithingTableMenu.this.broadcastChanges();

            LocksmithingTableMenu.this.access.execute((level, pos) -> {
                long l = level.getGameTime();
                if (LocksmithingTableMenu.this.lastSoundTime != l) {
                    level.playSound(null, pos, LocksmithSounds.UI_LOCKSMITHING_TABLE_TAKE_RESULT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    LocksmithingTableMenu.this.lastSoundTime = l;
                }

            });
            return stack;
        }
    }




}