package org.crafterscr.bountifulrequests.mixin;

import org.crafterscr.bountifulrequests.service.BoardPublicationService;

import io.ejekta.bountiful.content.board.BoardBlockEntity;

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
}
