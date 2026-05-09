# MobArmyBattle — Roadmap

Multi-Phase-Spielmodus für Paper-Plugin: Spieler farmen Mobs → bauen Wellen → kämpfen mit den Wellen des Gegners. Vollständige Spec: [`docs/superpowers/specs/2026-05-03-mob-army-battle-design.md`](docs/superpowers/specs/2026-05-03-mob-army-battle-design.md).

---

## Status-Übersicht

| Plan | Thema | Branch | Status |
|---|---|---|---|
| 1 | Skeleton + Domain-Modell + `/mab create/join/leave/start` | `feat/plan-1-skeleton` | ✅ Merged |
| 2 | Welt-Management (Lobby + Farm-Welt mit Seed) | `feat/plan-2-world-management` | ✅ Merged |
| 3 | Mob-Pool + Equipment-Tracking + Kill-Listener | `feat/plan-3-mob-pool` | ✅ Merged |
| 3.5 | Dynamic Teams + maxTeamSize (FFA-Modus) | `feat/plan-3.5-two-teams` | ✅ Merged |
| 4 | Wave-Building (Domain + Inventar-GUI + Forfeit) | `feat/plan-4-wave-build` | ✅ PR #6 offen |
| 5 | Battle-Engine (Pairing + Arena + WaveSpawner + Sieger) | `feat/plan-5-battle` | ✅ implementiert |
| 6 | Tournament + Bracket | — | 📋 geplant |
| 7 | SQLite + Stats + Leaderboard | — | 📋 geplant |
| 8 | Spectator + Sidebar-Scoreboard + UI-Polish | — | 📋 geplant |
| 9 | Config + Permissions + Konfigurierbare Werte | — | 📋 geplant |
| 10 | Final Polish + Testing | — | 📋 geplant |

---

## Plan-Details

### ✅ Plan 1 — Skeleton + Domain
**Ziel:** Plugin-Skeleton + Match/Team/MatchPhase State-Machine + 4 Befehle.
**Geliefert:** `Match`, `Team`, `MatchPhase`-Interface, 5 Phase-Stubs, `MatchManager`, `MabCommand`, Tab-Completion.
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-1-skeleton.md`

### ✅ Plan 2 — Welt-Management
**Ziel:** Zentrale Lobby-Welt + Farm-Welten mit gleichem Seed pro Team.
**Geliefert:** `WorldManager`, `LobbyChunkGenerator`, `WorldCleanup`, FarmPhase-Aktivierung, Lobby-Teleport bei Join, Auto-Leave bei Quit, Orphan-Welt-Cleanup.
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-2-world-management.md`

### ✅ Plan 3 — Mob-Pool
**Ziel:** Equipment-bewusstes Mob-Tracking pro Team.
**Geliefert:** `MobEntry` (mit Equipment-Signature), `MobPool` (LinkedHashMap-Aggregation, applyPenalty), `EquipmentSerializer` (LivingEntity → "main\|off\|helm\|chest\|legs\|boots"-String), `MobKillListener` (nur Farm-Phase + nur Team-Welt), `PlayerDeathFarmListener` (10% Pool-Penalty), `/mab pool`.
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-3-mob-pool.md`

### ✅ Plan 3.5 — Dynamic Teams
**Ziel:** Match unterstützt N Teams mit `maxTeamSize` statt fixer Modi.
**Geliefert:** `Match.maxTeamSize` (default 1 = Solo-FFA), `Team` mit max-size, dynamic team creation in `joinMatch`, auto-balance, `Match.canStart()` verlangt ≥2 aktive Teams. `/mab create N`, `/mab join <captain> [team]`. Beispiele: `/mab create 1` mit 8 Spielern → 8 Solo-Teams.
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-3-5-two-teams.md`

### ✅ Plan 4 — Wave-Building
**Ziel:** Inventar-GUI für Captain zum Bauen von 2 Wellen aus Pool.
**Geliefert:** `Wave` (finalisierbar, forfeit-fähig), `WaveSlot`, `WaveBuildGui` (54-Slot-Inventar mit Pool-Übersicht / Tabs / Reset / Forfeit / Confirm / Cancel), `/mab endfarm` für Phase-Übergang. Pool-0-Teams werden auto-disbanded (Lobby-Teleport + Match-Remove). Pool-Sharing über beide Wellen korrekt (3 Mobs total → max 3 verteilt). FinishedPhase räumt Spieler komplett auf. `/mab join` erlaubt Match-Wechsel.
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-4-wave-build.md`

### ✅ Plan 5 — Battle-Engine
**Ziel:** Vollständiger Battle-Lifecycle vom Pairing bis zum Sieger.
**Geliefert:**
- `TeamPair` + `TeamPairing` (paart N Teams in 2er-Gruppen, Bye bei ungerade)
- `BattleStats` (waves/kills/time pro Team) + `BattleResult` (hierarchische Sieger-Berechnung)
- `WorldManager.createArenaWorld` (VOID-Welt pro Team)
- `ArenaSpawnScanner` (findet `[MAB_SPAWN]`-Banner)
- `ArenaLoader` (`.nbt` via Paper StructureManager + 30×30 Bedrock-Fallback mit Marker-Bannern)
- `WaveSpawner` (Mobs aus Wave + Equipment-Materialien aus Signature)
- `BattleSession` (Pair-State: Welten, aktuelle Welle, Stats, alive-Mobs)
- `BattleManager` (Orchestriert: pairt, erstellt Welten, teleportiert, spawnt Wellen, watcht Mob-Deaths, ermittelt Sieger)
- `BattleEventListener` (kein Item-/XP-Drop in Arena, KeepInventory, Spectator-Mode bei Tod)
- `BattlePhase` aktiviert
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-5-battle-engine.md`

