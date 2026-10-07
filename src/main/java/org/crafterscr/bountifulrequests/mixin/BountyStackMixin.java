package org.crafterscr.bountifulrequests.mixin;

import java.util.UUID;

import org.crafterscr.bountifulrequests.data.ActiveClaim;
import org.crafterscr.bountifulrequests.data.RequestPublication;
import org.crafterscr.bountifulrequests.data.RequestSavedData;
import org.crafterscr.bountifulrequests.service.RequestCashInService;
import org.crafterscr.bountifulrequests.service.RequestManager;
import org.crafterscr.bountifulrequests.util.RequestBountyData;

import io.ejekta.bountiful.components.BountyStack;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
            method = "checkForCompletionAndAlert",
            at = @At("HEAD"),
            cancellable = true
    )
    private void bountifulrequests$checkCustomCompletion(
            Player player,
            CallbackInfo ci
    ) {
        BountyStack self =
                (BountyStack) (Object) this;

        UUID requestId =
                RequestBountyData.getRequestId(
                        self.getStack()
                );

        if (requestId == null) {
            return;
        }

        /*
         * La evaluación autoritativa de nuestros objetivos vive en servidor.
         * Evitamos que el cliente produzca un ping falso por su cuenta.
         */
        if (!(player instanceof ServerPlayer serverPlayer)) {
            ci.cancel();
            return;
        }

        RequestPublication publication =
                RequestSavedData.get(
                        serverPlayer.server
                ).publications.get(
                        requestId
                );

        if (publication == null) {
            return;
        }

        boolean hasCobblemonObjectives =
                publication.objectives.stream()
                        .anyMatch(
                                objective ->
                                        objective.isCobblemon()
                        );

        if (!hasCobblemonObjectives) {
            return;
        }

        ActiveClaim claim =
                publication.activeClaims.get(
                        serverPlayer.getUUID()
                );

        if (claim == null
                || !RequestManager.areCobblemonObjectivesComplete(
                publication,
                claim
        )) {

            self.setPing(false);
            ci.cancel();
        }
    }

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