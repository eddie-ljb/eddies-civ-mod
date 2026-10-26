package de.etiennebader.civ.domain.shared;

/**
 * Oberschnittstelle aller fachlichen Ereignisse.
 *
 * <p>Bewusst nicht versiegelt: Jedes Paket definiert seine eigene versiegelte
 * Event-Hierarchie (z. B. {@code PolityEvent}), weil versiegelte Untertypen ohne
 * {@code module-info.java} im selben Package liegen müssen.
 */
public interface DomainEvent {

    Day occurredOn();
}