### 📋 Plan 6 — Tournament + Bracket
**Ziel:** Single-Elimination-Tournaments mit `/mab tournament create/join/start`.
**Inhalt:**
- Tournament-Aggregat (Bracket, Runden, Pairings)
- Tournament-Master (eigener Captain-ähnlicher Status)
- Bye-System bei ungerader Team-Zahl
- 1-Min-Pause zwischen Bracket-Runden
- Ein laufendes Match → ein Bracket-Match. Sieger geht weiter.

### 📋 Plan 7 — SQLite + Stats + Leaderboard
**Ziel:** Persistente Match-Daten und Lifetime-Stats.
**Inhalt:**
- SQLite-Setup (`plugins/MobArmyBattle/data.db`)
- Schema: `matches`, `teams`, `team_members`, `wave_data`, `player_stats`
- Match-Replay-Daten beim FinishedPhase persistieren
- Lifetime-Stats pro Spieler (W/L, Total Kills, Avg-Battle-Zeit, Lieblings-Mob)
- `/mab stats [player]`, `/mab leaderboard`
- JSON-Backup bei DB-Fehler

### 📋 Plan 8 — Spectator + UI-Polish
**Ziel:** Sidebar-Scoreboard + Spectator-Mechaniken.
**Inhalt:**
- Sidebar-Scoreboard pro Phase (Pool/Wave-Counts/Battle-Stats)
- `/mab spectate <matchId>` für besiegte Tournament-Teams
- "Schneller fertig → kann Pair-Partner zuschauen" im Battle
- Lifecycle-Notifications (Title + Sound bei Phase-Wechsel)

### 📋 Plan 9 — Config + Permissions
**Ziel:** Alles konfigurierbar über `config.yml`.
**Inhalt:**
- `config.yml`: Phase-Dauern (60min Farm, 5min WaveBuild, 30s Vorbereitung, 10s Welle-Pause, 10min Hard-Timeout)
- Starter-Kit-Auswahl (`leather_full` / `iron_full` / custom)
- Death-Penalty-Modi (`drop_items`, `soft`, `none`, `hard`)
- WorldBorder-Radius, Mob-Spawn-Cap-Multiplier
- Punkte-System für Wellen (optional)
- Granuläre Permissions (`mobarmybattle.create/join/spectate/admin/...`)
- Reconnect-Grace (5 min Default)
- Auto-Farm-Phase-Timer (60 min)

### 📋 Plan 10 — Polish + Final Testing
**Ziel:** Bug-Fixing, fehlende Features, Production-Readiness.
**Inhalt:**
- Vorbereitungsphase (30s Block-Platzieren) vor jeder Welle
- Welle 1 → Pause (10s) → Welle 2 nahtlos
- Hard-Timeout pro Welle (10min)
- `/mab kick`, `/mab forcecancel`
- Async-Welt-Generierung
- End-to-End-Test mit allen Modi

---

## Code-Konventionen

- **Domain-Klassen Bukkit-frei** (`Match`, `Team`, `Pool`, `Wave`, `Battle*`) → unit-testbar mit JUnit 5 ohne MockBukkit
- **Bukkit-Adapter** in `command/`, `listener/`, `world/`, `wave/WaveBuildGui`, `battle/Arena*` + `WaveSpawner`
- **Phase-Klassen** haben 0-arg + `MobArmyBattle plugin`-arg Constructor (Tests vs. Production)
- **Player-facing-Texte und Exception-Messages auf Deutsch**
- **Conventional Commits** (`feat:`, `fix:`, `refactor:`)
- **Branch-Namen**: `feat/plan-<N>-<topic>`

## Build & Test

```bash
./gradlew.bat build         # compile + test
./gradlew.bat test          # alle Tests (132 grün)
./gradlew.bat runServer     # lokaler Paper-Server mit Plugin
```

## Aktuelle Test-Coverage

| Bereich | Tests |
|---|---|
| `Team` | 15 |
| `Match` | 13 |
| `MatchManager` | 19 |
| `MobEntry` | 7 |
| `MobPool` | 13 |
| `Wave` | 17 |
| `WaveSlot` | 5 |
| `MatchMode` (entfernt in 3.5) | — |
| `TeamPair` | 6 |
| `TeamPairing` | 6 |
| `BattleStats` | 5 |
| `BattleResult` | 5 |
| `WorldCleanup` | 3 |
| **Gesamt** | **~132** |

Bukkit-Code (GUI, Welten, Listener, BattleManager) wird manuell via `runServer` verifiziert.
