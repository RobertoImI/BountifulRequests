package org.crafterscr.bountifulrequests.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.crafterscr.bountifulrequests.BountifulRequests;
import org.crafterscr.bountifulrequests.client.ClientDraftState;
import org.crafterscr.bountifulrequests.data.ObjectiveSpec;
import org.crafterscr.bountifulrequests.data.RequestDraft;
import org.crafterscr.bountifulrequests.service.RequestManager;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
        modid = BountifulRequests.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD
)
public final class RequestNetwork {

    public static final Gson GSON =
            new GsonBuilder()
                    .create();

    private RequestNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(
            RegisterPayloadHandlersEvent event
    ) {
        var registrar =
                event.registrar("1");

        registrar.playToServer(
                EditorActionPayload.TYPE,
                EditorActionPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player()
                            instanceof ServerPlayer player)) {
                        return;
                    }

                    handleServer(
                            player,
                            payload
                    );
                }
        );

        registrar.playToClient(
                DraftSyncPayload.TYPE,
                DraftSyncPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() ->
                            ClientDraftState.receive(
                                    payload.json()
                            )
                    );
                }
        );
    }

    private static void handleServer(
            ServerPlayer player,
            EditorActionPayload packet
    ) {
        boolean sync = true;

        switch (packet.action()) {

            case "SYNC" -> {
                // Nada. Sólo responder.
            }

            case "SET_TITLE" -> {
                RequestManager.setTitle(
                        player,
                        packet.text()
                );

                // Evita reconstruir el GUI por cada letra.
                sync = false;
            }

            case "SET_RARITY" -> {
                RequestManager.setRarity(
                        player,
                        packet.value()
                );

                sync = false;
            }

            case "SET_DURATION" -> {
                RequestManager.setDuration(
                        player,
                        packet.longValue()
                );

                sync = false;
            }

            case "ADD_OBJECTIVE" -> {
                try {
                    ObjectiveSpec.Kind kind =
                            ObjectiveSpec.Kind.valueOf(
                                    packet.text()
                            );

                    ObjectiveSpec objective;

                    if (kind
                            == ObjectiveSpec.Kind.BOUNTIFUL_ENTRY) {

                        objective =
                                ObjectiveSpec.bountifulEntry(
                                        packet.text2()
                                );

                    } else {
                        objective =
                                ObjectiveSpec.direct(
                                        kind,
                                        packet.text2(),
                                        packet.value()
                                );
                    }

                    RequestManager.addObjective(
                            player,
                            objective
                    );
                } catch (Exception ignored) {
                }
            }

            case "REMOVE_OBJECTIVE" ->
                    RequestManager.removeObjective(
                            player,
                            packet.value()
                    );

            case "PUBLISH_BOARD" ->
                    RequestManager.beginPublish(
                            player,
                            RequestDraft.PendingMode.BOARD,
                            1
                    );

            case "CREATE_PAPER" ->
                    RequestManager.beginPublish(
                            player,
                            RequestDraft.PendingMode.HANDOUT,
                            1
                    );

            case "PUBLISH_ROTATION" ->
                    RequestManager.beginPublish(
                            player,
                            RequestDraft.PendingMode.ROTATION,
                            Math.max(1, packet.value())
                    );

            case "UNDO" ->
                    RequestManager.undoPending(
                            player
                    );

            case "CANCEL_DRAFT" -> {
                RequestManager.cancelDraft(
                        player
                );

                return;
            }

            case "COLLECT_DELIVERIES" ->
                    RequestManager.collectDeliveries(
                            player
                    );

            case "COLLECT_RETURNS" ->
                    RequestManager.collectReturns(
                            player
                    );
        }

        if (sync) {
            sendDraftSync(player);
        }
    }

    public static void sendDraftSync(
            ServerPlayer player
    ) {
        DraftView view =
                RequestManager.createView(
                        player
                );

        PacketDistributor.sendToPlayer(
                player,
                new DraftSyncPayload(
                        GSON.toJson(view)
                )
        );
    }
}