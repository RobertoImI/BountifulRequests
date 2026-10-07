package org.crafterscr.bountifulrequests.client;

/**
 * DTO seguro para el editor.
 *
 * No referencia clases de Cobblemon, por lo que RequestEditorScreen puede
 * cargarse aunque la integración opcional no esté instalada.
 */
public record CobblemonOption(
        String id,
        String label
) {
}
