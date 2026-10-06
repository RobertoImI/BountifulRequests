package org.crafterscr.bountifulrequests.mixin;

import org.crafterscr.bountifulrequests.service.RequestCashInService;
import org.crafterscr.bountifulrequests.util.RequestBountyData;

import io.ejekta.bountiful.components.BountyStack;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sólo intercepta Bounties marcados por Bountiful Requests.
 *
 * Todo Bounty normal continúa exactamente con la implementación
 * original de Bountiful.
 */
@Mixin(
        value = BountyStack.class,
        remap = false
)
public abstract class BountyStackMixin {

    @Inject(
            method = "tryCashIn",
            at = @At("HEAD"),
            cancellable = true
    )
    private void bountifulrequests$cashIn(
            Player player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        BountyStack self =
                (BountyStack) (Object) this;

        if (!RequestBountyData.isRequest(
                self.getStack()
        )) {
            return;
        }

        Boolean result =
                RequestCashInService.tryCashIn(
                        serverPlayer,
                        self.getStack()
                );

        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}