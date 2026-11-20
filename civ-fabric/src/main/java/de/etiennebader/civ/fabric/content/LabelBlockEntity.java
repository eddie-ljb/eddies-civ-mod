package de.etiennebader.civ.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Hält den vom Spieler vergebenen Text (eine oder mehrere Zeilen) und optional eine Farbe für
 * Straßenschilder, Hausnummern, Orts-, Länder- und Nationenschilder. Der Text wird beim Setzen
 * aus dem im Amboss vergebenen Namen des Items übernommen ({@link LabelBlock#setPlacedBy}); bei
 * mehrzeiligen Schildern trennt der Spieler die Zeilen im Amboss-Namen mit {@code |}.
 */
public final class LabelBlockEntity extends BlockEntity {

    private static final String LINES_KEY = "Lines";
    private static final String COLOR_KEY = "Color";
    private static final int MAX_LINE_LENGTH = 32;

    private List<String> lines = List.of();
    private DyeColor color;

    public LabelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public List<String> lines() {
        return lines;
    }

    public void setLines(List<String> newLines, int maxLines) {
        List<String> trimmed = new ArrayList<>(newLines.size());
        for (String line : newLines) {
            if (trimmed.size() >= maxLines) {
                break;
            }
            String cut = line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) : line;
            trimmed.add(cut);
        }
        this.lines = List.copyOf(trimmed);
        setChanged();
    }

    public DyeColor color() {
        return color;
    }

    public void setColor(DyeColor color) {
        this.color = color;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (String line : lines) {
            list.add(net.minecraft.nbt.StringTag.valueOf(line));
        }
        tag.put(LINES_KEY, list);
        if (color != null) {
            tag.putString(COLOR_KEY, color.getSerializedName());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        List<String> loaded = new ArrayList<>();
        if (tag.get(LINES_KEY) instanceof net.minecraft.nbt.ListTag list) {
            for (Tag element : list) {
                if (element instanceof net.minecraft.nbt.StringTag stringTag) {
                    loaded.add(stringTag.asString(""));
                }
            }
        }
        this.lines = List.copyOf(loaded);
        this.color = tag.contains(COLOR_KEY) ? DyeColor.byName(tag.getString(COLOR_KEY, ""), null) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    public String joinedLines() {
        return String.join(" / ", lines);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LabelBlockEntity other)) {
            return false;
        }
        return lines.equals(other.lines) && color == other.color;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lines, color);
    }
}
