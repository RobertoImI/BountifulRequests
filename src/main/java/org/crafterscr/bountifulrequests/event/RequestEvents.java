package org.crafterscr.bountifulrequests.event;

import org.crafterscr.bountifulrequests.BountifulRequests;
import org.crafterscr.bountifulrequests.command.RequestsCommands;
import org.crafterscr.bountifulrequests.menu.RequestEditorOpener;
import org.crafterscr.bountifulrequests.network.RequestNetwork;
import org.crafterscr.bountifulrequests.service.RequestManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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

        if (!event.getEntity()
                .isShiftKeyDown()) {
            return;
        }

        if (!event.getItemStack()
                .isEmpty()) {
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

    /**
     * Si pierde conexión durante la cuenta regresiva,
     * anulamos la publicación para evitar accidentes.
     */
    @SubscribeEvent
    public static void logout(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        RequestManager.undoPending(
                player
        );
    }
}