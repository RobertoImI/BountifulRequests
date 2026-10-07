package org.crafterscr.bountifulrequests.menu;

import java.util.UUID;

import org.crafterscr.bountifulrequests.data.RequestSavedData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Backend del GUI.
 */
public final class RequestEditorMenu
        extends AbstractContainerMenu {

    private static final int REWARD_COUNT = 9;

    private final Container rewards;

    private final UUID owner;
    private final MinecraftServer server;

    /**
     * Control visual usado por el cliente.
     *
     * Los slots reales del menú siempre existen para mantener la
     * sincronización con el servidor, pero sólo deben dibujarse y aceptar
     * interacción en la vista principal del editor.
     */
    private boolean mainSlotsVisible = true;

    /**
     * Constructor CLIENT.
     */
    public RequestEditorMenu(
            int containerId,
            Inventory inventory
    ) {
        this(
                containerId,
                inventory,
                new SimpleContainer(9),
                null,
                null
        );
    }

    /**
     * Constructor SERVER.
     */
    public RequestEditorMenu(
            int containerId,
            Inventory inventory,
            Container rewards,
            UUID owner,
            MinecraftServer server
    ) {
        super(
                ModMenus.REQUEST_EDITOR.get(),
                containerId
        );

        this.rewards = rewards;
        this.owner = owner;
        this.server = server;

        // --------------------------------------------------------
        // 3 x 3 reward slots
        // --------------------------------------------------------

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int index =
                        column + row * 3;

                int x =
                        260 + column * 18;

                int y =
                        55 + row * 18;

                addSlot(
                        new Slot(
                                rewards,
                                index,
                                x,
                                y
                        ) {
                            @Override
                            public boolean mayPlace(
                                    ItemStack stack
                            ) {
                                return !isServerLocked();
                            }

                            @Override
                            public boolean mayPickup(
                                    Player player
                            ) {
                                return !isServerLocked();
                            }

                            @Override
                            public boolean isActive() {
                                return mainSlotsVisible;
                            }
                        }
                );
            }
        }

        // --------------------------------------------------------
        // Player inventory
        // --------------------------------------------------------

        int inventoryX = 40;
        int inventoryY = 178;

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(
                        new Slot(
                                inventory,
                                column + row * 9 + 9,
                                inventoryX + column * 18,
                                inventoryY + row * 18
                        ) {
                            @Override
                            public boolean isActive() {
                                return mainSlotsVisible;
                            }
                        }
                );
            }
        }

        // Hotbar
        for (int column = 0; column < 9; column++) {
            addSlot(
                    new Slot(
                            inventory,
                            column,
                            inventoryX + column * 18,
                            inventoryY + 58
                    ) {
                        @Override
                        public boolean isActive() {
                            return mainSlotsVisible;
                        }
                    }
            );
        }
    }

    /**
     * Cambia únicamente la visibilidad/interacción de los slots en el GUI.
     * El servidor conserva el inventario y el escrow sincronizados.
     */
    public void setMainSlotsVisible(boolean visible) {
        this.mainSlotsVisible = visible;
    }

    private boolean isServerLocked() {
        if (server == null || owner == null) {
            return false;
        }

        return RequestSavedData.get(server)
                .getOrCreateDraft(owner)
                .isPending();
    }

    public void clearRewardsAfterServerReset() {
        if (rewards
                instanceof DraftRewardContainer draftContainer) {

            draftContainer.clearWithoutPersist();
            broadcastChanges();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(
            Player player,
            int index
    ) {
        ItemStack empty =
                ItemStack.EMPTY;

        Slot slot =
                getSlot(index);

        if (!slot.hasItem()) {
            return empty;
        }

        ItemStack original =
                slot.getItem();

        ItemStack copy =
                original.copy();

        if (index < REWARD_COUNT) {
            // Reward -> inventory
            if (!moveItemStackTo(
                    original,
                    REWARD_COUNT,
                    slots.size(),
                    true
            )) {
                return ItemStack.EMPTY;
            }
        } else {
            // Inventory -> reward slots
            if (isServerLocked()) {
                return ItemStack.EMPTY;
            }

            if (!moveItemStackTo(
                    original,
                    0,
                    REWARD_COUNT,
                    false
            )) {
                return ItemStack.EMPTY;
            }
        }

        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copy;
    }
}