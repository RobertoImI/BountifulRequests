package org.crafterscr.bountifulrequests.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.crafterscr.bountifulrequests.data.ObjectiveSpec;
import org.crafterscr.bountifulrequests.data.RequestPublication;
import org.crafterscr.bountifulrequests.util.RequestBountyData;

import io.ejekta.bountiful.bounty.BountyRarity;
import io.ejekta.bountiful.components.BountyDataEntry;
import io.ejekta.bountiful.components.BountyInfo;
import io.ejekta.bountiful.components.BountyStack;
import io.ejekta.bountiful.content.BountifulContent;
import io.ejekta.bountiful.data.PoolEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Construye un BountyItem auténtico de Bountiful.
 *
 * Nuestro addon NO crea un item de papel alternativo.
 */
public final class BountyPaperFactory {

    private BountyPaperFactory() {
    }

    public static ItemStack create(
            RequestPublication publication,
            ServerLevel level,
            BlockPos boardPosition
    ) {
        ItemStack paper =
                new ItemStack(
                        BountifulContent.INSTANCE
                                .getBOUNTY_ITEM()
                );

        BountyStack bounty =
                new BountyStack(paper);

        BountyRarity rarity =
                BountyRarity.values()[
                        Math.max(
                                0,
                                Math.min(
                                        BountyRarity.values().length - 1,
                                        publication.rarity
                                )
                        )
                        ];

        List<BountyDataEntry> objectives =
                new ArrayList<>();

        int index = 0;

        for (ObjectiveSpec spec
                : publication.objectives) {

            BountyDataEntry entry =
                    createObjective(
                            publication,
                            spec,
                            rarity,
                            index++
                    );

            if (entry != null) {
                objectives.add(entry);
            }
        }

        List<BountyDataEntry> rewards =
                new ArrayList<>();

        List<ItemStack> displayedReward =
                publication.availableBundles
                        .isEmpty()
                        ? List.of()
                        : publication.availableBundles.get(0);

        index = 0;

        for (ItemStack stack : displayedReward) {
            if (stack.isEmpty()) {
                continue;
            }

            String itemId =
                    BuiltInRegistries.ITEM
                            .getKey(stack.getItem())
                            .toString();

            rewards.add(
                    new BountyDataEntry(
                            "bountifulrequests."
                                    + publication.id
                                    + ".reward."
                                    + index++,
                            itemId,
                            rarity,
                            "item",
                            stack.getCount(),
                            null,
                            null
                    )
            );
        }

        long now = level.getGameTime();

        /*
         * BOARD:
         * el timer comienza cuando fue publicada.
         *
         * ROTATION:
         * cada nuevo papel recibe un timer fresco.
         *
         * HANDOUT:
         * comienza desde su creación.
         */
        long startTime =
                publication.kind
                        == RequestPublication.Kind.ROTATION
                        ? now
                        : publication.publishTick;

        bounty.setObjs(objectives);
        bounty.setRews(rewards);

        bounty.setInfo(
                new BountyInfo(
                        rarity,
                        startTime,
                        publication.durationSeconds,
                        startTime,
                        null,
                        null,
                        null
                )
        );

        RequestBountyData.setRequestId(
                paper,
                publication.id
        );

        /*
         * Mostramos el título personalizado como nombre del ItemStack.
         */
        if (publication.title != null
                && !publication.title.isBlank()) {

            paper.set(
                    DataComponents.CUSTOM_NAME,
                    Component.literal(
                            publication.title
                    ).withStyle(rarity.getColor())
            );
        }

        return paper;
    }

    private static BountyDataEntry createObjective(
            RequestPublication publication,
            ObjectiveSpec spec,
            BountyRarity publicationRarity,
            int index
    ) {
        return switch (spec.kind) {

            case ITEM -> new BountyDataEntry(
                    id(publication, index),
                    spec.content,
                    publicationRarity,
                    "item",
                    spec.amount,
                    null,
                    null
            );

            case ITEM_TAG -> new BountyDataEntry(
                    id(publication, index),
                    spec.content,
                    publicationRarity,
                    "item_tag",
                    spec.amount,
                    null,
                    null
            );

            case ENTITY -> new BountyDataEntry(
                    id(publication, index),
                    spec.content,
                    publicationRarity,
                    "entity",
                    spec.amount,
                    null,
                    null
            );

            /*
             * Los Bountiful Entry ya deberían estar resueltos al publicar.
             */
            case BOUNTIFUL_RESOLVED -> {
                BountyRarity rarity =
                        BountyRarity.values()[
                                Math.max(
                                        0,
                                        Math.min(
                                                BountyRarity.values().length - 1,
                                                spec.entryRarity
                                        )
                                )
                                ];

                yield new BountyDataEntry(
                        spec.entryId,
                        spec.content,
                        rarity,
                        spec.logicName,
                        spec.amount,
                        spec.customName.isBlank()
                                ? null
                                : spec.customName,
                        spec.parsedData()
                );
            }

            case BOUNTIFUL_ENTRY -> null;
        };
    }

    private static String id(
            RequestPublication publication,
            int index
    ) {
        return "bountifulrequests."
                + publication.id
                + ".objective."
                + index;
    }

    /**
     * Resuelve una entrada avanzada de Bountiful UNA sola vez.
     *
     * Así un PoolEntry con rango 5-10 no cambia de cantidad cada vez
     * que el board vuelve a generar el papel.
     */
    public static ObjectiveSpec resolveBountifulEntry(
            ObjectiveSpec source,
            ServerLevel level,
            BlockPos position
    ) {
        PoolEntry poolEntry =
                BountifulContent.INSTANCE
                        .getPoolEntryMap()
                        .get(source.content);

        if (poolEntry == null) {
            return null;
        }

        try {
            BountyDataEntry generated =
                    poolEntry.toEntry(
                                    level,
                                    position,
                                    null,
                                    Collections.emptySet(),
                                    false
                            )
                            .getDataEntry();

            return ObjectiveSpec.resolved(
                    generated
            );
        } catch (Exception exception) {
            return null;
        }
    }
}