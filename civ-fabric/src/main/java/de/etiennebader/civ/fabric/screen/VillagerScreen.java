package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.application.population.Satisfaction;
import de.etiennebader.civ.application.population.VillagerDialogue;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.population.VillagerIdentity;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.villager.VillagerService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Menü eines Dorfbewohners (Schleichen + Rechtsklick): oben seine Auskunft (Name, Persönlichkeit, Stimmung und alles
 * Übrige aus {@code /civ dorfbewohner info}), darunter die Handlungen: mit ihm reden, ihn politisch beeinflussen und
 * ihm Post schicken. Dieselben Handlungen gibt es weiter als Befehle.
 */
public final class VillagerScreen extends MenuScreen {

    private static final int[] TALK_SLOTS = {19, 20, 21, 22};
    private static final int[] MAIL_SLOTS = {28, 29, 30, 31};
    private static final int INFLUENCE_SLOT = 24;

    private final PersonRef villager;

    public VillagerScreen(CivServer civ, ServerPlayer player, PersonRef villager) {
        super(civ, player, Optional.empty());
        this.villager = villager;
    }

    @Override
    public int rows() {
        return 5;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.VILLAGER_TITLE.text(VillagerIdentity.of(villager).fullName()));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(4, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        VillagerIdentity identity = VillagerIdentity.of(villager);
        Optional<PolityView> town = civ.queries().municipalityOf(villager);
        Icon who = Icon.of(Items.NAME_TAG)
                .name(Component.literal(identity.fullName()).withStyle(style -> style.withItalic(false)
                        .withColor(Icon.TITLE)))
                .line(GuiKeys.VILLAGER_REGISTER.text(identity.registerNumber()));
        town.ifPresentOrElse(found -> who.line(Icon.literal(found.name().value(), ChatFormatting.GRAY)),
                () -> who.line(GuiKeys.VILLAGER_HOMELESS.text(), Icon.BAD));
        canvas.set(4, who.build());

        Personality personality = Personality.of(villager);
        canvas.set(11, Icon.of(MoodScreen.traitItem(personality.dominant()))
                .name(GuiKeys.VILLAGER_PERSONALITY.text(Message.of(TranslationKeys.trait(personality.dominant()))),
                        Icon.TITLE)
                .line(GuiKeys.VILLAGER_AXES.text(signed(personality.order()), signed(personality.thrift()),
                        signed(personality.belonging())))
                .line(Message.of(TranslationKeys.traitHint(personality.dominant())), Icon.HINT)
                .build());

        Optional<Satisfaction> satisfaction = civ.villagers().satisfactionOf(villager);
        satisfaction.ifPresent(mood -> canvas.set(13, moodIcon(mood)));

        List<Message> lines = civ.villagers().describe(villager).lines();
        Icon info = Icon.of(Items.BOOK).name(GuiKeys.VILLAGER_INFO.text(), Icon.TITLE);
        lines.forEach(line -> info.line(line, Icon.INFO));
        canvas.set(15, info.build());

        VillagerDialogue.Topic[] topics = VillagerDialogue.Topic.values();
        for (int i = 0; i < topics.length && i < TALK_SLOTS.length; i++) {
            VillagerDialogue.Topic topic = topics[i];
            Message topicName = Message.of(TranslationKeys.dialogueTopic(topic));
            canvas.set(TALK_SLOTS[i], Icon.of(Items.WRITABLE_BOOK)
                            .name(GuiKeys.VILLAGER_TALK.text(topicName), Icon.TITLE)
                            .line(GuiKeys.VILLAGER_TALK_HINT.text(), Icon.HINT).build(),
                    click -> act(click.player(), civ.villagers().talk(villager, topic)));
            canvas.set(MAIL_SLOTS[i], Icon.of(Items.PAPER)
                            .name(GuiKeys.VILLAGER_MAIL.text(topicName), Icon.TITLE)
                            .line(GuiKeys.VILLAGER_MAIL_HINT.text(), Icon.HINT).build(),
                    click -> act(click.player(), civ.villagers().sendMail(
                            de.etiennebader.civ.fabric.Players.ref(click.player()), villager,
                            topic.name().toLowerCase(Locale.ROOT))));
        }
        canvas.set(INFLUENCE_SLOT, Icon.of(Items.GOLDEN_CARROT)
                        .name(GuiKeys.VILLAGER_INFLUENCE.text(), Icon.TITLE)
                        .line(GuiKeys.VILLAGER_INFLUENCE_HINT.text(), Icon.HINT).build(),
                click -> act(click.player(), civ.villagers().influence(
                        de.etiennebader.civ.fabric.Players.caller(click.player()), villager)));
    }

    private void act(ServerPlayer clicker, Reply reply) {
        Feedback.inMenu(clicker, reply);
    }

    private net.minecraft.world.item.ItemStack moodIcon(Satisfaction mood) {
        int score = mood.score();
        Icon icon = Icon.of(score >= Satisfaction.CONTENT ? Items.EMERALD
                        : score < -Satisfaction.CONTENT ? Items.REDSTONE : Items.GLOWSTONE_DUST)
                .name(GuiKeys.VILLAGER_MOOD.text(VillagerService.moodWord(score), signed(score)), Icon.TITLE);
        for (Satisfaction.Factor factor : Satisfaction.Factor.values()) {
            int points = mood.points(factor);
            icon.line(GuiKeys.VILLAGER_FACTOR.text(Message.of(TranslationKeys.moodFactor(factor)), signed(points)),
                    points > 0 ? Icon.GOOD : points < 0 ? Icon.BAD : Icon.INFO);
        }
        icon.line(GuiKeys.VILLAGER_PRODUCTIVITY.text(mood.productivityPercent()), Icon.INFO);
        return icon.build();
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
