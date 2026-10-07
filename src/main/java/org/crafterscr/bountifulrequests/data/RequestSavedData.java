package org.crafterscr.bountifulrequests.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Almacenamiento persistente global.
 *
 * Se guarda en el Overworld porque contiene información del servidor
 * completo, no de una dimensión concreta.
 */
public final class RequestSavedData extends SavedData {

    private static final String FILE_NAME =
            "bountifulrequests";

    public final Map<UUID, RequestDraft> drafts =
            new LinkedHashMap<>();

    public final Map<UUID, RequestPublication> publications =
            new LinkedHashMap<>();

    /**
     * Objetos que otros jugadores entregaron al creador.
     */
    public final Map<UUID, List<ItemStack>> deliveries =
            new LinkedHashMap<>();

    /**
     * Recompensas devueltas por expiraciones/cancelaciones.
     */
    public final Map<UUID, List<ItemStack>> returns =
            new LinkedHashMap<>();

    /**
     * true  = Bountiful puede generar sus bounties automáticos normales.
     * false = el Board queda reservado para publicaciones de este addon.
     *
     * El valor por defecto es true para no cambiar el comportamiento de
     * mundos existentes hasta que un administrador lo desactive.
     */
    private boolean defaultBountifulRequestsEnabled = true;

    /**
     * Gestor global de rotaciones.
     *
     * rotationVisibleSlots = cuántas plantillas ROTATION intentamos mostrar
     * simultáneamente en cada Board.
     * rotationIntervalSeconds = cada cuánto cambiamos la selección.
     * nextRotationTick = siguiente momento programado para refrescar.
     */
    private int rotationVisibleSlots = 6;
    private long rotationIntervalSeconds = 30L * 60L;
    private long nextRotationTick = 0L;

    /**
     * Siguiente ID corto visible para administración.
     */
    private int nextShortId = 1;

