package org.crafterscr.bountifulrequests.command;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.crafterscr.bountifulrequests.BountifulRequests;
import org.crafterscr.bountifulrequests.data.RequestPublication;
import org.crafterscr.bountifulrequests.data.RequestSavedData;
import org.crafterscr.bountifulrequests.menu.RequestEditorOpener;
import org.crafterscr.bountifulrequests.service.RequestManager;
import org.crafterscr.bountifulrequests.service.RotationManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Administración de Bountiful Requests.
 *
 * IDs visibles:
 * - usamos un número corto (#1, #2, #3...) para operación diaria;
 * - el UUID interno se conserva y todavía se acepta como fallback.
 *
 * Bountiful protege /bo con OP 2, así que estos hijos heredan esa seguridad.
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
                                        .executes(context ->
                                                listRequests(
                                                        context.getSource(),
                                                        false
                                                )
                                        )
                        )

                        .then(
                                Commands.literal("remove")
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests(
                                                                (context, builder) ->
                                                                        suggestRequestIds(
                                                                                context.getSource(),
                                                                                builder,
                                                                                false
                                                                        )
                                                        )
                                                        .executes(context ->
                                                                removeRequest(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "id"
                                                                        ),
                                                                        false
                                                                )
                                                        )
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
                                Commands.literal("rotation")
                                        .then(
                                                Commands.literal("list")
                                                        .executes(context ->
                                                                listRequests(
                                                                        context.getSource(),
                                                                        true
                                                                )
                                                        )
                                        )
                                        .then(
                                                Commands.literal("status")
                                                        .executes(context ->
                                                                showRotationStatus(
                                                                        context.getSource()
                                                                )
                                                        )
                                        )
                                        .then(
                                                Commands.literal("refresh")
                                                        .executes(context -> {
                                                            RotationManager.refresh(
                                                                    context.getSource()
                                                                            .getServer()
                                                            );

                                                            context.getSource()
                                                                    .sendSuccess(
                                                                            () ->
                                                                                    Component.translatable(
                                                                                            "bountifulrequests.command.rotation_refreshed"
                                                                                    ),
                                                                            true
                                                                    );

                                                            return 1;
                                                        })
                                        )
                                        .then(
                                                Commands.literal("slots")
                                                        .then(
                                                                Commands.argument(
                                                                                "count",
                                                                                IntegerArgumentType.integer(
                                                                                        1,
                                                                                        21
                                                                                )
                                                                        )
                                                                        .executes(context -> {
                                                                            int count =
                                                                                    IntegerArgumentType.getInteger(
                                                                                            context,
                                                                                            "count"
                                                                                    );

                                                                            RequestSavedData data =
                                                                                    RequestSavedData.get(
                                                                                            context.getSource()
                                                                                                    .getServer()
                                                                                    );

                                                                            data.setRotationVisibleSlots(
                                                                                    count
                                                                            );

                                                                            RotationManager.refresh(
                                                                                    context.getSource()
                                                                                            .getServer()
                                                                            );

                                                                            context.getSource()
                                                                                    .sendSuccess(
                                                                                            () ->
                                                                                                    Component.translatable(
                                                                                                            "bountifulrequests.command.rotation_slots",
                                                                                                            count
                                                                                                    ),
                                                                                            true
                                                                                    );

                                                                            return 1;
                                                                        })
                                                        )
                                        )
                                        .then(
                                                Commands.literal("interval")
                                                        .then(
                                                                Commands.argument(
                                                                                "minutes",
                                                                                IntegerArgumentType.integer(
                                                                                        1,
                                                                                        10080
                                                                                )
                                                                        )
                                                                        .executes(context -> {
                                                                            int minutes =
                                                                                    IntegerArgumentType.getInteger(
                                                                                            context,
                                                                                            "minutes"
                                                                                    );

                                                                            RequestSavedData data =
                                                                                    RequestSavedData.get(
                                                                                            context.getSource()
                                                                                                    .getServer()
                                                                                    );

                                                                            data.setRotationIntervalSeconds(
                                                                                    minutes * 60L
                                                                            );

                                                                            RotationManager.refresh(
                                                                                    context.getSource()
                                                                                            .getServer()
                                                                            );

                                                                            context.getSource()
                                                                                    .sendSuccess(
                                                                                            () ->
                                                                                                    Component.translatable(
                                                                                                            "bountifulrequests.command.rotation_interval",
                                                                                                            minutes
                                                                                                    ),
                                                                                            true
                                                                                    );

                                                                            return 1;
                                                                        })
                                                        )
                                        )
                                        .then(
                                                Commands.literal("remove")
                                                        .then(
                                                                Commands.argument(
                                                                                "id",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(
                                                                                (context, builder) ->
                                                                                        suggestRequestIds(
                                                                                                context.getSource(),
                                                                                                builder,
                                                                                                true
                                                                                        )
                                                                        )
                                                                        .executes(context ->
                                                                                removeRequest(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "id"
                                                                                        ),
                                                                                        true
                                                                                )
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

        bo.addChild(
                requests
        );

        BountifulRequests.LOGGER.info(
                "Registered /bo requests commands."
        );
    }

    private static int listRequests(
            CommandSourceStack source,
            boolean rotationsOnly
    ) {
        RequestSavedData data =
                RequestSavedData.get(
                        source.getServer()
                );

        source.sendSuccess(
                () -> Component.translatable(
                        rotationsOnly
                                ? "bountifulrequests.command.rotation_list_header"
                                : "bountifulrequests.command.list_header"
                ),
                false
        );

        int shown = 0;

        for (RequestPublication publication
                : data.publications.values()) {

            if (publication.state
                    == RequestPublication.State.REMOVED) {
                continue;
            }

            if (rotationsOnly
                    && !publication.isRotation()) {
                continue;
            }

            String extra =
                    publication.isRotation()
                            ? (
                            publication.rotationActive
                                    ? "ACTIVE"
                                    : "POOL"
                    )
                            : publication.state.name();

            source.sendSuccess(
                    () -> Component.literal(
                            "#"
                                    + publication.shortId
                                    + " - "
                                    + publication.title
                                    + " ["
                                    + publication.kind
                                    + "/"
                                    + extra
                                    + "]"
                    ),
                    false
            );

            shown++;
        }

        if (shown == 0) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "bountifulrequests.command.list_empty"
                    ),
                    false
            );
        }

        return shown;
    }

    private static CompletableFuture<Suggestions>
    suggestRequestIds(
            CommandSourceStack source,
            SuggestionsBuilder builder,
            boolean rotationsOnly
    ) {
        RequestSavedData data =
                RequestSavedData.get(
                        source.getServer()
                );

        data.publications.values()
                .stream()
                .filter(publication ->
                        publication.state
                                != RequestPublication.State.REMOVED
                )
                .filter(publication ->
                        !rotationsOnly
                                || publication.isRotation()
                )
                .forEach(publication ->
                        builder.suggest(
                                Integer.toString(
                                        publication.shortId
                                ),
                                Component.literal(
                                        publication.title
                                )
                        )
                );

        return builder.buildFuture();
    }

    private static int removeRequest(
            CommandSourceStack source,
            String raw,
            boolean rotationsOnly
    ) {
        RequestSavedData data =
                RequestSavedData.get(
                        source.getServer()
                );

        RequestPublication publication =
                resolvePublication(
                        data,
                        raw
                );

        if (publication == null
                || (
                rotationsOnly
                        && !publication.isRotation()
        )
                || !RequestManager.removePublication(
                source.getServer(),
                publication.id
        )) {

            source.sendFailure(
                    Component.translatable(
                            "bountifulrequests.command.not_found"
                    )
            );

            return 0;
        }

        source.sendSuccess(
                () -> Component.translatable(
                        "bountifulrequests.command.removed_named",
                        publication.shortId,
                        publication.title
                ),
                true
        );

        return 1;
    }

    private static RequestPublication resolvePublication(
            RequestSavedData data,
            String raw
    ) {
        String normalized =
                raw.startsWith("#")
                        ? raw.substring(1)
                        : raw;

        try {
            int shortId =
                    Integer.parseInt(
                            normalized
                    );

            RequestPublication found =
                    data.findByShortId(
                            shortId
                    );

            if (found != null) {
                return found;
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            return data.publications.get(
                    UUID.fromString(raw)
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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

    private static int showRotationStatus(
            CommandSourceStack source
    ) {
        RequestSavedData data =
                RequestSavedData.get(
                        source.getServer()
                );

        long total =
                data.publications.values()
                        .stream()
                        .filter(RequestPublication::isRotation)
                        .filter(publication ->
                                publication.state
                                        == RequestPublication.State.OPEN
                        )
                        .count();

        long active =
                data.publications.values()
                        .stream()
                        .filter(RequestPublication::isRotation)
                        .filter(publication ->
                                publication.state
                                        == RequestPublication.State.OPEN
                        )
                        .filter(publication ->
                                publication.rotationActive
                        )
                        .count();

        long seconds =
                RotationManager.secondsUntilNextRefresh(
                        source.getServer()
                );

        source.sendSuccess(
                () -> Component.translatable(
                        "bountifulrequests.command.rotation_status",
                        active,
                        data.getRotationVisibleSlots(),
                        total,
                        data.getRotationIntervalSeconds() / 60L,
                        formatSeconds(seconds)
                ),
                false
        );

        return 1;
    }

    private static String formatSeconds(
            long seconds
    ) {
        long minutes =
                seconds / 60L;

        long remaining =
                seconds % 60L;

        return minutes
                + "m "
                + remaining
                + "s";
    }
}
