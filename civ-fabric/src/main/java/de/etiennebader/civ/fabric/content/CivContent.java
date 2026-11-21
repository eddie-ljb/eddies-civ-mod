package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.server.civil.CivilService.SignType;

import de.etiennebader.civ.EddiesCivMod;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Blöcke und Items des Mods. Wird einmal beim Start registriert.
 */
public final class CivContent {

    public static final Identifier TOWN_HALL_ID = EddiesCivMod.id("town_hall");
    public static final Identifier BANK_ID = EddiesCivMod.id("bank");
    public static final Identifier MARKET_ID = EddiesCivMod.id("market");
    public static final Identifier PLOT_WAND_ID = EddiesCivMod.id("plot_wand");
    public static final Identifier CONSTITUTION_ID = EddiesCivMod.id("constitution");
    public static final Identifier CONSTITUTION_COPY_ID = EddiesCivMod.id("constitution_copy");
    public static final Identifier BALLOT_BOX_ID = EddiesCivMod.id("ballot_box");
    public static final Identifier ELECTION_POSTER_ID = EddiesCivMod.id("election_poster");
    public static final Identifier MAILBOX_ID = EddiesCivMod.id("mailbox");
    public static final Identifier NEWSSTAND_ID = EddiesCivMod.id("newsstand");
    public static final Identifier STOCK_EXCHANGE_ID = EddiesCivMod.id("stock_exchange");
    public static final Identifier COURT_BENCH_ID = EddiesCivMod.id("court_bench");
    public static final Identifier LAW_BOOK_ID = EddiesCivMod.id("law_book");
    public static final Identifier STREET_SIGN_ID = EddiesCivMod.id("street_sign");
    public static final Identifier HOUSE_NUMBER_ID = EddiesCivMod.id("house_number");
    public static final Identifier TOWN_SIGN_ID = EddiesCivMod.id("town_sign");
    public static final Identifier COUNTRY_SIGN_ID = EddiesCivMod.id("country_sign");
    public static final Identifier NATION_SIGN_ID = EddiesCivMod.id("nation_sign");
    public static final Identifier LABEL_BLOCK_ENTITY_ID = EddiesCivMod.id("label");
    public static final Identifier TAB_ID = EddiesCivMod.id("civ");

    /** Hausnummern und Straßenschilder tragen eine Zeile, Orts-/Länder-/Nationenschilder bis zu drei. */
    private static final int SINGLE_LINE = 1;
    private static final int MULTI_LINE = 3;

    /** Wie Grundgestein: im Überlebensmodus nicht abbaubar, gegen Explosionen fest. */
    private static final float UNBREAKABLE = -1.0F;
    private static final float BLAST_PROOF = 3_600_000.0F;

    private final Block townHall;
    private final Item townHallItem;
    private final Block bank;
    private final Block market;
    private final Item plotWand;
    private final Block constitution;
    private final Item constitutionCopy;
    private final Block ballotBox;
    private final Block electionPoster;
    private final Block mailbox;
    private final Block newsstand;
    private final Block stockExchange;
    private final Block courtBench;
    private final Block streetSign;
    private final Block houseNumber;
    private final Block townSign;
    private final Block countrySign;
    private final Block nationSign;

    private CivContent(Block townHall, Item townHallItem, Block bank, Block market, Item plotWand, Block constitution,
                       Item constitutionCopy, Block ballotBox, Block electionPoster, Block mailbox, Block newsstand,
                       Block stockExchange, Block courtBench, Block streetSign, Block houseNumber, Block townSign,
                       Block countrySign, Block nationSign) {
        this.courtBench = courtBench;
        this.mailbox = mailbox;
        this.newsstand = newsstand;
        this.stockExchange = stockExchange;
        this.ballotBox = ballotBox;
        this.electionPoster = electionPoster;
        this.townHall = townHall;
        this.townHallItem = townHallItem;
        this.bank = bank;
        this.market = market;
        this.plotWand = plotWand;
        this.constitution = constitution;
        this.constitutionCopy = constitutionCopy;
        this.streetSign = streetSign;
        this.houseNumber = houseNumber;
        this.townSign = townSign;
        this.countrySign = countrySign;
        this.nationSign = nationSign;
    }

