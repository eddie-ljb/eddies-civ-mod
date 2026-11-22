package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.credit.CreditScore;
import de.etiennebader.civ.domain.credit.LoanStatus;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.Test;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerBorrowingTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef mayor = fixture.player();
    private final PlayerRef bank = fixture.player();
    private final PolityId town = fixture.found("Nordhain", mayor, 0, 0);
    private final PersonRef villager = fixture.villager();

    private void setUp(boolean selfService) {
        fixture.app.joinPolity().execute(new JoinPolity.Command(town, villager, MembershipType.RESIDENT, DAY));
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(bank, emeralds(10_000), DAY));
        fixture.app.credit().licenseBank(bank, DAY);
        fixture.app.credit().setSelfService(bank, selfService, DAY);
    }

    private long balance() {
        return fixture.app.queries().ledger().balanceOf(villager, EMERALD).amount();
    }

    @Test
    void brokeVillagerBorrowsOnceAndRepaysWhenAble() {
        setUp(true);
        fixture.app.villagerBorrowing().execute(DAY);
        assertEquals(VillagerBorrowing.LOAN_AMOUNT, balance());
        assertEquals(1, fixture.app.queries().credit().loansOfBorrower(villager).size());

        fixture.app.villagerBorrowing().execute(DAY.plus(1)); // schon verschuldet: kein zweiter Kredit
        assertEquals(1, fixture.app.queries().credit().loansOfBorrower(villager).size());

        fixture.app.creditTownIncome().creditEmployer(villager, emeralds(5_000), DAY.plus(2));
        fixture.app.villagerBorrowing().execute(DAY.plus(3));
        assertEquals(LoanStatus.REPAID, fixture.app.queries().credit().loansOfBorrower(villager).getFirst().status());
        assertEquals(CreditScore.START + CreditScore.REPAID_BONUS, fixture.app.queries().credit().scoreOf(villager));
    }

    @Test
    void noSelfServiceBankMeansNoLoan() {
        setUp(false);
        fixture.app.villagerBorrowing().execute(DAY);
        assertTrue(fixture.app.queries().credit().loans().isEmpty());
        assertEquals(0, balance());
    }

    @Test
    void defaultedVillagerGetsNoNewLoan() {
        setUp(true);
        fixture.app.villagerBorrowing().execute(DAY);
        // Er zahlt nicht: Kredit fällt aus, Score sinkt unter die Schwelle.
        fixture.app.credit().endOfDay(DAY.plus(VillagerBorrowing.TERM_DAYS + 1));
        fixture.app.credit().endOfDay(DAY.plus(VillagerBorrowing.TERM_DAYS + 2));
        assertEquals(CreditScore.START - CreditScore.DEFAULT_PENALTY, fixture.app.queries().credit().scoreOf(villager));
        // Das Guthaben ist noch da und über der Schwelle; er leiht sich nicht erneut.
        fixture.app.villagerBorrowing().execute(DAY.plus(40));
        assertEquals(1, fixture.app.queries().credit().loansOfBorrower(villager).size());
    }
}
