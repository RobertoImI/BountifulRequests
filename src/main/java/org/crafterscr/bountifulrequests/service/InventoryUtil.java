package org.crafterscr.bountifulrequests.service;

import java.util.ArrayList;
import java.util.List;

import org.crafterscr.bountifulrequests.data.RequestSavedData;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Operaciones seguras con inventarios.
 */
public final class InventoryUtil {

    private InventoryUtil() {
    }

    public static List<ItemStack> copyBundle(
            List<ItemStack> source
    ) {
        List<ItemStack> result = new ArrayList<>();

        for (ItemStack stack : source) {
            if (!stack.isEmpty()) {
                result.add(stack.copy());
            }
        }

        return result;
    }

    public static List<ItemStack> bundleFromDraft(
            NonNullList<ItemStack> source
    ) {
        return copyBundle(source);
    }

    /**
     * Retira del inventario una copia física del bundle solicitado.
     *
     * Primero verifica TODO. Solamente después empieza a quitar ítems.
     * Por lo tanto no hay retiros parciales.
     */
    public static List<ItemStack> takeExactBundle(
            ServerPlayer player,
            List<ItemStack> pattern
    ) {
        Inventory inventory = player.getInventory();

        // Verificación.
        for (int patternIndex = 0;
             patternIndex < pattern.size();
             patternIndex++) {

            ItemStack wanted =
                    pattern.get(patternIndex);

            if (wanted.isEmpty()) {
                continue;
            }

            int totalNeeded = 0;

            for (ItemStack other : pattern) {
                if (ItemStack.isSameItemSameComponents(
                        wanted,
                        other
                )) {
                    totalNeeded += other.getCount();
                }
            }

            int available = countMatching(
                    inventory,
                    wanted
            );

            if (available < totalNeeded) {
                return null;
            }

            // Evitamos verificar el mismo tipo muchas veces.
            boolean seenBefore = false;

            for (int j = 0; j < patternIndex; j++) {
                if (ItemStack.isSameItemSameComponents(
                        pattern.get(j),
                        wanted
                )) {
                    seenBefore = true;
                    break;
                }
            }

            if (seenBefore) {
                continue;
            }
        }

        List<ItemStack> taken = new ArrayList<>();

        // Ahora sí podemos retirar.
        for (ItemStack wanted : pattern) {
            if (wanted.isEmpty()) {
                continue;
            }

            int remaining = wanted.getCount();

            for (ItemStack inventoryStack : inventory.items) {
                if (remaining <= 0) {
                    break;
                }

                if (!ItemStack.isSameItemSameComponents(
                        wanted,
                        inventoryStack
                )) {
                    continue;
                }

                int amount =
                        Math.min(
                                remaining,
                                inventoryStack.getCount()
                        );

                ItemStack removed =
                        inventoryStack.copyWithCount(amount);

                inventoryStack.shrink(amount);

                taken.add(removed);

                remaining -= amount;
            }
        }

        inventory.setChanged();

        return taken;
    }

    private static int countMatching(
            Inventory inventory,
            ItemStack wanted
    ) {
        int result = 0;

        for (ItemStack stack : inventory.items) {
            if (ItemStack.isSameItemSameComponents(
                    wanted,
                    stack
            )) {
                result += stack.getCount();
            }
        }

        return result;
    }

    /**
     * Mete los objetos en el inventario.
     *
     * Si no caben, NO se tiran al suelo.
     * Se guardan en "Returns" para recogerlos posteriormente.
     */
    public static void giveOrQueue(
            ServerPlayer player,
            List<ItemStack> stacks,
            RequestSavedData data
    ) {
        List<ItemStack> leftovers =
                new ArrayList<>();

        for (ItemStack original : stacks) {
            ItemStack stack =
                    original.copy();

            player.getInventory().add(stack);

            if (!stack.isEmpty()) {
                leftovers.add(stack.copy());
            }
        }

        if (!leftovers.isEmpty()) {
            data.addReturn(
                    player.getUUID(),
                    leftovers
            );
        }

        player.getInventory().setChanged();
    }

    public record InventorySnapshot(
            List<ItemStack> main,
            List<ItemStack> offhand
    ) {
        public static InventorySnapshot capture(
                ServerPlayer player
        ) {
            List<ItemStack> main =
                    player.getInventory()
                            .items
                            .stream()
                            .map(ItemStack::copy)
                            .toList();

            List<ItemStack> offhand =
                    player.getInventory()
                            .offhand
                            .stream()
                            .map(ItemStack::copy)
                            .toList();

            return new InventorySnapshot(
                    main,
                    offhand
            );
        }

        /**
         * Restaura el inventario si algo falla durante la entrega.
         */
        public void restore(ServerPlayer player) {
            for (int i = 0; i < main.size(); i++) {
                player.getInventory()
                        .items
                        .set(i, main.get(i).copy());
            }

            for (int i = 0; i < offhand.size(); i++) {
                player.getInventory()
                        .offhand
                        .set(i, offhand.get(i).copy());
            }

            player.getInventory().setChanged();
        }

        /**
         * Calcula qué ItemStacks fueron realmente consumidos.
         */
        public List<ItemStack> removedItems(
                ServerPlayer player
        ) {
            List<ItemStack> result =
                    new ArrayList<>();

            diff(
                    main,
                    player.getInventory().items,
                    result
            );

            diff(
                    offhand,
                    player.getInventory().offhand,
                    result
            );

            return result;
        }

        private static void diff(
                List<ItemStack> before,
                List<ItemStack> after,
                List<ItemStack> output
        ) {
            for (int i = 0; i < before.size(); i++) {
                ItemStack oldStack =
                        before.get(i);

                ItemStack newStack =
                        after.get(i);

                if (oldStack.isEmpty()) {
                    continue;
                }

                if (newStack.isEmpty()) {
                    output.add(oldStack.copy());
                    continue;
                }

                if (ItemStack.isSameItemSameComponents(
                        oldStack,
                        newStack
                )) {
                    int removed =
                            oldStack.getCount()
                                    - newStack.getCount();

                    if (removed > 0) {
                        output.add(
                                oldStack.copyWithCount(removed)
                        );
                    }
                }
            }
        }
    }
}