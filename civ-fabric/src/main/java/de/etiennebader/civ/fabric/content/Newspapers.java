package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.server.press.PressService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Zeitungsausgaben als beschriebene Bücher.
 */
public final class Newspapers {

    /** Zeichen je Buchseite, mit Luft für Zeilenumbrüche. */
    private static final int PAGE_CHARS = 600;
    private static final int MAX_PAGES = 50;

    private Newspapers() {
    }

    public static ItemStack edition(PressService.Edition edition) {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (String page : edition.pages()) {
            for (String part : split(page)) {
                if (pages.size() < MAX_PAGES) {
                    pages.add(Filterable.passThrough(Component.literal(part)));
                }
            }
        }
        String title = edition.title().length() > 32 ? edition.title().substring(0, 32) : edition.title();
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title),
                edition.author(), 0, pages, true));
        return book;
    }

    private static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        String rest = text;
        while (rest.length() > PAGE_CHARS) {
            int cut = rest.lastIndexOf(' ', PAGE_CHARS);
            if (cut < PAGE_CHARS / 2) {
                cut = PAGE_CHARS;
            }
            parts.add(rest.substring(0, cut));
            rest = rest.substring(cut).stripLeading();
        }
        parts.add(rest);
        return parts;
    }
}
