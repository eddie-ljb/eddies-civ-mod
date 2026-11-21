package de.etiennebader.civ.application;

import de.etiennebader.civ.application.justice.JusticeUseCases;
import de.etiennebader.civ.application.civic.CivicUseCases;
import de.etiennebader.civ.application.civil.Addresses;
import de.etiennebader.civ.application.civil.CommunityUseCases;
import de.etiennebader.civ.application.civil.ContractUseCases;
import de.etiennebader.civ.application.civil.RunContracts;
import de.etiennebader.civ.application.civil.StreetUseCases;
import de.etiennebader.civ.application.company.CompanyUseCases;
import de.etiennebader.civ.application.company.ProductionUseCases;
import de.etiennebader.civ.application.company.Trading;
import de.etiennebader.civ.application.company.VillagerInvestors;
import de.etiennebader.civ.application.constitution.ChangePreamble;
import de.etiennebader.civ.application.constitution.ProclaimConstitution;
import de.etiennebader.civ.application.day.EndOfDay;
import de.etiennebader.civ.application.economy.CreditTownIncome;
import de.etiennebader.civ.application.economy.DepositCash;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.economy.WithdrawCash;
import de.etiennebader.civ.application.economy.WithdrawEmeralds;
import de.etiennebader.civ.application.federation.FoundFederation;
import de.etiennebader.civ.application.federation.JoinFederation;
import de.etiennebader.civ.application.federation.LeaveFederation;
import de.etiennebader.civ.application.land.AssignPlot;
import de.etiennebader.civ.application.land.BuyPlot;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.land.DissolvePlot;
import de.etiennebader.civ.application.land.OfferPlot;
import de.etiennebader.civ.application.land.SellPlot;
import de.etiennebader.civ.application.law.EnactParagraph;
import de.etiennebader.civ.application.law.RepealParagraph;
import de.etiennebader.civ.application.market.BuyFromMarket;
import de.etiennebader.civ.application.market.SellToMarket;
import de.etiennebader.civ.application.media.PressInfluence;
import de.etiennebader.civ.application.media.PressUseCases;
import de.etiennebader.civ.application.office.AppointOfficer;
import de.etiennebader.civ.application.office.CallElection;
import de.etiennebader.civ.application.office.CastBallot;
import de.etiennebader.civ.application.office.MakePromise;
import de.etiennebader.civ.application.office.PutUpPoster;
import de.etiennebader.civ.application.office.RunElections;
import de.etiennebader.civ.application.office.StandForElection;
import de.etiennebader.civ.application.office.VillagerVoter;
import de.etiennebader.civ.application.party.ChangePartyProfile;
import de.etiennebader.civ.application.party.FoundParty;
import de.etiennebader.civ.application.party.JoinParty;
import de.etiennebader.civ.application.party.LeaveParty;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.CastVote;
import de.etiennebader.civ.application.polity.ChangeTier;
import de.etiennebader.civ.application.polity.DissolvePolity;
import de.etiennebader.civ.application.polity.EnactStatute;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.application.polity.MoveSeat;
import de.etiennebader.civ.application.polity.ParliamentSession;
import de.etiennebader.civ.application.polity.ProposeLawChange;
import de.etiennebader.civ.application.polity.ProposeMotion;
import de.etiennebader.civ.application.polity.RepealStatute;
import de.etiennebader.civ.application.polity.TallyProposals;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.application.population.Campaign;
import de.etiennebader.civ.application.population.PersonalityLegislator;
import de.etiennebader.civ.application.population.PersonalityVoter;
import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.shop.BuyFromShop;
import de.etiennebader.civ.application.shop.CloseShop;
import de.etiennebader.civ.application.shop.OpenShop;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.application.support.ClaimWeighting;
import de.etiennebader.civ.application.territory.ClaimChunk;
import de.etiennebader.civ.application.territory.ReleaseChunk;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ClaimPolicy;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Einstiegspunkt der Application-Schicht: verdrahtet alle Use Cases mit Repositories,
 * ID-Erzeugung, Claim-Regeln und Event-Veröffentlichung. Minecraft und die Simulation
 * erzeugen jeweils eine Instanz mit ihren eigenen Adaptern.
 */
public final class CivApplication {

