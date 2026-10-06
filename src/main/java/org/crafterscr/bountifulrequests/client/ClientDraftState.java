package org.crafterscr.bountifulrequests.client;

import org.crafterscr.bountifulrequests.network.DraftView;
import org.crafterscr.bountifulrequests.network.RequestNetwork;

import net.minecraft.client.Minecraft;

public final class ClientDraftState {

    private static DraftView current;

    private ClientDraftState() {
    }

    public static DraftView get() {
        return current;
    }

    public static void receive(
            String json
    ) {
        current =
                RequestNetwork.GSON.fromJson(
                        json,
                        DraftView.class
                );

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.screen
                instanceof RequestEditorScreen editor) {

            editor.onServerSync(
                    current
            );
        }
    }
}