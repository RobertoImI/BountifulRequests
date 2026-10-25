package org.crafterscr.bountifulrequests.mixin;

import org.crafterscr.bountifulrequests.BountifulRequests;
import org.crafterscr.bountifulrequests.data.RequestSavedData;
import org.crafterscr.bountifulrequests.service.BoardPublicationService;

import io.ejekta.bountiful.content.BountifulContent;
import io.ejekta.bountiful.content.board.BoardBlockEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sincroniza periódicamente el Board con las publicaciones del addon.
 *
 * Nos enganchamos a upkeepTryInitialPopulation(), que es un método de
 * instancia llamado por Bountiful desde su ticker del servidor. Esto evita
 * depender del bridge estático generado por Kotlin para tick().
 */
@Mixin(
        value = BoardBlockEntity.class,
        remap = false
)
public abstract class BoardBlockEntityMixin {

    /**
     * Único punto que crea bounties automáticos de Bountiful, tanto durante
     * la población inicial como en las actualizaciones periódicas.
     */
    @Inject(
            method = "randomlyUpdateBoard",
            at = @At("HEAD"),
            cancellable = true
    )
    private void bountifulrequests$disableDefaultGeneration(
            CallbackInfo ci
    ) {
        BoardBlockEntity board =
                (BoardBlockEntity) (Object) this;

        if (!(board.getLevel()
                instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }

        RequestSavedData data =
                RequestSavedData.get(
                        level.getServer()
                );

        if (!data.areDefaultBountifulRequestsEnabled()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "upkeepTryInitialPopulation",
            at = @At("TAIL")
    )
    private void bountifulrequests$syncPublishedRequests(
            CallbackInfo ci
    ) {
        BoardBlockEntity board =
                (BoardBlockEntity) (Object) this;

        Level level =
                board.getLevel();

        if (level == null
                || level.isClientSide()) {
            return;
        }

        /*
         * Una vez por segundo es suficiente y evita recorrer SavedData
         * veinte veces por segundo por cada Board.
         */
        if (level.getGameTime() % 20L != 0L) {
            return;
        }

        BoardPublicationService.sync(
                board
        );
    }

    /**
     * Bountiful 8.0.0-beta.2 assumes every inserted decree has DECREE_DATA.
     * Invalid or legacy decree stacks may lack the component, causing an NPE
     * inside checkUserPlacedAllDecrees() during a board container click.
     *
     * Skip only the unsafe completion check for that stack; leave regular
     * decrees and the board inventory untouched.
     */
    @Inject(
            method = "checkUserPlacedAllDecrees(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void bountifulrequests$guardMissingDecreeData(
            ServerPlayer player,
            ItemStack decreeStack,
            CallbackInfo ci
    ) {
        // Empty stacks already return safely in Bountiful's own method.
        if (decreeStack.isEmpty()) {
            return;
        }

        if (decreeStack.get(BountifulContent.INSTANCE.getDECREE_DATA()) != null) {
            return;
        }

        BountifulRequests.LOGGER.warn(
                "Skipped unsafe Bountiful decree completion check: " +
                        "missing DECREE_DATA at board {} (player: {}, item: {}).",
                ((BoardBlockEntity) (Object) this).getBlockPos(),
                player.getGameProfile().getName(),
                decreeStack.getItem()
        );

        ci.cancel();
    }
}
