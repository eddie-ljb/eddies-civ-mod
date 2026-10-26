package de.etiennebader.civ.sim.agent;

/**
 * Was ein Akteur über seine Lage weiß, bevor er entscheidet. Die Simulation stellt diese
 * Fakten zusammen; {@link AgentBrain} bewertet sie, ohne selbst etwas nachzuschlagen.
 *
 * @param member                ob er einer Gemeinde angehört
 * @param soleMember            ob er deren einziges Mitglied ist
 * @param homeless              ob er als Mitglied kein Grundstück besitzt
 * @param plotAffordable        ob in seiner Gemeinde ein freies Grundstück liegt, das er bezahlen kann
 * @param canAffordFounding     ob er das Startkapital für eine Gründung hat
 * @param bestPolityAttraction  Attraktivität der besten Gemeinde, der er beitreten könnte (0 = keine)
 * @param cashGap               wie viel Bargeld ihm zum Wunschbestand fehlt (negativ = zu viel)
 * @param desiredCash           sein Wunschbestand an Bargeld
 * @param hasTradePartner       ob es andere Akteure gibt, denen er Bargeld geben kann
 * @param leaveThreshold        unterhalb dieser Zufriedenheit denkt er an Austritt
 */
public record AgentSituation(boolean member, boolean soleMember, boolean homeless, boolean plotAffordable,
                             boolean canAffordFounding, double bestPolityAttraction, long cashGap, long desiredCash,
                             boolean hasTradePartner, double leaveThreshold) {
}
