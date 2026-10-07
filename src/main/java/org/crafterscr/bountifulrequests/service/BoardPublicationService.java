package org.crafterscr.bountifulrequests.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
 * Mantiene sincronizadas nuestras publicaciones con cada Bountiful Board.
 *
 * Reglas importantes:
 * - una publicación BOARD/ROTATION activa debe aparecer en cada Board cargado;
 * - nunca duplicamos el mismo request en el mismo Board;
 * - si el Board está lleno, preferimos reemplazar un bounty NORMAL de
 *   Bountiful antes que perder una publicación creada por un jugador;
 * - nunca reemplazamos otro request de nuestro addon para hacer espacio.
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

        BoardInventory inventory =
                board.fullInventoryCopy();

        Set<UUID> existing =
                new HashSet<>();

        BoardBlockEntityAccessor accessor =
                (BoardBlockEntityAccessor) (Object) board;

        /*
         * 1) Limpiar requests que ya no deberían seguir visibles.
         */
        for (int slot = 0;
             slot < BOUNTY_SLOT_COUNT;
             slot++) {

            ItemStack stack =
                    inventory.getItem(slot);

            UUID id =
                    RequestBountyData.getRequestId(
                            stack
                    );

            if (id == null) {
                continue;
            }

            RequestPublication publication =
                    data.publications.get(id);

            if (publication == null
                    || !publication.isVisibleOnBoard()) {

                accessor.bountifulrequests$removeBounty(
                        slot
                );

                /*
                 * También actualizamos nuestra copia local para que este
                 * mismo ciclo pueda reutilizar inmediatamente el slot.
                 */
                inventory.removeItemNoUpdate(slot);

                continue;
            }

            existing.add(id);
        }

        /*
         * 2) Insertar publicaciones activas que falten.
         */
        for (RequestPublication publication
                : data.publications.values()) {

            if (!publication.isVisibleOnBoard()) {
                continue;
            }

            if (existing.contains(
                    publication.id
            )) {
                continue;
            }

            int targetSlot =
                    findInsertionSlot(
                            inventory
                    );

            if (targetSlot < 0) {
                /*
                 * Esto sólo ocurre si los 21 espacios están ocupados por
                 * requests activos de nuestro propio addon. En ese caso no
                 * sacrificamos otro contrato respaldado por escrow.
                 */
                BountifulRequests.LOGGER.warn(
                        "Could not publish request {} to Bountiful board at {}: all bounty slots are occupied by active requests.",
                        publication.id,
                        board.getBlockPos()
                );

                continue;
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

            /*
             * addBounty() no llama setChanged() por sí mismo cuando lo
             * invocamos directamente. Marcamos el BlockEntity para que la
             * publicación persista al guardar el mundo.
             */
            board.setChanged();
        }
    }

    /**
     * Prioridad:
     * 1. slot vacío;
     * 2. slot ocupado por un bounty normal de Bountiful;
     * 3. nunca reemplazar otro request de nuestro addon.
     */
    private static int findInsertionSlot(
            BoardInventory inventory
    ) {
        for (int slot = 0;
             slot < BOUNTY_SLOT_COUNT;
             slot++) {

            if (inventory.getItem(slot)
                    .isEmpty()) {

                return slot;
            }
        }

        for (int slot = 0;
             slot < BOUNTY_SLOT_COUNT;
             slot++) {

            ItemStack stack =
                    inventory.getItem(slot);

            if (RequestBountyData.getRequestId(
                    stack
            ) == null) {

                return slot;
            }
        }

        return -1;
    }
}
