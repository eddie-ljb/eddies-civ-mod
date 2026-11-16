package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Ein Verfassungsartikel einer ausgerufenen Verfassung lässt sich nicht verordnen, nur per Abstimmung ändern.
 */
public final class ConstitutionRequiresVoteException extends DomainException {

    public ConstitutionRequiresVoteException(String article) {
        super(ErrorCode.CONSTITUTION_REQUIRES_VOTE, article);
    }
}
