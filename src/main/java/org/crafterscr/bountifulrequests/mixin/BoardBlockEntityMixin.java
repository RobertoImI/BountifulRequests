package org.crafterscr.bountifulrequests.mixin;

import org.crafterscr.bountifulrequests.service.BoardPublicationService;

import io.ejekta.bountiful.content.board.BoardBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cada segundo sincronizamos el board.
 */
@Mixin(
        value = BoardBlockEntity.class,
        remap = false
)
public abstract class BoardBlockEntityMixin {

    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private static void bountifulrequests$tick(
            Level level,
            BlockPos pos,
            BlockState state,
            BoardBlockEntity board,
            CallbackInfo ci
    ) {
        if (level.isClientSide()) {
            return;
        }

        if (level.getGameTime() % 20L != 0L) {
            return;
        }

        BoardPublicationService.sync(
                board
        );
    }
}