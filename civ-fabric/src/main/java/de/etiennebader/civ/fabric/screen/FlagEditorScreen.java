package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.civic.Flag;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.content.Flags;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Flaggen-Editor im Spiel (Issue #82): Grundfarbe wählen, Muster in einer Farbe übereinanderlegen, in der Vorschau
 * ansehen und als Flagge der Polity speichern oder als Banner mitnehmen. Der Editor arbeitet auf einer Kopie; erst
 * „Speichern“ ändert die Flagge, und das darf wie sonst nur die Spitze der Polity.
 */
public final class FlagEditorScreen extends MenuScreen {

    private enum Mode {
        MAIN, PATTERN, COLOR
    }

    private static final int[] COLOR_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38};
    private static final int PAGE_SIZE = 28;

    private final PolityView polity;
    private String base;
    private final List<Flag.Layer> layers = new ArrayList<>();
    private Mode mode = Mode.MAIN;
    private String pattern = "";
    private int page;

    FlagEditorScreen(CivServer civ, ServerPlayer player, PolityView polity, Optional<Screen> parent) {
        super(civ, player, parent);
        this.polity = polity;
        Optional<Flag> existing = civ.civic().flagOf(polity.id());
        this.base = existing.map(Flag::base).orElse(DyeColor.WHITE.getSerializedName());
        existing.ifPresent(flag -> layers.addAll(flag.layers()));
    }

    /** Öffnet den Editor für die Flagge dieser Polity. */
    public static void open(CivServer civ, ServerPlayer player, PolityView polity, Optional<Screen> parent) {
        Screens.open(player, new FlagEditorScreen(civ, player, polity, parent));
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.FLAG_EDITOR_TITLE.text(polity.name().value()));
    }

    private Flag flag() {
        return new Flag(base, layers);
    }

    private ItemStack banner(Flag flag) {
        return Flags.banner(flag, player.level().registryAccess());
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        switch (mode) {
            case MAIN -> renderMain(canvas);
            case PATTERN -> renderPatterns(canvas);
            case COLOR -> renderColors(canvas);
        }
    }

    private void renderMain(Canvas canvas) {
        canvas.set(4, Icon.of(banner(flag())).name(GuiKeys.FLAG_EDITOR_PREVIEW.text(layers.size()), Icon.TITLE)
                .line(GuiKeys.FLAG_EDITOR_LAYERS.text(layers.size(), Flag.MAX_LAYERS), Icon.INFO).build());
        canvas.set(13, Icon.of(Items.PAPER).name(GuiKeys.FLAG_EDITOR_BASE.text(), Icon.INFO).build());
        int index = 0;
        for (DyeColor color : DyeColor.values()) {
            int slot = 19 + index / 7 * 9 + index % 7;
            index++;
            String name = color.getSerializedName();
            Icon icon = Icon.of(banner(new Flag(name, List.of()))).name(Component.translatable(
                    "color.minecraft." + name));
            if (name.equals(base)) {
                icon.glint();
            }
            canvas.set(slot, icon.build(), click -> {
                Feedback.click(click.player());
                base = name;
            });
        }
        canvas.set(46, Icon.of(Items.LOOM).name(GuiKeys.FLAG_EDITOR_ADD.text(), Icon.TITLE)
                .line(GuiKeys.FLAG_EDITOR_ADD_HINT.text(), Icon.HINT).build(), click -> {
            Feedback.click(click.player());
            if (layers.size() >= Flag.MAX_LAYERS) {
                Feedback.inMenu(click.player(), de.etiennebader.civ.server.command.Reply.failure(
                        Message.of(GuiKeys.FLAG_EDITOR_FULL.key(), Flag.MAX_LAYERS)));
                return;
            }
            page = 0;
            mode = Mode.PATTERN;
        });
        canvas.set(47, Icon.of(Items.BARRIER).name(GuiKeys.FLAG_EDITOR_UNDO.text(), Icon.BAD).build(), click -> {
            Feedback.click(click.player());
            if (!layers.isEmpty()) {
                layers.removeLast();
            }
        });
        canvas.set(51, Icon.of(Items.BANNER.white()).name(GuiKeys.FLAG_EDITOR_TAKE.text(), Icon.INFO).build(), click -> {
            Feedback.click(click.player());
            click.player().getInventory().placeItemBackInInventory(banner(flag()), Prediction.SERVER_ONLY);
        });
        canvas.set(52, Icon.of(Items.DYE.lime()).name(GuiKeys.FLAG_EDITOR_SAVE.text(), Icon.GOOD)
                .line(GuiKeys.FLAG_EDITOR_SAVE_HINT.text(polity.name().value()), Icon.HINT).build(), click -> {
            Feedback.click(click.player());
            report(click, civ.civic().setFlag(caller(), polity, Optional.of(flag())));
        });
    }

    private List<String> patternIds() {
        List<String> ids = new ArrayList<>();
        player.level().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).listElementIds()
                .forEach(key -> ids.add(key.identifier().toString()));
        ids.sort(String::compareTo);
        return ids;
    }

    private void renderPatterns(Canvas canvas) {
        List<String> ids = patternIds();
        int pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        canvas.set(4, Icon.of(Items.LOOM).name(GuiKeys.FLAG_EDITOR_PATTERNS.text(), Icon.TITLE)
                .line(GuiKeys.FLAG_EDITOR_PAGE.text(page + 1), Icon.INFO).build());
        for (int i = 0; i < PAGE_SIZE; i++) {
            int at = page * PAGE_SIZE + i;
            if (at >= ids.size()) {
                break;
            }
            String id = ids.get(at);
            // Vorschau: das Muster in Schwarz auf der gewählten Grundfarbe.
            ItemStack preview = banner(new Flag(base, List.of(new Flag.Layer(id, "black"))));
            int slot = 9 + i / 7 * 9 + i % 7 + (i % 7 >= 0 ? 1 : 0);
            canvas.set(slot, Icon.of(preview).name(Component.literal(id.substring(id.indexOf(':') + 1)))
                    .build(), click -> {
                Feedback.click(click.player());
                pattern = id;
                mode = Mode.COLOR;
            });
        }
        if (page > 0) {
            canvas.set(48, Icon.of(Items.ARROW).name(Component.literal("<")).build(), click -> page--);
        }
        if (page < pages - 1) {
            canvas.set(50, Icon.of(Items.ARROW).name(Component.literal(">")).build(), click -> page++);
        }
        canvas.set(49, Icon.of(Items.BARRIER).name(GuiKeys.BACK.text(), Icon.BAD).build(), click -> mode = Mode.MAIN);
    }

    private void renderColors(Canvas canvas) {
        canvas.set(4, Icon.of(banner(new Flag(base, List.of(new Flag.Layer(pattern, "black")))))
                .name(GuiKeys.FLAG_EDITOR_COLOR.text(), Icon.TITLE).build());
        int index = 0;
        for (DyeColor color : DyeColor.values()) {
            String name = color.getSerializedName();
            ItemStack preview = banner(new Flag(base, List.of(new Flag.Layer(pattern, name))));
            canvas.set(COLOR_SLOTS[index++], Icon.of(preview).name(Component.translatable("color.minecraft." + name))
                    .build(), click -> {
                Feedback.click(click.player());
                layers.add(new Flag.Layer(pattern, name));
                mode = Mode.MAIN;
            });
        }
        canvas.set(49, Icon.of(Items.BARRIER).name(GuiKeys.BACK.text(), Icon.BAD).build(), click -> mode = Mode.PATTERN);
    }
}
