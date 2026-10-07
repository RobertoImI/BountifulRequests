package org.crafterscr.bountifulrequests.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.item.ItemStack;

/**
 * Reserva una recompensa concreta para el jugador que tomó el papel.
 *
 * Una vez reservado, ni siquiera eliminar la rotación puede quitarle
 * esa recompensa al jugador.
 */
public final class ActiveClaim {

    public final UUID player;

    public long expiresAtTick;

    public final List<ItemStack> rewardBundle = new ArrayList<>();

    /**
     * Progreso server-side para objetivos que no pertenecen al sistema
     * nativo de Bountiful, como las misiones opcionales de Cobblemon.
     *
     * Key = índice del objetivo dentro de RequestPublication.objectives.
     */
    public final Map<Integer, Integer> objectiveProgress =
            new LinkedHashMap<>();

    public ActiveClaim(UUID player, long expiresAtTick) {
        this.player = player;
        this.expiresAtTick = expiresAtTick;
    }
}