    private final FoundPolity foundPolity;
    private final JoinPolity joinPolity;
    private final LeavePolity leavePolity;
    private final EnactStatute enactStatute;
    private final RepealStatute repealStatute;
    private final AmendLaw amendLaw;
    private final ClaimChunk claimChunk;
    private final ReleaseChunk releaseChunk;
    private final CreatePlot createPlot;
    private final AssignPlot assignPlot;
    private final SellPlot sellPlot;
    private final OfferPlot offerPlot;
    private final BuyPlot buyPlot;
    private final ChangeTier changeTier;
    private final ProposeLawChange proposeLawChange;
    private final ProposeMotion proposeMotion;
    private final EnactParagraph enactParagraph;
    private final RepealParagraph repealParagraph;
    private final CastVote castVote;
    private final TallyProposals tallyProposals;
    private final ParliamentSession parliamentSession;
    private final Campaign campaign;
    private final ContractUseCases contracts;
    private final RunContracts runContracts;
    private final StreetUseCases streets;
    private final CommunityUseCases community;
    private final VillagerVoter villagerVoter;
    private final MakePromise makePromise;
    private final PutUpPoster putUpPoster;
    private final DissolvePolity dissolvePolity;
    private final Trading trading;
    private final CompanyUseCases companies;
    private final ProductionUseCases production;
    private final PressUseCases press;
    private final CivicUseCases civic;
    private final JusticeUseCases justice;
    private final PressInfluence pressInfluence;
    private final VillagerInvestors villagerInvestors;
    private final VillagerAmbitions villagerAmbitions;
    private final FoundParty foundParty;
    private final JoinParty joinParty;
    private final LeaveParty leaveParty;
    private final ChangePartyProfile changePartyProfile;
    private final MoveSeat moveSeat;
    private final OpenShop openShop;
    private final CloseShop closeShop;
    private final BuyFromShop buyFromShop;
    private final CreditTownIncome creditTownIncome;
    private final DissolvePlot dissolvePlot;
    private final DepositEmeralds depositEmeralds;
    private final WithdrawEmeralds withdrawEmeralds;
    private final WithdrawCash withdrawCash;
    private final DepositCash depositCash;
    private final TransferFunds transferFunds;
    private final SellToMarket sellToMarket;
    private final BuyFromMarket buyFromMarket;
    private final EndOfDay endOfDay;
    private final CallElection callElection;
    private final StandForElection standForElection;
    private final CastBallot castBallot;
    private final AppointOfficer appointOfficer;
    private final RunElections runElections;
    private final FoundFederation foundFederation;
    private final JoinFederation joinFederation;
    private final ProclaimConstitution proclaimConstitution;
    private final ChangePreamble changePreamble;
    private final LeaveFederation leaveFederation;
    private final WorldQueries queries;

    public CivApplication(Repositories repositories, IdGenerator ids, ClaimPolicy claimPolicy,
                          DomainEventPublisher publisher) {
        this(repositories, ids, claimPolicy, publisher, ClaimWeighting.ALL_MEMBERS);
    }

