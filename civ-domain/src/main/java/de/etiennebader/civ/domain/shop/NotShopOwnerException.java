package de.etiennebader.civ.domain.shop;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Der Laden gehört jemand anderem.
 */
public final class NotShopOwnerException extends DomainException {

    public NotShopOwnerException(ActorRef actor) {
        super(ErrorCode.NOT_SHOP_OWNER, actor);
    }
}
