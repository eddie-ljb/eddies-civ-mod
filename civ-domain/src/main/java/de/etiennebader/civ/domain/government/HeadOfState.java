package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.shared.ErrorCode;

import java.util.Objects;

/**
 * Das konfigurierbare Oberhaupt einer Ebene. Befugnisse stehen in der Verfassung.
 *
 * @param form      Staatsform
 * @param title     Amtsbezeichnung, z. B. „Bundespräsident“
 * @param selection Wahl oder Ernennung
 * @param salary    tägliches Gehalt aus der Gemeindekasse
 * @param veto      darf Gesetze mit Veto stoppen
 * @param appointsGovernment benennt und entlässt die Regierung
 * @param callsElections     darf Neuwahlen anordnen
 */
public record HeadOfState(StateForm form, String title, Selection selection, long salary, boolean veto,
                          boolean appointsGovernment, boolean callsElections) {

    public static final int MAX_TITLE = 32;
    public static final HeadOfState DEFAULT =
            new HeadOfState(StateForm.REPUBLIC, StateForm.REPUBLIC.defaultTitle(), Selection.ELECTION, 0, true, true, true);

    public HeadOfState {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(selection, "selection");
        title = title.strip();
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw new GovernmentException(ErrorCode.INVALID_GOVERNMENT_VALUE, title);
        }
        if (salary < 0) {
            throw new GovernmentException(ErrorCode.INVALID_GOVERNMENT_VALUE, salary);
        }
    }

    public HeadOfState withForm(StateForm newForm) {
        return new HeadOfState(newForm, newForm.defaultTitle(), selection, salary, veto, appointsGovernment, callsElections);
    }

    public HeadOfState withTitle(String newTitle) {
        return new HeadOfState(form, newTitle, selection, salary, veto, appointsGovernment, callsElections);
    }

    public HeadOfState withSelection(Selection newSelection) {
        return new HeadOfState(form, title, newSelection, salary, veto, appointsGovernment, callsElections);
    }

    public HeadOfState withSalary(long newSalary) {
        return new HeadOfState(form, title, selection, newSalary, veto, appointsGovernment, callsElections);
    }

    public HeadOfState withPowers(boolean newVeto, boolean newAppoints, boolean newCalls) {
        return new HeadOfState(form, title, selection, salary, newVeto, newAppoints, newCalls);
    }
}
