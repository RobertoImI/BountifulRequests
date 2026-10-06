package org.crafterscr.bountifulrequests.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * Misión que todavía está siendo editada.
 *
 * Los objetos de rewards YA no están en el inventario del jugador.
 * Están físicamente almacenados aquí.
 */
public final class RequestDraft {

    public enum PendingMode {
        NONE,
        HANDOUT,
        BOARD,
        ROTATION
    }

    public final UUID owner;

    public String title = "";
    public int rarity = 0;

    // 30 minutos por defecto.
    public long durationSeconds = 30 * 60L;

    public final List<ObjectiveSpec> objectives = new ArrayList<>();

    // 9 slots físicos de recompensa.
    public NonNullList<ItemStack> rewards =
            NonNullList.withSize(9, ItemStack.EMPTY);

    public PendingMode pendingMode = PendingMode.NONE;

    // Momento en que terminarán los 10 segundos de anulación.
    public long pendingUntilTick = -1;

    // Sólo usado para ROTATION.
    public int rotationUses = 1;

    /**
     * Las recompensas adicionales de una rotación se retiran del inventario
     * inmediatamente al pulsar "Add to Rotation".
     */
    public final List<List<ItemStack>> pendingExtraBundles =
            new ArrayList<>();

    public RequestDraft(UUID owner) {
        this.owner = owner;
    }

    public boolean isPending() {
        return pendingMode != PendingMode.NONE;
    }

    public void clearPending() {
        pendingMode = PendingMode.NONE;
        pendingUntilTick = -1;
        rotationUses = 1;
        pendingExtraBundles.clear();
    }

    /**
     * Limpia el editor después de que la publicación se haya realizado.
     */
    public void resetAfterPublish() {
        title = "";
        rarity = 0;
        durationSeconds = 30 * 60L;

        objectives.clear();

        rewards = NonNullList.withSize(9, ItemStack.EMPTY);

        clearPending();
    }
}