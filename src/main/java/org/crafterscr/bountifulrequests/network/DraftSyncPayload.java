package org.crafterscr.bountifulrequests.network;

import org.crafterscr.bountifulrequests.BountifulRequests;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DraftSyncPayload(
        String json
) implements CustomPacketPayload {

    public static final Type<DraftSyncPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            BountifulRequests.MOD_ID,
                            "draft_sync"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            DraftSyncPayload
            > STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) ->
                            buffer.writeUtf(
                                    payload.json,
                                    32767
                            ),
                    buffer ->
                            new DraftSyncPayload(
                                    buffer.readUtf(32767)
                            )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}