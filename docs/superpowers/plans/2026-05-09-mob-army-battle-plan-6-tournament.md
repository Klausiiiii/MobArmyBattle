# MobArmyBattle Plan 6: Tournament + Bracket

**Goal:** Single-Elimination-Tournaments mit `/mab tournament create/join/start/list/next`. Tournament-Master sammelt Captains, startet Bracket, jede Runde paart die überlebenden Captains zu 1v1-Matches. Sieger der Runde gehen weiter, bis ein Captain als Tournament-Sieger übrig bleibt.

**Architecture:**
- Domain: `Tournament` (Aggregate-Root) hält registrierte Captains + Rounds-Liste. `TournamentRound` enthält `TournamentPairing`s (+ optionales Bye). Domain ist Bukkit-frei und unit-testbar.
- Adapter: `TournamentManager` (Bukkit-aware) verwaltet Tournament-Instanzen + Match-Erstellung pro Pairing + Match-Result-Auswertung via BattleManager-Listener.
- Match-Integration: `BattleManager` publiziert ein "Battle finished"-Event mit Winner-Captain. TournamentManager hört zu, ruft `Tournament.recordPairingWinner`, und schedulet bei voll-komplettierter Runde die nächste.

**Scope-Cut für v1:**
- **Solo-Only Tournaments**: Jeder registrierte Captain ist ein 1-Person-Team. Kein Mergen von Multi-Player-Teams in Tournament-Matches.
- **Auto-advance** bei vollständiger Runde mit kurzer Pause (15 sec, statt Spec 60 sec — kürzer für Dev-Testing).
- **Tournament-State** in-memory; Server-Restart wischt offene Tournaments.

---

## Files

| Datei | Zweck | Status |
|---|---|---|
| `tournament/Tournament.java` | Aggregate-Root | Create |
| `tournament/TournamentRound.java` | Eine Runde mit Pairings | Create |
| `tournament/TournamentPairing.java` | Captain-vs-Captain | Create |
| `tournament/TournamentManager.java` | Bukkit-aware Orchestrator | Create |
| `battle/BattleManager.java` | + Listener-Liste für Battle-End-Events | Modify |
| `command/MabCommand.java` | + `tournament` Subcommand | Modify |
| `MobArmyBattle.java` | + TournamentManager wiring | Modify |
| `test/tournament/TournamentTest.java` | Domain-Tests | Create |
| `test/tournament/TournamentRoundTest.java` | Round-Tests | Create |
| `test/tournament/TournamentPairingTest.java` | Pairing-Tests | Create |

---

## Tasks

### Task 1: TournamentPairing (Domain)
- Felder: `captainA`, `captainB`, optional `matchId`, optional `winner`
- Methoden: `setMatchId`, `setWinner`, `getOtherCaptain`, `involves(uuid)`
- Tests: pairing identity, setting winner, involves-checks

### Task 2: TournamentRound (Domain)
- Felder: `number`, `pairings`, optional `byeCaptain`
- Methoden: `findPairing(a, b)`, `isComplete()`, `getWinners()` (returns winner-uuids + bye if exists)
- Tests: complete-detection, winners include bye, find-pairing

### Task 3: Tournament (Aggregate)
- Felder: `id`, `name`, `masterId`, `registered`, `rounds`, `status`, optional `winner`
- Methoden:
  - `register(uuid)` / `unregister(uuid)` — nur in REGISTERING state
  - `start(Random rng)` — baut Round 1, status RUNNING
  - `getCurrentRound()`
  - `recordPairingWinner(matchId, winnerCaptainId)`
  - `advanceToNextRound(Random rng)` — wenn aktuelle Runde komplett, baut Folge-Runde aus Winners; bei 1 Gewinner → FINISHED
  - `getStatus()`, `getWinner()`
- Tests:
  - 4 captains → round 1 mit 2 pairings
  - 5 captains → round 1 mit 2 pairings + 1 bye
  - 2 captains → round 1, recordWinner, advance → FINISHED
  - Bye-captain advances zur nächsten Runde
  - Cannot register after start

### Task 4: TournamentManager (Bukkit-aware)
- HashMap `<String name, Tournament>`
- HashMap `<UUID captainId, Tournament>` — captain → tournament reverse-index
- HashMap `<String matchId, Tournament>` — Match → Tournament reverse-index für Result-Routing
- Methoden:
  - `create(name, masterUuid)` — neues Tournament, REGISTERING
  - `join(captainUuid, name)` — captain registriert
  - `start(name)` — baut Round 1, erstellt Matches via MatchManager, startet sie automatisch
  - `onBattleFinished(matchId, winnerCaptain)` — Listener-Callback. Records Winner. Bei vollständiger Runde: 15 sec Pause, dann `advanceToNextRound` + neue Matches.
- Match-Erstellung: Pro Pairing erstellt ein Match mit `maxTeamSize=1`, captainA als team-1-captain, captainB als team-2-captain. Match wird auto-zu-FARM-transitioniert, sodass Spieler direkt farmen können (kein /mab start nötig).

### Task 5: BattleManager Listener-Hook
- Add `List<BiConsumer<Match, UUID>> battleEndListeners`
- Add `addBattleEndListener(BiConsumer<Match, UUID> listener)`
- Bei BattleResult-Ermittlung in `checkSessionEnd`: rufe alle Listener mit (match, winnerCaptainUuid)
- Falls beide Teams im Pair den selben Score haben → Tournament-Logik soll nicht crashen → BattleResult muss "draw" handhaben können oder einen der beiden willkürlich wählen. Für Plan 6: bei draw → captainA wins (deterministisch).

### Task 6: /mab tournament Commands
- `/mab tournament create <name>` — nur, wenn man weder in Match noch Tournament ist
- `/mab tournament join <name>` — captain registriert; muss in seinem eigenen Match (Captain-Status) oder gar nicht in Match sein. Bei Match: Match wird verworfen (leaveMatch alle Members), captain wird einzig registriert.
- `/mab tournament start` — nur master
- `/mab tournament list` — listet aktive Tournaments + Status
- `/mab tournament leave` — captain unregistriert (nur in REGISTERING-state)

### Task 7: Wiring
- `MobArmyBattle.onEnable`: `tournamentManager = new TournamentManager(this, matchManager)`. `battleManager.addBattleEndListener(tournamentManager::onBattleFinished)`.
- Getter `getTournamentManager()`.

---

## Akzeptanzkriterien

- [ ] 4 Captains können sich in Tournament registrieren
- [ ] `/mab tournament start` paart sie zu 2 Matches (Round 1)
- [ ] Beide Matches laufen parallel durch farm → wave-build → battle
- [ ] Sieger werden erkannt; nach 15 sec startet Round 2 (Final)
- [ ] Sieger des Finals wird als Tournament-Sieger announced + alle teleported zur Lobby
- [ ] Bye-System: 5 Captains → 2 Pairings + 1 Bye in Round 1, Bye geht direkt in Round 2
- [ ] Tests grün
