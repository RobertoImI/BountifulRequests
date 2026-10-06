package org.crafterscr.bountifulrequests.service;

import java.util.List;
import java.util.UUID;

import org.crafterscr.bountifulrequests.data.RequestPublication;
import org.crafterscr.bountifulrequests.data.RequestSavedData;
import org.crafterscr.bountifulrequests.util.RequestBountyData;

import io.ejekta.bountiful.bounty.types.IBountyObjective;
import io.ejekta.bountiful.bounty.types.Progress;
import io.ejekta.bountiful.components.BountyDataEntry;
import io.ejekta.bountiful.components.BountyStack;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Reemplaza SOLAMENTE el cash-in de nuestros papeles.
 *
 * Los Bounties normales de Bountiful siguen utilizando la lógica
 * original sin alteraciones.
 */
public final class RequestCashInService {

    private RequestCashInService() {
    }

    public static Boolean tryCashIn(
            ServerPlayer player,
            ItemStack paper
    ) {
        UUID requestId =
                RequestBountyData.getRequestId(
                        paper
                );

        /*
         * null significa:
         * "esto no es nuestro, deja que Bountiful continúe normalmente".
         */
        if (requestId == null) {
            return null;
        }

        RequestSavedData data =
                RequestSavedData.get(player.server);

        RequestPublication publication =
                data.publications.get(requestId);

        if (publication == null) {
            player.sendSystemMessage(
                    Component.translatable(
                            "bountifulrequests.message.invalid_request"
                    )
            );

            return false;
        }

        BountyStack bounty =
                new BountyStack(paper);

        if (bounty.getInfo()
                .timeLeftTicks(
                        player.level()
                ) <= 0) {

            player.sendSystemMessage(
                    Component.translatable(
                            "bountifulrequests.message.expired"
                    )
            );

            return false;
        }

        /*
         * HANDOUT se reclama al intentar completarlo.
         */
        if (!RequestManager.ensureHandoutClaim(
                player,
                paper,
                publication
        )) {
            player.sendSystemMessage(
                    Component.translatable(
                            "bountifulrequests.message.not_claimed"
                    )
            );

            return false;
        }

        /*
         * Verificamos TODOS los objetivos antes de consumir absolutamente
         * nada.
         */
        for (BountyDataEntry entry
                : bounty.getObjs()) {

            if (!(entry.getLogic()
                    instanceof IBountyObjective objective)) {

                return false;
            }

            Progress progress =
                    objective.getProgress(
                            entry,
                            player,
                            bounty.progressOf(entry)
                    );

            if (!progress.isComplete()) {
                player.sendSystemMessage(
                        Component.translatable(
                                "bountiful.tooltip.requirements"
                        )
                );

                return false;
            }
        }

        /*
         * Snapshot por seguridad.
         *
         * Si un objetivo falla al consumir después de que otro ya quitó
         * objetos, restauramos todo.
         */
        InventoryUtil.InventorySnapshot snapshot =
                InventoryUtil.InventorySnapshot.capture(
                        player
                );

        for (BountyDataEntry entry
                : bounty.getObjs()) {

            if (!(entry.getLogic()
                    instanceof IBountyObjective objective)) {

                snapshot.restore(player);
                return false;
            }

            boolean consumed =
                    objective.consumeObjectives(
                            entry,
                            player,
                            bounty.progressOf(entry)
                    );

            if (!consumed) {
                snapshot.restore(player);

                player.sendSystemMessage(
                        Component.translatable(
                                "bountiful.tooltip.requirements"
                        )
                );

                return false;
            }
        }

        /*
         * Esto obtiene exactamente los stacks que Bountiful consumió.
         *
         * Así funcionan también:
         * - Item
         * - Item Tag
         * - Items con componentes especiales
         * - Entradas Bountiful existentes
         */
        List<ItemStack> delivered =
                snapshot.removedItems(
                        player
                );

        boolean completed =
                RequestManager.complete(
                        player,
                        requestId,
                        delivered
                );

        if (!completed) {
            snapshot.restore(player);

            return false;
        }

        return true;
    }
}