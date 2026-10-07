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
 * Una aparición de rotación es GLOBAL:
 * - se muestra en todos los Boards;
 * - el primer jugador que la toma la desactiva para todos;
 * - si nadie la toma, desaparece cuando vence su duración;
 * - una plantilla inactiva puede volver a ser elegida en una rotación futura.
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

        boolean changed = false;

        /*
         * Si una misión rotativa sigue en el Board sin que nadie la tome,
         * su propia duración manda. Al vencer deja de estar visible en TODOS
         * los Boards y queda disponible para ciclos futuros.
         */
        for (RequestPublication publication
                : data.publications.values()) {

            if (!publication.isRotation()
                    || !publication.rotationActive) {

                continue;
            }

            if (publication.rotationVisibleUntilTick > 0L
                    && now >= publication.rotationVisibleUntilTick) {

                publication.rotationActive = false;
                publication.rotationAppearanceStartTick = 0L;
                publication.rotationVisibleUntilTick = 0L;

                changed = true;
            }
        }

        if (changed) {
            data.setDirty();
        }

        /*
         * El cambio general del pool ocurre con el intervalo administrativo.
         * Las misiones que vencieron o fueron tomadas NO se reponen de forma
         * inmediata: esperan al siguiente refresh de rotación.
         */
        if (data.getNextRotationTick() <= 0L
                || now >= data.getNextRotationTick()) {

            refresh(server);
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
         * Si todavía hay plazas libres, permitimos que una plantilla recién
         * creada entre de inmediato. Si el cupo está lleno esperará su turno.
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

        long now =
                server.overworld()
                        .getGameTime();

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
         * Primero intentamos usar plantillas que NO estaban visibles en el
         * ciclo anterior. Así el Board cambia de verdad cuando hay suficiente
         * contenido.
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
         * Si el pool es pequeño, completamos los huecos con algunas de las
         * que ya estaban activas.
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

            publication.rotationActive =
                    shouldBeActive;

            if (shouldBeActive) {
                /*
                 * Esta es una NUEVA aparición global.
                 * Todos los Boards compartirán exactamente estos ticks.
                 */
                publication.rotationAppearanceStartTick =
                        now;

                publication.rotationVisibleUntilTick =
                        now
                                + publication.durationSeconds
                                * 20L;

            } else {
                publication.rotationAppearanceStartTick =
                        0L;

                publication.rotationVisibleUntilTick =
                        0L;
            }
        }

        data.setNextRotationTick(
                now
                        + data.getRotationIntervalSeconds()
                        * 20L
        );

        data.setDirty();
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
