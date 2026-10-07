package org.crafterscr.bountifulrequests.event;

import org.crafterscr.bountifulrequests.BountifulRequests;
import org.crafterscr.bountifulrequests.command.RequestsCommands;
import org.crafterscr.bountifulrequests.menu.RequestEditorOpener;
import org.crafterscr.bountifulrequests.network.RequestNetwork;
import org.crafterscr.bountifulrequests.service.BoardPublicationService;
import org.crafterscr.bountifulrequests.service.RequestManager;

import net.minecraft.core.registries.BuiltInRegistries;
import io.ejekta.bountiful.content.board.BoardBlockEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(
        modid = BountifulRequests.MOD_ID
)
public final class RequestEvents {

    private static final ResourceLocation BOUNTIFUL_BOARD =
            ResourceLocation.fromNamespaceAndPath(
                    "bountiful",
                    "bountyboard"
            );

    private RequestEvents() {
    }

    /**
     * LOWEST garantiza que Bountiful ya haya creado /bo.
     */
    @SubscribeEvent(
            priority = EventPriority.LOWEST
    )
    public static void registerCommands(
            RegisterCommandsEvent event
    ) {
        RequestsCommands.register(
                event.getDispatcher()
        );
    }

    /**
     * Jugadores normales:
     *
     * Shift + clic derecho + mano vacía sobre un Bountiful Board.
     */
    @SubscribeEvent
    public static void rightClickBoard(
            PlayerInteractEvent.RightClickBlock event
    ) {
        if (event.getHand()
                != InteractionHand.MAIN_HAND) {
            return;
        }

        ResourceLocation blockId =
                BuiltInRegistries.BLOCK.getKey(
                        event.getLevel()
                                .getBlockState(
                                        event.getPos()
                                )
                                .getBlock()
                );

        if (!BOUNTIFUL_BOARD.equals(
                blockId
        )) {
            return;
        }

        if (event.getLevel()
                .isClientSide()) {
            return;
        }

        /*
         * Antes de que Bountiful abra su GUI sincronizamos el Board.
         * De esta forma una misión recién creada con "Publicar ahora"
         * aparece en el mismo momento en que cualquier jugador abre el
         * tablón, incluso si todavía no llegó el siguiente tick de sync.
         */
        if (event.getLevel()
                .getBlockEntity(
                        event.getPos()
                ) instanceof BoardBlockEntity board) {

            BoardPublicationService.sync(
                    board
            );
        }

        /*
         * Clic normal:
         * dejamos que Bountiful abra su tablero como siempre.
         */
        if (!event.getEntity()
                .isShiftKeyDown()) {
            return;
        }

        /*
         * Shift + mano vacía:
         * abre nuestro editor de encargos.
         */
        if (!event.getItemStack()
                .isEmpty()) {
            return;
        }

        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        RequestEditorOpener.open(
                player
        );

        event.setCancellationResult(
                InteractionResult.SUCCESS
        );

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void serverTick(
            ServerTickEvent.Post event
    ) {
        /*
         * El Manager internamente puede trabajar cada tick,
         * pero actualmente las operaciones son muy pequeñas.
         */
        RequestManager.tick(
                event.getServer()
        );
    }

}