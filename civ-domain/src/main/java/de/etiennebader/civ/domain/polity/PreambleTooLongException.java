package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Die Präambel ist länger als {@link Constitution#MAX_PREAMBLE} Zeichen.
 */
public final class PreambleTooLongException extends DomainException {

    public PreambleTooLongException(int length) {
        super(ErrorCode.PREAMBLE_TOO_LONG, Integer.toString(length), Integer.toString(Constitution.MAX_PREAMBLE));
    }
}
