package org.crafterscr.bountifulrequests.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.crafterscr.bountifulrequests.BountifulRequests;
import org.crafterscr.bountifulrequests.data.RequestPublication;
import org.crafterscr.bountifulrequests.data.RequestSavedData;
import org.crafterscr.bountifulrequests.mixin.BoardBlockEntityAccessor;
import org.crafterscr.bountifulrequests.util.RequestBountyData;

import io.ejekta.bountiful.content.board.BoardBlockEntity;
import io.ejekta.bountiful.content.board.BoardInventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Sincroniza publicaciones personalizadas con cada Bountiful Board.
 *
 * Prioridad del tablón:
 * 1. ROTATION activas del ciclo actual.
 * 2. BOARD publicados directamente por jugadores/admins.
 * 3. Bounties default de Bountiful, cuando defaults=on.
 *
 * De esta forma las plazas configuradas de rotación siguen apareciendo incluso
 * cuando el servidor tiene muchos pedidos de jugadores.
 */
public final class BoardPublicationService {

    private static final int BOUNTY_SLOT_COUNT = 21;

    private BoardPublicationService() {
    }

    public static void sync(
            BoardBlockEntity board
    ) {
        if (!(board.getLevel()
                instanceof ServerLevel level)) {

            return;
        }

        RequestSavedData data =
                RequestSavedData.get(
                        level.getServer()
                );

        /*
         * Protección extra de orden de ticks:
         * si Bountiful elimina un papel vencido antes de que RotationManager
         * ejecute su tick, no permitimos que sync() lo vuelva a crear durante
         * ese pequeño intervalo.
         */
        long now =
                level.getGameTime();

        boolean expiredRotation = false;

        for (RequestPublication publication
                : data.publications.values()) {

            if (!publication.isRotation()
                    || !publication.rotationActive) {

                continue;
            }

            if (publication.rotationVisibleUntilTick > 0L
                    && now >= publication.rotationVisibleUntilTick) {

                publication.rotationActive = false;
                publication.rotationAppearanceStartTick = 0L;
                publication.rotationVisibleUntilTick = 0L;

                expiredRotation = true;
            }
        }

        if (expiredRotation) {
            data.setDirty();
        }

        BoardInventory inventory =
                board.fullInventoryCopy();

        Set<UUID> existing =
                new HashSet<>();

        BoardBlockEntityAccessor accessor =
                (BoardBlockEntityAccessor) (Object) board;

        /*
         * 1) Limpieza:
         * - publicaciones eliminadas/completadas/no visibles;
         * - bounties default cuando custom-only está activado.
         */
        for (int slot = 0;
             slot < BOUNTY_SLOT_COUNT;
             slot++) {

            ItemStack stack =
                    inventory.getItem(slot);

            if (stack.isEmpty()) {
                continue;
            }

            UUID id =
                    RequestBountyData.getRequestId(
                            stack
                    );

            if (id == null) {
                if (!data.areDefaultBountifulRequestsEnabled()) {
                    removeSlot(
                            accessor,
                            board,
                            inventory,
                            slot
                    );
                }

                continue;
            }

            RequestPublication publication =
                    data.publications.get(id);

            if (publication == null
                    || !publication.isVisibleOnBoard()) {

                removeSlot(
                        accessor,
                        board,
                        inventory,
                        slot
                );

                continue;
            }

            existing.add(id);
        }

        /*
         * 2) Las rotaciones activas se insertan primero.
         */
        List<RequestPublication> visible =
                data.publications.values()
                        .stream()
                        .filter(
                                RequestPublication::isVisibleOnBoard
                        )
                        .sorted(
                                Comparator.comparing(
                                        publication ->
                                                publication.isRotation()
                                                        ? 0
                                                        : 1
                                )
                        )
                        .toList();

        for (RequestPublication publication
                : visible) {

            if (existing.contains(
                    publication.id
            )) {
                continue;
            }

            int targetSlot =
                    findInsertionSlot(
                            inventory,
                            data,
                            publication.isRotation()
                    );

            if (targetSlot < 0) {
                BountifulRequests.LOGGER.warn(
                        "Could not publish request #{} ({}) to Bountiful board at {}: no eligible bounty slot is available.",
                        publication.shortId,
                        publication.title,
                        board.getBlockPos()
                );

                continue;
            }

            /*
             * Si una ROTATION necesita una plaza y el Board está totalmente
             * ocupado por publicaciones normales nuestras, retiramos una de
             * esas publicaciones del Board (NO de SavedData). Podrá reaparecer
             * cuando exista espacio.
             */
            ItemStack replaced =
                    inventory.getItem(
                            targetSlot
                    );

            UUID replacedId =
                    RequestBountyData.getRequestId(
                            replaced
                    );

            if (!replaced.isEmpty()) {
                removeSlot(
                        accessor,
                        board,
                        inventory,
                        targetSlot
                );

                if (replacedId != null) {
                    existing.remove(
                            replacedId
                    );
                }
            }

            ItemStack paper =
                    BountyPaperFactory.create(
                            publication,
                            level,
                            board.getBlockPos()
                    );

            accessor.bountifulrequests$addBounty(
                    targetSlot,
                    paper
            );

            inventory.setItem(
                    targetSlot,
                    paper.copy()
            );

            existing.add(
                    publication.id
            );

            board.setChanged();
        }
    }

    private static void removeSlot(
            BoardBlockEntityAccessor accessor,
            BoardBlockEntity board,
            BoardInventory inventory,
            int slot
    ) {
        accessor.bountifulrequests$removeBounty(
                slot
        );

        inventory.removeItemNoUpdate(
                slot
        );

        board.setChanged();
    }

    /**
     * Busca una plaza respetando prioridad.
     *
     * Para cualquier publicación:
     * 1. vacío;
     * 2. bounty default de Bountiful.
     *
     * Para ROTATION además:
     * 3. una publicación BOARD de nuestro addon.
     *
     * Nunca reemplazamos otra ROTATION activa.
     */
    private static int findInsertionSlot(
            BoardInventory inventory,
            RequestSavedData data,
            boolean rotationPriority
    ) {
        List<Integer> emptySlots =
                new ArrayList<>();

        List<Integer> defaultSlots =
                new ArrayList<>();

        List<Integer> normalRequestSlots =
                new ArrayList<>();

        for (int slot = 0;
             slot < BOUNTY_SLOT_COUNT;
             slot++) {

            ItemStack stack =
                    inventory.getItem(slot);

            if (stack.isEmpty()) {
                emptySlots.add(slot);
                continue;
            }

            UUID id =
                    RequestBountyData.getRequestId(
                            stack
                    );

            if (id == null) {
                defaultSlots.add(slot);
                continue;
            }

            RequestPublication existingPublication =
                    data.publications.get(id);

            if (existingPublication != null
                    && !existingPublication.isRotation()) {

                normalRequestSlots.add(slot);
            }
        }

        /*
         * Igual que la sensación del Bountiful original: no llenamos el Board
         * de izquierda a derecha. Cada publicación elige una posición al azar
         * dentro del grupo de slots que puede ocupar.
         */
        if (!emptySlots.isEmpty()) {
            return randomSlot(
                    emptySlots
            );
        }

        if (!defaultSlots.isEmpty()) {
            return randomSlot(
                    defaultSlots
            );
        }

        if (rotationPriority
                && !normalRequestSlots.isEmpty()) {

            return randomSlot(
                    normalRequestSlots
            );
        }

        return -1;
    }

    private static int randomSlot(
            List<Integer> slots
    ) {
        return slots.get(
                ThreadLocalRandom.current()
                        .nextInt(
                                slots.size()
                        )
        );
    }
}
