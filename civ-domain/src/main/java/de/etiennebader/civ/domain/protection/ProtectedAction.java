package de.etiennebader.civ.domain.protection;

import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.StandardParameters;

/**
 * Was ein Akteur an einem Block tun möchte.
 */
public enum ProtectedAction {

    /** Blöcke setzen, abbauen oder verändern, z. B. mit Hacke, Eimer oder Feuerzeug. */
    BUILD(StandardParameters.OUTSIDERS_MAY_BUILD),
    /** Türen, Falltüren, Tore, Knöpfe, Hebel und Betten benutzen. */
    INTERACT(StandardParameters.OUTSIDERS_MAY_INTERACT),
    /** Truhen, Fässer, Öfen und andere Behälter öffnen. */
    OPEN_CONTAINER(StandardParameters.OUTSIDERS_MAY_OPEN_CONTAINERS);

    private final ParameterKey<Boolean> outsiderPermission;

    ProtectedAction(ParameterKey<Boolean> outsiderPermission) {
        this.outsiderPermission = outsiderPermission;
    }

    /** Das Gesetz, mit dem eine Gemeinde diese Handlung für Nicht-Mitglieder erlaubt. */
    public ParameterKey<Boolean> outsiderPermission() {
        return outsiderPermission;
    }
}
