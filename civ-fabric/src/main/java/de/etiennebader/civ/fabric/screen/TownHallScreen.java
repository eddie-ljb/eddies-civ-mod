package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.TownHalls;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.town.TownOverview;
import de.etiennebader.civ.server.town.TownService;
import de.etiennebader.civ.server.town.Viewer;
import de.etiennebader.civ.server.villager.VillagerService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Das Menü des Gemeindehauses: Steckbrief, Mitglieder, Recht, Kasse, Karte, Beitreten und Austreten.
 */
public final class TownHallScreen extends MenuScreen {

    private final PolityId town;
    private final String initialName;
    private boolean dissolveArmed;

    public TownHallScreen(CivServer civ, ServerPlayer player, TownOverview town) {
        super(civ, player, Optional.empty());
        this.town = town.id();
        this.initialName = town.name();
    }

    @Override
    public int rows() {
        return 5;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.TOWN_TITLE.text(initialName));
    }

    private Optional<TownOverview> overview() {
        return civ.queries().polity(town).map(polity -> civ.towns().overview(polity, Players.ref(player)));
    }

    @Override
    public void render(Canvas canvas) {
        Optional<TownOverview> found = overview();
        if (found.isEmpty()) {
            canvas.set(22, Icon.of(Items.BARRIER).name(Message.of(MessageKey.TOWN_HALL_ORPHANED), Icon.BAD).build());
            return;
        }
        TownOverview town = found.get();
        Viewer viewer = town.viewer();
        canvas.fillRow(0, Icon.of(Items.STAINED_GLASS_PANE.yellow()).hidden().build());
        canvas.fillRow(4, Icon.of(Items.STAINED_GLASS_PANE.yellow()).hidden().build());

        canvas.set(4, Icon.of(Items.BELL).name(GuiKeys.OVERVIEW.text(town.name()))
                .line(GuiKeys.OVERVIEW_TIER.text(town.tier()))
                .line(GuiKeys.OVERVIEW_FOUNDED.text(town.foundedOn()))
                .line(GuiKeys.OVERVIEW_MAYOR.text(town.mayor()))
                .line(GuiKeys.OVERVIEW_MEMBERS.text(town.members().size()))
                .line(GuiKeys.OVERVIEW_VILLAGERS.text(town.villagers(),
                        money(civ.villagers().dailyIncome(civ.queries().polity(this.town).orElseThrow()))))
                .line(TownService.territorySummary(town))
                .line(federationLine())
                .line(GuiKeys.OVERVIEW_TREASURY.text(money(town.treasury())))
                .line(moodLine())
                .blank()
                .line(GuiKeys.OVERVIEW_ROLE.text(role(viewer)), viewer.isMember() ? Icon.GOOD : Icon.HINT)
                .hint(GuiKeys.OVERVIEW_MOOD_HINT.text())
                .glint().build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new MoodScreen(civ, player, this.town, town.name(), this));
        });

        canvas.set(38, Icon.of(Items.ENCHANTED_BOOK).name(GuiKeys.LAW_BOOK_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.LAW_BOOK_OPEN_HINT.text()).build(), click -> civ.queries().polity(this.town)
                .ifPresent(polity -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new LawBookScreen(civ, player, polity, Optional.of(this)));
                }));

        canvas.set(42, Icon.of(Items.BANNER.purple()).name(GuiKeys.PARTIES.text(), Icon.TITLE)
                .hint(GuiKeys.PARTIES_HINT.text()).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new PartiesScreen(civ, player, Optional.of(this)));
        });

        canvas.set(37, Icon.of(Items.SPYGLASS).name(GuiKeys.ELECTIONS_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.ELECTIONS_OPEN_HINT.text()).build(), click -> civ.queries().polity(this.town)
                .ifPresent(polity -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new ElectionsScreen(civ, player, polity, Optional.of(this)));
                }));

        canvas.set(41, Icon.of(Items.LECTERN).name(GuiKeys.PARLIAMENT_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.PARLIAMENT_OPEN_HINT.text()).build(), click -> civ.queries().polity(this.town)
                .ifPresent(polity -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new ParliamentScreen(civ, player, polity, Optional.of(this)));
                }));

        canvas.set(1, Icon.of(Items.EMERALD).name(GuiKeys.EXCHANGE_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.EXCHANGE_OPEN_HINT.text()).build(), click -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new ExchangeScreen(civ, player, Optional.of(this)));
                });
        canvas.set(7, Icon.of(Items.PAPER).name(GuiKeys.PRESS_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.PRESS_OPEN_HINT.text()).build(), click -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new PressScreen(civ, player, Optional.of(this)));
                });
        renderLevels(canvas);
        canvas.set(2, Icon.of(Items.WRITABLE_BOOK).name(GuiKeys.MAIL_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.MAIL_OPEN_HINT.text()).build(), click -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new MailScreen(civ, player, Optional.of(this)));
                });
        canvas.set(6, Icon.of(Items.PLAYER_HEAD).name(GuiKeys.FRIENDS_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.FRIENDS_OPEN_HINT.text()).build(), click -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new FriendsScreen(civ, player, Optional.of(this)));
                });
        canvas.set(39, Icon.of(Items.FILLED_MAP).name(GuiKeys.CONTRACTS_OPEN.text(), Icon.TITLE)
                .hint(GuiKeys.CONTRACTS_OPEN_HINT.text()).build(), click -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new ContractsScreen(civ, player, Optional.of(this)));
                });

        canvas.set(40, Icon.of(Items.WRITTEN_BOOK).name(GuiKeys.CONSTITUTION_TOWN_HALL.text(), Icon.TITLE)
                .hint(GuiKeys.CONSTITUTION_TOWN_HALL_HINT.text()).build(), click -> civ.queries().polity(this.town)
                .ifPresent(polity -> {
                    Feedback.click(click.player());
                    Screens.open(click.player(), new ConstitutionScreen(civ, player, polity, Optional.of(this)));
                }));

        canvas.set(19, Icon.of(Items.PLAYER_HEAD).name(GuiKeys.MEMBERS.text(town.members().size()))
                .hint(GuiKeys.MEMBERS_HINT.text()).count(town.members().size()).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new MembersScreen(civ, player, this.town, this));
        });

        canvas.set(21, Icon.of(Items.WRITABLE_BOOK).name(GuiKeys.LAWS.text())
                .hint(GuiKeys.LAWS_HINT.text()).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new LawsScreen(civ, player, this.town, this));
        });

        Icon treasury = Icon.of(Items.GOLD_BLOCK).name(GuiKeys.TREASURY.text(money(town.treasury())));
        if (viewer.isMember()) {
            treasury.hint(GuiKeys.TREASURY_PAY_IN_HINT.text());
        }
        if (viewer == Viewer.MAYOR) {
            treasury.hint(GuiKeys.TREASURY_TAKE_HINT.text());
        }
        canvas.set(23, treasury.build(), click -> {
            if (!viewer.isMember()) {
                return;
            }
            String amount = Integer.toString(click.multiplier());
            Reply reply = click.right()
                    ? civ.bank().takeFromTreasury(caller(), amount)
                    : civ.bank().payIntoTreasury(caller(), amount);
            report(click, reply);
        });

        canvas.set(25, Icon.of(Items.FILLED_MAP).name(GuiKeys.MAP.text())
                .hint(GuiKeys.MAP_HINT.text()).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new MapScreen(civ, player, Optional.of(this)));
        });

        int plotCount = civ.plots().plotsOf(this.town, Players.ref(player)).size();
        canvas.set(28, Icon.of(Items.OAK_SIGN).name(GuiKeys.PLOTS.text(plotCount))
                .hint(GuiKeys.PLOTS_HINT.text()).build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new PlotsScreen(civ, player, this.town, town.name(), this));
        });

        int votes = civ.queries().polity(this.town).map(polity -> polity.proposals().size()).orElse(0);
        Icon voteIcon = Icon.of(Items.LECTERN).name(GuiKeys.VOTES.text(votes)).hint(GuiKeys.VOTES_HINT.text());
        if (votes > 0) {
            voteIcon.glint();
        }
        canvas.set(32, voteIcon.build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new VotesScreen(civ, player, this.town, town.name(), this));
        });

        int elections = civ.queries().polity(this.town).map(polity -> polity.elections().size()).orElse(0);
        Icon officesIcon = Icon.of(Items.GOLDEN_HELMET).name(GuiKeys.OFFICES.text(elections))
                .hint(GuiKeys.OFFICES_HINT.text());
        if (elections > 0) {
            officesIcon.glint();
        }
        canvas.set(34, officesIcon.build(), click -> {
            Feedback.click(click.player());
            Screens.open(click.player(), new OfficesScreen(civ, player, this.town, town.name(), this));
        });

        if (viewer.isMember()) {
            canvas.set(30, Icon.of(Items.GRASS_BLOCK).name(GuiKeys.CLAIM_HERE.text())
                    .hint(GuiKeys.CLAIM_HERE_HINT.text(town.chunks(), town.claimLimit())).build(), click ->
                    report(click, civ.commands().claim(caller(), ClaimType.CORE)));
            canvas.set(43, Icon.of(Items.IRON_DOOR).name(GuiKeys.LEAVE.text(), Icon.BAD)
                    .hint(GuiKeys.LEAVE_HINT.text(town.name())).build(), click -> {
                if (click.shift()) {
                    report(click, civ.commands().leave(caller()));
                }
            });
        } else {
            canvas.set(30, Icon.of(Items.BANNER.lime()).name(GuiKeys.JOIN.text(), Icon.GOOD)
                    .hint(GuiKeys.JOIN_HINT.text(town.name())).build(), click -> {
                Reply reply = civ.commands().join(caller(), town.name());
                report(click, reply);
                if (reply.success()) {
                    Feedback.fanfare(click.player());
                }
            });
        }
        if (viewer == Viewer.MAYOR) {
            Icon dissolve = Icon.of(dissolveArmed ? Items.TNT : Items.BARRIER).name(GuiKeys.DISSOLVE.text(), Icon.BAD)
                    .hint(dissolveArmed ? GuiKeys.DISSOLVE_CONFIRM.text() : GuiKeys.DISSOLVE_HINT.text());
            if (dissolveArmed) {
                dissolve.glint();
            }
            canvas.set(44, dissolve.build(), click -> {
                if (!click.shift()) {
                    return;
                }
                if (!dissolveArmed) {
                    dissolveArmed = true;
                    Feedback.failure(click.player());
                    return;
                }
                Screens.close(click.player());
                TownHalls.apply(click.player(), civ.townHalls().dissolve(caller()));
            });
        }
        canvas.set(36, Icon.of(Items.KNOWLEDGE_BOOK).name(Message.of(MessageKey.HELP_HEADER))
                .line(Message.of(MessageKey.HELP_TOWN)).line(Message.of(MessageKey.HELP_LAND))
                .line(Message.of(MessageKey.HELP_BANK)).line(Message.of(MessageKey.HELP_MARKET)).build(),
                click -> {
                    Feedback.click(click.player());
                    Components.send(click.player(), civ.commands().help());
                });
    }

    /** Rechts oben: Menü des nächsthöheren Bundes (Land, Nation …), dort wechselt man weiter. */
    private void renderLevels(Canvas canvas) {
        Optional<PolityView> parent = civ.queries().parentOf(town);
        Icon icon = Icon.of(parent.isPresent() ? Items.BEACON : Items.GLASS).name(GuiKeys.LEVELS_OPEN.text(), Icon.TITLE);
        civ.queries().polity(town).ifPresent(polity -> {
            for (PolityView ancestor : civ.queries().ancestorsOf(polity.id())) {
                icon.line(GuiKeys.LEVEL_TAB.text(civ.levels().levelOf(ancestor).displayName(), ancestor.name().value()),
                        Icon.INFO);
            }
        });
        icon.hint(parent.isPresent() ? GuiKeys.LEVELS_OPEN_HINT.text() : GuiKeys.LEVELS_NONE_HINT.text());
        canvas.set(8, icon.build(), click -> parent.flatMap(found -> civ.queries().polity(found.id()))
                .ifPresent(found -> LevelScreen.open(civ, click.player(), found, Optional.of(this))));
    }

    private Message federationLine() {
        return civ.queries().polity(town).flatMap(polity -> civ.federations().belongsTo(polity))
                .orElseGet(GuiKeys.OVERVIEW_NO_FEDERATION::text);
    }

    private Message moodLine() {
        return civ.queries().polity(town)
                .map(polity -> civ.villagers().moodOf(polity))
                .filter(mood -> mood.villagers() > 0)
                .map(mood -> GuiKeys.OVERVIEW_MOOD.text(VillagerService.moodWord(mood.averageScore()),
                        mood.averageScore()))
                .orElseGet(GuiKeys.OVERVIEW_MOOD_NONE::text);
    }

    static Message role(Viewer viewer) {
        return switch (viewer) {
            case OUTSIDER -> GuiKeys.ROLE_OUTSIDER.text();
            case RESIDENT -> GuiKeys.ROLE_RESIDENT.text();
            case CITIZEN -> GuiKeys.ROLE_CITIZEN.text();
            case MAYOR -> GuiKeys.ROLE_MAYOR.text();
        };
    }
}
