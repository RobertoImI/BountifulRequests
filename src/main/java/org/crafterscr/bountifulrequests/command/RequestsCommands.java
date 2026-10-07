package org.crafterscr.bountifulrequests.command;

import java.util.UUID;

import org.crafterscr.bountifulrequests.BountifulRequests;
import org.crafterscr.bountifulrequests.data.RequestPublication;
import org.crafterscr.bountifulrequests.data.RequestSavedData;
import org.crafterscr.bountifulrequests.menu.RequestEditorOpener;
import org.crafterscr.bountifulrequests.service.RequestManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos:
 *
 * /bo requests editor
 * /bo requests list
 * /bo requests remove <id>
 * /bo requests defaults
 * /bo requests defaults on
 * /bo requests defaults off
 * /bo requests defaults status
 * /bo requests collect deliveries
 * /bo requests collect returns
 *
 * IMPORTANTE:
 * Bountiful protege /bo con OP 2.
 * Nuestros hijos mantienen esa seguridad.
 */
public final class RequestsCommands {

    private RequestsCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        CommandNode<CommandSourceStack> bo =
                dispatcher.getRoot()
                        .getChild("bo");

        if (bo == null) {
            BountifulRequests.LOGGER.error(
                    "Could not find Bountiful /bo command root."
            );

            return;
        }

        var requests =
                Commands.literal("requests")

                        .then(
                                Commands.literal("editor")
                                        .executes(context -> {
                                            ServerPlayer player =
                                                    context.getSource()
                                                            .getPlayerOrException();

                                            RequestEditorOpener.open(
                                                    player
                                            );

                                            return 1;
                                        })
                        )

                        .then(
                                Commands.literal("list")
                                        .executes(context -> {
                                            RequestSavedData data =
                                                    RequestSavedData.get(
                                                            context.getSource()
                                                                    .getServer()
                                                    );

                                            context.getSource()
                                                    .sendSuccess(
                                                            () ->
                                                                    Component.translatable(
                                                                            "bountifulrequests.command.list_header"
                                                                    ),
                                                            false
                                                    );

                                            for (RequestPublication publication
                                                    : data.publications.values()) {

                                                context.getSource()
                                                        .sendSuccess(
                                                                () ->
                                                                        Component.literal(
                                                                                publication.id
                                                                                        + " - "
                                                                                        + publication.title
                                                                                        + " ["
                                                                                        + publication.kind
                                                                                        + "/"
                                                                                        + publication.state
                                                                                        + "]"
                                                                        ),
                                                                false
                                                        );
                                            }

                                            return 1;
                                        })
                        )

                        .then(
                                Commands.literal("remove")
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests(
                                                                (context, builder) -> {
                                                                    RequestSavedData data =
                                                                            RequestSavedData.get(
                                                                                    context.getSource()
                                                                                            .getServer()
                                                                            );

                                                                    return SharedSuggestionProvider.suggest(
                                                                            data.publications
                                                                                    .keySet()
                                                                                    .stream()
                                                                                    .map(UUID::toString),
                                                                            builder
                                                                    );
                                                                }
                                                        )
                                                        .executes(context -> {
                                                            String raw =
                                                                    StringArgumentType.getString(
                                                                            context,
                                                                            "id"
                                                                    );

                                                            try {
                                                                UUID id =
                                                                        UUID.fromString(raw);

                                                                boolean success =
                                                                        RequestManager.removePublication(
                                                                                context.getSource()
                                                                                        .getServer(),
                                                                                id
                                                                        );

                                                                if (!success) {
                                                                    context.getSource()
                                                                            .sendFailure(
                                                                                    Component.translatable(
                                                                                            "bountifulrequests.command.not_found"
                                                                                    )
                                                                            );

                                                                    return 0;
                                                                }

                                                                context.getSource()
                                                                        .sendSuccess(
                                                                                () ->
                                                                                        Component.translatable(
                                                                                                "bountifulrequests.command.removed"
                                                                                        ),
                                                                                true
                                                                        );

                                                                return 1;

                                                            } catch (Exception exception) {
                                                                context.getSource()
                                                                        .sendFailure(
                                                                                Component.translatable(
                                                                                        "bountifulrequests.command.invalid_id"
                                                                                )
                                                                        );

                                                                return 0;
                                                            }
                                                        })
                                        )
                        )

                        .then(
                                Commands.literal("defaults")
                                        .executes(context ->
                                                showDefaultsStatus(
                                                        context.getSource()
                                                )
                                        )
                                        .then(
                                                Commands.literal("status")
                                                        .executes(context ->
                                                                showDefaultsStatus(
                                                                        context.getSource()
                                                                )
                                                        )
                                        )
                                        .then(
                                                Commands.literal("on")
                                                        .executes(context ->
                                                                setDefaults(
                                                                        context.getSource(),
                                                                        true
                                                                )
                                                        )
                                        )
                                        .then(
                                                Commands.literal("off")
                                                        .executes(context ->
                                                                setDefaults(
                                                                        context.getSource(),
                                                                        false
                                                                )
                                                        )
                                        )
                        )

                        .then(
                                Commands.literal("collect")
                                        .then(
                                                Commands.literal("deliveries")
                                                        .executes(context -> {
                                                            ServerPlayer player =
                                                                    context.getSource()
                                                                            .getPlayerOrException();

                                                            RequestManager.collectDeliveries(
                                                                    player
                                                            );

                                                            return 1;
                                                        })
                                        )
                                        .then(
                                                Commands.literal("returns")
                                                        .executes(context -> {
                                                            ServerPlayer player =
                                                                    context.getSource()
                                                                            .getPlayerOrException();

                                                            RequestManager.collectReturns(
                                                                    player
                                                            );

                                                            return 1;
                                                        })
                                        )
                        )
                        .build();

        /*
         * Añadimos nuestro nodo directamente al /bo existente.
         *
         * NO registramos una segunda raíz.
         */
        bo.addChild(
                requests
        );

        BountifulRequests.LOGGER.info(
                "Registered /bo requests commands."
        );
    }

    private static int setDefaults(
            CommandSourceStack source,
            boolean enabled
    ) {
        RequestSavedData data =
                RequestSavedData.get(
                        source.getServer()
                );

        data.setDefaultBountifulRequestsEnabled(
                enabled
        );

        source.sendSuccess(
                () -> Component.translatable(
                        enabled
                                ? "bountifulrequests.command.defaults_on"
                                : "bountifulrequests.command.defaults_off"
                ),
                true
        );

        return 1;
    }

    private static int showDefaultsStatus(
            CommandSourceStack source
    ) {
        RequestSavedData data =
                RequestSavedData.get(
                        source.getServer()
                );

        boolean enabled =
                data.areDefaultBountifulRequestsEnabled();

        source.sendSuccess(
                () -> Component.translatable(
                        "bountifulrequests.command.defaults_status",
                        Component.translatable(
                                enabled
                                        ? "bountifulrequests.command.enabled"
                                        : "bountifulrequests.command.disabled"
                        )
                ),
                false
        );

        return 1;
    }
}