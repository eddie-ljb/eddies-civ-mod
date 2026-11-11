package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.application.population.Satisfaction;
import de.etiennebader.civ.application.population.TownMood;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Trait;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.villager.VillagerService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Stimmung der Dorfbewohner: oben die Gesamtstimmung, darunter woraus sie sich zusammensetzt, unten die
 * Persönlichkeiten. Nur zum Ansehen.
 */
final class MoodScreen extends MenuScreen {

    private static final int[] FACTOR_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] TRAIT_SLOTS = {19, 20, 21, 23, 24, 25};

    private final PolityId town;
    private final String townName;

    MoodScreen(CivServer civ, ServerPlayer player, PolityId town, String townName, Screen parent) {
        super(civ, player, Optional.of(parent));
        this.town = town;
        this.townName = townName;
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.MOOD_TITLE.text(townName));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(3, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        Optional<PolityView> polity = civ.queries().polity(town);
        if (polity.isEmpty()) {
            return;
        }
        TownMood mood = civ.villagers().moodOf(polity.get());
        if (mood.villagers() == 0) {
            canvas.set(13, Icon.of(Items.PAPER).name(GuiKeys.MOOD_EMPTY.text(), Icon.INFO).build());
            return;
        }
        int score = mood.averageScore();
        Icon summary = Icon.of(score >= Satisfaction.CONTENT ? Items.EMERALD
                        : score < -Satisfaction.CONTENT ? Items.REDSTONE : Items.GLOWSTONE_DUST)
                .name(GuiKeys.MOOD_SUMMARY.text(VillagerService.moodWord(score), score), Icon.TITLE)
                .line(GuiKeys.MOOD_COUNTS.text(mood.content(), mood.villagers() - mood.content() - mood.discontent(),
                        mood.discontent()))
                .line(GuiKeys.MOOD_PRODUCTIVITY.text(mood.productivity() / mood.villagers()), Icon.INFO);
        if (mood.atRisk() > 0) {
            summary.line(GuiKeys.MOOD_AT_RISK.text(mood.atRisk()), Icon.BAD);
        }
        canvas.set(4, summary.build());
        int index = 0;
        for (Satisfaction.Factor factor : Satisfaction.Factor.values()) {
            int points = mood.averageFactors().getOrDefault(factor, 0);
            canvas.set(FACTOR_SLOTS[index++], factorIcon(factor, points));
        }
        index = 0;
        for (Trait trait : Trait.values()) {
            int count = mood.traits().getOrDefault(trait, 0);
            canvas.set(TRAIT_SLOTS[index++], Icon.of(traitItem(trait)).count(Math.max(1, Math.min(64, count)))
                    .name(Message.of(TranslationKeys.trait(trait)), Icon.TITLE)
                    .line(GuiKeys.MOOD_TRAIT_COUNT.text(count))
                    .line(Message.of(TranslationKeys.traitHint(trait)), Icon.HINT)
                    .build());
        }
    }

    private static ItemStack factorIcon(Satisfaction.Factor factor, int points) {
        Item item = switch (factor) {
            case BASE -> Items.BREAD;
            case ORDER -> Items.IRON_BARS;
            case TAXES -> Items.GOLD_NUGGET;
            case VOICE -> Items.WRITABLE_BOOK;
            case PROSPERITY -> Items.EMERALD_BLOCK;
        };
        return Icon.of(item)
                .name(Message.of(TranslationKeys.moodFactor(factor)), Icon.TITLE)
                .line(GuiKeys.MOOD_POINTS.text(points > 0 ? "+" + points : Integer.toString(points)),
                        points > 0 ? Icon.GOOD : points < 0 ? Icon.BAD : Icon.INFO)
                .line(Message.of(TranslationKeys.moodFactorHint(factor)), Icon.HINT)
                .build();
    }

    private static Item traitItem(Trait trait) {
        return switch (trait) {
            case ORDERLY -> Items.SHIELD;
            case FREE_SPIRITED -> Items.FEATHER;
            case THRIFTY -> Items.GOLD_INGOT;
            case GENEROUS -> Items.CAKE;
            case LOYAL -> Items.BED.red();
            case RESTLESS -> Items.LEATHER_BOOTS;
        };
    }
}
