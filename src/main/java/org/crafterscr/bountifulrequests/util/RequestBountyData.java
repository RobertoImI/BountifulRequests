package org.crafterscr.bountifulrequests.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.crafterscr.bountifulrequests.data.ObjectiveSpec;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

    private static final String COBBLEMON_OBJECTIVES =
            "BountifulRequestsCobblemonObjectives";

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

    /**
     * Copia en el propio papel una representación ligera de los objetivos
     * Cobblemon. Esto permite mostrar el objetivo y su progreso en el
     * tooltip del cliente sin exponer SavedData ni requerir paquetes extra.
     */
    public static void setCobblemonObjectives(
            ItemStack stack,
            List<ObjectiveSpec> objectives
    ) {
        CompoundTag root = copyCustomData(stack);
        ListTag list = new ListTag();

        for (int index = 0;
             index < objectives.size();
             index++) {

            ObjectiveSpec objective =
                    objectives.get(index);

            if (!objective.isCobblemon()) {
                continue;
            }

            CompoundTag tag =
                    new CompoundTag();

            tag.putInt("Index", index);
            tag.putString(
                    "Kind",
                    objective.kind.name()
            );
            tag.putString(
                    "Content",
                    objective.content
            );
            tag.putInt(
                    "Amount",
                    objective.amount
            );
            tag.putInt(
                    "Progress",
                    0
            );

            list.add(tag);
        }

        root.put(
                COBBLEMON_OBJECTIVES,
                list
        );

        setCustomData(
                stack,
                root
        );
    }

    public static List<CobblemonObjectiveView>
    getCobblemonObjectives(
            ItemStack stack
    ) {
        CustomData customData =
                stack.get(
                        DataComponents.CUSTOM_DATA
                );

        if (customData == null) {
            return List.of();
        }

        CompoundTag root =
                customData.copyTag();

        ListTag list =
                root.getList(
                        COBBLEMON_OBJECTIVES,
                        Tag.TAG_COMPOUND
                );

        List<CobblemonObjectiveView> result =
                new ArrayList<>();

        for (int i = 0;
             i < list.size();
             i++) {

            CompoundTag tag =
                    list.getCompound(i);

            result.add(
                    new CobblemonObjectiveView(
                            tag.getInt("Index"),
                            tag.getString("Kind"),
                            tag.getString("Content"),
                            Math.max(
                                    1,
                                    tag.getInt("Amount")
                            ),
                            Math.max(
                                    0,
                                    tag.getInt("Progress")
                            )
                    )
            );
        }

        return result;
    }

    public static void setCobblemonProgress(
            ItemStack stack,
            int objectiveIndex,
            int progress
    ) {
        CompoundTag root =
                copyCustomData(stack);

        ListTag list =
                root.getList(
                        COBBLEMON_OBJECTIVES,
                        Tag.TAG_COMPOUND
                );

        for (int i = 0;
             i < list.size();
             i++) {

            CompoundTag tag =
                    list.getCompound(i);

            if (tag.getInt("Index")
                    == objectiveIndex) {

                tag.putInt(
                        "Progress",
                        Math.max(0, progress)
                );

                break;
            }
        }

        root.put(
                COBBLEMON_OBJECTIVES,
                list
        );

        setCustomData(
                stack,
                root
        );
    }

    private static CompoundTag copyCustomData(
            ItemStack stack
    ) {
        CustomData existing =
                stack.get(
                        DataComponents.CUSTOM_DATA
                );

        return existing == null
                ? new CompoundTag()
                : existing.copyTag();
    }

    private static void setCustomData(
            ItemStack stack,
            CompoundTag tag
    ) {
        stack.set(
                DataComponents.CUSTOM_DATA,
                CustomData.of(tag)
        );
    }

    public record CobblemonObjectiveView(
            int index,
            String kind,
            String content,
            int amount,
            int progress
    ) {
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