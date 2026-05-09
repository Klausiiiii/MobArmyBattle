# MobArmyBattle Plan 7: SQLite + Stats + Leaderboard (v1)

**Goal:** Persistente Lifetime-Stats pro Spieler (Matches gespielt, gewonnen, Mob-Kills), abrufbar via `/mab stats [player]` und `/mab leaderboard`.

**Architecture:**
- Paper Library Loader für SQLite-JDBC (kein Shading nötig).
- `stats/StatsDatabase` — Connection-Mgmt + DDL.
- `stats/PlayerStats` — Domain-Value-Object (Bukkit-frei, unit-getestet).
- `stats/StatsRepository` — JDBC-Adapter für player_stats CRUD.
- `stats/StatsRecorder` — listens auf `BattleManager.addMatchCompletedListener` + persistiert Aggregat.
- `BattleManager.addMatchCompletedListener` — fires wenn alle Battle-Sessions eines Matches concluded.
- `MabCommand` + `/mab stats [player]` + `/mab leaderboard`.

**Scope-Cut für v1:**
- Nur `player_stats`-Tabelle (kein matches/teams/wave_data Schema).
- Nur Wins/Losses + Mob-Kills-Aggregat (kein avg_battle_time, favorite_mob).
- Kein JSON-Backup bei DB-Fehler — nur Log-Warning, Plugin läuft weiter.
- Synchronous DB-Writes (kein Async/Pool). Bei großen Servern wäre Async sinnvoll.

---

## Schema

```sql
CREATE TABLE IF NOT EXISTS player_stats (
    player_uuid TEXT PRIMARY KEY,
    matches_total INTEGER DEFAULT 0,
    matches_won INTEGER DEFAULT 0,
    mob_kills_total INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wins ON player_stats(matches_won DESC);
```

## Match-Completed Aggregat

Pro Match: Liste von (Team, isWinner, mobKills). Recorder iteriert alle Teams, für jedes Mitglied:
- `matches_total += 1`
- `matches_won += isWinner ? 1 : 0`
- `mob_kills_total += team.mobKills`

Bei Multi-Pair-Matches (z.B. 4 Teams paired): jedes Pair hat einen Winner. Spec sagt "Sieger = Pair-Winner", also alle Pair-Winners zählen als gewonnene Matches. Verlierer als verlorene.

## Files

| Datei | Zweck | Status |
|---|---|---|
| `src/main/resources/plugin.yml` | + `libraries: org.xerial:sqlite-jdbc:3.46.1.3` | Modify |
| `stats/PlayerStats.java` | Domain-Record | Create |
| `stats/StatsDatabase.java` | Connection + DDL | Create |
| `stats/StatsRepository.java` | CRUD | Create |
| `stats/StatsRecorder.java` | Match-Listener-Adapter | Create |
| `battle/BattleManager.java` | + matchCompletedListeners + MatchOutcome record | Modify |
| `command/MabCommand.java` | + stats/leaderboard subcommands | Modify |
| `MobArmyBattle.java` | wiring | Modify |
| `test/stats/PlayerStatsTest.java` | Domain-Tests | Create |

## Akzeptanzkriterien

- [ ] Plugin startet mit `data.db` in `plugins/MobArmyBattle/`
- [ ] Nach 1 Match: `player_stats` hat Einträge für beide Teams
- [ ] `/mab stats <player>` zeigt Lifetime-W/L + Kills
- [ ] `/mab leaderboard` zeigt Top-10 nach Wins
- [ ] Tests für PlayerStats grün
