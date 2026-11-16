package de.etiennebader.civ.fabric.content;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Das Gesetzbuch-Item und das Lesen von Paragraphen aus Buch und Feder.
 */
public final class LawBooks {

    private static Item item;

    /** Titel und Text eines Paragraphen aus einem beschriebenen Buch. */
    public record Draft(String title, String text) {
    }

    private LawBooks() {
    }

    static void init(Item lawBook) {
        item = Objects.requireNonNull(lawBook, "lawBook");
    }

    public static boolean isLawBook(ItemStack stack) {
        return item != null && stack.is(item);
    }

    /** Der gesamte Text eines Buchs mit Feder, Seiten durch Zeilenumbruch getrennt; leer, wenn es keins ist. */
    public static Optional<String> textOf(ItemStack stack) {
        if (!stack.is(Items.WRITABLE_BOOK)) {
            return Optional.empty();
        }
        WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (content == null) {
            return Optional.empty();
        }
        String text = content.getPages(false).collect(Collectors.joining("\n")).strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    /** Erste Zeile als Titel, der Rest als Text; ohne zweite Zeile ist der ganze Text auch der Titel. */
    public static Optional<Draft> draftOf(ItemStack stack) {
        return textOf(stack).map(text -> {
            int newline = text.indexOf('\n');
            if (newline < 0) {
                return new Draft(shorten(text), text);
            }
            return new Draft(shorten(text.substring(0, newline).strip()), text.substring(newline + 1).strip());
        });
    }

    private static String shorten(String title) {
        return title.length() <= 80 ? title : title.substring(0, 79).strip() + "…";
    }
}
