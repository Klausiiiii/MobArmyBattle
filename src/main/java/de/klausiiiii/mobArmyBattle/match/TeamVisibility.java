package de.klausiiiii.mobArmyBattle.match;

/**
 * How a {@link Team} accepts new members in a multi-team match.
 *
 * <ul>
 *   <li>{@link #PUBLIC} — anyone may join.</li>
 *   <li>{@link #PASSWORD} — joiner must provide the matching password.</li>
 *   <li>{@link #PRIVATE} — joiner must have been invited by the captain.</li>
 * </ul>
 */
public enum TeamVisibility {
    PUBLIC,
    PASSWORD,
    PRIVATE;
}
