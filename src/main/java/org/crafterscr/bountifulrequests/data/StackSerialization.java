package org.crafterscr.bountifulrequests.data;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Utilidades para persistir ItemStacks sin perder Data Components.
 *
 * Es importante para objetos modificados, encantamientos, nombres,
 * durabilidad, etc.
 */
public final class StackSerialization {

    private StackSerialization() {
    }

    public static CompoundTag saveStack(
            HolderLookup.Provider provider,
            ItemStack stack
    ) {
        CompoundTag wrapper = new CompoundTag();

        wrapper.put(
                "Stack",
                stack.saveOptional(provider)
        );

        return wrapper;
    }

    public static ItemStack loadStack(
            HolderLookup.Provider provider,
            CompoundTag wrapper
    ) {
        if (!wrapper.contains("Stack", Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }

        return ItemStack.parseOptional(
                provider,
                wrapper.getCompound("Stack")
        );
    }

    public static ListTag saveList(
            HolderLookup.Provider provider,
            List<ItemStack> stacks
    ) {
        ListTag list = new ListTag();

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            list.add(saveStack(provider, stack));
        }

        return list;
    }

    public static List<ItemStack> loadList(
            HolderLookup.Provider provider,
            ListTag list
    ) {
        List<ItemStack> result = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = loadStack(
                    provider,
                    list.getCompound(i)
            );

            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }

        return result;
    }

    public static ListTag saveFixed(
            HolderLookup.Provider provider,
            NonNullList<ItemStack> stacks
    ) {
        ListTag list = new ListTag();

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);

            if (stack.isEmpty()) {
                continue;
            }

            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.put("Data", saveStack(provider, stack));

            list.add(entry);
        }

        return list;
    }

    public static NonNullList<ItemStack> loadFixed(
            HolderLookup.Provider provider,
            ListTag list,
            int size
    ) {
        NonNullList<ItemStack> result =
                NonNullList.withSize(size, ItemStack.EMPTY);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            int slot = entry.getInt("Slot");

            if (slot < 0 || slot >= size) {
                continue;
            }

            result.set(
                    slot,
                    loadStack(provider, entry.getCompound("Data"))
            );
        }

        return result;
    }

    public static ListTag saveBundles(
            HolderLookup.Provider provider,
            List<List<ItemStack>> bundles
    ) {
        ListTag result = new ListTag();

        for (List<ItemStack> bundle : bundles) {
            CompoundTag tag = new CompoundTag();

            tag.put(
                    "Items",
                    saveList(provider, bundle)
            );

            result.add(tag);
        }

        return result;
    }

    public static List<List<ItemStack>> loadBundles(
            HolderLookup.Provider provider,
            ListTag list
    ) {
        List<List<ItemStack>> result = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);

            result.add(
                    loadList(
                            provider,
                            tag.getList("Items", Tag.TAG_COMPOUND)
                    )
            );
        }

        return result;
    }
}