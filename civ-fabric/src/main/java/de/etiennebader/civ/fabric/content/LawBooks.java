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

    /** Gesetz, Teil, Titel und Text eines Paragraphen aus einem beschriebenen Buch; Gesetz und Teil können leer sein. */
    public record Draft(String lawName, String partName, String title, String text) {
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

    /**
     * Optionale Kopfzeilen {@code Gesetz: …} und {@code Teil: …} (in beliebiger Reihenfolge), dann der Titel als erste
     * weitere Zeile und der Rest als Text; ohne zweite Zeile ist der ganze Text auch der Titel. Der Titel darf wie im
     * Befehl die Schreibweise {@code Gesetz > Teil > Titel} haben.
     */
    public static Optional<Draft> draftOf(ItemStack stack) {
        return textOf(stack).map(LawBooks::parseDraft);
    }

    static Draft parseDraft(String text) {
        String law = "";
        String part = "";
        String rest = text;
        while (true) {
            int newline = rest.indexOf('\n');
            String line = (newline < 0 ? rest : rest.substring(0, newline)).strip();
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            boolean isLaw = lower.startsWith("gesetz:");
            boolean isPart = lower.startsWith("teil:");
            if (newline < 0 || !(isLaw || isPart)) {
                break;
            }
            String value = line.substring(line.indexOf(':') + 1).strip();
            if (isLaw) {
                law = value;
            } else {
                part = value;
            }
            rest = rest.substring(newline + 1).strip();
        }
        int newline = rest.indexOf('\n');
        if (newline < 0) {
            return new Draft(law, part, shorten(rest), rest);
        }
        return new Draft(law, part, shorten(rest.substring(0, newline).strip()), rest.substring(newline + 1).strip());
    }

    private static String shorten(String title) {
        return title.length() <= 80 ? title : title.substring(0, 79).strip() + "…";
    }
}
