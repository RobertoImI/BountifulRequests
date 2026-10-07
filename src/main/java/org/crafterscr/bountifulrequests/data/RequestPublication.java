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
         * Plantilla administrativa permanente.
         *
         * Entra y sale del tablón según el gestor de rotaciones y genera una
         * copia de su recompensa por cada jugador que la completa.
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

    /**
     * ID corto y amigable para administración (/bo requests remove 12).
     * El UUID se mantiene internamente como identidad real.
     */
    public int shortId;

    public UUID owner;

    public String title;

    public int rarity;
    public long durationSeconds;

    public Kind kind;
    public State state = State.OPEN;

    public long publishTick;

    /**
     * Sólo ROTATION: indica si esta plantilla forma parte del conjunto
     * visible durante el ciclo actual.
     */
    public boolean rotationActive = false;

    /**
     * Ventana global de la aparición actual de una ROTATION.
     *
     * Todos los Boards muestran exactamente la misma aparición y el mismo
     * contador. Si alguien la toma o se vence, rotationActive pasa a false
     * hasta que el gestor la elija de nuevo en una rotación futura.
     */
    public long rotationAppearanceStartTick = 0L;
    public long rotationVisibleUntilTick = 0L;

    public final List<ObjectiveSpec> objectives = new ArrayList<>();

    /**
     * Escrow físico para BOARD/HANDOUT.
     *
     * Las ROTATION ya no consumen bundles finitos.
     */
    public final List<List<ItemStack>> availableBundles =
            new ArrayList<>();

    /**
     * Sólo ROTATION.
     *
     * Plantilla exacta de recompensa definida por el administrador.
     * Se copia para cada claim; nunca se consume ni se agota.
     */
    public final List<ItemStack> rotationRewardTemplate =
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
            case ROTATION ->
                    rotationActive
                            && !rotationRewardTemplate.isEmpty();
            case HANDOUT -> false;
        };
    }
}