    public static RequestSavedData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(
                                RequestSavedData::new,
                                RequestSavedData::load
                        ),
                        FILE_NAME
                );
    }

    public RequestDraft getOrCreateDraft(UUID owner) {
        return drafts.computeIfAbsent(
                owner,
                RequestDraft::new
        );
    }

    public boolean areDefaultBountifulRequestsEnabled() {
        return defaultBountifulRequestsEnabled;
    }

    public void setDefaultBountifulRequestsEnabled(
            boolean enabled
    ) {
        if (defaultBountifulRequestsEnabled == enabled) {
            return;
        }

        defaultBountifulRequestsEnabled = enabled;
        setDirty();
    }

    public int getRotationVisibleSlots() {
        return rotationVisibleSlots;
    }

    public void setRotationVisibleSlots(
            int slots
    ) {
        rotationVisibleSlots =
                Math.max(
                        1,
                        Math.min(21, slots)
                );

        setDirty();
    }

    public long getRotationIntervalSeconds() {
        return rotationIntervalSeconds;
    }

    public void setRotationIntervalSeconds(
            long seconds
    ) {
        rotationIntervalSeconds =
                Math.max(
                        60L,
                        seconds
                );

        setDirty();
    }

    public long getNextRotationTick() {
        return nextRotationTick;
    }

    public void setNextRotationTick(
            long tick
    ) {
        nextRotationTick =
                Math.max(0L, tick);

        setDirty();
    }

    public int allocateShortId() {
        int result =
                Math.max(
                        1,
                        nextShortId
                );

        nextShortId =
                result + 1;

        setDirty();

        return result;
    }

    public RequestPublication findByShortId(
            int shortId
    ) {
        return publications.values()
                .stream()
                .filter(publication ->
                        publication.shortId
                                == shortId
                )
                .findFirst()
                .orElse(null);
    }

    public void addDelivery(UUID owner, List<ItemStack> stacks) {
        addStacks(deliveries, owner, stacks);
    }

    public void addReturn(UUID owner, List<ItemStack> stacks) {
        addStacks(returns, owner, stacks);
    }

    private void addStacks(
            Map<UUID, List<ItemStack>> target,
            UUID owner,
            List<ItemStack> stacks
    ) {
        List<ItemStack> list =
                target.computeIfAbsent(owner, ignored -> new ArrayList<>());

        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                list.add(stack.copy());
            }
        }

        setDirty();
    }

    // ------------------------------------------------------------
    // SAVE
    // ------------------------------------------------------------

    @Override
    public CompoundTag save(
            CompoundTag root,
            HolderLookup.Provider provider
    ) {
        root.putBoolean(
                "DefaultBountifulRequestsEnabled",
                defaultBountifulRequestsEnabled
        );

        root.putInt(
                "RotationVisibleSlots",
                rotationVisibleSlots
        );

        root.putLong(
                "RotationIntervalSeconds",
                rotationIntervalSeconds
        );

        root.putLong(
                "NextRotationTick",
                nextRotationTick
        );

        root.putInt(
                "NextShortId",
                nextShortId
        );

        root.put("Drafts", saveDrafts(provider));
        root.put("Publications", savePublications(provider));

        root.put(
                "Deliveries",
                saveInbox(provider, deliveries)
        );

        root.put(
                "Returns",
                saveInbox(provider, returns)
        );

        return root;
    }

    private ListTag saveDrafts(
            HolderLookup.Provider provider
    ) {
        ListTag list = new ListTag();

        for (RequestDraft draft : drafts.values()) {
            CompoundTag tag = new CompoundTag();

            tag.putUUID("Owner", draft.owner);
            tag.putString("Title", draft.title);
            tag.putInt("Rarity", draft.rarity);
            tag.putLong(
                    "Duration",
                    draft.durationSeconds
            );

            ListTag objectiveTags = new ListTag();

            for (ObjectiveSpec objective : draft.objectives) {
                objectiveTags.add(objective.save());
            }

            tag.put("Objectives", objectiveTags);

            tag.put(
                    "Rewards",
                    StackSerialization.saveFixed(
                            provider,
                            draft.rewards
                    )
            );

            tag.putString(
                    "PendingMode",
                    draft.pendingMode.name()
            );

            tag.putLong(
                    "PendingUntil",
                    draft.pendingUntilTick
            );

            tag.putInt(
                    "RotationUses",
                    draft.rotationUses
            );

            tag.put(
                    "PendingExtras",
                    StackSerialization.saveBundles(
                            provider,
                            draft.pendingExtraBundles
                    )
            );

            list.add(tag);
        }

        return list;
    }

    private ListTag savePublications(
            HolderLookup.Provider provider
    ) {
        ListTag list = new ListTag();

        for (RequestPublication publication
                : publications.values()) {

            CompoundTag tag = new CompoundTag();

            tag.putUUID("Id", publication.id);
            tag.putInt(
                    "ShortId",
                    publication.shortId
            );
            tag.putUUID("Owner", publication.owner);

            tag.putString("Title", publication.title);

            tag.putInt(
                    "Rarity",
                    publication.rarity
            );

            tag.putLong(
                    "Duration",
                    publication.durationSeconds
            );

            tag.putString(
                    "Kind",
                    publication.kind.name()
            );

            tag.putString(
                    "State",
                    publication.state.name()
            );

            tag.putLong(
                    "PublishTick",
                    publication.publishTick
            );

            tag.putBoolean(
                    "RotationActive",
                    publication.rotationActive
            );

            ListTag objectiveTags = new ListTag();

            for (ObjectiveSpec objective
                    : publication.objectives) {

                objectiveTags.add(objective.save());
            }

            tag.put("Objectives", objectiveTags);

            tag.put(
                    "AvailableBundles",
                    StackSerialization.saveBundles(
                            provider,
                            publication.availableBundles
                    )
            );

            tag.put(
                    "RotationRewardTemplate",
                    StackSerialization.saveList(
                            provider,
                            publication.rotationRewardTemplate
                    )
            );

            ListTag claims = new ListTag();

            for (ActiveClaim claim
                    : publication.activeClaims.values()) {

                CompoundTag claimTag = new CompoundTag();

                claimTag.putUUID(
                        "Player",
                        claim.player
                );

                claimTag.putLong(
                        "Expires",
                        claim.expiresAtTick
                );

                claimTag.put(
                        "Reward",
                        StackSerialization.saveList(
                                provider,
                                claim.rewardBundle
                        )
                );

                ListTag progressTags =
                        new ListTag();

                claim.objectiveProgress.forEach(
                        (objectiveIndex, progress) -> {
                            CompoundTag progressTag =
                                    new CompoundTag();

                            progressTag.putInt(
                                    "Index",
                                    objectiveIndex
                            );

                            progressTag.putInt(
                                    "Progress",
                                    progress
                            );

                            progressTags.add(
                                    progressTag
                            );
                        }
                );

                claimTag.put(
                        "ObjectiveProgress",
                        progressTags
                );

                claims.add(claimTag);
            }

            tag.put("Claims", claims);

            list.add(tag);
        }

        return list;
    }

    private ListTag saveInbox(
            HolderLookup.Provider provider,
            Map<UUID, List<ItemStack>> map
    ) {
        ListTag result = new ListTag();

        map.forEach((owner, stacks) -> {
            CompoundTag tag = new CompoundTag();

            tag.putUUID("Owner", owner);

            tag.put(
                    "Items",
                    StackSerialization.saveList(
                            provider,
                            stacks
                    )
            );

            result.add(tag);
        });

        return result;
    }

    // ------------------------------------------------------------
    // LOAD
    // ------------------------------------------------------------

    public static RequestSavedData load(
            CompoundTag root,
            HolderLookup.Provider provider
    ) {
        RequestSavedData data =
                new RequestSavedData();

        /*
         * Compatibilidad hacia atrás:
         * si la key no existe, mantenemos el comportamiento original de
         * Bountiful (defaults activados).
         */
        if (root.contains(
                "DefaultBountifulRequestsEnabled"
        )) {
            data.defaultBountifulRequestsEnabled =
                    root.getBoolean(
                            "DefaultBountifulRequestsEnabled"
                    );
        }

        if (root.contains("RotationVisibleSlots")) {
            data.rotationVisibleSlots =
                    Math.max(
                            1,
                            Math.min(
                                    21,
                                    root.getInt(
                                            "RotationVisibleSlots"
                                    )
                            )
                    );
        }

        if (root.contains("RotationIntervalSeconds")) {
            data.rotationIntervalSeconds =
                    Math.max(
                            60L,
                            root.getLong(
                                    "RotationIntervalSeconds"
                            )
                    );
        }

        if (root.contains("NextRotationTick")) {
            data.nextRotationTick =
                    Math.max(
                            0L,
                            root.getLong(
                                    "NextRotationTick"
                            )
                    );
        }

        if (root.contains("NextShortId")) {
            data.nextShortId =
                    Math.max(
                            1,
                            root.getInt(
                                    "NextShortId"
                            )
                    );
        }

        loadDrafts(data, root, provider);
        loadPublications(data, root, provider);

        loadInbox(
                data.deliveries,
                root.getList("Deliveries", Tag.TAG_COMPOUND),
                provider
        );

        loadInbox(
                data.returns,
                root.getList("Returns", Tag.TAG_COMPOUND),
                provider
        );

        return data;
    }

    private static void loadDrafts(
            RequestSavedData data,
            CompoundTag root,
            HolderLookup.Provider provider
    ) {
        ListTag drafts =
                root.getList("Drafts", Tag.TAG_COMPOUND);

        for (int i = 0; i < drafts.size(); i++) {
            CompoundTag tag = drafts.getCompound(i);

            UUID owner = tag.getUUID("Owner");

            RequestDraft draft =
                    new RequestDraft(owner);

            draft.title = tag.getString("Title");
            draft.rarity = tag.getInt("Rarity");
            draft.durationSeconds =
                    tag.getLong("Duration");

            ListTag objectives =
                    tag.getList(
                            "Objectives",
                            Tag.TAG_COMPOUND
                    );

            for (int j = 0; j < objectives.size(); j++) {
                draft.objectives.add(
                        ObjectiveSpec.load(
                                objectives.getCompound(j)
                        )
                );
            }

            draft.rewards =
                    StackSerialization.loadFixed(
                            provider,
                            tag.getList(
                                    "Rewards",
                                    Tag.TAG_COMPOUND
                            ),
                            9
                    );

            try {
                draft.pendingMode =
                        RequestDraft.PendingMode.valueOf(
                                tag.getString("PendingMode")
                        );
            } catch (Exception ignored) {
                draft.pendingMode =
                        RequestDraft.PendingMode.NONE;
            }

            draft.pendingUntilTick =
                    tag.getLong("PendingUntil");

            draft.rotationUses =
                    Math.max(
                            1,
                            tag.getInt("RotationUses")
                    );

            draft.pendingExtraBundles.addAll(
                    StackSerialization.loadBundles(
                            provider,
                            tag.getList(
                                    "PendingExtras",
                                    Tag.TAG_COMPOUND
                            )
                    )
            );

            data.drafts.put(owner, draft);
        }
    }

    private static void loadPublications(
            RequestSavedData data,
            CompoundTag root,
            HolderLookup.Provider provider
    ) {
        ListTag publications =
                root.getList(
                        "Publications",
                        Tag.TAG_COMPOUND
                );

        for (int i = 0; i < publications.size(); i++) {
            CompoundTag tag =
                    publications.getCompound(i);

            RequestPublication publication =
                    new RequestPublication();

            publication.id =
                    tag.getUUID("Id");

            publication.shortId =
                    tag.contains("ShortId")
                            ? tag.getInt("ShortId")
                            : 0;

            publication.owner =
                    tag.getUUID("Owner");

            publication.title =
                    tag.getString("Title");

            publication.rarity =
                    tag.getInt("Rarity");

            publication.durationSeconds =
                    tag.getLong("Duration");

            try {
                publication.kind =
                        RequestPublication.Kind.valueOf(
                                tag.getString("Kind")
                        );
            } catch (Exception ignored) {
                publication.kind =
                        RequestPublication.Kind.BOARD;
            }

            try {
                publication.state =
                        RequestPublication.State.valueOf(
                                tag.getString("State")
                        );
            } catch (Exception ignored) {
                publication.state =
                        RequestPublication.State.OPEN;
            }

            publication.publishTick =
                    tag.getLong("PublishTick");

            publication.rotationActive =
                    tag.getBoolean(
                            "RotationActive"
                    );

            ListTag objectives =
                    tag.getList(
                            "Objectives",
                            Tag.TAG_COMPOUND
                    );

            for (int j = 0; j < objectives.size(); j++) {
                publication.objectives.add(
                        ObjectiveSpec.load(
                                objectives.getCompound(j)
                        )
                );
            }

            publication.availableBundles.addAll(
                    StackSerialization.loadBundles(
                            provider,
                            tag.getList(
                                    "AvailableBundles",
                                    Tag.TAG_COMPOUND
                            )
                    )
            );

            publication.rotationRewardTemplate.addAll(
                    StackSerialization.loadList(
                            provider,
                            tag.getList(
                                    "RotationRewardTemplate",
                                    Tag.TAG_COMPOUND
                            )
                    )
            );

            /*
             * Migración de rotaciones antiguas finitas:
             * usamos el primer bundle guardado como plantilla infinita.
             */
            if (publication.kind
                    == RequestPublication.Kind.ROTATION
                    && publication.rotationRewardTemplate.isEmpty()
                    && !publication.availableBundles.isEmpty()) {

                publication.rotationRewardTemplate.addAll(
                        publication.availableBundles
                                .get(0)
                                .stream()
                                .map(ItemStack::copy)
                                .toList()
                );

                publication.availableBundles.clear();

                if (publication.state
                        == RequestPublication.State.COMPLETED
                        || publication.state
                        == RequestPublication.State.EXPIRED) {

                    publication.state =
                            RequestPublication.State.OPEN;
                }
            }

            ListTag claims =
                    tag.getList(
                            "Claims",
                            Tag.TAG_COMPOUND
                    );

            for (int j = 0; j < claims.size(); j++) {
                CompoundTag claimTag =
                        claims.getCompound(j);

                UUID player =
                        claimTag.getUUID("Player");

                ActiveClaim claim =
                        new ActiveClaim(
                                player,
                                claimTag.getLong("Expires")
                        );

                claim.rewardBundle.addAll(
                        StackSerialization.loadList(
                                provider,
                                claimTag.getList(
                                        "Reward",
                                        Tag.TAG_COMPOUND
                                )
                        )
                );

                ListTag progressTags =
                        claimTag.getList(
                                "ObjectiveProgress",
                                Tag.TAG_COMPOUND
                        );

                for (int k = 0;
                     k < progressTags.size();
                     k++) {

                    CompoundTag progressTag =
                            progressTags.getCompound(k);

                    claim.objectiveProgress.put(
                            progressTag.getInt("Index"),
                            progressTag.getInt("Progress")
                    );
                }

                publication.activeClaims.put(
                        player,
                        claim
                );
            }

            data.publications.put(
                    publication.id,
                    publication
            );
        }

        /*
         * Mundos anteriores no tenían IDs cortos. Los asignamos una vez al
         * cargar y adelantamos el contador para evitar colisiones.
         */
        int maxShortId =
                data.publications.values()
                        .stream()
                        .mapToInt(publication ->
                                Math.max(
                                        0,
                                        publication.shortId
                                )
                        )
                        .max()
                        .orElse(0);

        for (RequestPublication publication
                : data.publications.values()) {

            if (publication.shortId <= 0) {
                publication.shortId =
                        ++maxShortId;
            }
        }

        data.nextShortId =
                Math.max(
                        data.nextShortId,
                        maxShortId + 1
                );
    }

    private static void loadInbox(
            Map<UUID, List<ItemStack>> target,
            ListTag list,
            HolderLookup.Provider provider
    ) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag =
                    list.getCompound(i);

            UUID owner =
                    tag.getUUID("Owner");

            target.put(
                    owner,
                    StackSerialization.loadList(
                            provider,
                            tag.getList(
                                    "Items",
                                    Tag.TAG_COMPOUND
                            )
                    )
            );
        }
    }
}