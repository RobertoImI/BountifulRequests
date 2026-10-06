package org.crafterscr.bountifulrequests.util;

import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Marca los papeles Bountiful creados por nuestro addon.
 */
public final class RequestBountyData {

    private static final String REQUEST_ID =
            "BountifulRequestsId";

    private RequestBountyData() {
    }

    public static void setRequestId(
            ItemStack stack,
            UUID id
    ) {
        CompoundTag tag = new CompoundTag();

        CustomData existing =
                stack.get(DataComponents.CUSTOM_DATA);

        if (existing != null) {
            tag = existing.copyTag();
        }

        tag.putUUID(REQUEST_ID, id);

        stack.set(
                DataComponents.CUSTOM_DATA,
                CustomData.of(tag)
        );
    }

    public static UUID getRequestId(
            ItemStack stack
    ) {
        CustomData customData =
                stack.get(DataComponents.CUSTOM_DATA);

        if (customData == null) {
            return null;
        }

        CompoundTag tag =
                customData.copyTag();

        if (!tag.contains(REQUEST_ID)) {
            return null;
        }

        try {
            return tag.getUUID(REQUEST_ID);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isRequest(
            ItemStack stack
    ) {
        return getRequestId(stack) != null;
    }
}