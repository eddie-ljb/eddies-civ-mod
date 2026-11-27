package de.etiennebader.civ.server.justice;

import de.etiennebader.civ.domain.justice.CourtCase;

/** Wird über wichtige Schritte eines Verfahrens benachrichtigt (z. B. die Polizei, die Haft vollstreckt). */
public interface CaseListener {

    /** Das Hauptverfahren wurde eröffnet; Zeugen und Beweise können nun eingebracht werden. */
    default void opened(CourtCase courtCase) {
    }

    /** Der Richter hat Haft in Tagen verhängt (Urteil noch nicht rechtskräftig). */
    default void sentenced(CourtCase courtCase, int days) {
    }

    /** Das Urteil ist rechtskräftig. */
    default void finalised(CourtCase courtCase) {
    }
}
