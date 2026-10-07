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

    private static final String REQUEST_TITLE =
            "BountifulRequestsTitle";

    private static final String REQUEST_EXPIRES_AT =
            "BountifulRequestsExpiresAt";

    private static final String REQUEST_RARITY =
            "BountifulRequestsRarity";

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

    /**
     * Datos visuales propios del encargo.
     *
     * Se guardan en el mismo CustomData del papel para poder reconstruir
     * dinámicamente el nombre en cliente sin depender del SavedData del
     * servidor.
     */
    public static void setDisplayData(
            ItemStack stack,
            String title,
            long expiresAtTick,
            int rarity
    ) {
        CompoundTag tag = new CompoundTag();

        CustomData existing =
                stack.get(DataComponents.CUSTOM_DATA);

        if (existing != null) {
            tag = existing.copyTag();
        }

        tag.putString(
                REQUEST_TITLE,
                title == null ? "" : title
        );

        tag.putLong(
                REQUEST_EXPIRES_AT,
                expiresAtTick
        );

        tag.putInt(
                REQUEST_RARITY,
                rarity
        );

        stack.set(
                DataComponents.CUSTOM_DATA,
                CustomData.of(tag)
        );
    }

    public static String getRequestTitle(
            ItemStack stack
    ) {
        CustomData customData =
                stack.get(DataComponents.CUSTOM_DATA);

        if (customData == null) {
            return "";
        }

        return customData
                .copyTag()
                .getString(REQUEST_TITLE);
    }

    public static long getExpiresAtTick(
            ItemStack stack
    ) {
        CustomData customData =
                stack.get(DataComponents.CUSTOM_DATA);

        if (customData == null) {
            return -1L;
        }

        CompoundTag tag =
                customData.copyTag();

        return tag.contains(REQUEST_EXPIRES_AT)
                ? tag.getLong(REQUEST_EXPIRES_AT)
                : -1L;
    }

    public static int getRequestRarity(
            ItemStack stack
    ) {
        CustomData customData =
                stack.get(DataComponents.CUSTOM_DATA);

        if (customData == null) {
            return 0;
        }

        CompoundTag tag =
                customData.copyTag();

        return tag.contains(REQUEST_RARITY)
                ? tag.getInt(REQUEST_RARITY)
                : 0;
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