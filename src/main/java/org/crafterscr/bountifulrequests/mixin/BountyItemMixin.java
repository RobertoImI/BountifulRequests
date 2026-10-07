package org.crafterscr.bountifulrequests.mixin;

import org.crafterscr.bountifulrequests.util.RequestBountyData;

import io.ejekta.bountiful.bounty.BountyRarity;
import io.ejekta.bountiful.content.item.BountyItem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Personaliza únicamente el nombre visual de los Bounties creados por
 * Bountiful Requests.
 *
 * Los papeles normales de Bountiful continúan usando su nombre original.
 */
@Mixin(
        value = BountyItem.class,
        remap = false
)
public abstract class BountyItemMixin {

    @Inject(
            method = "getName",
            at = @At("HEAD"),
            cancellable = true
    )
    private void bountifulrequests$customRequestName(
            ItemStack stack,
            CallbackInfoReturnable<Component> cir
    ) {
        if (!RequestBountyData.isRequest(stack)) {
            return;
        }

        String title =
                RequestBountyData.getRequestTitle(
                        stack
                );

        if (title == null || title.isBlank()) {
            return;
        }

        int rarityIndex =
                RequestBountyData.getRequestRarity(
                        stack
                );

        BountyRarity[] rarities =
                BountyRarity.values();

        rarityIndex =
                Math.max(
                        0,
                        Math.min(
                                rarities.length - 1,
                                rarityIndex
                        )
                );

        BountyRarity rarity =
                rarities[rarityIndex];

        /*
         * El nombre usa el color de la rareza.
         */
        MutableComponent result =
                Component.literal(title)
                        .withStyle(
                                rarity.getColor()
                        );

        if (rarity == BountyRarity.LEGENDARY) {
            result.withStyle(
                    ChatFormatting.BOLD
            );
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level != null) {
            long expiresAt =
                    RequestBountyData.getExpiresAtTick(
                            stack
                    );

            if (expiresAt >= 0L) {
                long remainingTicks =
                        Math.max(
                                0L,
                                expiresAt
                                        - minecraft.level.getGameTime()
                        );

                long remainingSeconds =
                        remainingTicks / 20L;

                /*
                 * El tiempo va a la par del nombre, igual que en Bountiful,
                 * pero conservamos nuestro título personalizado:
                 *
                 * Entrega de Hierro (23m 59s)
                 */
                result.append(
                        Component.literal(
                                        " ("
                                                + formatTime(
                                                remainingSeconds
                                        )
                                                + ")"
                                )
                                .withStyle(
                                        ChatFormatting.WHITE
                                )
                );
            }
        }

        cir.setReturnValue(result);
    }

    private static String formatTime(
            long totalSeconds
    ) {
        long hours =
                totalSeconds / 3600L;

        long minutes =
                (totalSeconds % 3600L) / 60L;

        long seconds =
                totalSeconds % 60L;

        if (hours > 0L) {
            return hours
                    + "h "
                    + minutes
                    + "m "
                    + seconds
                    + "s";
        }

        if (minutes > 0L) {
            return minutes
                    + "m "
                    + seconds
                    + "s";
        }

        return seconds + "s";
    }
}
