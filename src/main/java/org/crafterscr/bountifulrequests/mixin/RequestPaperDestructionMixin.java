package org.crafterscr.bountifulrequests.mixin;

import org.crafterscr.bountifulrequests.service.RequestManager;
import org.crafterscr.bountifulrequests.util.RequestBountyData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only a real destruction event unlocks a lost paper.
 * Simply dropping, storing or unloading it never cancels a mission.
 */
@Mixin(ItemEntity.class)
public abstract class RequestPaperDestructionMixin {

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("RETURN"))
    private void bountifulrequests$destroyed(
            DamageSource damageSource, float damage,
            CallbackInfoReturnable<Boolean> cir) {

        ItemEntity item = (ItemEntity) (Object) this;
        if (!(item.level() instanceof ServerLevel serverLevel)
                || !item.isRemoved()) return;

        ItemStack stack = item.getItem();
        if (!RequestBountyData.isRequest(stack)
                || RequestBountyData.getClaimId(stack) == null) return;

        RequestManager.onPaperDestroyed(serverLevel.getServer(), stack);
    }
}
