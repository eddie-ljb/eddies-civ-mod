package de.etiennebader.civ.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorCodeTest {

    @Test
    @DisplayName("Übersetzungsschlüssel folgt der Sprachdatei-Konvention")
    void translationKeyFollowsConvention() {
        assertEquals("eddies-civ-mod.error.already_member", ErrorCode.ALREADY_MEMBER.translationKey());
        assertEquals("eddies-civ-mod.error.insufficient_funds", ErrorCode.INSUFFICIENT_FUNDS.translationKey());
    }
}