    public static CivContent register(TownHallBlock.PlacementHandler townHallPlacement) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, TOWN_HALL_ID);
        Block townHall = Registry.register(BuiltInRegistries.BLOCK, blockKey, new TownHallBlock(
                BlockBehaviour.Properties.of()
                        .setId(blockKey)
                        .mapColor(MapColor.STONE)
                        .strength(UNBREAKABLE, BLAST_PROOF)
                        .sound(SoundType.STONE)
                        .noLootTable(),
                townHallPlacement));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, TOWN_HALL_ID);
        Item townHallItem = Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(townHall, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

        Block bank = simpleBlock(BANK_ID, MapColor.METAL, SoundType.METAL, 5.0F);
        Block market = simpleBlock(MARKET_ID, MapColor.WOOD, SoundType.WOOD, 2.5F);
        Item bankItem = blockItem(BANK_ID, bank);
        Item marketItem = blockItem(MARKET_ID, market);

        ResourceKey<Item> wandKey = ResourceKey.create(Registries.ITEM, PLOT_WAND_ID);
        Item plotWand = Registry.register(BuiltInRegistries.ITEM, wandKey,
                new Item(new Item.Properties().setId(wandKey).stacksTo(1).rarity(Rarity.UNCOMMON)));

        Block constitution = simpleBlock(CONSTITUTION_ID, MapColor.WOOD, SoundType.WOOD, 2.5F);
        Item constitutionItem = blockItem(CONSTITUTION_ID, constitution);
        ResourceKey<Item> copyKey = ResourceKey.create(Registries.ITEM, CONSTITUTION_COPY_ID);
        Item constitutionCopy = Registry.register(BuiltInRegistries.ITEM, copyKey,
                new Item(new Item.Properties().setId(copyKey).stacksTo(1).rarity(Rarity.UNCOMMON)));
        ConstitutionCopies.init(constitutionCopy);
        ResourceKey<Item> lawBookKey = ResourceKey.create(Registries.ITEM, LAW_BOOK_ID);
        Item lawBook = Registry.register(BuiltInRegistries.ITEM, lawBookKey,
                new Item(new Item.Properties().setId(lawBookKey).stacksTo(1).rarity(Rarity.UNCOMMON)));
        LawBooks.init(lawBook);

        Block ballotBox = simpleBlock(BALLOT_BOX_ID, MapColor.WOOD, SoundType.WOOD, 2.0F);
        Item ballotBoxItem = blockItem(BALLOT_BOX_ID, ballotBox);
        Block electionPoster = simpleBlock(ELECTION_POSTER_ID, MapColor.COLOR_GREEN, SoundType.WOOD, 1.0F);
        Item electionPosterItem = blockItem(ELECTION_POSTER_ID, electionPoster);
        Block mailbox = simpleBlock(MAILBOX_ID, MapColor.COLOR_YELLOW, SoundType.METAL, 2.0F);
        Item mailboxItem = blockItem(MAILBOX_ID, mailbox);
        Block newsstand = simpleBlock(NEWSSTAND_ID, MapColor.WOOD, SoundType.WOOD, 2.0F);
        Item newsstandItem = blockItem(NEWSSTAND_ID, newsstand);
        Block stockExchange = simpleBlock(STOCK_EXCHANGE_ID, MapColor.METAL, SoundType.METAL, 3.0F);
        Item stockExchangeItem = blockItem(STOCK_EXCHANGE_ID, stockExchange);
        Block courtBench = simpleBlock(COURT_BENCH_ID, MapColor.COLOR_BROWN, SoundType.WOOD, 2.5F);
        Item courtBenchItem = blockItem(COURT_BENCH_ID, courtBench);

        // Straßenschilder, Hausnummern und Orts-/Länder-/Nationenschilder teilen sich LabelBlock/-BlockEntity.
        // Der BlockEntityType braucht beim Bauen bereits die fertigen Blöcke, die Blöcke selbst aber (über
        // LabelBlock#newBlockEntity) erst den fertigen Typ -- deshalb zuerst ein leerer Holder, der nach dem
        // Registrieren des Typs befüllt wird; die Blöcke lesen ihn erst beim tatsächlichen Platzieren aus.
        @SuppressWarnings("unchecked")
        BlockEntityType<LabelBlockEntity>[] labelTypeHolder = new BlockEntityType[1];
        java.util.function.Supplier<BlockEntityType<LabelBlockEntity>> labelType = () -> labelTypeHolder[0];

        Block streetSign = labelBlock(STREET_SIGN_ID, MapColor.WOOD, SoundType.WOOD, 1.0F, labelType, SINGLE_LINE, SignType.STREET);
        Item streetSignItem = blockItem(STREET_SIGN_ID, streetSign);
        Block houseNumber = labelBlock(HOUSE_NUMBER_ID, MapColor.METAL, SoundType.METAL, 1.0F, labelType, SINGLE_LINE, SignType.HOUSE);
        Item houseNumberItem = blockItem(HOUSE_NUMBER_ID, houseNumber);
        Block townSign = labelBlock(TOWN_SIGN_ID, MapColor.WOOD, SoundType.WOOD, 1.5F, labelType, MULTI_LINE, SignType.TOWN);
        Item townSignItem = blockItem(TOWN_SIGN_ID, townSign);
        Block countrySign = labelBlock(COUNTRY_SIGN_ID, MapColor.WOOD, SoundType.WOOD, 1.5F, labelType, MULTI_LINE, SignType.COUNTRY);
        Item countrySignItem = blockItem(COUNTRY_SIGN_ID, countrySign);
        Block nationSign = labelBlock(NATION_SIGN_ID, MapColor.WOOD, SoundType.WOOD, 1.5F, labelType, MULTI_LINE, SignType.NATION);
        Item nationSignItem = blockItem(NATION_SIGN_ID, nationSign);

        ResourceKey<BlockEntityType<?>> entityTypeKey = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, LABEL_BLOCK_ENTITY_ID);
        labelTypeHolder[0] = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, entityTypeKey,
                new BlockEntityType<>((pos, state) -> new LabelBlockEntity(labelTypeHolder[0], pos, state),
                        java.util.Set.of(streetSign, houseNumber, townSign, countrySign, nationSign)));

        registerTab(townHallItem, bankItem, marketItem, constitutionItem, lawBook, plotWand, ballotBoxItem,
                electionPosterItem, mailboxItem, newsstandItem, stockExchangeItem, courtBenchItem, streetSignItem,
                houseNumberItem, townSignItem, countrySignItem, nationSignItem);
        return new CivContent(townHall, townHallItem, bank, market, plotWand, constitution, constitutionCopy, ballotBox,
                electionPoster, mailbox, newsstand, stockExchange, courtBench, streetSign, houseNumber, townSign,
                countrySign, nationSign);
    }

    /**
     * Eigener Reiter im Kreativinventar: zuerst die Blöcke und Werkzeuge des Mods, danach Vanilla-Items,
     * die man zum Ausprobieren braucht (Geld, Ladentruhe, Dorfbewohner, Dinge für den Gebietsschutz).
     */
    private static void registerTab(Item townHall, Item bank, Item market, Item constitution, Item lawBook,
                                    Item plotWand, Item ballotBox, Item electionPoster, Item mailbox,
                                    Item newsstand, Item stockExchange, Item courtBench, Item streetSign,
                                    Item houseNumber, Item townSign, Item countrySign, Item nationSign) {
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, TAB_ID);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + EddiesCivMod.MOD_ID + ".civ"))
                .icon(() -> new ItemStack(townHall))
                .displayItems((parameters, output) -> {
                    output.accept(townHall);
                    output.accept(bank);
                    output.accept(market);
                    output.accept(constitution);
                    output.accept(lawBook);
                    output.accept(ballotBox);
                    output.accept(electionPoster);
                    output.accept(mailbox);
                    output.accept(newsstand);
                    output.accept(stockExchange);
                    output.accept(courtBench);
                    output.accept(streetSign);
                    output.accept(houseNumber);
                    output.accept(townSign);
                    output.accept(countrySign);
                    output.accept(nationSign);
                    output.accept(Items.WRITABLE_BOOK);
                    output.accept(plotWand);
                    output.accept(Items.EMERALD);
                    output.accept(Items.EMERALD_BLOCK);
                    output.accept(Items.CHEST);
                    output.accept(Items.VILLAGER_SPAWN_EGG);
                    output.accept(Items.CREEPER_SPAWN_EGG);
                    output.accept(Items.ENDERMAN_SPAWN_EGG);
                    output.accept(Items.TNT);
                    output.accept(Items.FLINT_AND_STEEL);
                    output.accept(Items.LAVA_BUCKET);
                    output.accept(Items.PISTON);
                    output.accept(Items.HOPPER);
                })
                .build());
    }

    /** Ein Block ohne eigenes Verhalten; Rechtsklicks fängt {@code ProtectionListener} ab und öffnet das Menü. */
    private static Block simpleBlock(Identifier id, MapColor color, SoundType sound, float hardness) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of()
                .setId(key)
                .mapColor(color)
                .strength(hardness, 6.0F)
                .sound(sound)));
    }

    /** Straßenschild/Hausnummer/Orts-/Länder-/Nationenschild: trägt den im Amboss vergebenen Text. */
    private static Block labelBlock(Identifier id, MapColor color, SoundType sound, float hardness,
                                     java.util.function.Supplier<BlockEntityType<LabelBlockEntity>> entityType,
                                     int maxLines, SignType signType) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, key, new LabelBlock(BlockBehaviour.Properties.of()
                .setId(key)
                .mapColor(color)
                .strength(hardness, 6.0F)
                .noOcclusion()
                .sound(sound), entityType, maxLines, signType));
    }

    private static Item blockItem(Identifier id, Block block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key,
                new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }

    public Block townHall() {
        return townHall;
    }

    public Item townHallItem() {
        return townHallItem;
    }

    public Block bank() {
        return bank;
    }

    public Block market() {
        return market;
    }

    /** Zeigt die Verfassung der Gemeinde, in deren Gebiet er steht. */
    public Block constitution() {
        return constitution;
    }

    /** Abschrift einer Verfassung; Rechtsklick öffnet sie. */
    public Item constitutionCopy() {
        return constitutionCopy;
    }

    /** Wahlurne: Rechtsklick öffnet die Stimmzettel aller laufenden Wahlen am Ort. */
    public Block ballotBox() {
        return ballotBox;
    }

    /** Wahlplakat (Litfaßsäule): wirbt für den Aufsteller oder seine Partei. */
    public Block electionPoster() {
        return electionPoster;
    }

    public Block mailbox() {
        return mailbox;
    }

    public Block newsstand() {
        return newsstand;
    }

    public Block stockExchange() {
        return stockExchange;
    }

    /** Richterbank: Rechtsklick öffnet das Gericht der Gemeinde, in der sie steht. */
    public Block courtBench() {
        return courtBench;
    }

    /** Werkzeug für Grundstücke: Linksklick setzt Ecke 1, Rechtsklick Ecke 2. */
    public Item plotWand() {
        return plotWand;
    }

    /** Straßenschild: einzeiliger, im Amboss vergebener Text. */
    public Block streetSign() {
        return streetSign;
    }

    /** Hausnummer: einzeiliger, im Amboss vergebener Text. */
    public Block houseNumber() {
        return houseNumber;
    }

    /** Ortsschild: bis zu drei Zeilen, im Amboss mit {@code |} getrennt. */
    public Block townSign() {
        return townSign;
    }

    /** Länderschild: bis zu drei Zeilen, im Amboss mit {@code |} getrennt. */
    public Block countrySign() {
        return countrySign;
    }

    /** Nationenschild: bis zu drei Zeilen, im Amboss mit {@code |} getrennt. */
    public Block nationSign() {
        return nationSign;
    }
}
