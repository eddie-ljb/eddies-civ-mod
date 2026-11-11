package de.etiennebader.civ.application;

import de.etiennebader.civ.application.day.EndOfDay;
import de.etiennebader.civ.application.economy.CreditTownIncome;
import de.etiennebader.civ.application.economy.DepositCash;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.economy.WithdrawCash;
import de.etiennebader.civ.application.economy.WithdrawEmeralds;
import de.etiennebader.civ.application.land.AssignPlot;
import de.etiennebader.civ.application.land.BuyPlot;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.land.DissolvePlot;
import de.etiennebader.civ.application.land.OfferPlot;
import de.etiennebader.civ.application.land.SellPlot;
import de.etiennebader.civ.application.market.BuyFromMarket;
import de.etiennebader.civ.application.market.SellToMarket;
import de.etiennebader.civ.application.office.AppointOfficer;
import de.etiennebader.civ.application.office.CallElection;
import de.etiennebader.civ.application.office.CastBallot;
import de.etiennebader.civ.application.office.RunElections;
import de.etiennebader.civ.application.office.StandForElection;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.CastVote;
import de.etiennebader.civ.application.polity.ChangeTier;
import de.etiennebader.civ.application.polity.DissolvePolity;
import de.etiennebader.civ.application.polity.EnactStatute;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.application.polity.MoveSeat;
import de.etiennebader.civ.application.polity.ProposeLawChange;
import de.etiennebader.civ.application.polity.RepealStatute;
import de.etiennebader.civ.application.polity.TallyProposals;
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
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.territory.ClaimPolicy;

import java.util.Objects;

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
    private final CastVote castVote;
    private final TallyProposals tallyProposals;
    private final DissolvePolity dissolvePolity;
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
        this.castVote = new CastVote(aggregates, ids);
        this.tallyProposals = new TallyProposals(aggregates, ids);
        this.callElection = new CallElection(aggregates, ids);
        this.standForElection = new StandForElection(aggregates);
        this.castBallot = new CastBallot(aggregates);
        this.appointOfficer = new AppointOfficer(aggregates);
        this.runElections = new RunElections(aggregates, ids, new PersonalityVoter());
        this.dissolvePolity = new DissolvePolity(aggregates);
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

    public ProposeLawChange proposeLawChange() {
        return proposeLawChange;
    }

    public CastVote castVote() {
        return castVote;
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
