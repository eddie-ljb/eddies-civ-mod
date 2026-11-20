package de.etiennebader.civ.fabric.content;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
        List<String> trimmed = new java.util.ArrayList<>(newLines.size());
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.TypedOutputList<String> list = output.list(LINES_KEY, Codec.STRING);
        for (String line : lines) {
            list.add(line);
        }
        if (color != null) {
            output.putString(COLOR_KEY, color.getSerializedName());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.lines = List.copyOf(input.listOrEmpty(LINES_KEY, Codec.STRING).stream().toList());
        this.color = input.getString(COLOR_KEY).map(name -> DyeColor.byName(name, null)).orElse(null);
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
