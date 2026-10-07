package org.crafterscr.bountifulrequests.client;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalTypes;

/**
 * Datos de Cobblemon usados únicamente por el editor cliente.
 *
 * RequestEditorScreen comprueba ModList antes de llamar esta clase.
 */
public final class CobblemonClientBridge {

    private CobblemonClientBridge() {
    }

    public static List<CobblemonOption> speciesOptions() {
        return PokemonSpecies.getSpecies()
                .stream()
                .filter(species ->
                        species.getImplemented()
                )
                .map(species ->
                        new CobblemonOption(
                                species.getResourceIdentifier()
                                        .toString()
                                        .toLowerCase(
                                                Locale.ROOT
                                        ),
                                species.getTranslatedName()
                                        .getString()
                        )
                )
                .sorted(
                        Comparator.comparing(
                                CobblemonOption::label,
                                String.CASE_INSENSITIVE_ORDER
                        )
                )
                .toList();
    }

    public static List<CobblemonOption> typeOptions() {
        return ElementalTypes.all()
                .stream()
                .map(type ->
                        new CobblemonOption(
                                type.getName()
                                        .toLowerCase(
                                                Locale.ROOT
                                        ),
                                type.getDisplayName()
                                        .getString()
                        )
                )
                .sorted(
                        Comparator.comparing(
                                CobblemonOption::label,
                                String.CASE_INSENSITIVE_ORDER
                        )
                )
                .toList();
    }

    public static String speciesLabel(
            String id
    ) {
        if ("*".equals(id)) {
            return "*";
        }

        return speciesOptions()
                .stream()
                .filter(option ->
                        option.id().equalsIgnoreCase(
                                id
                        )
                )
                .map(
                        CobblemonOption::label
                )
                .findFirst()
                .orElse(id);
    }

    public static String typeLabel(
            String id
    ) {
        return typeOptions()
                .stream()
                .filter(option ->
                        option.id().equalsIgnoreCase(
                                id
                        )
                )
                .map(
                        CobblemonOption::label
                )
                .findFirst()
                .orElse(id);
    }
}
