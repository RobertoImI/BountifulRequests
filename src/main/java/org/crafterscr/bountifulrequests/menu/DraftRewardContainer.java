package org.crafterscr.bountifulrequests.menu;

import java.util.UUID;

import org.crafterscr.bountifulrequests.data.RequestDraft;
import org.crafterscr.bountifulrequests.data.RequestSavedData;

import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Los slots del editor están conectados directamente al SavedData.
 */
public final class DraftRewardContainer
        extends SimpleContainer {

    private final MinecraftServer server;
    private final UUID owner;

    private boolean suppressSave = false;

    public DraftRewardContainer(
            MinecraftServer server,
            UUID owner
    ) {
        super(9);

        this.server = server;
        this.owner = owner;

        RequestDraft draft =
                RequestSavedData.get(server)
                        .getOrCreateDraft(owner);

        suppressSave = true;

        for (int i = 0; i < 9; i++) {
            setItem(
                    i,
                    draft.rewards.get(i).copy()
            );
        }

        suppressSave = false;
    }

    @Override
    public void setChanged() {
        super.setChanged();

        if (suppressSave) {
            return;
        }

        RequestSavedData data =
                RequestSavedData.get(server);

        RequestDraft draft =
                data.getOrCreateDraft(owner);

        NonNullList<ItemStack> stacks =
                NonNullList.withSize(
                        9,
                        ItemStack.EMPTY
                );

        for (int i = 0; i < 9; i++) {
            stacks.set(
                    i,
                    getItem(i).copy()
            );
        }

        draft.rewards = stacks;

        data.setDirty();
    }

    /**
     * Se usa cuando los objetos ya fueron transferidos a una publicación.
     */
    public void clearWithoutPersist() {
        suppressSave = true;

        clearContent();

        suppressSave = false;
    }
}