    /**
     * @param weighting wie Mitglieder für die Gebietsgröße zählen
     */
    public CivApplication(Repositories repositories, IdGenerator ids, ClaimPolicy claimPolicy,
                          DomainEventPublisher publisher, ClaimWeighting weighting) {
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(claimPolicy, "claimPolicy");
        Aggregates aggregates = new Aggregates(repositories, publisher);
        this.foundPolity = new FoundPolity(aggregates, ids, claimPolicy, weighting);
        this.joinPolity = new JoinPolity(aggregates);
        this.leavePolity = new LeavePolity(aggregates);
        this.enactStatute = new EnactStatute(aggregates, ids);
        this.repealStatute = new RepealStatute(aggregates);
        this.amendLaw = new AmendLaw(aggregates, ids);
        this.claimChunk = new ClaimChunk(aggregates, claimPolicy, weighting);
        this.releaseChunk = new ReleaseChunk(aggregates);
        this.createPlot = new CreatePlot(aggregates, ids);
        this.assignPlot = new AssignPlot(aggregates);
        this.sellPlot = new SellPlot(aggregates);
        this.offerPlot = new OfferPlot(aggregates);
        this.buyPlot = new BuyPlot(aggregates);
        this.changeTier = new ChangeTier(aggregates);
        this.proposeLawChange = new ProposeLawChange(aggregates, ids);
        this.proposeMotion = new ProposeMotion(aggregates, ids);
        this.enactParagraph = new EnactParagraph(aggregates, ids, proposeMotion);
        this.repealParagraph = new RepealParagraph(aggregates, proposeMotion);
        this.castVote = new CastVote(aggregates, ids);
        this.tallyProposals = new TallyProposals(aggregates, ids);
        this.callElection = new CallElection(aggregates, ids);
        this.standForElection = new StandForElection(aggregates);
        this.castBallot = new CastBallot(aggregates);
        this.appointOfficer = new AppointOfficer(aggregates);
        Function<PolityView, LawView> effectiveLaws = polity -> new WorldQueries(repositories).effectiveLaws(polity);
        Function<ActorRef, Optional<PolityView>> partyOf = actor -> new WorldQueries(repositories).partyOf(actor);
        Function<ActorRef, Optional<PolityId>> homeOf = actor -> new WorldQueries(repositories).municipalityOf(actor)
                .map(PolityView::id);
        this.campaign = new Campaign(partyOf, actor -> new WorldQueries(repositories).municipalityOf(actor),
                effectiveLaws);
        this.pressInfluence = new PressInfluence(() -> new WorldQueries(repositories).press(),
                actor -> new WorldQueries(repositories).municipalityOf(actor), partyOf);
        this.villagerVoter = new PersonalityVoter(effectiveLaws, partyOf,
                (villager, candidate, election, polity) -> campaign.bonus(villager, candidate, election, polity)
                        + pressInfluence.bonus(villager, candidate, election, polity));
        this.runElections = new RunElections(aggregates, ids, villagerVoter, partyOf, homeOf);
        this.contracts = new ContractUseCases(aggregates, ids);
        this.runContracts = new RunContracts(aggregates);
        this.streets = new StreetUseCases(aggregates);
        this.community = new CommunityUseCases(aggregates, ids);
        this.makePromise = new MakePromise(aggregates);
        this.putUpPoster = new PutUpPoster(aggregates);
        this.parliamentSession = new ParliamentSession(aggregates, ids,
                new PersonalityLegislator(effectiveLaws, partyOf));
        this.foundFederation = new FoundFederation(aggregates, ids);
        this.joinFederation = new JoinFederation(aggregates);
        this.proclaimConstitution = new ProclaimConstitution(aggregates);
        this.changePreamble = new ChangePreamble(aggregates);
        this.leaveFederation = new LeaveFederation(aggregates);
        this.dissolvePolity = new DissolvePolity(aggregates);
        this.trading = new Trading(aggregates, ids);
        this.companies = new CompanyUseCases(aggregates, ids, trading, dissolvePolity);
        this.production = new ProductionUseCases(aggregates, companies);
        this.press = new PressUseCases(aggregates, ids);
        this.civic = new CivicUseCases(aggregates, ids);
        this.justice = new JusticeUseCases(aggregates, ids);
        this.villagerInvestors = new VillagerInvestors(() -> new WorldQueries(repositories), trading);
        this.foundParty = new FoundParty(aggregates, ids);
        this.villagerAmbitions = new VillagerAmbitions(() -> new WorldQueries(repositories), companies, foundParty,
                standForElection);
        this.joinParty = new JoinParty(aggregates);
        this.leaveParty = new LeaveParty(aggregates, dissolvePolity);
        this.changePartyProfile = new ChangePartyProfile(aggregates);
        this.moveSeat = new MoveSeat(aggregates);
        this.openShop = new OpenShop(aggregates);
        this.closeShop = new CloseShop(aggregates);
        this.buyFromShop = new BuyFromShop(aggregates);
        this.creditTownIncome = new CreditTownIncome(aggregates);
        this.dissolvePlot = new DissolvePlot(aggregates);
        this.depositEmeralds = new DepositEmeralds(aggregates);
        this.withdrawEmeralds = new WithdrawEmeralds(aggregates);
        this.withdrawCash = new WithdrawCash(aggregates);
        this.depositCash = new DepositCash(aggregates);
        this.transferFunds = new TransferFunds(aggregates);
        this.sellToMarket = new SellToMarket(aggregates);
        this.buyFromMarket = new BuyFromMarket(aggregates);
        this.endOfDay = new EndOfDay(aggregates);
        this.queries = new WorldQueries(repositories);
    }

    public FoundPolity foundPolity() {
        return foundPolity;
    }

    public JoinPolity joinPolity() {
        return joinPolity;
    }

    public LeavePolity leavePolity() {
        return leavePolity;
    }

    public EnactStatute enactStatute() {
        return enactStatute;
    }

    public RepealStatute repealStatute() {
        return repealStatute;
    }

    public AmendLaw amendLaw() {
        return amendLaw;
    }

    public ClaimChunk claimChunk() {
        return claimChunk;
    }

    public ReleaseChunk releaseChunk() {
        return releaseChunk;
    }

    public CreatePlot createPlot() {
        return createPlot;
    }

