package de.etiennebader.civ.fabric.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Was auf einer Flagge, einem Wappen oder einem Wahlplakat zu sehen ist: die Kennung ({@code blank},
 * {@code color:<farbe>} oder {@code image:<hash>}) und bei einem Bild dessen PNG-Bytes. Die Bytes liegen im Block, damit
 * jeder Spieler das Bild ohne zusätzliche Dateien sieht; der Client malt es selbst auf das Modell (ohne den Papierrand
 * einer Karte).
 */
public final class EmblemBlockEntity extends BlockEntity {

    /** Größer als das erlaubte 128 × 128 großes PNG je wird, ist es kein gültiges Bild; schützt die Pakete. */
    public static final int MAX_PNG_BYTES = 120_000;

    private static final String STYLE_KEY = "Style";
    private static final String LENGTH_KEY = "PngLength";
    private static final String PNG_KEY = "Png";

    private String style = "blank";
    private byte[] png = new byte[0];

    public EmblemBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public String style() {
        return style;
    }

    /** PNG-Bytes des Bildes; leer, wenn es keines gibt. */
    public byte[] png() {
        return png;
    }

    public void set(String newStyle, byte[] newPng) {
        this.style = newStyle;
        this.png = newPng.length > MAX_PNG_BYTES ? new byte[0] : newPng;
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString(STYLE_KEY, style);
        if (png.length > 0) {
            output.putInt(LENGTH_KEY, png.length);
            int[] packed = new int[(png.length + 3) / 4];
            for (int i = 0; i < png.length; i++) {
                packed[i / 4] |= (png[i] & 0xFF) << (24 - 8 * (i % 4));
            }
            output.putIntArray(PNG_KEY, packed);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.style = input.getStringOr(STYLE_KEY, "blank");
        int length = input.getIntOr(LENGTH_KEY, 0);
        int[] packed = input.getIntArray(PNG_KEY).orElse(new int[0]);
        if (length <= 0 || length > MAX_PNG_BYTES || packed.length < (length + 3) / 4) {
            this.png = new byte[0];
            return;
        }
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (packed[i / 4] >>> (24 - 8 * (i % 4)));
        }
        this.png = bytes;
    }
}
