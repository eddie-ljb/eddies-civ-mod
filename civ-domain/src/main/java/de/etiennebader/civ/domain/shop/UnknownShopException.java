package de.etiennebader.civ.domain.shop;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.territory.BlockPosition;

/**
 * An dieser Stelle ist kein Laden.
 */
public final class UnknownShopException extends DomainException {

    public UnknownShopException(BlockPosition position) {
        super(ErrorCode.UNKNOWN_SHOP, position);
    }
}
