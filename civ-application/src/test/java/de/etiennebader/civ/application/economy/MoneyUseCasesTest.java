package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyUseCasesTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final LedgerView ledger = fixture.app.queries().ledger();

    @Test
    @DisplayName("Smaragde ein- und auszahlen verändert die Geldmenge")
    void emeraldsChangeSupply() {
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(500), DAY));
        fixture.app.withdrawEmeralds().execute(new WithdrawEmeralds.Command(alice, emeralds(200), DAY));

        assertEquals(emeralds(300), ledger.balanceOf(alice, EMERALD));
        assertEquals(emeralds(300), ledger.totalSupply(EMERALD));
    }

    @Test
    @DisplayName("Bargeld: Alice hebt ab, Bob zahlt es ein")
    void cashChangesHands() {
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(500), DAY));

        fixture.app.withdrawCash().execute(new WithdrawCash.Command(alice, emeralds(100), DAY));
        fixture.app.depositCash().execute(new DepositCash.Command(bob, emeralds(100), DAY));

        assertEquals(emeralds(400), ledger.balanceOf(alice, EMERALD));
        assertEquals(emeralds(100), ledger.balanceOf(bob, EMERALD));
        assertEquals(emeralds(0), ledger.cashInCirculation(EMERALD));
    }

    @Test
    @DisplayName("Überweisung ohne Deckung → INSUFFICIENT_FUNDS")
    void transferWithoutFunds() {
        assertEquals(ErrorCode.INSUFFICIENT_FUNDS, assertThrows(DomainException.class,
                () -> fixture.app.transferFunds().execute(
                        new TransferFunds.Command(alice, bob, emeralds(1), TransferReason.TRADE, DAY))).code());
    }
}
