package org.crafterscr.bountifulrequests.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.crafterscr.bountifulrequests.data.RequestPublication;
import org.crafterscr.bountifulrequests.data.RequestSavedData;

import net.minecraft.server.MinecraftServer;

/**
 * Selección global de plantillas administrativas ROTATION.
 *
 * Las rotaciones son permanentes y no se agotan. En cada ciclo elegimos un
 * subconjunto para mostrar en los Bountiful Boards. Cuando existen suficientes
 * alternativas, priorizamos las que NO estaban visibles en el ciclo anterior
 * para que el tablón realmente cambie.
 */
public final class RotationManager {

    private RotationManager() {
    }

    public static void tick(
            MinecraftServer server
    ) {
        long now =
                server.overworld()
                        .getGameTime();

        // Sólo revisar una vez por segundo.
        if (now % 20L != 0L) {
            return;
        }

        RequestSavedData data =
                RequestSavedData.get(server);

        if (data.getNextRotationTick() <= 0L
                || now >= data.getNextRotationTick()) {

            refresh(
                    server
            );
        }
    }

    public static void onRotationCreated(
            MinecraftServer server
    ) {
        RequestSavedData data =
                RequestSavedData.get(server);

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

        /*
         * Si hay un espacio libre, recalculamos ya para que la nueva plantilla
         * pueda entrar sin esperar al próximo ciclo. Si el pool ya está lleno,
         * esperará su turno normal.
         */
        if (active
                < data.getRotationVisibleSlots()) {

            refresh(server);
        }
    }

    public static void refresh(
            MinecraftServer server
    ) {
        RequestSavedData data =
                RequestSavedData.get(server);

        List<RequestPublication> candidates =
                data.publications.values()
                        .stream()
                        .filter(RequestPublication::isRotation)
                        .filter(publication ->
                                publication.state
                                        == RequestPublication.State.OPEN
                        )
                        .filter(publication ->
                                !publication.rotationRewardTemplate
                                        .isEmpty()
                        )
                        .toList();

        int target =
                Math.min(
                        data.getRotationVisibleSlots(),
                        candidates.size()
                );

        List<RequestPublication> inactive =
                new ArrayList<>();

        List<RequestPublication> previouslyActive =
                new ArrayList<>();

        for (RequestPublication publication
                : candidates) {

            if (publication.rotationActive) {
                previouslyActive.add(
                        publication
                );
            } else {
                inactive.add(
                        publication
                );
            }
        }

        Collections.shuffle(inactive);
        Collections.shuffle(previouslyActive);

        Set<RequestPublication> selected =
                new HashSet<>();

        /*
         * Primero entran las que no estaban visibles. Esto produce rotación
         * real cuando hay más plantillas que slots.
         */
        for (RequestPublication publication
                : inactive) {

            if (selected.size() >= target) {
                break;
            }

            selected.add(
                    publication
            );
        }

        /*
         * Si no existen suficientes alternativas, completamos con algunas de
         * las que ya estaban visibles.
         */
        for (RequestPublication publication
                : previouslyActive) {

            if (selected.size() >= target) {
                break;
            }

            selected.add(
                    publication
            );
        }

        boolean changed = false;

        for (RequestPublication publication
                : data.publications.values()) {

            if (!publication.isRotation()) {
                continue;
            }

            boolean shouldBeActive =
                    publication.state
                            == RequestPublication.State.OPEN
                            && selected.contains(
                            publication
                    );

            if (publication.rotationActive
                    != shouldBeActive) {

                publication.rotationActive =
                        shouldBeActive;

                changed = true;
            }
        }

        long now =
                server.overworld()
                        .getGameTime();

        data.setNextRotationTick(
                now
                        + data.getRotationIntervalSeconds()
                        * 20L
        );

        if (changed) {
            data.setDirty();
        }
    }

    public static long secondsUntilNextRefresh(
            MinecraftServer server
    ) {
        RequestSavedData data =
                RequestSavedData.get(server);

        long now =
                server.overworld()
                        .getGameTime();

        return Math.max(
                0L,
                (
                        data.getNextRotationTick()
                                - now
                ) / 20L
        );
    }
}
