package de.etiennebader.civ.application.query;

import de.etiennebader.civ.domain.justice.JudiciaryView;
import de.etiennebader.civ.domain.production.ProductionView;
import de.etiennebader.civ.domain.civic.CivicView;
import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.federation.Federations;
import de.etiennebader.civ.application.law.EffectiveLaws;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.community.CommunityView;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.contract.ContractRegistryView;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.exchange.StockExchangeView;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.market.MarketView;
import de.etiennebader.civ.domain.market.PriceLevel;
import de.etiennebader.civ.domain.media.PressView;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.protection.Denial;
import de.etiennebader.civ.domain.protection.Hazard;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.protection.ProtectionContext;
import de.etiennebader.civ.domain.protection.ProtectionRules;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shop.ShopRegistryView;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.TerritoryView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lesender Zugriff auf den gesamten Zustand.
 */
public final class WorldQueries {

    private static final ProtectionRules PROTECTION_RULES = new ProtectionRules();

    private final Repositories repositories;
    private final ProtectionContext protectionContext = new ProtectionContext() {
        @Override
        public TerritoryView territory() {
            return WorldQueries.this.territory();
        }

        @Override
        public Optional<? extends PolityView> polity(PolityId id) {
            return WorldQueries.this.polity(id);
        }

        @Override
        public Optional<? extends LandView> land(PolityId polity) {
            return repositories.land().findByPolity(polity);
        }

        @Override
        public LawView laws(PolityView polity) {
            return effectiveLaws(polity);
        }

        @Override
        public boolean mayUsePlot(ActorRef actor, PolityId polity, PlotId plot) {
            return isTenant(actor, polity, plot) || isTrustedFriend(actor, polity, plot);
        }

        @Override
        public boolean isAlly(ActorRef actor, PolityView polity) {
            return WorldQueries.this.isAlly(actor, polity);
        }
    };

    /** Alle Verträge. */
    public ContractRegistryView contracts() {
        return repositories.contracts().load();
    }

    /** Aktien, Orders und Kurse. */
    public StockExchangeView exchange() {
        return repositories.exchange().load();
    }

    /** Alle Zeitungsartikel. */
    public CivicView civic() {
        return repositories.civic().load();
    }

    public ProductionView production() {
        return repositories.production().load();
    }

    public JudiciaryView justice() {
        return repositories.justice().load();
    }

    public PressView press() {
        return repositories.press().load();
    }

    /** Alle Firmen, in Gründungsreihenfolge. */
    public List<PolityView> companies() {
        return polities().stream().filter(polity -> polity.kind() == PolityKind.COMPANY).toList();
    }

    /** Alle Zeitungen, in Gründungsreihenfolge. */
    public List<PolityView> newspapers() {
        return polities().stream().filter(polity -> polity.kind() == PolityKind.NEWSPAPER).toList();
    }

    /** Briefe, Freundschaften und Chat. */
    public CommunityView community() {
        return repositories.community().load();
    }

    /** Ob der Akteur ein Grundstück per geltendem Mietvertrag nutzt. */
    public boolean isTenant(ActorRef actor, PolityId polity, PlotId plot) {
        Contract.PlotRef ref = new Contract.PlotRef(polity, plot);
        return contracts().active().stream()
                .anyMatch(contract -> contract.kind() == ContractKind.LEASE && contract.payer().equals(actor)
                        && contract.plot().filter(ref::equals).isPresent());
    }

    /**
     * Ob der Akteur als Freund auf diesem Grundstück bauen darf: Inhaber oder Mieter ist ein Spieler, der ihm mit
     * {@code /civ freund vertrauen} Baurecht gegeben hat.
     */
    public boolean isTrustedFriend(ActorRef actor, PolityId polity, PlotId plot) {
        if (!(actor instanceof PlayerRef friend)) {
            return false;
        }
        CommunityView community = community();
        List<ActorRef> users = new ArrayList<>();
        repositories.land().findByPolity(polity).flatMap(land -> land.plot(plot)).ifPresent(found ->
                users.add(found.holder()));
        leaseOf(polity, plot).ifPresent(lease -> users.add(lease.payer()));
        return users.stream().anyMatch(user -> user instanceof PlayerRef owner && community.trusts(owner, friend));
    }

