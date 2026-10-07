package org.crafterscr.bountifulrequests.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.crafterscr.bountifulrequests.data.ActiveClaim;
import org.crafterscr.bountifulrequests.data.ObjectiveSpec;
import org.crafterscr.bountifulrequests.data.RequestDraft;
import org.crafterscr.bountifulrequests.data.RequestPublication;
import org.crafterscr.bountifulrequests.data.RequestSavedData;
import org.crafterscr.bountifulrequests.menu.RequestEditorMenu;
import org.crafterscr.bountifulrequests.network.DraftView;
import org.crafterscr.bountifulrequests.network.RequestNetwork;
import org.crafterscr.bountifulrequests.util.RequestBountyData;

import io.ejekta.bountiful.components.BountyStack;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.ModList;

/**
 * Núcleo del addon.
 *
 * Todas las operaciones importantes pasan por aquí para que la lógica
 * siga siendo exclusivamente server-side.
 */
public final class RequestManager {

    private RequestManager() {
    }

    // ------------------------------------------------------------
    // DRAFT
    // ------------------------------------------------------------

    public static RequestDraft draft(
            ServerPlayer player
    ) {
        return RequestSavedData
                .get(player.server)
                .getOrCreateDraft(
                        player.getUUID()
                );
    }

    public static void setTitle(
            ServerPlayer player,
            String title
    ) {
        RequestDraft draft = draft(player);

        if (draft.isPending()) {
            return;
        }

        draft.title =
                title.length() > 64
                        ? title.substring(0, 64)
                        : title;

        RequestSavedData.get(player.server)
                .setDirty();
    }

    public static void setRarity(
            ServerPlayer player,
            int rarity
    ) {
        RequestDraft draft = draft(player);

        if (draft.isPending()) {
            return;
        }

        draft.rarity =
                Math.max(0, Math.min(4, rarity));

        RequestSavedData.get(player.server)
                .setDirty();
    }

    public static void setDuration(
            ServerPlayer player,
            long seconds
    ) {
        RequestDraft draft = draft(player);

        if (draft.isPending()) {
            return;
        }

        // Entre 1 minuto y 7 días.
        draft.durationSeconds =
                Math.max(
                        60,
                        Math.min(
                                7L * 24 * 60 * 60,
                                seconds
                        )
                );

        RequestSavedData.get(player.server)
                .setDirty();
    }

    public static void addObjective(
            ServerPlayer player,
            ObjectiveSpec objective
    ) {
        RequestDraft draft = draft(player);

        if (draft.isPending()) {
            return;
        }

        /*
         * Permisos reales en servidor.
         *
         * Jugadores normales:
         * - ITEM
         * - ENTITY
         *
         * Sólo OP:
         * - ITEM_TAG
         * - objetivos COBBLEMON
         *
         * Las antiguas BOUNTIFUL_ENTRY se conservan únicamente para
         * compatibilidad con mundos guardados y no se pueden crear nuevas.
         */
        if (objective.kind
                == ObjectiveSpec.Kind.BOUNTIFUL_ENTRY
                || objective.kind
                == ObjectiveSpec.Kind.BOUNTIFUL_RESOLVED) {

            return;
        }

        if (objective.kind
                == ObjectiveSpec.Kind.ITEM_TAG
                && !player.hasPermissions(2)) {

            player.sendSystemMessage(
                    Component.translatable(
                            "bountifulrequests.message.objective_op_only"
                    )
            );

            return;
        }

        if (objective.isCobblemon()) {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(
                        Component.translatable(
                                "bountifulrequests.message.objective_op_only"
                        )
                );

                return;
            }

            if (!ModList.get()
                    .isLoaded("cobblemon")) {

                player.sendSystemMessage(
                        Component.translatable(
                                "bountifulrequests.message.cobblemon_missing"
                        )
                );

                return;
            }
        }

        if (draft.objectives.size() >= 8) {
            player.sendSystemMessage(
                    Component.translatable(
                            "bountifulrequests.message.too_many_objectives"
                    )
            );

            return;
        }

