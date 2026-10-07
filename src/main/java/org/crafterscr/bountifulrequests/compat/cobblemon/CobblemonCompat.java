package org.crafterscr.bountifulrequests.compat.cobblemon;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import org.crafterscr.bountifulrequests.BountifulRequests;
import org.crafterscr.bountifulrequests.service.RequestManager;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.server.level.ServerPlayer;

/**
 * Integración opcional con Cobblemon 1.7.x.
 *
 * Esta clase sólo se carga cuando NeoForge confirma que Cobblemon está
 * instalado, por lo que Bountiful Requests sigue funcionando sin Cobblemon.
 */
public final class CobblemonCompat {

    private static boolean initialized = false;

    private CobblemonCompat() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;

        CobblemonEvents.POKEMON_CAPTURED.subscribe(
                (Consumer<PokemonCapturedEvent>)
                        CobblemonCompat::onPokemonCaptured
        );

        CobblemonEvents.BATTLE_FAINTED.subscribe(
                (Consumer<BattleFaintedEvent>)
                        CobblemonCompat::onBattleFainted
        );

        BountifulRequests.LOGGER.info(
                "Cobblemon compatibility enabled."
        );
    }

    private static void onPokemonCaptured(
            PokemonCapturedEvent event
    ) {
        Pokemon pokemon =
                event.getPokemon();

        RequestManager.recordCobblemonEvent(
                event.getPlayer(),
                true,
                speciesId(pokemon),
                typeIds(pokemon)
        );
    }

    /**
     * Cada vez que un Pokémon se debilita en batalla, acreditamos la derrota
     * a los jugadores del lado contrario al Pokémon derrotado.
     *
     * Esto funciona para combates salvajes, NPCs y combates múltiples.
     */
    private static void onBattleFainted(
            BattleFaintedEvent event
    ) {
        Pokemon defeated =
                event.getKilled()
                        .getEffectedPokemon();

        BattleActor defeatedActor =
                event.getKilled()
                        .getActor();

        for (ServerPlayer player
                : event.getBattle()
                .getPlayers()) {

            BattleActor playerActor =
                    event.getBattle()
                            .getActor(player);

            if (playerActor == null) {
                continue;
            }

            if (playerActor.getSide()
                    == defeatedActor.getSide()) {

                continue;
            }

            RequestManager.recordCobblemonEvent(
                    player,
                    false,
                    speciesId(defeated),
                    typeIds(defeated)
            );
        }
    }

    private static String speciesId(
            Pokemon pokemon
    ) {
        return pokemon.getSpecies()
                .getResourceIdentifier()
                .toString()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private static Set<String> typeIds(
            Pokemon pokemon
    ) {
        Set<String> result =
                new HashSet<>();

        for (ElementalType type
                : pokemon.getForm()
                .getTypes()) {

            result.add(
                    type.getName()
                            .toLowerCase(
                                    Locale.ROOT
                            )
            );
        }

        return result;
    }
}
