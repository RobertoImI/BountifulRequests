package org.crafterscr.bountifulrequests.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.crafterscr.bountifulrequests.data.RequestPublication;
import org.crafterscr.bountifulrequests.data.RequestSavedData;
import org.crafterscr.bountifulrequests.mixin.BoardBlockEntityAccessor;
import org.crafterscr.bountifulrequests.util.RequestBountyData;

import io.ejekta.bountiful.content.board.BoardBlockEntity;
import io.ejekta.bountiful.content.board.BoardInventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Mantiene sincronizadas nuestras publicaciones con cada Board cargado.
 *
 * Esto significa que:
 * - un Board que se cargue mañana recibe las misiones activas;
 * - un Board nuevo recibe las misiones;
 * - una misión ya reclamada desaparece;
 * - una rotación vuelve a aparecer mientras tenga stock.
 */
public final class BoardPublicationService {

    private BoardPublicationService() {
    }

    public static void sync(
            BoardBlockEntity board
    ) {
        if (!(board.getLevel() instanceof ServerLevel level)) {
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

        /*
         * IMPORTANTE:
         *
         * BoardBlockEntity no implementa esta interfaz en tiempo de compilación.
         * Mixin agrega la interfaz en runtime.
         *
         * Como BoardBlockEntity viene de Kotlin y Java la considera final,
         * necesitamos pasar primero por Object para que el compilador permita
         * el cast.
         */
        BoardBlockEntityAccessor accessor =
                (BoardBlockEntityAccessor) (Object) board;

        /*
         * Primero retiramos publicaciones nuestras que ya no deberían
         * aparecer.
         */
        for (int slot = 0;
             slot < 21;
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

            /*
             * Si la publicación ya no existe o dejó de ser visible,
             * retiramos el papel de este Board.
             */
            if (publication == null
                    || !publication.isVisibleOnBoard()) {

                accessor.bountifulrequests$removeBounty(
                        slot
                );

                continue;
            }

            existing.add(id);
        }

        /*
         * Después insertamos las publicaciones activas
         * que todavía no estén presentes en este Board.
         */
        for (RequestPublication publication
                : data.publications.values()) {

            if (!publication.isVisibleOnBoard()) {
                continue;
            }

            /*
             * Evita duplicar la misma publicación en un Board.
             */
            if (existing.contains(
                    publication.id
            )) {
                continue;
            }

            ItemStack paper =
                    BountyPaperFactory.create(
                            publication,
                            level,
                            board.getBlockPos()
                    );

            /*
             * Utilizamos la lógica interna de Bountiful para escoger
             * automáticamente un slot libre del Board.
             */
            accessor.bountifulrequests$addRandom(
                    paper
            );

            /*
             * También lo marcamos localmente para evitar intentar insertar
             * dos veces la misma publicación durante esta sincronización.
             */
            existing.add(
                    publication.id
            );
        }
    }
}