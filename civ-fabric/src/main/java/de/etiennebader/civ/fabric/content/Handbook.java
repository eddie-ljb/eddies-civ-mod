package de.etiennebader.civ.fabric.content;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Handbuch im Spiel: ein Buch mit Seiten aus den Sprachdateien, damit es in der Sprache des Spielers erscheint.
 * Neue Spieler bekommen es beim ersten Betreten, alle anderen mit {@code /civ handbuch}.
 */
public final class Handbook {

    public static final int PAGES = 14;
    /** Markierung am Spieler, dass er das Handbuch schon bekommen hat; wird mit dem Spieler gespeichert. */
    private static final String RECEIVED = "eddies_civ_handbook";

    private Handbook() {
    }

    public static ItemStack book() {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int page = 1; page <= PAGES; page++) {
            pages.add(Filterable.passThrough(Component.translatable("eddies-civ-mod.handbook.page_" + page)));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Civ-Handbuch"),
                "Eddies Civ Mod", 0, pages, true));
        book.set(DataComponents.CUSTOM_NAME, Component.translatable("eddies-civ-mod.handbook.title"));
        return book;
    }

    public static void give(ServerPlayer player) {
        player.getInventory().placeItemBackInInventory(book(), Prediction.SERVER_ONLY);
    }

    /** Beim ersten Betreten: Handbuch ins Inventar. */
    public static void giveOnFirstJoin(ServerPlayer player) {
        if (player.entityTags().contains(RECEIVED)) {
            return;
        }
        player.addTag(RECEIVED);
        give(player);
    }
}
