package org.crafterscr.bountifulrequests.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.ejekta.bountiful.components.BountyDataEntry;
import net.minecraft.nbt.CompoundTag;

/**
 * Representa un objetivo elegido desde nuestro editor.
 *
 * DIRECT:
 * - ITEM
 * - ITEM_TAG
 * - ENTITY
 *
 * BOUNTIFUL_ENTRY permite reutilizar literalmente una entrada ya cargada
 * por Bountiful, conservando compatibilidad con criteria y futuros tipos.
 */
public final class ObjectiveSpec {

    public enum Kind {
        ITEM,
        ITEM_TAG,
        ENTITY,

        // Durante edición contiene el ID de un PoolEntry.
        BOUNTIFUL_ENTRY,

        // Una vez publicada ya fue resuelta a datos estables.
        BOUNTIFUL_RESOLVED
    }

    public Kind kind;

    public String content = "";
    public int amount = 1;

    // Datos usados al resolver un PoolEntry real de Bountiful.
    public String entryId = "";
    public String logicName = "";
    public String customName = "";
    public String dataJson = "";
    public int entryRarity = 0;

    public ObjectiveSpec() {
    }

    public static ObjectiveSpec direct(Kind kind, String content, int amount) {
        ObjectiveSpec spec = new ObjectiveSpec();

        spec.kind = kind;
        spec.content = content;
        spec.amount = Math.max(1, amount);

        spec.logicName = switch (kind) {
            case ITEM -> "item";
            case ITEM_TAG -> "item_tag";
            case ENTITY -> "entity";
            default -> "";
        };

        return spec;
    }

    public static ObjectiveSpec bountifulEntry(String entryId) {
        ObjectiveSpec spec = new ObjectiveSpec();

        spec.kind = Kind.BOUNTIFUL_ENTRY;
        spec.content = entryId;
        spec.entryId = entryId;

        return spec;
    }

    /**
     * Convierte una BountyDataEntry creada por Bountiful en información
     * persistente para que la misión NO cambie cada vez que aparece
     * nuevamente en un Board.
     */
    public static ObjectiveSpec resolved(BountyDataEntry entry) {
        ObjectiveSpec spec = new ObjectiveSpec();

        spec.kind = Kind.BOUNTIFUL_RESOLVED;
        spec.content = entry.getContent();
        spec.amount = entry.getAmount();
        spec.entryId = entry.getId();
        spec.logicName = entry.getLogicName();
        spec.customName = entry.getName() == null ? "" : entry.getName();
        spec.entryRarity = entry.getRarity().ordinal();

        JsonObject data = entry.getData();
        if (data != null) {
            spec.dataJson = data.toString();
        }

        return spec;
    }

    public JsonObject parsedData() {
        if (dataJson == null || dataJson.isBlank()) {
            return null;
        }

        try {
            return JsonParser.parseString(dataJson).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putString("Kind", kind.name());
        tag.putString("Content", content);
        tag.putInt("Amount", amount);

        tag.putString("EntryId", entryId);
        tag.putString("Logic", logicName);
        tag.putString("Name", customName);
        tag.putString("Data", dataJson);
        tag.putInt("EntryRarity", entryRarity);

        return tag;
    }

    public static ObjectiveSpec load(CompoundTag tag) {
        ObjectiveSpec spec = new ObjectiveSpec();

        try {
            spec.kind = Kind.valueOf(tag.getString("Kind"));
        } catch (Exception ignored) {
            spec.kind = Kind.ITEM;
        }

        spec.content = tag.getString("Content");
        spec.amount = Math.max(1, tag.getInt("Amount"));

        spec.entryId = tag.getString("EntryId");
        spec.logicName = tag.getString("Logic");
        spec.customName = tag.getString("Name");
        spec.dataJson = tag.getString("Data");
        spec.entryRarity = tag.getInt("EntryRarity");

        return spec;
    }
}