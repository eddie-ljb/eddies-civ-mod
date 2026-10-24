package de.etiennebader.civ.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CivDomainTest {

    @Test
    void meldetStatus() {
        assertEquals("Domänenkern geladen", CivDomain.status());
    }
}