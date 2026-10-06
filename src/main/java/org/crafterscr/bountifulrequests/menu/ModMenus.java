package org.crafterscr.bountifulrequests.menu;

import java.util.function.Supplier;

import org.crafterscr.bountifulrequests.BountifulRequests;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>>
            REGISTER =
            DeferredRegister.create(
                    Registries.MENU,
                    BountifulRequests.MOD_ID
            );

    public static final Supplier<MenuType<RequestEditorMenu>>
            REQUEST_EDITOR =
            REGISTER.register(
                    "request_editor",
                    () -> new MenuType<>(
                            RequestEditorMenu::new,
                            FeatureFlags.DEFAULT_FLAGS
                    )
            );
}