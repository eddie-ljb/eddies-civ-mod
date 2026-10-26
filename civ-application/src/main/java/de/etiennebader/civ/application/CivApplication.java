package de.etiennebader.civ.application;

import de.etiennebader.civ.application.day.EndOfDay;
import de.etiennebader.civ.application.economy.DepositCash;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.economy.WithdrawCash;
import de.etiennebader.civ.application.economy.WithdrawEmeralds;
import de.etiennebader.civ.application.land.AssignPlot;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.land.DissolvePlot;
import de.etiennebader.civ.application.land.SellPlot;
import de.etiennebader.civ.application.polity.EnactStatute;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.application.polity.RepealStatute;
import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.Aggregates;
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
    private final ClaimChunk claimChunk;
    private final ReleaseChunk releaseChunk;
    private final CreatePlot createPlot;
    private final AssignPlot assignPlot;
    private final SellPlot sellPlot;
    private final DissolvePlot dissolvePlot;
    private final DepositEmeralds depositEmeralds;
    private final WithdrawEmeralds withdrawEmeralds;
    private final WithdrawCash withdrawCash;
    private final DepositCash depositCash;
    private final TransferFunds transferFunds;
    private final EndOfDay endOfDay;
    private final WorldQueries queries;

    public CivApplication(Repositories repositories, IdGenerator ids, ClaimPolicy claimPolicy,
                          DomainEventPublisher publisher) {
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(claimPolicy, "claimPolicy");
        Aggregates aggregates = new Aggregates(repositories, publisher);
        this.foundPolity = new FoundPolity(aggregates, ids, claimPolicy);
        this.joinPolity = new JoinPolity(aggregates);
        this.leavePolity = new LeavePolity(aggregates);
        this.enactStatute = new EnactStatute(aggregates, ids);
        this.repealStatute = new RepealStatute(aggregates);
        this.claimChunk = new ClaimChunk(aggregates, claimPolicy);
        this.releaseChunk = new ReleaseChunk(aggregates);
        this.createPlot = new CreatePlot(aggregates, ids);
        this.assignPlot = new AssignPlot(aggregates);
        this.sellPlot = new SellPlot(aggregates);
        this.dissolvePlot = new DissolvePlot(aggregates);
        this.depositEmeralds = new DepositEmeralds(aggregates);
        this.withdrawEmeralds = new WithdrawEmeralds(aggregates);
        this.withdrawCash = new WithdrawCash(aggregates);
        this.depositCash = new DepositCash(aggregates);
        this.transferFunds = new TransferFunds(aggregates);
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

    public EndOfDay endOfDay() {
        return endOfDay;
    }

    public WorldQueries queries() {
        return queries;
    }
}
