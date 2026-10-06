package org.crafterscr.bountifulrequests.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.crafterscr.bountifulrequests.data.ObjectiveSpec;
import org.crafterscr.bountifulrequests.menu.RequestEditorMenu;
import org.crafterscr.bountifulrequests.network.DraftView;
import org.crafterscr.bountifulrequests.network.EditorActionPayload;

import io.ejekta.bountiful.bounty.BountyRarity;
import io.ejekta.bountiful.bounty.types.IBountyObjective;
import io.ejekta.bountiful.content.BountifulContent;
import io.ejekta.bountiful.data.PoolEntry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * GUI principal.
 *
 * No utiliza una textura externa en esta V1.
 * Todo se dibuja programáticamente.
 */
public final class RequestEditorScreen
        extends AbstractContainerScreen<RequestEditorMenu> {

    private enum Mode {
        MAIN,
        TYPE_SELECTOR,
        ITEM_BROWSER,
        ENTITY_BROWSER,
        TAG_BROWSER,
        BOUNTIFUL_BROWSER
    }

    private DraftView state;

    private Mode mode = Mode.MAIN;

    private EditBox titleBox;
    private EditBox durationBox;

    private EditBox searchBox;
    private EditBox amountBox;
    private EditBox usesBox;

    private int browserOffset = 0;

    private int localRarity = 0;

    public RequestEditorScreen(
            RequestEditorMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);

        imageWidth = 338;
        imageHeight = 230;

        inventoryLabelY = 132;
        inventoryLabelX = 88;
    }

    @Override
    protected void init() {
        super.init();

        PacketDistributor.sendToServer(
                EditorActionPayload.simple(
                        "SYNC"
                )
        );
    }

    public void onServerSync(
            DraftView view
    ) {
        this.state = view;
        this.localRarity = view.rarity();

        /*
         * Si acaba de publicarse, volvemos a MAIN.
         */
        mode = Mode.MAIN;

        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();

        if (state == null) {
            return;
        }

        boolean pending =
                !"NONE".equals(
                        state.pendingMode()
                );

        if (pending) {
            buildPendingWidgets();
            return;
        }

        switch (mode) {
            case MAIN ->
                    buildMainWidgets();

            case TYPE_SELECTOR ->
                    buildTypeWidgets();

            case ITEM_BROWSER,
                 ENTITY_BROWSER,
                 TAG_BROWSER,
                 BOUNTIFUL_BROWSER ->
                    buildBrowserWidgets();
        }
    }

    // ------------------------------------------------------------
    // MAIN
    // ------------------------------------------------------------

    private void buildMainWidgets() {
        int x = leftPos;
        int y = topPos;

        titleBox =
                new EditBox(
                        font,
                        x + 15,
                        y + 25,
                        190,
                        18,
                        Component.translatable(
                                "bountifulrequests.gui.title_field"
                        )
                );

        titleBox.setMaxLength(64);
        titleBox.setValue(state.title());

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

        durationBox =
                new EditBox(
                        font,
                        x + 15,
                        y + 112,
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

                                    button.setMessage(
                                            rarityText()
                                    );

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
                                x + 80,
                                y + 112,
                                125,
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

                                    mode =
                                            Mode.TYPE_SELECTOR;

                                    rebuildWidgets();
                                }
                        )
                        .bounds(
                                x + 15,
                                y + 84,
                                190,
                                20
                        )
                        .build()
        );

        /*
         * Remove buttons de objetivos.
         */
        int objectiveY = y + 51;

        for (int i = 0;
             i < Math.min(
                     state.objectives().size(),
                     3
             );
             i++) {

            final int index = i;

            addRenderableWidget(
                    Button.builder(
                                    Component.literal("×"),
                                    button -> {
                                        PacketDistributor.sendToServer(
                                                new EditorActionPayload(
                                                        "REMOVE_OBJECTIVE",
                                                        "",
                                                        "",
                                                        index,
                                                        0,
                                                        false
                                                )
                                        );
                                    }
                            )
                            .bounds(
                                    x + 185,
                                    objectiveY + i * 10,
                                    20,
                                    10
                            )
                            .build()
            );
        }

        /*
         * Crear papel.
         */
        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.create_paper"
                                ),
                                button -> {
                                    syncDuration();

                                    PacketDistributor.sendToServer(
                                            EditorActionPayload.simple(
                                                    "CREATE_PAPER"
                                            )
                                    );
                                }
                        )
                        .bounds(
                                x + 15,
                                y + 208,
                                92,
                                18
                        )
                        .build()
        );

        /*
         * Publicar en todos los boards.
         */
        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.publish_now"
                                ),
                                button -> {
                                    syncDuration();

                                    PacketDistributor.sendToServer(
                                            EditorActionPayload.simple(
                                                    "PUBLISH_BOARD"
                                            )
                                    );
                                }
                        )
                        .bounds(
                                x + 112,
                                y + 208,
                                92,
                                18
                        )
                        .build()
        );

        if (state.admin()) {
            usesBox =
                    new EditBox(
                            font,
                            x + 212,
                            y + 183,
                            38,
                            18,
                            Component.translatable(
                                    "bountifulrequests.gui.rotation_uses"
                            )
                    );

            usesBox.setValue(
                    Integer.toString(
                            Math.max(
                                    1,
                                    state.rotationUses()
                            )
                    )
            );

            addRenderableWidget(
                    usesBox
            );

            addRenderableWidget(
                    Button.builder(
                                    Component.translatable(
                                            "bountifulrequests.gui.add_rotation"
                                    ),
                                    button -> {
                                        syncDuration();

                                        int uses =
                                                parseInt(
                                                        usesBox.getValue(),
                                                        1
                                                );

                                        PacketDistributor.sendToServer(
                                                new EditorActionPayload(
                                                        "PUBLISH_ROTATION",
                                                        "",
                                                        "",
                                                        Math.max(1, uses),
                                                        0,
                                                        false
                                                )
                                        );
                                    }
                            )
                            .bounds(
                                    x + 255,
                                    y + 183,
                                    68,
                                    18
                            )
                            .build()
            );
        }

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
                                x + 212,
                                y + 112,
                                111,
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
                                x + 212,
                                y + 132,
                                111,
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
                                        PacketDistributor.sendToServer(
                                                EditorActionPayload.simple(
                                                        "CANCEL_DRAFT"
                                                )
                                        )
                        )
                        .bounds(
                                x + 212,
                                y + 208,
                                111,
                                18
                        )
                        .build()
        );
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
                                niceName(
                                        rarity.name()
                                )
                        )
                )
                .withStyle(
                        rarity.getColor()
                );
    }

    // ------------------------------------------------------------
    // TYPE SELECTOR
    // ------------------------------------------------------------

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
                                y + 55,
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
                                x + 175,
                                y + 55,
                                125,
                                25
                        )
                        .build()
        );

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
                                y + 90,
                                125,
                                25
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.objective.bountiful"
                                ),
                                button ->
                                        openBrowser(
                                                Mode.BOUNTIFUL_BROWSER
                                        )
                        )
                        .bounds(
                                x + 175,
                                y + 90,
                                125,
                                25
                        )
                        .build()
        );

        addBackButton();
    }

    // ------------------------------------------------------------
    // BROWSER
    // ------------------------------------------------------------

    private void openBrowser(Mode newMode) {
        mode = newMode;
        browserOffset = 0;

        rebuildWidgets();
    }

    private void buildBrowserWidgets() {
        int x = leftPos;
        int y = topPos;

        searchBox =
                new EditBox(
                        font,
                        x + 15,
                        y + 25,
                        210,
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
                ignored ->
                        browserOffset = 0
        );

        addRenderableWidget(
                searchBox
        );

        amountBox =
                new EditBox(
                        font,
                        x + 240,
                        y + 25,
                        55,
                        18,
                        Component.translatable(
                                "bountifulrequests.gui.amount"
                        )
                );

        amountBox.setValue("1");

        if (mode
                == Mode.BOUNTIFUL_BROWSER) {
            amountBox.setEditable(false);
        }

        addRenderableWidget(
                amountBox
        );

        addBackButton();
    }

    private void addBackButton() {
        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.back"
                                ),
                                button -> {
                                    mode = Mode.MAIN;
                                    rebuildWidgets();
                                }
                        )
                        .bounds(
                                leftPos + 15,
                                topPos + 202,
                                75,
                                18
                        )
                        .build()
        );
    }

    // ------------------------------------------------------------
    // RENDER
    // ------------------------------------------------------------

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {
        int x = leftPos;
        int y = topPos;

        graphics.fill(
                x,
                y,
                x + imageWidth,
                y + imageHeight,
                0xEE181818
        );

        graphics.fill(
                x + 4,
                y + 4,
                x + imageWidth - 4,
                y + 20,
                0xFF303030
        );

        if (state == null) {
            return;
        }

        boolean pending =
                !"NONE".equals(
                        state.pendingMode()
                );

        if (pending) {
            renderPending(
                    graphics,
                    x,
                    y
            );

            return;
        }

        switch (mode) {
            case MAIN ->
                    renderMain(
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
                            y + 35,
                            0xFFFFFF
                    );

            case ITEM_BROWSER ->
                    renderItems(
                            graphics,
                            x,
                            y,
                            mouseX,
                            mouseY
                    );

            case ENTITY_BROWSER ->
                    renderEntities(
                            graphics,
                            x,
                            y
                    );

            case TAG_BROWSER ->
                    renderTags(
                            graphics,
                            x,
                            y
                    );

            case BOUNTIFUL_BROWSER ->
                    renderBountifulEntries(
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
        graphics.drawString(
                font,
                Component.translatable(
                        "bountifulrequests.gui.objectives"
                ),
                x + 15,
                y + 44,
                0xFFFFFF
        );

        for (int i = 0;
             i < Math.min(
                     state.objectives().size(),
                     3
             );
             i++) {

            ObjectiveSpec objective =
                    state.objectives().get(i);

            graphics.drawString(
                    font,
                    objectiveText(objective),
                    x + 18,
                    y + 54 + i * 10,
                    0xDDDDDD
            );
        }

        graphics.drawString(
                font,
                Component.translatable(
                        "bountifulrequests.gui.duration_minutes"
                ),
                x + 15,
                y + 103,
                0xAAAAAA
        );

        graphics.drawString(
                font,
                Component.translatable(
                        "bountifulrequests.gui.rewards"
                ),
                x + 232,
                y + 38,
                0xFFFFFF
        );

        // Slot backgrounds.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int sx =
                        x + 231 + column * 18;

                int sy =
                        y + 49 + row * 18;

                graphics.fill(
                        sx,
                        sy,
                        sx + 18,
                        sy + 18,
                        0xFF555555
                );

                graphics.fill(
                        sx + 1,
                        sy + 1,
                        sx + 17,
                        sy + 17,
                        0xFF202020
                );
            }
        }

        graphics.drawString(
                font,
                Component.translatable(
                        "container.inventory"
                ),
                x + 88,
                y + 133,
                0xAAAAAA
        );

        if (state.admin()) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "bountifulrequests.gui.rotation_uses"
                    ),
                    x + 212,
                    y + 173,
                    0xAAAAAA
            );
        }
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

        int start =
                Math.min(
                        browserOffset,
                        Math.max(
                                0,
                                items.size() - 45
                        )
                );

        for (int i = 0;
             i < 45
                     && start + i < items.size();
             i++) {

            int column =
                    i % 9;

            int row =
                    i / 9;

            int sx =
                    x + 25 + column * 20;

            int sy =
                    y + 55 + row * 20;

            ItemStack stack =
                    new ItemStack(
                            items.get(start + i)
                    );

            graphics.fill(
                    sx - 1,
                    sy - 1,
                    sx + 18,
                    sy + 18,
                    0xFF383838
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

        renderTextRows(
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
                        .toList()
        );
    }

    private void renderTags(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        renderTextRows(
                graphics,
                x,
                y,
                filteredTags()
                        .stream()
                        .map(id -> "#" + id)
                        .toList()
        );
    }

    private void renderBountifulEntries(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        renderTextRows(
                graphics,
                x,
                y,
                filteredBountiful()
                        .stream()
                        .map(entry ->
                                entry.getId()
                                        + "  §8"
                                        + entry.getContent()
                        )
                        .toList()
        );
    }

    private void renderTextRows(
            GuiGraphics graphics,
            int x,
            int y,
            List<String> rows
    ) {
        int start =
                Math.min(
                        browserOffset,
                        Math.max(
                                0,
                                rows.size() - 10
                        )
                );

        for (int i = 0;
             i < 10
                     && start + i < rows.size();
             i++) {

            int sy =
                    y + 54 + i * 13;

            graphics.fill(
                    x + 18,
                    sy,
                    x + 315,
                    sy + 12,
                    0xFF282828
            );

            graphics.drawString(
                    font,
                    rows.get(start + i),
                    x + 23,
                    sy + 2,
                    0xFFFFFF
            );
        }
    }

    private void renderPending(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        long seconds =
                Math.max(
                        0,
                        (state.pendingRemainingTicks() + 19)
                                / 20
                );

        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "bountifulrequests.gui.pending"
                ),
                x + imageWidth / 2,
                y + 80,
                0xFFD56A
        );

        graphics.drawCenteredString(
                font,
                Long.toString(seconds),
                x + imageWidth / 2,
                y + 104,
                0xFFFFFF
        );
    }

    // ------------------------------------------------------------
    // PENDING BUTTON
    // ------------------------------------------------------------

    private void buildPendingWidgets() {
        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "bountifulrequests.gui.undo"
                                ),
                                button ->
                                        PacketDistributor.sendToServer(
                                                EditorActionPayload.simple(
                                                        "UNDO"
                                                )
                                        )
                        )
                        .bounds(
                                leftPos + 104,
                                topPos + 130,
                                130,
                                22
                        )
                        .build()
        );
    }

    // ------------------------------------------------------------
    // CLICK BROWSER
    // ------------------------------------------------------------

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button == 0
                && state != null) {

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

            if (mode == Mode.BOUNTIFUL_BROWSER
                    && clickBountiful(mouseX, mouseY)) {

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
        int localX =
                (int) mouseX
                        - leftPos
                        - 25;

        int localY =
                (int) mouseY
                        - topPos
                        - 55;

        if (localX < 0
                || localY < 0) {
            return false;
        }

        int column =
                localX / 20;

        int row =
                localY / 20;

        if (column >= 9
                || row >= 5) {
            return false;
        }

        int index =
                browserOffset
                        + row * 9
                        + column;

        List<Item> items =
                filteredItems();

        if (index < 0
                || index >= items.size()) {
            return false;
        }

        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        items.get(index)
                );

        sendObjective(
                ObjectiveSpec.Kind.ITEM,
                id.toString()
        );

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
                browserOffset + row;

        if (index >= entities.size()) {
            return false;
        }

        sendObjective(
                ObjectiveSpec.Kind.ENTITY,
                entities.get(index)
                        .toString()
        );

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
                browserOffset + row;

        if (index >= tags.size()) {
            return false;
        }

        sendObjective(
                ObjectiveSpec.Kind.ITEM_TAG,
                tags.get(index)
                        .toString()
        );

        return true;
    }

    private boolean clickBountiful(
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

        List<PoolEntry> entries =
                filteredBountiful();

        int index =
                browserOffset + row;

        if (index >= entries.size()) {
            return false;
        }

        sendObjective(
                ObjectiveSpec.Kind.BOUNTIFUL_ENTRY,
                entries.get(index)
                        .getId()
        );

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

        if (x < 18
                || x > 315
                || y < 54
                || y >= 184) {
            return -1;
        }

        return (y - 54) / 13;
    }

    private void sendObjective(
            ObjectiveSpec.Kind kind,
            String content
    ) {
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
                        kind.name(),
                        content,
                        amount,
                        0,
                        false
                )
        );

        mode = Mode.MAIN;
    }

    // ------------------------------------------------------------
    // SCROLL
    // ------------------------------------------------------------

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (mode != Mode.MAIN
                && mode != Mode.TYPE_SELECTOR) {

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

    // ------------------------------------------------------------
    // FILTERS
    // ------------------------------------------------------------

    private String query() {
        if (searchBox == null) {
            return "";
        }

        return searchBox
                .getValue()
                .toLowerCase(
                        Locale.ROOT
                )
                .trim();
    }

    private List<Item> filteredItems() {
        String query = query();

        return BuiltInRegistries.ITEM
                .stream()
                .filter(item ->
                        item != Items.AIR
                )
                .filter(item -> {
                    ResourceLocation id =
                            BuiltInRegistries.ITEM
                                    .getKey(item);

                    String name =
                            item.getDescription()
                                    .getString()
                                    .toLowerCase(
                                            Locale.ROOT
                                    );

                    String key =
                            id.toString()
                                    .toLowerCase(
                                            Locale.ROOT
                                    );

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
                                    .toLowerCase(
                                            Locale.ROOT
                                    );

                    return query.isBlank()
                            || name.contains(query)
                            || id.toString()
                            .toLowerCase(
                                    Locale.ROOT
                            )
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
                                .toLowerCase(
                                        Locale.ROOT
                                )
                                .contains(query)
                )
                .sorted()
                .toList();
    }

    private List<PoolEntry> filteredBountiful() {
        String query = query();

        return BountifulContent.INSTANCE
                .getPoolEntryMap()
                .values()
                .stream()
                .filter(entry ->
                        entry.getTypeLogic()
                                instanceof IBountyObjective
                )
                .filter(entry ->
                        query.isBlank()
                                || entry.getId()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                                .contains(query)
                                || entry.getContent()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                                .contains(query)
                )
                .sorted(
                        Comparator.comparing(
                                PoolEntry::getId
                        )
                )
                .toList();
    }

    // ------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------

    private Component objectiveText(
            ObjectiveSpec objective
    ) {
        String prefix =
                switch (objective.kind) {
                    case ITEM -> "📦 ";
                    case ITEM_TAG -> "# ";
                    case ENTITY -> "☠ ";
                    case BOUNTIFUL_ENTRY,
                         BOUNTIFUL_RESOLVED -> "◆ ";
                };

        return Component.literal(
                prefix
                        + objective.content
                        + (
                        objective.kind
                                == ObjectiveSpec.Kind.BOUNTIFUL_ENTRY
                                ? ""
                                : " x" + objective.amount
                )
        );
    }

    private static int parseInt(
            String value,
            int fallback
    ) {
        try {
            return Integer.parseInt(
                    value
            );
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(
            String value,
            long fallback
    ) {
        try {
            return Long.parseLong(
                    value
            );
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String niceName(
            String input
    ) {
        String lower =
                input.toLowerCase(
                        Locale.ROOT
                );

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