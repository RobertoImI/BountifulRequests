package org.crafterscr.bountifulrequests.network;

import org.crafterscr.bountifulrequests.BountifulRequests;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Un único payload de acciones mantiene el protocolo pequeño y sencillo.
 */
public record EditorActionPayload(
        String action,
        String text,
        String text2,
        int value,
        long longValue,
        boolean flag
) implements CustomPacketPayload {

    public static final Type<EditorActionPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            BountifulRequests.MOD_ID,
                            "editor_action"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            EditorActionPayload
            > STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUtf(payload.action);
                        buffer.writeUtf(payload.text);
                        buffer.writeUtf(payload.text2);
                        buffer.writeVarInt(payload.value);
                        buffer.writeVarLong(payload.longValue);
                        buffer.writeBoolean(payload.flag);
                    },
                    buffer -> new EditorActionPayload(
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readVarInt(),
                            buffer.readVarLong(),
                            buffer.readBoolean()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static EditorActionPayload simple(
            String action
    ) {
        return new EditorActionPayload(
                action,
                "",
                "",
                0,
                0,
                false
        );
    }
}