    /** Der geltende Mietvertrag eines Grundstücks. */
    public Optional<Contract> leaseOf(PolityId polity, PlotId plot) {
        Contract.PlotRef ref = new Contract.PlotRef(polity, plot);
        return contracts().active().stream()
                .filter(contract -> contract.kind() == ContractKind.LEASE && contract.plot().filter(ref::equals).isPresent())
                .findFirst();
    }

    /**
     * Ob der Akteur in dieser Polity über ein Bündnis nicht als Fremder gilt: Er gehört einer Gemeinde oder einem Bund
     * an, der mit ihr oder einem ihrer Bünde verbündet ist.
     */
    public boolean isAlly(ActorRef actor, PolityView polity) {
        List<PolityId> protectedSide = new ArrayList<>();
        protectedSide.add(polity.id());
        ancestorsOf(polity.id()).forEach(ancestor -> protectedSide.add(ancestor.id()));
        List<Contract> alliances = contracts().active().stream()
                .filter(contract -> contract.kind() == ContractKind.ALLIANCE)
                .toList();
        if (alliances.isEmpty()) {
            return false;
        }
        Optional<PolityView> home = municipalityOf(actor);
        if (home.isEmpty()) {
            return false;
        }
        List<PolityId> actorSide = new ArrayList<>();
        actorSide.add(home.get().id());
        ancestorsOf(home.get().id()).forEach(ancestor -> actorSide.add(ancestor.id()));
        for (Contract alliance : alliances) {
            if (alliance.offeror() instanceof PolityRef one && alliance.counterparty() instanceof PolityRef other) {
                if (protectedSide.contains(one.id()) && actorSide.contains(other.id())
                        || protectedSide.contains(other.id()) && actorSide.contains(one.id())) {
                    return true;
                }
            }
        }
        return false;
    }

    public WorldQueries(Repositories repositories) {
        this.repositories = Objects.requireNonNull(repositories, "repositories");
    }

    /** Alle Polities in der Reihenfolge des Repositorys. */
    public List<PolityView> polities() {
        return List.copyOf(repositories.polities().findAll());
    }

    public Optional<PolityView> polity(PolityId id) {
        return repositories.polities().findById(id).map(PolityView.class::cast);
    }

    /** Der Bund, dem die Polity direkt angehört. */
    public Optional<PolityView> parentOf(PolityId id) {
        return Federations.parentOf(repositories.polities().findAll(), id).map(PolityView.class::cast);
    }

    /** Alle Bünde über der Polity, vom nächsten bis zum obersten. */
    public List<PolityView> ancestorsOf(PolityId id) {
        return Federations.ancestorsOf(repositories.polities().findAll(), id).stream()
                .map(PolityView.class::cast)
                .toList();
    }

    /** Das Recht, das in der Polity tatsächlich gilt: eigenes Recht, wo nötig von Bundesrecht verdrängt. */
    public EffectiveLaws effectiveLaws(PolityView polity) {
        Objects.requireNonNull(polity, "polity");
        return new EffectiveLaws(polity, ancestorsOf(polity.id()));
    }

    /** Die Partei, der ein Akteur angehört. */
    public Optional<PolityView> partyOf(ActorRef actor) {
        Objects.requireNonNull(actor, "actor");
        return repositories.polities().findAll().stream()
                .filter(polity -> polity.kind() == PolityKind.PARTY && polity.isMember(actor))
                .map(PolityView.class::cast)
                .findFirst();
    }

    /** Eine Partei darf im festgelegten Gebiet und allen ihm untergeordneten Gebietskörperschaften antreten. */
    public boolean partyEligibleIn(PolityView party, PolityId electionPolity) {
        return party.partyProfile().flatMap(profile -> profile.area())
                .map(area -> area.equals(electionPolity)
                        || ancestorsOf(electionPolity).stream().anyMatch(ancestor -> ancestor.id().equals(area)))
                .orElse(true);
    }

