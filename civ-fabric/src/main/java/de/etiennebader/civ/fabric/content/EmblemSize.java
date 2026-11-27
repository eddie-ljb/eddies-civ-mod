package de.etiennebader.civ.fabric.content;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Die Größe von Flagge, Wappen und Wahlplakat: S ist das ursprüngliche Maß (ein Block hoch), XL doppelt so hoch und
 * breit. Mehr lässt ein Blockmodell nicht zu, denn es darf höchstens zwei Blöcke über seinen Block hinausragen.
 */
public enum EmblemSize implements StringRepresentable {
    S(1.0F), M(1.33F), L(1.66F), XL(2.0F);

    private final float factor;

    EmblemSize(float factor) {
        this.factor = factor;
    }

    /** Wie viel größer als S: Modell und Bild werden um diesen Faktor vom Fuß des Blocks aus vergrößert. */
    public float factor() {
        return factor;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Die Größe zu diesem Namen ({@code s}, {@code M} ...); sonst S. */
    public static EmblemSize parse(String name) {
        for (EmblemSize size : values()) {
            if (size.name().equalsIgnoreCase(name)) {
                return size;
            }
        }
        return S;
    }
}