    public AssignPlot assignPlot() {
        return assignPlot;
    }

    public SellPlot sellPlot() {
        return sellPlot;
    }

    public OfferPlot offerPlot() {
        return offerPlot;
    }

    public BuyPlot buyPlot() {
        return buyPlot;
    }

    public ChangeTier changeTier() {
        return changeTier;
    }

    public ProposeMotion proposeMotion() {
        return proposeMotion;
    }

    public EnactParagraph enactParagraph() {
        return enactParagraph;
    }

    public RepealParagraph repealParagraph() {
        return repealParagraph;
    }

    public ProposeLawChange proposeLawChange() {
        return proposeLawChange;
    }

    public CastVote castVote() {
        return castVote;
    }

    /** Wie Dorfbewohner wählen, z. B. für Umfragen. */
    public VillagerVoter villagerVoter() {
        return villagerVoter;
    }

    public ContractUseCases contracts() {
        return contracts;
    }

    public RunContracts runContracts() {
        return runContracts;
    }

    public StreetUseCases streets() {
        return streets;
    }

    public CommunityUseCases community() {
        return community;
    }

    /** Hausnummern und Wohnsitze. */
    public Addresses addresses() {
        return new Addresses(queries);
    }

    public Campaign campaign() {
        return campaign;
    }

    public MakePromise makePromise() {
        return makePromise;
    }

    public PutUpPoster putUpPoster() {
        return putUpPoster;
    }

    public ParliamentSession parliamentSession() {
        return parliamentSession;
    }

    public TallyProposals tallyProposals() {
        return tallyProposals;
    }

    public CallElection callElection() {
        return callElection;
    }

    public StandForElection standForElection() {
        return standForElection;
    }

    public CastBallot castBallot() {
        return castBallot;
    }

    public AppointOfficer appointOfficer() {
        return appointOfficer;
    }

    public RunElections runElections() {
        return runElections;
    }

    public FoundFederation foundFederation() {
        return foundFederation;
    }

    public ProclaimConstitution proclaimConstitution() {
        return proclaimConstitution;
    }

    public ChangePreamble changePreamble() {
        return changePreamble;
    }

    public JoinFederation joinFederation() {
        return joinFederation;
    }

    public LeaveFederation leaveFederation() {
        return leaveFederation;
    }

    public FoundParty foundParty() {
        return foundParty;
    }

    public JoinParty joinParty() {
        return joinParty;
    }

    public LeaveParty leaveParty() {
        return leaveParty;
    }

    public ChangePartyProfile changePartyProfile() {
        return changePartyProfile;
    }

    public Trading trading() {
        return trading;
    }

    public CompanyUseCases companies() {
        return companies;
    }

    /** Gerichte und Verfahren. */
    /** Produktion der Firmen. */
    public ProductionUseCases production() {
        return production;
    }

    public JusticeUseCases justice() {
        return justice;
    }

    /** Wahlkreise und Hoheitszeichen. */
    public CivicUseCases civic() {
        return civic;
    }

    public PressUseCases press() {
        return press;
    }

    public PressInfluence pressInfluence() {
        return pressInfluence;
    }

    public VillagerAmbitions villagerAmbitions() {
        return villagerAmbitions;
    }

    public VillagerInvestors villagerInvestors() {
        return villagerInvestors;
    }

    public DissolvePolity dissolvePolity() {
        return dissolvePolity;
    }

    public MoveSeat moveSeat() {
        return moveSeat;
    }

    public OpenShop openShop() {
        return openShop;
    }

    public CloseShop closeShop() {
        return closeShop;
    }

    public BuyFromShop buyFromShop() {
        return buyFromShop;
    }

    public CreditTownIncome creditTownIncome() {
        return creditTownIncome;
    }

    public DissolvePlot dissolvePlot() {
        return dissolvePlot;
    }

    public DepositEmeralds depositEmeralds() {
        return depositEmeralds;
    }

    public WithdrawEmeralds withdrawEmeralds() {
        return withdrawEmeralds;
    }

    public WithdrawCash withdrawCash() {
        return withdrawCash;
    }

    public DepositCash depositCash() {
        return depositCash;
    }

    public TransferFunds transferFunds() {
        return transferFunds;
    }

    public SellToMarket sellToMarket() {
        return sellToMarket;
    }

    public BuyFromMarket buyFromMarket() {
        return buyFromMarket;
    }

    public EndOfDay endOfDay() {
        return endOfDay;
    }

    public WorldQueries queries() {
        return queries;
    }
}
