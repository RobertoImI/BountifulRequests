package org.crafterscr.bountifulrequests.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public final class RequestEditorOpener {

    private RequestEditorOpener() {
    }

    public static void open(
            ServerPlayer player
    ) {
        DraftRewardContainer rewards =
                new DraftRewardContainer(
                        player.server,
                        player.getUUID()
                );

        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, user) ->
                                new RequestEditorMenu(
                                        containerId,
                                        inventory,
                                        rewards,
                                        player.getUUID(),
                                        player.server
                                ),
                        Component.translatable(
                                "bountifulrequests.gui.title"
                        )
                )
        );
    }
}