package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.domain.civic.Anthem;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.UUID;

/**
 * Hymnen-Schallplatte: eine gewöhnliche Schallplatte, die über ihre Zusatzdaten auf die Hymne einer Polity
 * verweist. Sie trägt nur die Kennung der Polity; welche Hymne (und welche Aufnahme) beim Abspielen gilt, entscheidet
 * der Stand der Polity zu diesem Zeitpunkt, die Platte veraltet also nie.
 */
public final class AnthemRecords {

    private static final String POLITY_KEY = "civ_anthem_polity";

    private AnthemRecords() {
    }

    public static ItemStack create(PolityId polity, String polityName, Anthem anthem) {
        ItemStack stack = new ItemStack(Items.MUSIC_DISC_CAT);
        CompoundTag tag = new CompoundTag();
        tag.putString(POLITY_KEY, polity.value().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,
                Components.of(Message.of(MessageKey.ANTHEM_RECORD_NAME, polityName + ": " + anthem.title())));
        return stack;
    }

    /** Die Polity, auf die die Platte verweist; leer bei jeder anderen Schallplatte. */
    public static Optional<PolityId> polityOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (!stack.is(Items.MUSIC_DISC_CAT) || data == null) {
            return Optional.empty();
        }
        try {
            return data.copyTag().getString(POLITY_KEY).map(UUID::fromString).map(PolityId::new);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
