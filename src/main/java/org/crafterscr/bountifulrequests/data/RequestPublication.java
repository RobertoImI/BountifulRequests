package org.crafterscr.bountifulrequests.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.item.ItemStack;

/**
 * Una misión ya publicada.
 */
public final class RequestPublication {

    public enum Kind {
        /**
         * Genera un Bountiful paper físico y se lo entrega al creador.
         */
        HANDOUT,

        /**
         * Aparece en todos los Bountiful Boards.
         * Sólo puede ser reclamada una vez.
         */
        BOARD,

        /**
         * Publicación administrativa repetible.
         * Cada claim consume un reward bundle real.
         */
        ROTATION
    }

    public enum State {
        OPEN,
        CLAIMED,
        COMPLETED,
        EXPIRED,

        /**
         * Ya no acepta nuevos jugadores, pero hay claims existentes
         * que siguen estando garantizados.
         */
        CLOSING,

        REMOVED
    }

    public UUID id;
    public UUID owner;

    public String title;

    public int rarity;
    public long durationSeconds;

    public Kind kind;
    public State state = State.OPEN;

    public long publishTick;

    public final List<ObjectiveSpec> objectives = new ArrayList<>();

    /**
     * Cada elemento es UNA recompensa completa.
     *
     * Ejemplo:
     * reward = 5 diamantes
     * usos = 10
     *
     * Aquí existirán físicamente 10 bundles de 5 diamantes.
     */
    public final List<List<ItemStack>> availableBundles =
            new ArrayList<>();

    public final Map<UUID, ActiveClaim> activeClaims =
            new LinkedHashMap<>();

    public boolean isRotation() {
        return kind == Kind.ROTATION;
    }

    public boolean isVisibleOnBoard() {
        if (state != State.OPEN) {
            return false;
        }

        return switch (kind) {
            case BOARD -> !availableBundles.isEmpty();
            case ROTATION -> !availableBundles.isEmpty();
            case HANDOUT -> false;
        };
    }
}