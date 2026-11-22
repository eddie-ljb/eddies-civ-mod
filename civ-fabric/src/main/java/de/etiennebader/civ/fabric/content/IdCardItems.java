package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.application.civil.IdCards;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.population.Gender;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Personalausweise (#20): ein Item mit Name, Anschrift und Staatsbürgerschaft einer Person.
 *
 * <p>Der Ausweis ist eine Momentaufnahme; ob die Angaben noch stimmen, prüft {@code /civ ausweis pruefen}
 * gegen das Register. Weitergabe ist möglich (der Ausweis weist nur den eingetragenen Inhaber aus).
 */
public final class IdCardItems {

    private static final String HOLDER_KEY = "eddies_civ_id_holder";
    private static final String VILLAGER_KEY = "eddies_civ_id_villager";
    private static final String NAME_KEY = "eddies_civ_id_name";
    private static final String GENDER_KEY = "eddies_civ_id_gender";
    private static final String ADDRESS_KEY = "eddies_civ_id_address";
    private static final String CITIZEN_KEY = "eddies_civ_id_citizen";
    private static final String DAY_KEY = "eddies_civ_id_day";

    /** Inhaber und Angaben eines gelesenen Ausweises. */
    public record Card(ActorRef holder, IdCards.Data data) {
    }

    private static Item item;

    private IdCardItems() {
    }

    static void init(Item idCard) {
        item = Objects.requireNonNull(idCard, "idCard");
    }

    public static Item item() {
        return Objects.requireNonNull(item, "Ausweis ist noch nicht registriert");
    }

    public static boolean isIdCard(ItemStack stack) {
        return item != null && stack.is(item);
    }

    public static ItemStack create(ActorRef holder, IdCards.Data data) {
        ItemStack stack = new ItemStack(item());
        CompoundTag tag = new CompoundTag();
        tag.putString(HOLDER_KEY, holderId(holder).toString());
        tag.putBoolean(VILLAGER_KEY, holder instanceof PersonRef);
        tag.putString(NAME_KEY, data.holderName());
        data.gender().ifPresent(value -> tag.putString(GENDER_KEY, value.name()));
        data.address().ifPresent(value -> tag.putString(ADDRESS_KEY, value));
        data.citizenOf().ifPresent(value -> tag.putString(CITIZEN_KEY, value));
        tag.putLong(DAY_KEY, data.issuedOn().value());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Components.of(GuiKeys.ID_CARD_NAME.text(data.holderName()))
                .withStyle(style -> style.withItalic(false)));

        List<Component> lore = new ArrayList<>();
        lore.add(Components.plain(GuiKeys.ID_CARD_NAME_LINE.text(data.holderName()), ChatFormatting.GRAY));
        data.gender().ifPresent(gender -> lore.add(Components.plain(
                Message.of(MessageKey.ID_CARD_GENDER, Message.of(switch (gender) {
                    case FEMALE -> MessageKey.GENDER_FEMALE;
                    case MALE -> MessageKey.GENDER_MALE;
                    case DIVERSE -> MessageKey.GENDER_DIVERSE;
                })), ChatFormatting.GRAY)));
        lore.add(Components.plain(data.address().map(line -> Message.of(MessageKey.ID_CARD_ADDRESS, line))
                .orElseGet(() -> Message.of(MessageKey.ID_CARD_ADDRESS_NONE)), ChatFormatting.GRAY));
        lore.add(Components.plain(data.citizenOf().map(town -> Message.of(MessageKey.ID_CARD_CITIZEN_OF, town))
                .orElseGet(() -> Message.of(MessageKey.ID_CARD_CITIZEN_NONE)), ChatFormatting.GRAY));
        lore.add(Components.plain(GuiKeys.ID_CARD_ISSUED_LINE.text(data.issuedOn().value()), ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    /** Liest einen Ausweis; leer bei fremden oder beschädigten Daten. */
    public static Optional<Card> read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (!isIdCard(stack) || data == null) {
            return Optional.empty();
        }
        try {
            CompoundTag tag = data.copyTag();
            UUID id = UUID.fromString(tag.getString(HOLDER_KEY).orElseThrow());
            boolean villager = tag.getBoolean(VILLAGER_KEY).orElse(false);
            ActorRef holder = villager ? new PersonRef(new PersonId(id)) : new PlayerRef(new PlayerId(id));
            IdCards.Data card = new IdCards.Data(tag.getString(NAME_KEY).orElseThrow(),
                    tag.getString(GENDER_KEY).map(Gender::valueOf),
                    tag.getString(ADDRESS_KEY), tag.getString(CITIZEN_KEY),
                    new Day(tag.getLong(DAY_KEY).orElseThrow()));
            return Optional.of(new Card(holder, card));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static UUID holderId(ActorRef holder) {
        return switch (holder) {
            case PlayerRef player -> player.id().value();
            case PersonRef person -> person.id().value();
            default -> throw new IllegalArgumentException("Kein Ausweis für " + holder);
        };
    }
}
