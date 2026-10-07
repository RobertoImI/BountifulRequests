package org.crafterscr.bountifulrequests.mixin;

import java.util.List;

import org.crafterscr.bountifulrequests.client.CobblemonClientBridge;
import org.crafterscr.bountifulrequests.data.ObjectiveSpec;
import org.crafterscr.bountifulrequests.util.RequestBountyData;

import io.ejekta.bountiful.bounty.BountyRarity;
import io.ejekta.bountiful.content.item.BountyItem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import net.neoforged.fml.ModList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    /**
     * Añade los objetivos de Cobblemon al tooltip de nuestros encargos.
     * El progreso viene del CustomData del propio papel y por eso se
     * sincroniza normalmente con el inventario del jugador.
     */
    @Inject(
            method = "appendHoverText",
            at = @At("TAIL")
    )
    private void bountifulrequests$cobblemonTooltip(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag,
            CallbackInfo ci
    ) {
        List<RequestBountyData.CobblemonObjectiveView> objectives =
                RequestBountyData.getCobblemonObjectives(
                        stack
                );

        if (objectives.isEmpty()) {
            return;
        }

        tooltip.add(
                Component.translatable(
                                "bountifulrequests.tooltip.cobblemon"
                        )
                        .withStyle(
                                ChatFormatting.GOLD
                        )
        );

        for (RequestBountyData.CobblemonObjectiveView objective
                : objectives) {

            ObjectiveSpec.Kind kind;

            try {
                kind =
                        ObjectiveSpec.Kind.valueOf(
                                objective.kind()
                        );
            } catch (Exception ignored) {
                continue;
            }

            String target =
                    objective.content();

            if (ModList.get()
                    .isLoaded("cobblemon")) {

                if (kind
                        == ObjectiveSpec.Kind.COBBLEMON_CAPTURE_SPECIES
                        || kind
                        == ObjectiveSpec.Kind.COBBLEMON_DEFEAT_SPECIES) {

                    target =
                            "*".equals(
                                    objective.content()
                            )
                                    ? Component.translatable(
                                    "bountifulrequests.gui.cobblemon.any"
                            ).getString()
                                    : CobblemonClientBridge.speciesLabel(
                                    objective.content()
                            );

                } else {
                    target =
                            CobblemonClientBridge.typeLabel(
                                    objective.content()
                            );
                }
            }

            String translationKey =
                    switch (kind) {
                        case COBBLEMON_CAPTURE_SPECIES ->
                                "bountifulrequests.tooltip.cobblemon.capture";
                        case COBBLEMON_DEFEAT_SPECIES ->
                                "bountifulrequests.tooltip.cobblemon.defeat";
                        case COBBLEMON_CAPTURE_TYPE ->
                                "bountifulrequests.tooltip.cobblemon.capture_type";
                        case COBBLEMON_DEFEAT_TYPE ->
                                "bountifulrequests.tooltip.cobblemon.defeat_type";
                        default -> null;
                    };

            if (translationKey == null) {
                continue;
            }

            boolean complete =
                    objective.progress()
                            >= objective.amount();

            tooltip.add(
                    Component.translatable(
                                    translationKey,
                                    target,
                                    objective.progress(),
                                    objective.amount()
                            )
                            .withStyle(
                                    complete
                                            ? ChatFormatting.GREEN
                                            : ChatFormatting.GRAY
                            )
            );
        }
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
