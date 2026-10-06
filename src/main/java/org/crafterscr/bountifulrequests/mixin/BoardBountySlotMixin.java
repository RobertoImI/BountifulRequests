package org.crafterscr.bountifulrequests.mixin;

import org.crafterscr.bountifulrequests.service.RequestManager;
import org.crafterscr.bountifulrequests.util.RequestBountyData;

import io.ejekta.bountiful.content.gui.BoardBountySlot;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Antes de que Bountiful permita sacar un papel del Board reservamos
 * físicamente su recompensa.
 */
@Mixin(
        value = BoardBountySlot.class,
        remap = false
)
public abstract class BoardBountySlotMixin {

    @Inject(
            method = "mayPickup",
            at = @At("HEAD"),
            cancellable = true
    )
    private void bountifulrequests$claim(
            Player player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack stack =
                ((Slot) (Object) this).getItem();

        if (!RequestBountyData.isRequest(stack)) {
            return;
        }

        boolean allowed =
                RequestManager.claimFromBoard(
                        serverPlayer,
                        stack
                );

        if (!allowed) {
            cir.setReturnValue(false);
        }
    }
}