    public Optional<PolityView> electoralPartyOf(ActorRef actor, PolityId electionPolity) {
        return partyOf(actor).filter(party -> partyEligibleIn(party, electionPolity));
    }

    /** Alle Parteien, in Reihenfolge ihrer Gründung. */
    public List<PolityView> parties() {
        return repositories.polities().findAll().stream()
                .filter(polity -> polity.kind() == PolityKind.PARTY)
                .map(PolityView.class::cast)
                .toList();
    }

    /** Alle Bünde, in Reihenfolge ihrer Gründung. */
    public List<PolityView> federations() {
        return repositories.polities().findAll().stream()
                .filter(polity -> polity.kind() == PolityKind.FEDERATION)
                .map(PolityView.class::cast)
                .toList();
    }

    /** Sucht eine Polity nach ihrem Namen, ohne Groß- und Kleinschreibung zu beachten. */
    public Optional<PolityView> polityNamed(String name) {
        Objects.requireNonNull(name, "name");
        String wanted = name.strip();
        return repositories.polities().findAll().stream()
                .filter(polity -> polity.name().value().equalsIgnoreCase(wanted))
                .map(PolityView.class::cast)
                .findFirst();
    }

    /** Die Gemeinde, der ein Akteur angehört, falls es eine gibt. */
    public Optional<PolityView> municipalityOf(ActorRef actor) {
        Objects.requireNonNull(actor, "actor");
        return repositories.polities().findAll().stream()
                .filter(polity -> polity.kind() == PolityKind.TERRITORIAL && polity.isMember(actor))
                .map(PolityView.class::cast)
                .findFirst();
    }

    /** Die Gemeinde, deren Gemeindehaus an dieser Stelle steht. */
    public Optional<PolityView> polityWithSeat(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        return repositories.polities().findAll().stream()
                .filter(polity -> polity.seat().filter(position::equals).isPresent())
                .map(PolityView.class::cast)
                .findFirst();
    }

    /**
     * Ob ein Akteur an einem Block handeln darf (Gebietsschutz).
     *
     * @return die Verweigerung samt Grund; leer, wenn die Handlung erlaubt ist
     */
    public Optional<Denial> access(ActorRef actor, ProtectedAction action, BlockPosition position) {
        return PROTECTION_RULES.check(actor, action, position, protectionContext);
    }

    /** Ob eine Naturgewalt wie eine Explosion an diesem Block wirken darf. */
    public boolean allows(Hazard hazard, BlockPosition position) {
        return PROTECTION_RULES.allows(hazard, position, protectionContext);
    }

    public TerritoryView territory() {
        return repositories.territory().load();
    }

    public ShopRegistryView shops() {
        return repositories.shops().load();
    }

    public MarketView market() {
        return repositories.market().load();
    }

    /**
     * Das Preisniveau aus Geldmenge und Bevölkerung.
     *
     * @param population Zahl der Wirtschaftsteilnehmer, z. B. bekannte Spieler
     * @param reference  Geldmenge je Kopf, bei der Preise ihrem Grundpreis entsprechen
     */
    public PriceLevel priceLevel(int population, Money reference) {
        return PriceLevel.of(ledger().totalSupply(reference.currency()), population, reference);
    }

    /**
     * Wie {@link #priceLevel(int, Money)}, aber mit einstellbaren Preisfaktor-Grenzen und -Dämpfung
     * aus der Server-Konfiguration.
     */
    public PriceLevel priceLevel(int population, Money reference, double minFactor, double maxFactor, double damping) {
        return PriceLevel.of(ledger().totalSupply(reference.currency()), population, reference, minFactor, maxFactor,
                damping);
    }

    /**
     * @throws IllegalArgumentException wenn es für die Polity kein Grundbuch gibt
     */
    public LandView land(PolityId polity) {
        return repositories.land().findByPolity(polity)
                .orElseThrow(() -> new IllegalArgumentException("Kein Grundbuch für " + polity));
    }

    public LedgerView ledger() {
        return repositories.ledger().load();
    }
}