        /*
         * No permitimos duplicar un Bountiful Entry porque su entryId
         * es usado por Bountiful para el progreso de criteria.
         */
        if (objective.kind
                == ObjectiveSpec.Kind.BOUNTIFUL_ENTRY) {

            boolean duplicate =
                    draft.objectives.stream()
                            .anyMatch(existing ->
                                    existing.kind
                                            == ObjectiveSpec.Kind.BOUNTIFUL_ENTRY
                                            && existing.content.equals(
                                            objective.content
                                    )
                            );

            if (duplicate) {
                return;
            }
        }

        draft.objectives.add(objective);

        RequestSavedData.get(player.server)
                .setDirty();
    }

    public static void removeObjective(
            ServerPlayer player,
            int index
    ) {
        RequestDraft draft = draft(player);

        if (draft.isPending()) {
            return;
        }

        if (index < 0
                || index >= draft.objectives.size()) {
            return;
        }

        draft.objectives.remove(index);

        RequestSavedData.get(player.server)
                .setDirty();
    }

    // ------------------------------------------------------------
    // BEGIN PUBLISH
    // ------------------------------------------------------------

    public static boolean beginPublish(
            ServerPlayer player,
            RequestDraft.PendingMode mode,
            int rotationUses
    ) {
        RequestSavedData data =
                RequestSavedData.get(player.server);

        RequestDraft draft =
                data.getOrCreateDraft(
                        player.getUUID()
                );

        if (draft.isPending()) {
            return false;
        }

        if (draft.title.isBlank()) {
            player.sendSystemMessage(
                    Component.translatable(
                            "bountifulrequests.message.missing_title"
                    )
            );

            return false;
        }

        if (draft.objectives.isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable(
                            "bountifulrequests.message.missing_objective"
                    )
            );

            return false;
        }

        /*
         * Revalidamos permisos al publicar. Así un borrador antiguo o un
         * jugador que haya perdido OP no puede saltarse las restricciones.
         */
        for (ObjectiveSpec objective
                : draft.objectives) {

            if (objective.kind
                    == ObjectiveSpec.Kind.ITEM_TAG
                    && !player.hasPermissions(2)) {

                player.sendSystemMessage(
                        Component.translatable(
                                "bountifulrequests.message.objective_op_only"
                        )
                );

                return false;
            }

            if (objective.isCobblemon()) {
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(
                            Component.translatable(
                                    "bountifulrequests.message.objective_op_only"
                            )
                    );

                    return false;
                }

                if (!ModList.get()
                        .isLoaded("cobblemon")) {

                    player.sendSystemMessage(
                            Component.translatable(
                                    "bountifulrequests.message.cobblemon_missing"
                            )
                    );

                    return false;
                }
            }

            if (objective.kind
                    == ObjectiveSpec.Kind.BOUNTIFUL_ENTRY
                    || objective.kind
                    == ObjectiveSpec.Kind.BOUNTIFUL_RESOLVED) {

                player.sendSystemMessage(
                        Component.translatable(
                                "bountifulrequests.message.unsupported_objective"
                        )
                );

                return false;
            }
        }

        List<ItemStack> firstBundle =
                InventoryUtil.bundleFromDraft(
                        draft.rewards
                );

        if (firstBundle.isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable(
                            "bountifulrequests.message.missing_reward"
                    )
            );

            return false;
        }

        if (mode
                == RequestDraft.PendingMode.ROTATION) {

            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(
                        Component.translatable(
                                "bountifulrequests.message.admin_only"
                        )
                );

                return false;
            }

            rotationUses =
                    Math.max(1, rotationUses);

            List<List<ItemStack>> consumed =
                    new ArrayList<>();

            /*
             * El primer bundle ya está físicamente dentro del editor.
             *
             * Si uses = 10, retiramos otros 9 bundles REALES
             * del inventario.
             */
            for (int i = 1; i < rotationUses; i++) {
                List<ItemStack> extra =
                        InventoryUtil.takeExactBundle(
                                player,
                                firstBundle
                        );

                if (extra == null) {
                    /*
                     * Rollback de todos los bundles extra que ya habíamos
                     * retirado.
                     */
                    for (List<ItemStack> bundle : consumed) {
                        InventoryUtil.giveOrQueue(
                                player,
                                bundle,
                                data
                        );
                    }

                    player.sendSystemMessage(
                            Component.translatable(
                                    "bountifulrequests.message.not_enough_rotation_rewards"
                            )
                    );

                    return false;
                }

                consumed.add(extra);
            }

            draft.pendingExtraBundles.addAll(
                    consumed
            );

            draft.rotationUses =
                    rotationUses;
        }

        /*
         * La confirmación se hace en el GUI ANTES de enviar esta acción.
         * Una vez que el servidor recibe la confirmación, publica de inmediato.
         *
         * Conservamos pendingMode únicamente como estado transitorio interno
         * porque finalizePending() ya utiliza ese valor para decidir si será
         * HANDOUT, BOARD o ROTATION.
         */
        draft.pendingMode = mode;
        draft.pendingUntilTick = -1L;

        data.setDirty();

        finalizePending(
                player.server,
                data,
                draft
        );

        return true;
    }

    // ------------------------------------------------------------
    // UNDO
    // ------------------------------------------------------------

    public static void undoPending(
            ServerPlayer player
    ) {
        RequestSavedData data =
                RequestSavedData.get(player.server);

        RequestDraft draft =
                data.getOrCreateDraft(
                        player.getUUID()
                );

        if (!draft.isPending()) {
            return;
        }

        /*
         * El reward principal queda en el editor.
         *
         * Los bundles extra que quitamos por una rotación sí vuelven
         * al jugador.
         */
        for (List<ItemStack> bundle
                : draft.pendingExtraBundles) {

            InventoryUtil.giveOrQueue(
                    player,
                    bundle,
                    data
            );
        }

        draft.clearPending();

        data.setDirty();

        player.sendSystemMessage(
                Component.translatable(
                        "bountifulrequests.message.publication_cancelled"
                )
        );
    }

    // ------------------------------------------------------------
    // CANCEL DRAFT
    // ------------------------------------------------------------

    public static void cancelDraft(
            ServerPlayer player
    ) {
        RequestSavedData data =
                RequestSavedData.get(player.server);

        RequestDraft draft =
                data.getOrCreateDraft(
                        player.getUUID()
                );

        List<ItemStack> all = new ArrayList<>();

        all.addAll(
                InventoryUtil.bundleFromDraft(
                        draft.rewards
                )
        );

        for (List<ItemStack> bundle
                : draft.pendingExtraBundles) {

            all.addAll(bundle);
        }

        InventoryUtil.giveOrQueue(
                player,
                all,
                data
        );

        draft.resetAfterPublish();

        data.setDirty();

        if (player.containerMenu
                instanceof RequestEditorMenu menu) {

            menu.clearRewardsAfterServerReset();
        }

        player.closeContainer();
    }

    // ------------------------------------------------------------
    // TICK
    // ------------------------------------------------------------

    public static void tick(
            MinecraftServer server
    ) {
        long now =
                server.overworld().getGameTime();

        RequestSavedData data =
                RequestSavedData.get(server);

        boolean changed = false;

        /*
         * Compatibilidad con mundos guardados por versiones anteriores:
         * si quedó una publicación pendiente del antiguo temporizador,
         * la finalizamos inmediatamente. Las nuevas publicaciones ya no
         * utilizan cuenta regresiva.
         */
        for (RequestDraft draft
                : new ArrayList<>(
                data.drafts.values()
        )) {

            if (!draft.isPending()) {
                continue;
            }

            finalizePending(
                    server,
                    data,
                    draft
            );

            changed = true;
        }

        /*
         * Expiraciones.
         */
        for (RequestPublication publication
                : data.publications.values()) {

            // Claims individuales.
            List<UUID> expiredPlayers =
                    new ArrayList<>();

            for (ActiveClaim claim
                    : publication.activeClaims.values()) {

                if (now >= claim.expiresAtTick) {
                    expireClaim(
                            data,
                            publication,
                            claim
                    );

                    expiredPlayers.add(
                            claim.player
                    );

                    changed = true;
                }
            }

            for (UUID player : expiredPlayers) {
                publication.activeClaims.remove(
                        player
                );
            }

            /*
             * Misión BOARD/HANDOUT que jamás fue reclamada.
             */
            if (publication.kind
                    != RequestPublication.Kind.ROTATION
                    && publication.state
                    == RequestPublication.State.OPEN) {

                long expires =
                        publication.publishTick
                                + publication.durationSeconds * 20L;

                if (now >= expires) {
                    for (List<ItemStack> bundle
                            : publication.availableBundles) {

                        data.addReturn(
                                publication.owner,
                                bundle
                        );
                    }

                    publication.availableBundles.clear();
                    publication.state =
                            RequestPublication.State.EXPIRED;

                    changed = true;
                }
            }

            /*
             * Rotación completamente agotada.
             */
            if (publication.kind
                    == RequestPublication.Kind.ROTATION
                    && publication.state
                    == RequestPublication.State.OPEN
                    && publication.availableBundles.isEmpty()
                    && publication.activeClaims.isEmpty()) {

                publication.state =
                        RequestPublication.State.COMPLETED;

                changed = true;
            }

            if (publication.state
                    == RequestPublication.State.CLOSING
                    && publication.activeClaims.isEmpty()) {

                publication.state =
                        RequestPublication.State.REMOVED;

                changed = true;
            }
        }

        if (changed) {
            data.setDirty();
        }
    }

    private static void finalizePending(
            MinecraftServer server,
            RequestSavedData data,
            RequestDraft draft
    ) {
        RequestPublication publication =
                new RequestPublication();

        publication.id =
                UUID.randomUUID();

        publication.owner =
                draft.owner;

        publication.title =
                draft.title;

        publication.rarity =
                draft.rarity;

        publication.durationSeconds =
                draft.durationSeconds;

        publication.publishTick =
                server.overworld().getGameTime();

        publication.kind =
                switch (draft.pendingMode) {
                    case HANDOUT ->
                            RequestPublication.Kind.HANDOUT;

                    case BOARD ->
                            RequestPublication.Kind.BOARD;

                    case ROTATION ->
                            RequestPublication.Kind.ROTATION;

                    default ->
                            RequestPublication.Kind.BOARD;
                };

        /*
         * Resolvemos todas las entradas especiales de Bountiful ahora.
         */
        ServerPlayer owner =
                server.getPlayerList()
                        .getPlayer(draft.owner);

        for (ObjectiveSpec spec
                : draft.objectives) {

            if (spec.kind
                    == ObjectiveSpec.Kind.BOUNTIFUL_ENTRY) {

                if (owner == null) {
                    continue;
                }

                ObjectiveSpec resolved =
                        BountyPaperFactory.resolveBountifulEntry(
                                spec,
                                owner.serverLevel(),
                                owner.blockPosition()
                        );

                if (resolved != null) {
                    publication.objectives.add(
                            resolved
                    );
                }

            } else {
                publication.objectives.add(spec);
            }
        }

        List<ItemStack> primary =
                InventoryUtil.bundleFromDraft(
                        draft.rewards
                );

        publication.availableBundles.add(
                primary
        );

        for (List<ItemStack> extra
                : draft.pendingExtraBundles) {

            publication.availableBundles.add(
                    InventoryUtil.copyBundle(extra)
            );
        }

        data.publications.put(
                publication.id,
                publication
        );

        RequestPublication.Kind finalKind =
                publication.kind;

        draft.resetAfterPublish();

        /*
         * Actualizamos cualquier editor abierto.
         */
        if (owner != null) {
            if (owner.containerMenu
                    instanceof RequestEditorMenu menu) {

                menu.clearRewardsAfterServerReset();
            }

            /*
             * HANDOUT:
             * damos el Bountiful Paper al creador.
             */
            if (finalKind
                    == RequestPublication.Kind.HANDOUT) {

                ItemStack paper =
                        BountyPaperFactory.create(
                                publication,
                                owner.serverLevel(),
                                owner.blockPosition()
                        );

                InventoryUtil.giveOrQueue(
                        owner,
                        List.of(paper),
                        data
                );
            }

            owner.sendSystemMessage(
                    Component.translatable(
                            "bountifulrequests.message.published"
                    )
            );

            RequestNetwork.sendDraftSync(
                    owner
            );
        }

        data.setDirty();
    }

    // ------------------------------------------------------------
    // CLAIM
    // ------------------------------------------------------------

    public static boolean claimFromBoard(
            ServerPlayer player,
            ItemStack paper
    ) {
        UUID requestId =
                RequestBountyData.getRequestId(
                        paper
                );

        if (requestId == null) {
            return true;
        }

        RequestSavedData data =
                RequestSavedData.get(player.server);

        RequestPublication publication =
                data.publications.get(requestId);

        if (publication == null) {
            return false;
        }

        if (publication.kind
                == RequestPublication.Kind.HANDOUT) {
            return true;
        }

        /*
         * Si ya tiene claim y todavía conserva el papel,
         * no puede tomar otro.
         */
        ActiveClaim existing =
                publication.activeClaims.get(
                        player.getUUID()
                );

        if (existing != null) {
            return !playerHasPaper(
                    player,
                    requestId
            );
        }

        if (!publication.isVisibleOnBoard()) {
            return false;
        }

        if (publication.availableBundles.isEmpty()) {
            return false;
        }

        BountyStack bounty =
                new BountyStack(paper);

        long remaining =
                bounty.getInfo()
                        .timeLeftTicks(
                                player.level()
                        );

        if (remaining <= 0) {
            return false;
        }

        List<ItemStack> reserved =
                publication.availableBundles
                        .remove(0);

        ActiveClaim claim =
                new ActiveClaim(
                        player.getUUID(),
                        player.server
                                .overworld()
                                .getGameTime()
                                + remaining
                );

        claim.rewardBundle.addAll(
                reserved
        );

        publication.activeClaims.put(
                player.getUUID(),
                claim
        );

        if (publication.kind
                == RequestPublication.Kind.BOARD) {

            publication.state =
                    RequestPublication.State.CLAIMED;
        }

        data.setDirty();

        return true;
    }

    /**
     * HANDOUT no pasa por un Board, por lo que se reserva para quien
     * intente entregarlo por primera vez.
     */
    public static boolean ensureHandoutClaim(
            ServerPlayer player,
            ItemStack paper,
            RequestPublication publication
    ) {
        if (publication.kind
                != RequestPublication.Kind.HANDOUT) {

            return publication.activeClaims
                    .containsKey(player.getUUID());
        }

        ActiveClaim existing =
                publication.activeClaims.get(
                        player.getUUID()
                );

        if (existing != null) {
            return true;
        }

        if (publication.state
                != RequestPublication.State.OPEN
                || publication.availableBundles.isEmpty()) {

            return false;
        }

        long remaining =
                new BountyStack(paper)
                        .getInfo()
                        .timeLeftTicks(player.level());

        if (remaining <= 0) {
            return false;
        }

        ActiveClaim claim =
                new ActiveClaim(
                        player.getUUID(),
                        player.server
                                .overworld()
                                .getGameTime()
                                + remaining
                );

        claim.rewardBundle.addAll(
                publication.availableBundles.remove(0)
        );

        publication.activeClaims.put(
                player.getUUID(),
                claim
        );

        publication.state =
                RequestPublication.State.CLAIMED;

        RequestSavedData.get(player.server)
                .setDirty();

        return true;
    }

    private static boolean playerHasPaper(
            ServerPlayer player,
            UUID requestId
    ) {
        for (ItemStack stack
                : player.getInventory().items) {

            UUID found =
                    RequestBountyData.getRequestId(
                            stack
                    );

            if (requestId.equals(found)) {
                return true;
            }
        }

        return false;
    }

    // ------------------------------------------------------------
    // COBBLEMON PROGRESS
    // ------------------------------------------------------------

    /**
     * Recibe eventos ya normalizados desde la integración opcional.
     *
     * Esta clase no referencia tipos de Cobblemon, por lo que el addon
     * continúa cargando perfectamente cuando Cobblemon no está instalado.
     */
    public static void recordCobblemonEvent(
            ServerPlayer player,
            boolean capture,
            String speciesId,
            Set<String> pokemonTypes
    ) {
        RequestSavedData data =
                RequestSavedData.get(
                        player.server
                );

        String normalizedSpecies =
                speciesId == null
                        ? ""
                        : speciesId.toLowerCase(
                                Locale.ROOT
                        );

        Set<String> normalizedTypes =
                pokemonTypes.stream()
                        .map(type ->
                                type.toLowerCase(
                                        Locale.ROOT
                                )
                        )
                        .collect(
                                java.util.stream.Collectors.toSet()
                        );

        /*
         * Los HANDOUT no pasan por BoardBountySlot.
         *
         * Sólo reservamos un HANDOUT si ESTE evento realmente coincide con
         * uno de sus objetivos Cobblemon. Así capturar cualquier Pokémon no
         * bloquea accidentalmente otros papeles HANDOUT que el jugador lleve.
         */
        ensureMatchingHeldHandoutClaims(
                player,
                data,
                capture,
                normalizedSpecies,
                normalizedTypes
        );

        boolean changed = false;

        for (RequestPublication publication
                : data.publications.values()) {

            ActiveClaim claim =
                    publication.activeClaims.get(
                            player.getUUID()
                    );

            if (claim == null) {
                continue;
            }

            for (int index = 0;
                 index < publication.objectives.size();
                 index++) {

                ObjectiveSpec objective =
                        publication.objectives.get(
                                index
                        );

                if (!objective.isCobblemon()) {
                    continue;
                }

                if (!matchesCobblemonEvent(
                        objective,
                        capture,
                        normalizedSpecies,
                        normalizedTypes
                )) {
                    continue;
                }

                int oldProgress =
                        claim.objectiveProgress
                                .getOrDefault(
                                        index,
                                        0
                                );

                if (oldProgress
                        >= objective.amount) {
                    continue;
                }

                int newProgress =
                        Math.min(
                                objective.amount,
                                oldProgress + 1
                        );

                claim.objectiveProgress.put(
                        index,
                        newProgress
                );

                syncCobblemonProgressToPaper(
                        player,
                        publication.id,
                        index,
                        newProgress
                );

                changed = true;
            }
        }

        if (changed) {
            data.setDirty();
        }
    }

    private static boolean matchesCobblemonEvent(
            ObjectiveSpec objective,
            boolean capture,
            String speciesId,
            Set<String> pokemonTypes
    ) {
        String content =
                objective.content == null
                        ? ""
                        : objective.content.toLowerCase(
                                Locale.ROOT
                        );

        return switch (objective.kind) {
            case COBBLEMON_CAPTURE_SPECIES ->
                    capture
                            && (
                            "*".equals(content)
                                    || content.equals(
                                    speciesId
                            )
                    );

            case COBBLEMON_DEFEAT_SPECIES ->
                    !capture
                            && (
                            "*".equals(content)
                                    || content.equals(
                                    speciesId
                            )
                    );

            case COBBLEMON_CAPTURE_TYPE ->
                    capture
                            && pokemonTypes.contains(
                            content
                    );

            case COBBLEMON_DEFEAT_TYPE ->
                    !capture
                            && pokemonTypes.contains(
                            content
                    );

            default -> false;
        };
    }

    private static void ensureMatchingHeldHandoutClaims(
            ServerPlayer player,
            RequestSavedData data,
            boolean capture,
            String speciesId,
            Set<String> pokemonTypes
    ) {
        for (int slot = 0;
             slot < player.getInventory()
                     .getContainerSize();
             slot++) {

            ItemStack stack =
                    player.getInventory()
                            .getItem(slot);

            UUID requestId =
                    RequestBountyData.getRequestId(
                            stack
                    );

            if (requestId == null) {
                continue;
            }

            RequestPublication publication =
                    data.publications.get(
                            requestId
                    );

            if (publication == null
                    || publication.kind
                    != RequestPublication.Kind.HANDOUT
                    || publication.activeClaims
                    .containsKey(
                            player.getUUID()
                    )) {

                continue;
            }

            boolean matchingObjective =
                    publication.objectives.stream()
                            .filter(
                                    ObjectiveSpec::isCobblemon
                            )
                            .anyMatch(
                                    objective ->
                                            matchesCobblemonEvent(
                                                    objective,
                                                    capture,
                                                    speciesId,
                                                    pokemonTypes
                                            )
                            );

            if (!matchingObjective) {
                continue;
            }

            ensureHandoutClaim(
                    player,
                    stack,
                    publication
            );
        }
    }

    private static void syncCobblemonProgressToPaper(
            ServerPlayer player,
            UUID requestId,
            int objectiveIndex,
            int progress
    ) {
        for (int slot = 0;
             slot < player.getInventory()
                     .getContainerSize();
             slot++) {

            ItemStack stack =
                    player.getInventory()
                            .getItem(slot);

            UUID paperRequestId =
                    RequestBountyData.getRequestId(
                            stack
                    );

            if (!requestId.equals(
                    paperRequestId
            )) {
                continue;
            }

            RequestBountyData.setCobblemonProgress(
                    stack,
                    objectiveIndex,
                    progress
            );

            /*
             * Reutilizamos el aviso/sonido nativo de Bountiful cuando TODOS
             * los objetivos (incluidos los nuestros) estén realmente listos.
             */
            new BountyStack(
                    stack
            ).checkForCompletionAndAlert(
                    player
            );
        }
    }

    public static boolean areCobblemonObjectivesComplete(
            RequestPublication publication,
            ActiveClaim claim
    ) {
        for (int index = 0;
             index < publication.objectives.size();
             index++) {

            ObjectiveSpec objective =
                    publication.objectives.get(
                            index
                    );

            if (!objective.isCobblemon()) {
                continue;
            }

            int progress =
                    claim.objectiveProgress
                            .getOrDefault(
                                    index,
                                    0
                            );

            if (progress < objective.amount) {
                return false;
            }
        }

        return true;
    }

    // ------------------------------------------------------------
    // COMPLETE
    // ------------------------------------------------------------

    public static boolean complete(
            ServerPlayer player,
            UUID requestId,
            List<ItemStack> deliveredItems
    ) {
        RequestSavedData data =
                RequestSavedData.get(player.server);

        RequestPublication publication =
                data.publications.get(requestId);

        if (publication == null) {
            return false;
        }

        ActiveClaim claim =
                publication.activeClaims.remove(
                        player.getUUID()
                );

        if (claim == null) {
            return false;
        }

        /*
         * Lo que pidió el creador no desaparece.
         * Va a su bandeja "Deliveries".
         */
        if (!deliveredItems.isEmpty()) {
            data.addDelivery(
                    publication.owner,
                    deliveredItems
            );
        }

        /*
         * La recompensa reservada se entrega al aventurero.
         */
        InventoryUtil.giveOrQueue(
                player,
                claim.rewardBundle,
                data
        );

        if (publication.kind
                == RequestPublication.Kind.ROTATION) {

            if (publication.availableBundles.isEmpty()
                    && publication.activeClaims.isEmpty()) {

                publication.state =
                        RequestPublication.State.COMPLETED;
            }

        } else {
            publication.state =
                    RequestPublication.State.COMPLETED;
        }

        data.setDirty();

        ServerPlayer owner =
                player.server
                        .getPlayerList()
                        .getPlayer(
                                publication.owner
                        );

        if (owner != null) {
            owner.sendSystemMessage(
                    Component.translatable(
                            "bountifulrequests.message.request_completed",
                            publication.title,
                            player.getName()
                    )
            );
        }

        return true;
    }

    // ------------------------------------------------------------
    // EXPIRATION
    // ------------------------------------------------------------

    private static void expireClaim(
            RequestSavedData data,
            RequestPublication publication,
            ActiveClaim claim
    ) {
        if (publication.kind
                == RequestPublication.Kind.ROTATION
                && publication.state
                != RequestPublication.State.CLOSING) {

            /*
             * COPIA de rotación expirada:
             * vuelve a la reserva de la rotación.
             */
            publication.availableBundles.add(
                    InventoryUtil.copyBundle(
                            claim.rewardBundle
                    )
            );

        } else {
            /*
             * Misión normal expirada o rotación cerrada:
             * vuelve al creador.
             */
            data.addReturn(
                    publication.owner,
                    claim.rewardBundle
            );
        }

        if (publication.kind
                != RequestPublication.Kind.ROTATION) {

            publication.state =
                    RequestPublication.State.EXPIRED;
        }
    }

    // ------------------------------------------------------------
    // ADMIN REMOVE
    // ------------------------------------------------------------

    public static boolean removePublication(
            MinecraftServer server,
            UUID id
    ) {
        RequestSavedData data =
                RequestSavedData.get(server);

        RequestPublication publication =
                data.publications.get(id);

        if (publication == null) {
            return false;
        }

        for (List<ItemStack> bundle
                : publication.availableBundles) {

            data.addReturn(
                    publication.owner,
                    bundle
            );
        }

        publication.availableBundles.clear();

        if (publication.activeClaims.isEmpty()) {
            publication.state =
                    RequestPublication.State.REMOVED;
        } else {
            /*
             * Los jugadores que ya aceptaron mantienen sus rewards.
             */
            publication.state =
                    RequestPublication.State.CLOSING;
        }

        data.setDirty();

        return true;
    }

    // ------------------------------------------------------------
    // INBOX
    // ------------------------------------------------------------

    public static void collectDeliveries(
            ServerPlayer player
    ) {
        collectInbox(
                player,
                true
        );
    }

    public static void collectReturns(
            ServerPlayer player
    ) {
        collectInbox(
                player,
                false
        );
    }

    private static void collectInbox(
            ServerPlayer player,
            boolean delivery
    ) {
        RequestSavedData data =
                RequestSavedData.get(player.server);

        List<ItemStack> source =
                (delivery
                        ? data.deliveries
                        : data.returns)
                        .computeIfAbsent(
                                player.getUUID(),
                                ignored -> new ArrayList<>()
                        );

        List<ItemStack> remaining =
                new ArrayList<>();

        for (ItemStack original : source) {
            ItemStack stack =
                    original.copy();

            player.getInventory().add(
                    stack
            );

            if (!stack.isEmpty()) {
                remaining.add(
                        stack.copy()
                );
            }
        }

        source.clear();
        source.addAll(remaining);

        data.setDirty();
    }

    // ------------------------------------------------------------
    // GUI VIEW
    // ------------------------------------------------------------

    public static DraftView createView(
            ServerPlayer player
    ) {
        RequestSavedData data =
                RequestSavedData.get(player.server);

        RequestDraft draft =
                data.getOrCreateDraft(
                        player.getUUID()
                );

        long now =
                player.server
                        .overworld()
                        .getGameTime();

        long pendingRemaining =
                draft.isPending()
                        ? Math.max(
                        0,
                        draft.pendingUntilTick - now
                )
                        : 0;

        return new DraftView(
                draft.title,
                draft.rarity,
                draft.durationSeconds,
                new ArrayList<>(
                        draft.objectives
                ),
                draft.pendingMode.name(),
                pendingRemaining,
                draft.rotationUses,
                player.hasPermissions(2),
                data.deliveries
                        .getOrDefault(
                                player.getUUID(),
                                List.of()
                        )
                        .size(),
                data.returns
                        .getOrDefault(
                                player.getUUID(),
                                List.of()
                        )
                        .size()
        );
    }
}