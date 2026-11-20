package de.etiennebader.civ.application.construction;

import de.etiennebader.civ.application.company.CompanyUseCases;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.construction.Blueprint;
import de.etiennebader.civ.domain.construction.ConstructionJob;
import de.etiennebader.civ.domain.construction.ConstructionJobId;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.production.Branch;
import de.etiennebader.civ.domain.production.Enterprise;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bauaufträge von Baufirmen (#19): eine Baufirma (Firma mit Branche {@link Branch#CONSTRUCTION}) weist einem
 * angestellten Bauarbeiter-Dorfbewohner einen {@link Blueprint} zu, der dann über mehrere Tage nachgebaut wird.
 * Der Lohn läuft über den normalen Arbeitsvertrag der Firma (siehe {@code CompanyService#hire}), nicht über eine
 * eigene Bezahlung je Block.
 *
 * <p><b>Bewusste Einschränkung:</b> Bauaufträge liegen anders als die übrigen Aggregate dieses Mods nur im
 * Arbeitsspeicher des laufenden Servers, nicht im Datenbestand (siehe {@code docs/issue-19-bauarbeiter-villager.md}
 * für die Begründung und was ein vollwertiges, neustartfestes Aggregat bräuchte). Ein laufender Bauauftrag geht
 * daher bei einem Serverneustart verloren; das Material dafür nicht, es bleibt im Firmenlager.
 */
public final class ConstructionUseCases {

    private final Aggregates aggregates;
    private final CompanyUseCases companies;
    private final IdGenerator ids;
    private final Map<ConstructionJobId, ConstructionJob> jobs = new ConcurrentHashMap<>();

    public ConstructionUseCases(Aggregates aggregates, CompanyUseCases companies, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.companies = Objects.requireNonNull(companies, "companies");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /** Alle laufenden und abgeschlossenen Bauaufträge einer Baufirma. */
    public List<ConstructionJob> jobsOf(PolityId company) {
        return jobs.values().stream().filter(job -> job.company().equals(company)).toList();
    }

    public Optional<ConstructionJob> job(ConstructionJobId id) {
        return Optional.ofNullable(jobs.get(id));
    }

    /**
     * Weist einem angestellten Bauarbeiter einen Bauauftrag zu. Die Firma muss die Branche
     * {@link Branch#CONSTRUCTION} gewählt haben und der Dorfbewohner einen laufenden Arbeitsvertrag mit ihr.
     */
    public ConstructionJob assign(ActorRef head, PolityId company, ActorRef worker, Blueprint blueprint,
                                  BlockPosition origin, Day today) {
        companies.requireHead(head, company, PolityKind.COMPANY);
        Enterprise enterprise = aggregates.production().enterpriseOf(company)
                .orElseThrow(() -> new IllegalStateException("Firma hat noch keine Branche gewählt"));
        if (enterprise.branch() != Branch.CONSTRUCTION) {
            throw new IllegalStateException("Firma ist keine Baufirma (Branche " + Branch.CONSTRUCTION.id() + ")");
        }
        if (!isEmployedBy(company, worker)) {
            throw new IllegalStateException("Bauarbeiter hat keinen Arbeitsvertrag mit dieser Firma");
        }
        ConstructionJob job = ConstructionJob.start(ConstructionJobId.generate(ids), company, worker, blueprint,
                origin, today);
        jobs.put(job.id(), job);
        return job;
    }

    /** Materialbedarf der nächsten {@code capacity} noch fehlenden Blöcke, je Ware. */
    public Map<Commodity, Long> materialsFor(ConstructionJob job, Blueprint blueprint, int capacity) {
        Map<Commodity, Long> needed = new LinkedHashMap<>();
        for (ConstructionJob.AbsolutePlacement placement : job.next(blueprint, capacity)) {
            needed.merge(Commodity.of(placement.block()), 1L, Long::sum);
        }
        return needed;
    }

    /**
     * Baut bis zu {@code capacity} weitere Blöcke des Auftrags, sofern das Material dafür vollständig im
     * Firmenlager liegt; sonst pausiert der Auftrag unverändert (kein Teilbau mit fehlendem Material). Gibt die
     * tatsächlich gesetzten Blöcke zurück (leer, wenn pausiert oder bereits fertig).
     */
    public List<ConstructionJob.AbsolutePlacement> advance(ConstructionJobId jobId, Blueprint blueprint, int capacity,
                                                            Day today) {
        ConstructionJob job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Unbekannter Bauauftrag: " + jobId);
        }
        if (job.done(blueprint)) {
            return List.of();
        }
        List<ConstructionJob.AbsolutePlacement> batch = job.next(blueprint, capacity);
        if (batch.isEmpty()) {
            return List.of();
        }
        Map<Commodity, Long> needed = new LinkedHashMap<>();
        for (ConstructionJob.AbsolutePlacement placement : batch) {
            needed.merge(Commodity.of(placement.block()), 1L, Long::sum);
        }
        Enterprise enterprise = aggregates.production().enterpriseOf(job.company())
                .orElseThrow(() -> new IllegalStateException("Baufirma ohne Branche: " + job.company()));
        for (Map.Entry<Commodity, Long> entry : needed.entrySet()) {
            if (enterprise.stockOf(entry.getKey()) < entry.getValue()) {
                return List.of();
            }
        }
        var registry = aggregates.production();
        for (Map.Entry<Commodity, Long> entry : needed.entrySet()) {
            registry.take(job.company(), entry.getKey(), entry.getValue(), today);
        }
        aggregates.commit(registry);
        ConstructionJob advanced = job.advanced(blueprint, batch.size());
        jobs.put(jobId, advanced);
        return batch;
    }

    /** Legt Material ins Lager der Baufirma, z. B. nachdem der Spieler es dorthin gebracht hat. */
    public void supply(ActorRef head, PolityId company, Commodity commodity, long quantity, Day today) {
        companies.requireHead(head, company, PolityKind.COMPANY);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Menge muss positiv sein: " + quantity);
        }
        var registry = aggregates.production();
        registry.produce(company, Map.of(commodity, quantity), today);
        aggregates.commit(registry);
    }

    private boolean isEmployedBy(PolityId company, ActorRef worker) {
        ActorRef employer = aggregates.polity(company).asActor();
        return aggregates.contracts().contracts().stream()
                .filter(Contract::active)
                .anyMatch(contract -> contract.kind() == ContractKind.EMPLOYMENT
                        && contract.offeror().equals(employer) && contract.counterparty().equals(worker));
    }
}
