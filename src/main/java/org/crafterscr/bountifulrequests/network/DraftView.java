package org.crafterscr.bountifulrequests.network;

import java.util.List;

import org.crafterscr.bountifulrequests.data.ObjectiveSpec;

/**
 * Sólo contiene información visual.
 *
 * Nunca confiamos en estos datos para entregar recompensas.
 */
public record DraftView(
        String title,
        int rarity,
        long durationSeconds,
        List<ObjectiveSpec> objectives,
        String pendingMode,
        long pendingRemainingTicks,
        int rotationUses,
        boolean admin,
        int deliveries,
        int returns
) {
}