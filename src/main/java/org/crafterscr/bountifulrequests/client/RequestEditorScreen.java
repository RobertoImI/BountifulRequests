package org.crafterscr.bountifulrequests.client;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.crafterscr.bountifulrequests.data.ObjectiveSpec;
import org.crafterscr.bountifulrequests.menu.RequestEditorMenu;
import org.crafterscr.bountifulrequests.network.DraftView;
import org.crafterscr.bountifulrequests.network.EditorActionPayload;

import io.ejekta.bountiful.bounty.BountyRarity;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Editor principal de Bountiful Requests.
 *
 * El diseño se dibuja programáticamente para no depender de una textura GUI.
 * Toda acción sensible sigue validándose en el servidor.
 */
public final class RequestEditorScreen
        extends AbstractContainerScreen<RequestEditorMenu> {

    private static final int GUI_WIDTH = 350;
    private static final int GUI_HEIGHT = 278;

    // Deben coincidir con RequestEditorMenu.
    private static final int REWARD_X = 260;
    private static final int REWARD_Y = 55;
    private static final int INVENTORY_X = 40;
    private static final int INVENTORY_Y = 178;
    private static final int HOTBAR_Y = INVENTORY_Y + 58;

    private enum Mode {
        MAIN,
        TYPE_SELECTOR,
        ITEM_BROWSER,
        ENTITY_BROWSER,
        TAG_BROWSER,
        COBBLEMON_SELECTOR,
        COBBLEMON_SPECIES_BROWSER,
        COBBLEMON_TYPE_BROWSER,
        CONFIRMATION
    }

    /**
     * La confirmación es puramente previa.
     * Nada se publica hasta que el usuario pulse Confirmar.
     */
    private enum ConfirmAction {
        CREATE_PAPER,
        PUBLISH_BOARD,
        PUBLISH_ROTATION,
        CANCEL_DRAFT
    }

    private DraftView state;

    private Mode mode = Mode.MAIN;
    private ConfirmAction confirmAction;

    private EditBox titleBox;
    private EditBox durationBox;
    private EditBox searchBox;
    private EditBox amountBox;
    private EditBox usesBox;
    private Button browserAddButton;

    private int browserOffset = 0;
    private int localRarity = 0;
    private int confirmedRotationUses = 1;

    /**
     * Selección temporal de los navegadores.
     *
     * Tocar una entrada ya no la agrega inmediatamente. Primero queda
     * resaltada y el jugador confirma con "Agregar objetivo".
     */
    private ObjectiveSpec.Kind selectedKind;
    private String selectedContent;

    /**
     * Acción elegida dentro del submenú Cobblemon.
     */
    private ObjectiveSpec.Kind pendingCobblemonKind;

    /*
     * Cobblemon puede tener cientos o miles de especies (incluyendo addons).
     * Cacheamos las opciones al abrir el navegador para no reconstruir y
     * ordenar la Pokédex completa en cada frame.
     */
    private List<CobblemonOption> cobblemonSpeciesOptions = List.of();
    private List<CobblemonOption> cobblemonTypeOptions = List.of();

    public RequestEditorScreen(
            RequestEditorMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);

        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;

        // Los títulos vanilla se ocultan en renderLabels().
        inventoryLabelX = INVENTORY_X;
        inventoryLabelY = 165;
    }

    @Override
    protected void init() {
        super.init();

        PacketDistributor.sendToServer(
                EditorActionPayload.simple("SYNC")
        );
    }

    /**
     * Evita que AbstractContainerScreen pinte el título e inventario
     * por defecto encima de nuestro layout personalizado.
     */
    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        // Se dibujan manualmente en renderBg().
    }

    public void onServerSync(DraftView view) {
        this.state = view;
        this.localRarity = view.rarity();

        /*
         * Un sync normalmente significa que una acción terminó o que el
         * servidor actualizó el borrador. Volvemos a la vista principal.
         */
        mode = Mode.MAIN;
        confirmAction = null;
        selectedKind = null;
        selectedContent = null;
        pendingCobblemonKind = null;

        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();

        // Evita mantener referencias a EditBox que ya no están en pantalla.
        titleBox = null;
        durationBox = null;
        searchBox = null;
        amountBox = null;
        usesBox = null;
        browserAddButton = null;

        /*
         * Los slots reales siguen existiendo y sincronizados, pero sólo se
         * muestran en MAIN. Esto evita que el inventario "flote" detrás del
         * selector, los navegadores y la confirmación.
         */
        menu.setMainSlotsVisible(mode == Mode.MAIN);

        if (state == null) {
            return;
        }

        switch (mode) {
            case MAIN -> buildMainWidgets();
            case TYPE_SELECTOR -> buildTypeWidgets();
            case COBBLEMON_SELECTOR -> buildCobblemonWidgets();
            case ITEM_BROWSER,
                 ENTITY_BROWSER,
                 TAG_BROWSER,
                 COBBLEMON_SPECIES_BROWSER,
                 COBBLEMON_TYPE_BROWSER -> buildBrowserWidgets();
            case CONFIRMATION -> buildConfirmationWidgets();
        }
    }

    // ---------------------------------------------------------------------
    // MAIN
    // ---------------------------------------------------------------------

    private void buildMainWidgets() {
        int x = leftPos;
        int y = topPos;

        titleBox = new EditBox(
                font,
                x + 16,
                y + 36,
                210,
                18,
                Component.translatable(
                        "bountifulrequests.gui.title_field"
                )
        );

        titleBox.setMaxLength(64);
        titleBox.setValue(state.title());
        titleBox.setTextColor(0xFFFFFF);

        titleBox.setResponder(value ->
                PacketDistributor.sendToServer(
                        new EditorActionPayload(
                                "SET_TITLE",
                                value,
                                "",
                                0,
                                0,
                                false
                        )
                )
        );

        addRenderableWidget(titleBox);

        durationBox = new EditBox(
                font,
                x + 16,
                y + 144,
                58,
                18,
                Component.translatable(
                        "bountifulrequests.gui.duration"
                )
        );

        durationBox.setValue(
                Long.toString(
                        Math.max(
                                1,
                                state.durationSeconds() / 60
                        )
                )
        );

        addRenderableWidget(durationBox);

        addRenderableWidget(
                Button.builder(
                                rarityText(),
                                button -> {
                                    localRarity =
                                            (localRarity + 1)
                                                    % BountyRarity.values().length;

                                    button.setMessage(rarityText());

                                    PacketDistributor.sendToServer(
                                            new EditorActionPayload(
                                                    "SET_RARITY",
                                                    "",
                                                    "",
                                                    localRarity,
                                                    0,
                                                    false
                                            )
                                    );
                                }
                        )
                        .bounds(
                                x + 82,
                                y + 144,
                                144,
                                18
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.add_objective"
                                ),
                                button -> {
                                    syncDuration();
                                    mode = Mode.TYPE_SELECTOR;
                                    rebuildWidgets();
                                }
                        )
                        .bounds(
                                x + 16,
                                y + 112,
                                210,
                                20
                        )
                        .build()
        );

        /*
         * Botones para quitar los objetivos visibles.
         */
        int objectiveY = y + 72;

        for (int i = 0;
             i < Math.min(state.objectives().size(), 3);
             i++) {

            final int index = i;

            addRenderableWidget(
                    Button.builder(
                                    Component.literal("×"),
                                    button ->
                                            PacketDistributor.sendToServer(
                                                    new EditorActionPayload(
                                                            "REMOVE_OBJECTIVE",
                                                            "",
                                                            "",
                                                            index,
                                                            0,
                                                            false
                                                    )
                                            )
                            )
                            .bounds(
                                    x + 206,
                                    objectiveY + i * 12 - 2,
                                    20,
                                    11
                            )
                            .build()
            );
        }

        /*
         * La publicación ya no se ejecuta al tocar el botón.
         * Primero se abre una pantalla de confirmación.
         */
        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.create_paper"
                                ),
                                button -> {
                                    syncDuration();
                                    openConfirmation(
                                            ConfirmAction.CREATE_PAPER
                                    );
                                }
                        )
                        .bounds(
                                x + 16,
                                y + 256,
                                100,
                                18
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.publish_now"
                                ),
                                button -> {
                                    syncDuration();
                                    openConfirmation(
                                            ConfirmAction.PUBLISH_BOARD
                                    );
                                }
                        )
                        .bounds(
                                x + 125,
                                y + 256,
                                100,
                                18
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.cancel_draft"
                                ),
                                button ->
                                        openConfirmation(
                                                ConfirmAction.CANCEL_DRAFT
                                        )
                        )
                        .bounds(
                                x + 234,
                                y + 256,
                                100,
                                18
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.collect_deliveries",
                                        state.deliveries()
                                ),
                                button ->
                                        PacketDistributor.sendToServer(
                                                EditorActionPayload.simple(
                                                        "COLLECT_DELIVERIES"
                                                )
                                        )
                        )
                        .bounds(
                                x + 240,
                                y + 116,
                                94,
                                18
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.collect_returns",
                                        state.returns()
                                ),
                                button ->
                                        PacketDistributor.sendToServer(
                                                EditorActionPayload.simple(
                                                        "COLLECT_RETURNS"
                                                )
                                        )
                        )
                        .bounds(
                                x + 240,
                                y + 138,
                                94,
                                18
                        )
                        .build()
        );

        if (state.admin()) {
            usesBox = new EditBox(
                    font,
                    x + 240,
                    y + 176,
                    94,
                    18,
                    Component.translatable(
                            "bountifulrequests.gui.rotation_uses"
                    )
            );

            usesBox.setValue(
                    Integer.toString(
                            Math.max(1, state.rotationUses())
                    )
            );

            addRenderableWidget(usesBox);

            addRenderableWidget(
                    Button.builder(
                                    Component.translatable(
                                            "bountifulrequests.gui.add_rotation"
                                    ),
                                    button -> {
                                        syncDuration();

                                        confirmedRotationUses =
                                                Math.max(
                                                        1,
                                                        parseInt(
                                                                usesBox.getValue(),
                                                                1
                                                        )
                                                );

                                        openConfirmation(
                                                ConfirmAction.PUBLISH_ROTATION
                                        );
                                    }
                            )
                            .bounds(
                                    x + 240,
                                    y + 199,
                                    94,
                                    18
                            )
                            .build()
            );
        }
    }

    private void syncDuration() {
        if (durationBox == null) {
            return;
        }

        long minutes =
                Math.max(
                        1,
                        parseLong(
                                durationBox.getValue(),
                                30
                        )
                );

        PacketDistributor.sendToServer(
                new EditorActionPayload(
                        "SET_DURATION",
                        "",
                        "",
                        0,
                        minutes * 60L,
                        false
                )
        );
    }

    private Component rarityText() {
        BountyRarity rarity =
                BountyRarity.values()[
                        Math.max(
                                0,
                                Math.min(
                                        BountyRarity.values().length - 1,
                                        localRarity
                                )
                        )
                        ];

        return Component.translatable(
                        "bountifulrequests.gui.rarity",
                        Component.literal(
                                niceName(rarity.name())
                        )
                )
                .withStyle(rarity.getColor());
    }

    // ---------------------------------------------------------------------
    // CONFIRMATION
    // ---------------------------------------------------------------------

    private void openConfirmation(ConfirmAction action) {
        confirmAction = action;
        mode = Mode.CONFIRMATION;
        rebuildWidgets();
    }

    private void buildConfirmationWidgets() {
        int x = leftPos;
        int y = topPos;

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.confirm.confirm"
                                ),
                                button -> executeConfirmedAction()
                        )
                        .bounds(
                                x + 70,
                                y + 174,
                                100,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.confirm.keep_editing"
                                ),
                                button -> {
                                    confirmAction = null;
                                    mode = Mode.MAIN;
                                    rebuildWidgets();
                                }
                        )
                        .bounds(
                                x + 180,
                                y + 174,
                                100,
                                20
                        )
                        .build()
        );
    }

    private void executeConfirmedAction() {
        if (confirmAction == null) {
            mode = Mode.MAIN;
            rebuildWidgets();
            return;
        }

        switch (confirmAction) {
            case CREATE_PAPER ->
                    PacketDistributor.sendToServer(
                            EditorActionPayload.simple(
                                    "CREATE_PAPER"
                            )
                    );

            case PUBLISH_BOARD ->
                    PacketDistributor.sendToServer(
                            EditorActionPayload.simple(
                                    "PUBLISH_BOARD"
                            )
                    );

            case PUBLISH_ROTATION ->
                    PacketDistributor.sendToServer(
                            new EditorActionPayload(
                                    "PUBLISH_ROTATION",
                                    "",
                                    "",
                                    confirmedRotationUses,
                                    0,
                                    false
                            )
                    );

            case CANCEL_DRAFT ->
                    PacketDistributor.sendToServer(
                            EditorActionPayload.simple(
                                    "CANCEL_DRAFT"
                            )
                    );
        }

        /*
         * Para las publicaciones el servidor enviará un nuevo DraftSync.
         * Para cancelar el borrador el contenedor se cierra desde servidor.
         */
        if (confirmAction != ConfirmAction.CANCEL_DRAFT) {
            mode = Mode.MAIN;
        }

        confirmAction = null;
    }

    private Component confirmationMessage() {
        if (confirmAction == null) {
            return Component.empty();
        }

        return switch (confirmAction) {
            case CREATE_PAPER ->
                    Component.translatable(
                            "bountifulrequests.gui.confirm.create_paper"
                    );

            case PUBLISH_BOARD ->
                    Component.translatable(
                            "bountifulrequests.gui.confirm.publish_now"
                    );

            case PUBLISH_ROTATION ->
                    Component.translatable(
                            "bountifulrequests.gui.confirm.rotation",
                            confirmedRotationUses
                    );

            case CANCEL_DRAFT ->
                    Component.translatable(
                            "bountifulrequests.gui.confirm.cancel_draft"
                    );
        };
    }

    // ---------------------------------------------------------------------
    // TYPE SELECTOR
    // ---------------------------------------------------------------------

    private void buildTypeWidgets() {
        int x = leftPos;
        int y = topPos;

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.objective.item"
                                ),
                                button ->
                                        openBrowser(
                                                Mode.ITEM_BROWSER
                                        )
                        )
                        .bounds(
                                x + 35,
                                y + 70,
                                125,
                                25
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.objective.entity"
                                ),
                                button ->
                                        openBrowser(
                                                Mode.ENTITY_BROWSER
                                        )
                        )
                        .bounds(
                                x + 190,
                                y + 70,
                                125,
                                25
                        )
                        .build()
        );

        /*
         * Jugadores normales sólo ven Objeto y Eliminar entidad.
         */
        if (state.admin()) {
            addRenderableWidget(
                    Button.builder(
                                    Component.translatable(
                                            "bountifulrequests.gui.objective.tag"
                                    ),
                                    button ->
                                            openBrowser(
                                                    Mode.TAG_BROWSER
                                            )
                            )
                            .bounds(
                                    x + 35,
                                    y + 105,
                                    125,
                                    25
                            )
                            .build()
            );

            if (ModList.get()
                    .isLoaded("cobblemon")) {

                addRenderableWidget(
                        Button.builder(
                                        Component.translatable(
                                                "bountifulrequests.gui.objective.cobblemon"
                                        ),
                                        button -> {
                                            mode =
                                                    Mode.COBBLEMON_SELECTOR;

                                            rebuildWidgets();
                                        }
                                )
                                .bounds(
                                        x + 190,
                                        y + 105,
                                        125,
                                        25
                                )
                                .build()
                );
            }
        }

        addBackButton();
    }

    private void buildCobblemonWidgets() {
        int x = leftPos;
        int y = topPos;

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.cobblemon.capture"
                                ),
                                button ->
                                        openCobblemonBrowser(
                                                ObjectiveSpec.Kind.COBBLEMON_CAPTURE_SPECIES,
                                                Mode.COBBLEMON_SPECIES_BROWSER
                                        )
                        )
                        .bounds(
                                x + 35,
                                y + 70,
                                125,
                                25
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.cobblemon.defeat"
                                ),
                                button ->
                                        openCobblemonBrowser(
                                                ObjectiveSpec.Kind.COBBLEMON_DEFEAT_SPECIES,
                                                Mode.COBBLEMON_SPECIES_BROWSER
                                        )
                        )
                        .bounds(
                                x + 190,
                                y + 70,
                                125,
                                25
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.cobblemon.capture_type"
                                ),
                                button ->
                                        openCobblemonBrowser(
                                                ObjectiveSpec.Kind.COBBLEMON_CAPTURE_TYPE,
                                                Mode.COBBLEMON_TYPE_BROWSER
                                        )
                        )
                        .bounds(
                                x + 35,
                                y + 105,
                                125,
                                25
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.cobblemon.defeat_type"
                                ),
                                button ->
                                        openCobblemonBrowser(
                                                ObjectiveSpec.Kind.COBBLEMON_DEFEAT_TYPE,
                                                Mode.COBBLEMON_TYPE_BROWSER
                                        )
                        )
                        .bounds(
                                x + 190,
                                y + 105,
                                125,
                                25
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.back"
                                ),
                                button -> {
                                    mode = Mode.TYPE_SELECTOR;
                                    pendingCobblemonKind = null;
                                    rebuildWidgets();
                                }
                        )
                        .bounds(
                                leftPos + 18,
                                topPos + 246,
                                80,
                                20
                        )
                        .build()
        );
    }

    // ---------------------------------------------------------------------
    // BROWSER
    // ---------------------------------------------------------------------

    private void openBrowser(Mode newMode) {
        mode = newMode;
        browserOffset = 0;
        selectedKind = null;
        selectedContent = null;
        pendingCobblemonKind = null;
        rebuildWidgets();
    }

    private void openCobblemonBrowser(
            ObjectiveSpec.Kind kind,
            Mode browserMode
    ) {
        pendingCobblemonKind = kind;
        mode = browserMode;
        browserOffset = 0;
        selectedKind = null;
        selectedContent = null;

        if (ModList.get()
                .isLoaded("cobblemon")) {

            if (browserMode
                    == Mode.COBBLEMON_SPECIES_BROWSER) {

                List<CobblemonOption> options =
                        new java.util.ArrayList<>();

                options.add(
                        new CobblemonOption(
                                "*",
                                Component.translatable(
                                        "bountifulrequests.gui.cobblemon.any"
                                ).getString()
                        )
                );

                options.addAll(
                        CobblemonClientBridge.speciesOptions()
                );

                cobblemonSpeciesOptions =
                        List.copyOf(options);

            } else if (browserMode
                    == Mode.COBBLEMON_TYPE_BROWSER) {

                cobblemonTypeOptions =
                        CobblemonClientBridge.typeOptions();
            }
        }

        rebuildWidgets();
    }

    private void buildBrowserWidgets() {
        int x = leftPos;
        int y = topPos;

        searchBox = new EditBox(
                font,
                x + 30,
                y + 40,
                200,
                18,
                Component.translatable(
                        "bountifulrequests.gui.search"
                )
        );

        searchBox.setHint(
                Component.translatable(
                        "bountifulrequests.gui.search"
                )
        );

        searchBox.setResponder(
                ignored -> {
                    browserOffset = 0;

                    // Una búsqueda nueva invalida la selección anterior para
                    // evitar agregar accidentalmente algo que ya no se ve.
                    selectedKind = null;
                    selectedContent = null;

                    if (browserAddButton != null) {
                        browserAddButton.active = false;
                    }
                }
        );

        addRenderableWidget(searchBox);

        amountBox = new EditBox(
                font,
                x + 245,
                y + 40,
                74,
                18,
                Component.translatable(
                        "bountifulrequests.gui.amount"
                )
        );

        amountBox.setValue("1");

        addRenderableWidget(amountBox);

        browserAddButton =
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.browser_add"
                                ),
                                button -> addSelectedObjective()
                        )
                        .bounds(
                                x + 120,
                                y + 246,
                                112,
                                20
                        )
                        .build();

        browserAddButton.active = false;
        addRenderableWidget(browserAddButton);

        addBackButton();
    }

    private void addBackButton() {
        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.back"
                                ),
                                button -> {
                                    if (mode == Mode.COBBLEMON_SPECIES_BROWSER
                                            || mode == Mode.COBBLEMON_TYPE_BROWSER) {

                                        mode = Mode.COBBLEMON_SELECTOR;
                                    } else {
                                        mode = Mode.MAIN;
                                    }

                                    selectedKind = null;
                                    selectedContent = null;
                                    rebuildWidgets();
                                }
                        )
                        .bounds(
                                leftPos + 18,
                                topPos + 246,
                                80,
                                20
                        )
                        .build()
        );
    }

    // ---------------------------------------------------------------------
    // RENDER
    // ---------------------------------------------------------------------

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {
        int x = leftPos;
        int y = topPos;

        // Fondo principal.
        graphics.fill(
                x,
                y,
                x + imageWidth,
                y + imageHeight,
                0xF0181818
        );

        // Marco exterior.
        drawBorder(
                graphics,
                x,
                y,
                imageWidth,
                imageHeight,
                0xFF4B4B4B
        );

        // Barra superior.
        graphics.fill(
                x + 6,
                y + 6,
                x + imageWidth - 6,
                y + 29,
                0xFF303030
        );

        graphics.drawCenteredString(
                font,
                this.title,
                x + imageWidth / 2,
                y + 13,
                0xFFFFFF
        );

        if (state == null) {
            return;
        }

        switch (mode) {
            case MAIN -> renderMain(
                    graphics,
                    x,
                    y
            );

            case TYPE_SELECTOR ->
                    graphics.drawCenteredString(
                            font,
                            Component.translatable(
                                    "bountifulrequests.gui.select_type"
                            ),
                            x + imageWidth / 2,
                            y + 47,
                            0xFFFFFF
                    );

            case COBBLEMON_SELECTOR ->
                    graphics.drawCenteredString(
                            font,
                            Component.translatable(
                                    "bountifulrequests.gui.cobblemon.select"
                            ),
                            x + imageWidth / 2,
                            y + 47,
                            0xFFFFFF
                    );

            case ITEM_BROWSER -> {
                renderBrowserHeader(graphics, x, y);
                renderItems(
                        graphics,
                        x,
                        y,
                        mouseX,
                        mouseY
                );
            }

            case ENTITY_BROWSER -> {
                renderBrowserHeader(graphics, x, y);
                renderEntities(
                        graphics,
                        x,
                        y
                );
            }

            case TAG_BROWSER -> {
                renderBrowserHeader(graphics, x, y);
                renderTags(
                        graphics,
                        x,
                        y
                );
            }

            case COBBLEMON_SPECIES_BROWSER -> {
                renderBrowserHeader(graphics, x, y);
                renderCobblemonSpecies(
                        graphics,
                        x,
                        y
                );
            }

            case COBBLEMON_TYPE_BROWSER -> {
                renderBrowserHeader(graphics, x, y);
                renderCobblemonTypes(
                        graphics,
                        x,
                        y
                );
            }

            case CONFIRMATION ->
                    renderConfirmation(
                            graphics,
                            x,
                            y
                    );
        }
    }

    private void renderMain(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        // Panel izquierdo: misión.
        graphics.fill(
                x + 10,
                y + 32,
                x + 232,
                y + 166,
                0x88242424
        );

        drawBorder(
                graphics,
                x + 10,
                y + 32,
                222,
                134,
                0xFF3D3D3D
        );

        graphics.drawString(
                font,
                Component.translatable(
                        "bountifulrequests.gui.objectives"
                ),
                x + 16,
                y + 61,
                0xFFFFFF
        );

        for (int i = 0;
             i < Math.min(
                     state.objectives().size(),
                     3
             );
             i++) {

            Component text =
                    objectiveText(
                            state.objectives().get(i)
                    );

            String clipped =
                    font.plainSubstrByWidth(
                            text.getString(),
                            180
                    );

            graphics.drawString(
                    font,
                    clipped,
                    x + 18,
                    y + 73 + i * 12,
                    0xDDDDDD
            );
        }

        if (state.objectives().size() > 3) {
            graphics.drawString(
                    font,
                    Component.literal(
                            "+" + (state.objectives().size() - 3)
                    ),
                    x + 185,
                    y + 97,
                    0xAAAAAA
            );
        }

        graphics.drawString(
                font,
                Component.translatable(
                        "bountifulrequests.gui.duration_minutes"
                ),
                x + 16,
                y + 134,
                0xAAAAAA
        );

        // Panel derecho: recompensas.
        graphics.fill(
                x + 236,
                y + 32,
                x + 340,
                y + 225,
                0x88242424
        );

        drawBorder(
                graphics,
                x + 236,
                y + 32,
                104,
                193,
                0xFF3D3D3D
        );

        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "bountifulrequests.gui.rewards"
                ),
                x + 287,
                y + 39,
                0xFFFFFF
        );

        // 3x3 reward slots perfectamente centrados bajo "Recompensas".
        drawSlotGrid(
                graphics,
                x + REWARD_X,
                y + REWARD_Y,
                3,
                3
        );

        if (state.admin()) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "bountifulrequests.gui.rotation_uses"
                    ),
                    x + 240,
                    y + 165,
                    0xAAAAAA
            );
        }

        // Inventario.
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "container.inventory"
                ),
                x + INVENTORY_X + 81,
                y + 166,
                0xFFFFFF
        );

        drawSlotGrid(
                graphics,
                x + INVENTORY_X,
                y + INVENTORY_Y,
                9,
                3
        );

        drawSlotGrid(
                graphics,
                x + INVENTORY_X,
                y + HOTBAR_Y,
                9,
                1
        );
    }

    private void renderBrowserHeader(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        Component browserTitle =
                switch (mode) {
                    case ITEM_BROWSER ->
                            Component.translatable(
                                    "bountifulrequests.gui.objective.item"
                            );

                    case ENTITY_BROWSER ->
                            Component.translatable(
                                    "bountifulrequests.gui.objective.entity"
                            );

                    case TAG_BROWSER ->
                            Component.translatable(
                                    "bountifulrequests.gui.objective.tag"
                            );

                    case COBBLEMON_SPECIES_BROWSER ->
                            Component.translatable(
                                    pendingCobblemonKind
                                            == ObjectiveSpec.Kind.COBBLEMON_CAPTURE_SPECIES
                                            ? "bountifulrequests.gui.cobblemon.capture"
                                            : "bountifulrequests.gui.cobblemon.defeat"
                            );

                    case COBBLEMON_TYPE_BROWSER ->
                            Component.translatable(
                                    pendingCobblemonKind
                                            == ObjectiveSpec.Kind.COBBLEMON_CAPTURE_TYPE
                                            ? "bountifulrequests.gui.cobblemon.capture_type"
                                            : "bountifulrequests.gui.cobblemon.defeat_type"
                            );

                    default -> Component.empty();
                };

        graphics.drawCenteredString(
                font,
                browserTitle,
                x + imageWidth / 2,
                y + 64,
                0xDDDDDD
        );
    }

    private void renderConfirmation(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        final int boxWidth = 270;
        final int boxHeight = 138;
        final int boxX = x + (imageWidth - boxWidth) / 2;
        final int boxY = y + 58;

        graphics.fill(
                boxX,
                boxY,
                boxX + boxWidth,
                boxY + boxHeight,
                0xFF202020
        );

        drawBorder(
                graphics,
                boxX,
                boxY,
                boxWidth,
                boxHeight,
                0xFF777777
        );

        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "bountifulrequests.gui.confirm.title"
                ),
                boxX + boxWidth / 2,
                boxY + 16,
                0xFFFFFF
        );

        /*
         * El texto se parte al ancho real de la caja para que nunca vuelva
         * a salirse del panel, incluso con idiomas más largos.
         */
        List<FormattedCharSequence> messageLines =
                font.split(
                        confirmationMessage(),
                        boxWidth - 28
                );

        int textY = boxY + 42;

        for (FormattedCharSequence line : messageLines) {
            graphics.drawCenteredString(
                    font,
                    line,
                    boxX + boxWidth / 2,
                    textY,
                    0xDDDDDD
            );

            textY += 11;
        }

        textY += 7;

        List<FormattedCharSequence> noteLines =
                font.split(
                        Component.translatable(
                                "bountifulrequests.gui.confirm.note"
                        ),
                        boxWidth - 28
                );

        for (FormattedCharSequence line : noteLines) {
            graphics.drawCenteredString(
                    font,
                    line,
                    boxX + boxWidth / 2,
                    textY,
                    0xAAAAAA
            );

            textY += 10;
        }
    }

    private static void drawSlotGrid(
            GuiGraphics graphics,
            int startX,
            int startY,
            int columns,
            int rows
    ) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0;
                 column < columns;
                 column++) {

                int sx =
                        startX + column * 18;

                int sy =
                        startY + row * 18;

                // Borde claro del slot.
                graphics.fill(
                        sx - 1,
                        sy - 1,
                        sx + 17,
                        sy + 17,
                        0xFF5C5C5C
                );

                // Interior oscuro.
                graphics.fill(
                        sx,
                        sy,
                        sx + 16,
                        sy + 16,
                        0xFF202020
                );
            }
        }
    }

    private static void drawBorder(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        graphics.fill(
                x,
                y,
                x + width,
                y + 1,
                color
        );

        graphics.fill(
                x,
                y + height - 1,
                x + width,
                y + height,
                color
        );

        graphics.fill(
                x,
                y,
                x + 1,
                y + height,
                color
        );

        graphics.fill(
                x + width - 1,
                y,
                x + width,
                y + height,
                color
        );
    }

    private void renderItems(
            GuiGraphics graphics,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        List<Item> items =
                filteredItems();

        /*
         * 9 columnas de 18 px separadas por 2 px.
         * El conjunto completo queda centrado bajo el título "Objeto".
         */
        final int gridWidth = 178;
        final int gridX = x + (imageWidth - gridWidth) / 2;
        final int gridY = y + 82;

        int start =
                clampedItemOffset(
                        items.size()
                );

        for (int i = 0;
             i < 45
                     && start + i < items.size();
             i++) {

            int column = i % 9;
            int row = i / 9;

            int sx =
                    gridX + column * 20;

            int sy =
                    gridY + row * 20;

            Item item =
                    items.get(start + i);

            ItemStack stack =
                    new ItemStack(item);

            ResourceLocation itemId =
                    BuiltInRegistries.ITEM
                            .getKey(item);

            boolean selected =
                    selectedKind
                            == ObjectiveSpec.Kind.ITEM
                            && itemId.toString()
                            .equals(selectedContent);

            graphics.fill(
                    sx - 2,
                    sy - 2,
                    sx + 19,
                    sy + 19,
                    selected
                            ? 0xFFFFFFFF
                            : 0xFF444444
            );

            graphics.fill(
                    sx,
                    sy,
                    sx + 17,
                    sy + 17,
                    selected
                            ? 0xFF555555
                            : 0xFF242424
            );

            graphics.renderItem(
                    stack,
                    sx,
                    sy
            );

            if (mouseX >= sx
                    && mouseX < sx + 18
                    && mouseY >= sy
                    && mouseY < sy + 18) {

                graphics.renderTooltip(
                        font,
                        stack,
                        mouseX,
                        mouseY
                );
            }
        }
    }

    private void renderEntities(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        List<ResourceLocation> entities =
                filteredEntities();

        renderSelectableRows(
                graphics,
                x,
                y,
                entities.stream()
                        .map(id -> {
                            EntityType<?> type =
                                    BuiltInRegistries.ENTITY_TYPE
                                            .get(id);

                            return type.getDescription()
                                    .getString()
                                    + "  §8"
                                    + id;
                        })
                        .toList(),
                entities.stream()
                        .map(ResourceLocation::toString)
                        .toList(),
                ObjectiveSpec.Kind.ENTITY
        );
    }

    private void renderTags(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        List<ResourceLocation> tags =
                filteredTags();

        renderSelectableRows(
                graphics,
                x,
                y,
                tags.stream()
                        .map(id -> "#" + id)
                        .toList(),
                tags.stream()
                        .map(ResourceLocation::toString)
                        .toList(),
                ObjectiveSpec.Kind.ITEM_TAG
        );
    }

    private void renderCobblemonSpecies(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        List<CobblemonOption> options =
                filteredCobblemonSpecies();

        renderSelectableRows(
                graphics,
                x,
                y,
                options.stream()
                        .map(CobblemonOption::label)
                        .toList(),
                options.stream()
                        .map(CobblemonOption::id)
                        .toList(),
                pendingCobblemonKind
        );
    }

    private void renderCobblemonTypes(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        List<CobblemonOption> options =
                filteredCobblemonTypes();

        renderSelectableRows(
                graphics,
                x,
                y,
                options.stream()
                        .map(CobblemonOption::label)
                        .toList(),
                options.stream()
                        .map(CobblemonOption::id)
                        .toList(),
                pendingCobblemonKind
        );
    }

    private void renderSelectableRows(
            GuiGraphics graphics,
            int x,
            int y,
            List<String> rows,
            List<String> values,
            ObjectiveSpec.Kind kind
    ) {
        int start =
                clampedTextOffset(
                        rows.size()
                );

        for (int i = 0;
             i < 10
                     && start + i < rows.size();
             i++) {

            int sy =
                    y + 82 + i * 14;

            String value =
                    values.get(start + i);

            boolean selected =
                    selectedKind == kind
                            && value.equals(selectedContent);

            graphics.fill(
                    x + 32,
                    sy,
                    x + 318,
                    sy + 13,
                    selected
                            ? 0xFF555555
                            : 0xFF282828
            );

            if (selected) {
                drawBorder(
                        graphics,
                        x + 31,
                        sy - 1,
                        288,
                        15,
                        0xFFFFFFFF
                );
            }

            graphics.drawString(
                    font,
                    rows.get(start + i),
                    x + 37,
                    sy + 2,
                    0xFFFFFF
            );
        }
    }

    // ---------------------------------------------------------------------
    // CLICK BROWSER
    // ---------------------------------------------------------------------

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button == 0 && state != null) {
            if (mode == Mode.ITEM_BROWSER
                    && clickItem(mouseX, mouseY)) {
                return true;
            }

            if (mode == Mode.ENTITY_BROWSER
                    && clickEntity(mouseX, mouseY)) {
                return true;
            }

            if (mode == Mode.TAG_BROWSER
                    && clickTag(mouseX, mouseY)) {
                return true;
            }

            if (mode == Mode.COBBLEMON_SPECIES_BROWSER
                    && clickCobblemonSpecies(mouseX, mouseY)) {
                return true;
            }

            if (mode == Mode.COBBLEMON_TYPE_BROWSER
                    && clickCobblemonType(mouseX, mouseY)) {
                return true;
            }
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button
        );
    }

    private boolean clickItem(
            double mouseX,
            double mouseY
    ) {
        final int gridWidth = 178;
        final int gridX =
                leftPos
                        + (imageWidth - gridWidth) / 2;

        final int gridY =
                topPos + 82;

        int localX =
                (int) mouseX - gridX;

        int localY =
                (int) mouseY - gridY;

        if (localX < 0 || localY < 0) {
            return false;
        }

        int column = localX / 20;
        int row = localY / 20;

        if (column >= 9 || row >= 5) {
            return false;
        }

        List<Item> items =
                filteredItems();

        int index =
                clampedItemOffset(
                        items.size()
                )
                        + row * 9
                        + column;

        if (index < 0 || index >= items.size()) {
            return false;
        }

        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        items.get(index)
                );

        selectedKind =
                ObjectiveSpec.Kind.ITEM;

        selectedContent =
                id.toString();

        if (browserAddButton != null) {
            browserAddButton.active = true;
        }

        return true;
    }

    private boolean clickEntity(
            double mouseX,
            double mouseY
    ) {
        int row =
                textRowAt(
                        mouseX,
                        mouseY
                );

        if (row < 0) {
            return false;
        }

        List<ResourceLocation> entities =
                filteredEntities();

        int index =
                clampedTextOffset(
                        entities.size()
                ) + row;

        if (index >= entities.size()) {
            return false;
        }

        selectedKind =
                ObjectiveSpec.Kind.ENTITY;

        selectedContent =
                entities.get(index).toString();

        if (browserAddButton != null) {
            browserAddButton.active = true;
        }

        return true;
    }

    private boolean clickTag(
            double mouseX,
            double mouseY
    ) {
        int row =
                textRowAt(
                        mouseX,
                        mouseY
                );

        if (row < 0) {
            return false;
        }

        List<ResourceLocation> tags =
                filteredTags();

        int index =
                clampedTextOffset(
                        tags.size()
                ) + row;

        if (index >= tags.size()) {
            return false;
        }

        selectedKind =
                ObjectiveSpec.Kind.ITEM_TAG;

        selectedContent =
                tags.get(index).toString();

        if (browserAddButton != null) {
            browserAddButton.active = true;
        }

        return true;
    }

    private boolean clickCobblemonSpecies(
            double mouseX,
            double mouseY
    ) {
        int row =
                textRowAt(
                        mouseX,
                        mouseY
                );

        if (row < 0
                || pendingCobblemonKind == null) {
            return false;
        }

        List<CobblemonOption> options =
                filteredCobblemonSpecies();

        int index =
                clampedTextOffset(
                        options.size()
                ) + row;

        if (index >= options.size()) {
            return false;
        }

        selectedKind =
                pendingCobblemonKind;

        selectedContent =
                options.get(index).id();

        if (browserAddButton != null) {
            browserAddButton.active = true;
        }

        return true;
    }

    private boolean clickCobblemonType(
            double mouseX,
            double mouseY
    ) {
        int row =
                textRowAt(
                        mouseX,
                        mouseY
                );

        if (row < 0
                || pendingCobblemonKind == null) {
            return false;
        }

        List<CobblemonOption> options =
                filteredCobblemonTypes();

        int index =
                clampedTextOffset(
                        options.size()
                ) + row;

        if (index >= options.size()) {
            return false;
        }

        selectedKind =
                pendingCobblemonKind;

        selectedContent =
                options.get(index).id();

        if (browserAddButton != null) {
            browserAddButton.active = true;
        }

        return true;
    }

    private int textRowAt(
            double mouseX,
            double mouseY
    ) {
        int x =
                (int) mouseX - leftPos;

        int y =
                (int) mouseY - topPos;

        if (x < 32
                || x > 318
                || y < 82
                || y >= 222) {

            return -1;
        }

        return (y - 82) / 14;
    }

    private void addSelectedObjective() {
        if (selectedKind == null
                || selectedContent == null) {

            return;
        }

        int amount =
                Math.max(
                        1,
                        parseInt(
                                amountBox == null
                                        ? "1"
                                        : amountBox.getValue(),
                                1
                        )
                );

        PacketDistributor.sendToServer(
                new EditorActionPayload(
                        "ADD_OBJECTIVE",
                        selectedKind.name(),
                        selectedContent,
                        amount,
                        0,
                        false
                )
        );

        mode = Mode.MAIN;
        selectedKind = null;
        selectedContent = null;
    }

    // ---------------------------------------------------------------------
    // KEYBOARD
    // ---------------------------------------------------------------------

    /**
     * AbstractContainerScreen normalmente usa la tecla de inventario (E)
     * para cerrar el contenedor.
     *
     * Cuando un EditBox tiene foco, la misma tecla debe poder escribirse en
     * el título o buscador sin cerrar la pantalla.
     */
    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (getFocused() instanceof EditBox editBox
                && editBox.isFocused()
                && minecraft != null
                && minecraft.options.keyInventory.matches(
                        keyCode,
                        scanCode
                )) {

            return true;
        }

        return super.keyPressed(
                keyCode,
                scanCode,
                modifiers
        );
    }

    // ---------------------------------------------------------------------
    // SCROLL
    // ---------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (mode == Mode.ITEM_BROWSER
                || mode == Mode.ENTITY_BROWSER
                || mode == Mode.TAG_BROWSER
                || mode == Mode.COBBLEMON_SPECIES_BROWSER
                || mode == Mode.COBBLEMON_TYPE_BROWSER) {

            if (scrollY < 0) {
                browserOffset +=
                        mode == Mode.ITEM_BROWSER
                                ? 9
                                : 1;

            } else if (scrollY > 0) {
                browserOffset -=
                        mode == Mode.ITEM_BROWSER
                                ? 9
                                : 1;
            }

            browserOffset =
                    Math.max(
                            0,
                            browserOffset
                    );

            return true;
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY
        );
    }

    // ---------------------------------------------------------------------
    // FILTERS
    // ---------------------------------------------------------------------

    private int clampedTextOffset(
            int size
    ) {
        return Math.min(
                browserOffset,
                Math.max(
                        0,
                        size - 10
                )
        );
    }

    private int clampedItemOffset(
            int size
    ) {
        int maxOffset =
                Math.max(
                        0,
                        size - 45
                );

        /*
         * El navegador de items avanza por filas de 9.
         */
        maxOffset =
                (maxOffset / 9) * 9;

        return Math.min(
                browserOffset,
                maxOffset
        );
    }

    private String query() {
        if (searchBox == null) {
            return "";
        }

        return searchBox
                .getValue()
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private List<Item> filteredItems() {
        String query = query();

        return BuiltInRegistries.ITEM
                .stream()
                .filter(item -> item != Items.AIR)
                .filter(item -> {
                    ResourceLocation id =
                            BuiltInRegistries.ITEM
                                    .getKey(item);

                    String name =
                            item.getDescription()
                                    .getString()
                                    .toLowerCase(Locale.ROOT);

                    String key =
                            id.toString()
                                    .toLowerCase(Locale.ROOT);

                    return query.isBlank()
                            || name.contains(query)
                            || key.contains(query)
                            || id.getNamespace()
                            .contains(query);
                })
                .sorted(
                        Comparator.comparing(
                                item ->
                                        BuiltInRegistries.ITEM
                                                .getKey(item)
                                                .toString()
                        )
                )
                .toList();
    }

    private List<ResourceLocation> filteredEntities() {
        String query = query();

        return BuiltInRegistries.ENTITY_TYPE
                .keySet()
                .stream()
                .filter(id -> {
                    EntityType<?> entity =
                            BuiltInRegistries.ENTITY_TYPE
                                    .get(id);

                    String name =
                            entity.getDescription()
                                    .getString()
                                    .toLowerCase(Locale.ROOT);

                    return query.isBlank()
                            || name.contains(query)
                            || id.toString()
                            .toLowerCase(Locale.ROOT)
                            .contains(query)
                            || id.getNamespace()
                            .contains(query);
                })
                .sorted()
                .toList();
    }

    private List<ResourceLocation> filteredTags() {
        String query = query();

        return BuiltInRegistries.ITEM
                .getTags()
                .map(pair ->
                        pair.getFirst()
                                .location()
                )
                .filter(id ->
                        query.isBlank()
                                || id.toString()
                                .toLowerCase(Locale.ROOT)
                                .contains(query)
                )
                .sorted()
                .toList();
    }

    private List<CobblemonOption> filteredCobblemonSpecies() {
        if (!ModList.get()
                .isLoaded("cobblemon")) {

            return List.of();
        }

        String query = query();

        return cobblemonSpeciesOptions.stream()
                .filter(option ->
                        query.isBlank()
                                || option.label()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                                .contains(query)
                                || option.id()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                                .contains(query)
                )
                .toList();
    }

    private List<CobblemonOption> filteredCobblemonTypes() {
        if (!ModList.get()
                .isLoaded("cobblemon")) {

            return List.of();
        }

        String query = query();

        return cobblemonTypeOptions
                .stream()
                .filter(option ->
                        query.isBlank()
                                || option.label()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                                .contains(query)
                                || option.id()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                                .contains(query)
                )
                .toList();
    }

    // ---------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------

    private Component objectiveText(
            ObjectiveSpec objective
    ) {
        try {
            return switch (objective.kind) {
                case ITEM -> {
                    ResourceLocation id =
                            ResourceLocation.parse(
                                    objective.content
                            );

                    Item item =
                            BuiltInRegistries.ITEM.get(id);

                    yield item.getDescription()
                            .copy()
                            .append(
                                    " x" + objective.amount
                            );
                }

                case ENTITY -> {
                    ResourceLocation id =
                            ResourceLocation.parse(
                                    objective.content
                            );

                    EntityType<?> type =
                            BuiltInRegistries.ENTITY_TYPE
                                    .get(id);

                    yield type.getDescription()
                            .copy()
                            .append(
                                    " x" + objective.amount
                            );
                }

                case ITEM_TAG ->
                        Component.literal(
                                "#" + objective.content
                                        + " x"
                                        + objective.amount
                        );

                case COBBLEMON_CAPTURE_SPECIES,
                     COBBLEMON_DEFEAT_SPECIES,
                     COBBLEMON_CAPTURE_TYPE,
                     COBBLEMON_DEFEAT_TYPE ->
                        cobblemonObjectiveText(
                                objective
                        );

                case BOUNTIFUL_ENTRY,
                     BOUNTIFUL_RESOLVED ->
                        Component.literal(
                                objective.content
                                        + (
                                        objective.kind
                                                == ObjectiveSpec.Kind.BOUNTIFUL_ENTRY
                                                ? ""
                                                : " x" + objective.amount
                                )
                        );
            };

        } catch (Exception ignored) {
            return Component.literal(
                    objective.content
                            + " x"
                            + objective.amount
            );
        }
    }

    private Component cobblemonObjectiveText(
            ObjectiveSpec objective
    ) {
        String target =
                objective.content;

        if (ModList.get()
                .isLoaded("cobblemon")) {

            if (objective.kind
                    == ObjectiveSpec.Kind.COBBLEMON_CAPTURE_SPECIES
                    || objective.kind
                    == ObjectiveSpec.Kind.COBBLEMON_DEFEAT_SPECIES) {

                target =
                        "*".equals(
                                objective.content
                        )
                                ? Component.translatable(
                                "bountifulrequests.gui.cobblemon.any"
                        ).getString()
                                : CobblemonClientBridge.speciesLabel(
                                objective.content
                        );

            } else {
                target =
                        CobblemonClientBridge.typeLabel(
                                objective.content
                        );
            }
        }

        String actionKey =
                switch (objective.kind) {
                    case COBBLEMON_CAPTURE_SPECIES ->
                            "bountifulrequests.gui.cobblemon.capture_short";
                    case COBBLEMON_DEFEAT_SPECIES ->
                            "bountifulrequests.gui.cobblemon.defeat_short";
                    case COBBLEMON_CAPTURE_TYPE ->
                            "bountifulrequests.gui.cobblemon.capture_type_short";
                    case COBBLEMON_DEFEAT_TYPE ->
                            "bountifulrequests.gui.cobblemon.defeat_type_short";
                    default ->
                            "bountifulrequests.gui.objective.cobblemon";
                };

        return Component.translatable(
                actionKey,
                target,
                objective.amount
        );
    }

    private static int parseInt(
            String value,
            int fallback
    ) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(
            String value,
            long fallback
    ) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String niceName(String input) {
        String lower =
                input.toLowerCase(Locale.ROOT);

        return Character.toUpperCase(
                lower.charAt(0)
        ) + lower.substring(1);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBackground(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        renderTooltip(
                graphics,
                mouseX,
                mouseY
        );
    }
}
