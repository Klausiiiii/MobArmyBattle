# MobArmyBattle — Design-Spezifikation

**Datum:** 2026-05-03
**Status:** Entwurf zur Implementierung
**Plattform:** Paper API 26.1.2, Java 25, Gradle Kotlin DSL

## 1. Zweck und Vision

MobArmyBattle ist ein Minecraft-Plugin, das einen kompetitiven Multi-Phase-Spielmodus implementiert. Spieler farmen in einer Survival-Phase Mobs, bauen daraus zwei Wellen, und die Wellen werden im Battle gegen das gegnerische Team eingesetzt. Solo (1v1) bis 4v4-Team-Modus, asymmetrische Größen (z.B. 2v3) erlaubt. Single-Elimination-Bracket-Tournaments unterstützt.

## 2. Spielablauf

### 2.1 Phasen eines Matches

| Phase | Default-Dauer | Beschreibung |
|---|---|---|
| Lobby | offen | Captain wartet auf Spieler, kann Match starten |
| Farm | 60 min | Pro Team eigene Welt mit gleichem Seed; Mobs farmen |
| Wave-Build | 5 min | GUI-basiert Wellen aus Pool zusammenstellen |
| Battle | 30s Vorb. + 10min Welle 1 + 10s Pause + 30s Vorb. + 10min Welle 2 | Parallele Arena pro Team, Spieler kämpft gegen Gegner-Wellen |
| Finished | sofort | Sieger berechnen, Stats schreiben, Welten löschen |

### 2.2 Sieger-Bedingung

Hierarchisch in dieser Reihenfolge:
1. Mehr überlebte Wellen
2. Bei Gleichstand: mehr Mob-Kills
3. Bei Gleichstand: schnellere Battle-Zeit

### 2.3 Match-Erstellung (Captain-Modell)

- Spieler nutzt `/mab create <mode>` und wird Captain seines Teams.
- Andere Spieler joinen mit `/mab join <captain>`.
- Captain wählt Modus (1v1 / 2v2 / 3v3 / 4v4 oder asymmetrisch wie 2v3). "Solo" = 1-Spieler-Team. Team-Modus = 2-4 Spieler pro Team.
- Captain kann Spieler kicken/inviten und Match manuell starten.
- Bei asymmetrischen Matches: kein Sonder-Balancing; Spieler-Entscheidung beim Team-Wählen.

### 2.4 Tournament

- `/mab tournament create <name>` — globaler Tournament-Master erstellt Tournament.
- `/mab tournament join <name>` — Captains joinen mit ihren Teams.
- `/mab tournament start` — Master startet Bracket.
- Single-Elimination, zufällige Pairings pro Runde.
- Bei ungerader Team-Zahl: 1 zufälliges Team bekommt Bye-Slot in Runde 1.
- 1 min Pause zwischen Bracket-Runden.
- Sieger = letztes übriges Team.

## 3. Architektur

### 3.1 High-Level

Multi-Match-Engine mit zentralem `MatchManager`, der beliebig viele parallele `Match`-Instanzen verwaltet. State-Pattern für Match-Phasen. Multiverse-Core als Soft-Dependency für Welt-Verwaltung (Fallback auf eigene `WorldCreator`-Implementierung). SQLite für Persistenz.

```
┌───────────────────────────────────────────────────────────┐
│              MobArmyBattle (JavaPlugin)                   │
│  ┌────────────┐ ┌──────────────┐ ┌──────────────────┐     │
│  │MatchMgr    │ │TournamentMgr │ │StatsManager      │     │
│  │ - Match #1 │ │ - Bracket    │ │ (SQLite)         │     │
│  │ - Match #2 │ │ - Pairings   │ │  + Leaderboard   │     │
│  └────────────┘ └──────────────┘ └──────────────────┘     │
│  ┌────────────┐ ┌──────────────┐ ┌──────────────────┐     │
│  │WorldMgr    │ │LobbyMgr      │ │ConfigManager     │     │
│  │ + Multiverse│(zentrale Welt)│  (config.yml)     │     │
│  └────────────┘ └──────────────┘ └──────────────────┘     │
└───────────────────────────────────────────────────────────┘
```

### 3.2 Domain-Modell

| Klasse | Verantwortung |
|---|---|
| `Match` | Aggregat-Root. Hält Teams, Welten, aktuelle Phase. Sieger-Berechnung. |
| `Team` | 1-4 Spieler + Captain + gemeinsamer `MobPool`. |
| `MobPool` | Liste von `MobEntry` (Equipment-bewusst). Add/Remove pro Kill. |
| `MobEntry` | EntityType + Equipment-Snapshot (6 ItemStacks: Helm/Brust/Beine/Stiefel/Hand/Offhand) + Counter. |
| `Wave` | Liste von `WaveSlot` (MobEntry + Anzahl). Min. 1 Mob, kein Hard-Cap. |
| `Tournament` | Bracket. Liste von Runden, je Runde Liste von `Match`. Bye-Logik. |
| `MatchPhase` | Interface: `onEnter()`, `onExit()`, `tick()`, `getPhaseType()`. |

### 3.3 Phasen (State-Pattern)

Jede Phase ist eigene Klasse, implementiert `MatchPhase`-Interface:

- `LobbyPhase` — Captain wartet, kann starten.
- `FarmPhase` — pro Team Welt erstellen + teleportieren, Mob-Kills tracken.
- `WaveBuildPhase` — GUIs öffnen, Wellen-Vorschläge sammeln, Captain bestätigt.
- `BattlePhase` — Arena erstellen + teleportieren, Wellen spawnen, Sieger erkennen.
- `FinishedPhase` — Sieger ermitteln, Stats schreiben, Welten cleanup.

Übergänge zeit- oder ereignisbasiert. `tick()` wird sekündlich vom `MatchManager` für alle aktiven Matches aufgerufen.

## 4. Welten

### 4.1 Lobby-Welt (`mab_lobby`)

Zentrale, vorgebaute Welt. Plugin generiert Default-Lobby beim ersten Start (kleine Plattform mit NPCs/Schildern für Stats und Tournament-Beitritt). Admin kann erweitern. Alle Spieler kommen hier hin, wenn nicht in Match.

### 4.2 Farm-Welt (`mab_farm_<matchId>_<teamId>`)

Pro Team in einem Match eine eigene Welt. **Identischer Seed** für alle Teams desselben Matches → gleiche Bedingungen. Vanilla-Generation. WorldBorder-konfigurierbar (Default 1000 Blöcke). Spieler dürfen voll bauen (auch Mob-Farms, Lava-Trapping).

### 4.3 Arena-Welt (`mab_arena_<matchId>_<teamId>`)

Pro Team in einem Match eine eigene Arena. Geladen via Paper's `StructureManager` aus einer NBT-Strukturdatei in `plugins/MobArmyBattle/arenas/`. Admin definiert 1+ Strukturen (entweder per Vanilla-Struktur-Block exportiert oder als `.nbt`-Dateien direkt platziert). Bei mehreren Strukturen: zufällig pro Match gewählt (konfigurierbar: fixed/random).

**Wichtig:** Beide Teams desselben Matches bekommen die **identische** Arena geladen (gleiche Bedingungen). Bei `random`-Modus wird die Auswahl einmal pro Match getroffen, nicht pro Team.

Vorteil von Paper's `StructureManager` gegenüber WorldEdit: keine externe Dependency, natives Vanilla-NBT-Format.

**Spawn-Punkte für Mobs:** Im Schematic platziert Admin spezielle Marker (Banner mit Lore-Tag `[MAB_SPAWN]`). Plugin scannt geladene Arena nach diesen Bannern, nutzt deren Positionen als Spawn-Koordinaten und entfernt die Banner danach.

### 4.4 Cleanup

Nach Match-Ende: Welt entladen (`Bukkit.unloadWorld(world, false)`), Verzeichnis rekursiv löschen. Match-Daten (Replay) bleiben in SQLite. Beim Plugin-Start: alle existierenden `mab_farm_*` und `mab_arena_*` Verzeichnisse als Orphans erkennen und löschen.

## 5. Pool & Equipment-Tracking

### 5.1 Was zählt

- Alle Vanilla-Mobs (alles, was im Vanilla-Statistik-Screen "Mob-Kills" auftaucht).
- Spieler muss letzten Hit gemacht haben (strikter Player-Kill).
- Im Team: Hit muss von einem Teammitglied kommen.
- Mobs werden Equipment-bewusst getrackt (separater Pool-Eintrag pro einzigartiger Equipment-Kombination).
- Mob-Reiter (Spider Jockey, Chicken Jockey) zählen als 1 Eintrag, spawnen als Reiter.

### 5.2 Mob-Kill-Listener-Logik

```
on EntityDeathEvent:
  if not (entity in farm-world of active match): skip
  killer = entity.getKiller()
  if killer == null: skip  // strict player-kill
  team = match.findTeamOf(killer)
  if team == null: skip  // killer not in any team

  snapshot = captureEquipmentSnapshot(entity)
  entry = new MobEntry(entity.getType(), snapshot)
  team.getPool().add(entry)
```

### 5.3 Equipment-Snapshot

Pro Mob: 6 `ItemStack`s (Helm, Brust, Beine, Stiefel, Mainhand, Offhand). Inkl. NBT (Verzauberungen, Custom-Names). Serialisiert via `BukkitObjectOutputStream` → Base64-String für SQLite-Speicherung.

`MobEntry`-Hash: SHA-256 über (`EntityType.name()` + serialisierte Equipment-Liste). Identische Equipment-Kombinationen → ein Pool-Eintrag mit Counter, statt Listen-Explosion.

### 5.4 Pool bei Tod-Penalty

Default: Soft-Penalty. Bei Tod in Farm-Phase verliert der Spieler X% des persönlich beigetragenen Pools (X konfigurierbar). Andere Tod-Varianten via Config:
- `drop_items` — klassisch, Items droppen, kein Pool-Penalty
- `soft` — Default, Pool-Penalty
- `none` — kein Penalty
- `hard` — Spieler ausgeschieden bis Battle

## 6. Wave-Building

### 6.1 GUI

Inventar-basiert (6 Reihen, double-chest-style):

```
[Reihe 1-2] Pool-Übersicht: Mob-Spawn-Eier, Lore zeigt verfügbare Anzahl
            Klick = Mob in aktuelle Welle hinzufügen
            Right-Click auf Mob in Welle = entfernen

[Reihe 3]   Tab-Switcher: [Welle 1] [Welle 2] (aktive markiert)
            Trennlinien

[Reihe 4-5] Aktuelle Welle: gepackte Mobs (Spawn-Egg + Lore "x N")

[Reihe 6]   Buttons:
            - Captain: [Bestätigen] [Reset Welle]
            - Teammates: [Vorschlagen]
```

### 6.2 Vorschlags-System

- Teammates können Mobs in eine Vorschlags-Liste packen.
- Captain sieht Vorschläge, kann übernehmen oder verwerfen.
- Captain hat finales Veto über die Welle.

### 6.3 Constraints

- Min. 1 Mob pro Welle (sonst Welle nicht bestätigbar).
- Pool-Limit: man kann nur einsetzen, was gefarmt wurde.
- Optional aktivierbar (Config): Punkte-Budget pro Welle.
  - Default-Punkte: Zombie=1, Skelett=2, Creeper=3, Witch=5, gerüstete Varianten +Modifier.
  - Default-Budget: 100 Punkte pro Welle.
- Sichtbarkeit: Wellen sind komplett geheim für den Gegner.

## 7. Battle

### 7.1 Setup

- Beide Teams werden parallel in eigene Arena-Welten teleportiert.
- Spieler-Equipment: aus Farm-Phase übernommen.
- Vor Welle 1: 30s Vorbereitungsphase (konfigurierbar) — Spieler darf Blöcke aus Inventar platzieren (Mauern, Fallen).
- Während Welle: Live-Bauen erlaubt.

### 7.2 Spawn-Verhalten

- Wellen spawnen an den im Schematic definierten Spawn-Punkten.
- Mobs mit voller HP, mit ihrem Equipment-Snapshot.
- Spawn-Reihenfolge konfigurierbar (Default: alles auf einmal pro Welle).
- Welle 1 → Cleanup übriger Mobs → 10s Pause → Welle 2 (konfigurierbar).
- Hard-Timeout pro Welle: 10 min (konfigurierbar). Nach Timeout: Welle gilt als "nicht überlebt".

### 7.3 Tod im Battle

**Solo (1-Spieler-Team):** Permanent down. Kein Respawn. Statistik wird beim Tod eingefroren:
- Aktuelle Welle gilt als **nicht überlebt** (egal wie viele Mobs schon tot waren).
- Mob-Kills bis zum Tod zählen voll mit.
- Battle-Zeit = Zeit bis zum Tod.
Sieger wird nach diesen finalen Werten gegen die finalen Werte des Gegners verglichen (Wellen > Kills > Zeit).

**Team:** Tot bis Welle vorbei (Spectator-Modus). Bei Welle 2 Respawn mit allen Items, kein Drop. Wenn alle Teammates während Welle 1 sterben: Welle 1 gilt als "nicht überlebt", aber das Match geht weiter zu Welle 2 (alle respawnen mit allen Items). Sterben alle in Welle 2: beide Wellen gelten als "nicht überlebt", Match endet sofort.

### 7.4 Spectator-Logik

- Wenn ein Team beide Wellen vor dem Gegner abschließt, dürfen Teammitglieder via `/mab spectate <opponent-team>` zur Gegner-Arena. Sonst keine Live-Info zum Gegner.
- Besiegte Tournament-Teams: vollständiger Spectator-Zugriff auf alle aktiven Match-Welten bis Tournament-Ende.

## 8. Persistenz (SQLite)

Datei: `plugins/MobArmyBattle/data.db`

### 8.1 Schema

```sql
CREATE TABLE matches (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    winner_team_id INTEGER,
    mode TEXT NOT NULL  -- "1v1", "2v3", etc.
);

CREATE TABLE teams (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    match_id INTEGER NOT NULL REFERENCES matches(id),
    captain_uuid TEXT NOT NULL,
    waves_survived INTEGER DEFAULT 0,
    mob_kills INTEGER DEFAULT 0,
    battle_time_seconds INTEGER
);

CREATE TABLE team_members (
    team_id INTEGER NOT NULL REFERENCES teams(id),
    player_uuid TEXT NOT NULL,
    PRIMARY KEY (team_id, player_uuid)
);

CREATE TABLE wave_data (
    team_id INTEGER NOT NULL REFERENCES teams(id),
    wave_number INTEGER NOT NULL,
    mob_data BLOB NOT NULL,  -- serialisierter Wave-Inhalt (MobEntry-Liste mit Equipment)
    PRIMARY KEY (team_id, wave_number)
);

CREATE TABLE player_stats (
    player_uuid TEXT PRIMARY KEY,
    matches_total INTEGER DEFAULT 0,
    matches_won INTEGER DEFAULT 0,
    mob_kills_total INTEGER DEFAULT 0,
    avg_battle_time_seconds REAL DEFAULT 0,
    favorite_mob TEXT  -- am häufigsten in Wellen verwendet
);

CREATE INDEX idx_matches_ended ON matches(ended_at);
CREATE INDEX idx_player_stats_wins ON player_stats(matches_won DESC);
```

### 8.2 Leaderboard

```sql
SELECT player_uuid, matches_won, matches_total
FROM player_stats
ORDER BY matches_won DESC, matches_total ASC
LIMIT 10;
```

### 8.3 Fehler-Fallback

Wenn SQLite-Write fehlschlägt: Match-Replay als JSON-Backup in `plugins/MobArmyBattle/failed-writes/<timestamp>.json`. Plugin läuft weiter, Admin kann später manuell importieren.

## 9. UI / Anzeigen

### 9.1 Sidebar-Scoreboard

Sichtbar in allen Phasen außer Lobby. Inhalt dynamisch pro Phase:

**Farm-Phase:**
```
=== MobArmyBattle ===
Phase: Farm
Zeit: 47:23
─────────────────
Team-Pool:
 Zombie       x84
 Skelett(Iron)x12
 ...
─────────────────
Match: 4v4 vs Foo
```

**Wave-Build-Phase:**
```
=== MobArmyBattle ===
Phase: Wave-Build
Zeit: 03:41
─────────────────
Welle 1: 12 Mobs
Welle 2: 0 Mobs (leer!)
─────────────────
Match: 4v4 vs Foo
```

**Battle-Phase:**
```
=== MobArmyBattle ===
Phase: Battle - Welle 1
Zeit: 02:14
─────────────────
Mobs übrig: 8 / 20
Kills: 12
Team: 3/4 lebt
─────────────────
Match: 4v4 vs Foo
```

### 9.2 Match-Lifecycle-Notifications

- Match-Start: Title + Sound für alle Spieler
- Phase-Übergang: Title + Sound
- 1-Min-Warnung vor Phase-Ende: Action-Bar + Sound
- Welle-Spawn: Title + Sound
- Welle-Bestanden: Title + Sound
- Match-Sieger: Title + Sound + Chat-Broadcast

## 10. Befehle und Permissions

### 10.1 Spieler-Befehle

| Befehl | Permission | Beschreibung |
|---|---|---|
| `/mab create <mode>` | `mobarmybattle.create` | Match erstellen, Captain werden |
| `/mab join <captain>` | `mobarmybattle.join` | Match beitreten |
| `/mab leave` | `mobarmybattle.join` | Match verlassen |
| `/mab start` | `mobarmybattle.create` | Captain startet Match |
| `/mab kick <player>` | `mobarmybattle.create` | Captain kickt aus Team |
| `/mab spectate <matchId>` | `mobarmybattle.spectate` | Match-Welt als Spectator betreten |
| `/mab stats [player]` | `mobarmybattle.stats` | Eigene oder fremde Stats |
| `/mab leaderboard` | `mobarmybattle.stats` | Top-10-Liste |

### 10.2 Tournament-Befehle

| Befehl | Permission |
|---|---|
| `/mab tournament create <name>` | `mobarmybattle.tournament` |
| `/mab tournament join <name>` | `mobarmybattle.tournament` |
| `/mab tournament start` | `mobarmybattle.tournament` |
| `/mab tournament list` | `mobarmybattle.tournament` |

### 10.3 Admin-Befehle

| Befehl | Permission |
|---|---|
| `/mab forcecancel <matchId>` | `mobarmybattle.admin` |
| `/mab reload` | `mobarmybattle.admin` |
| `/mab cleanup` | `mobarmybattle.admin` (löscht Orphan-Welten manuell) |

### 10.4 Default-Permissions

- `mobarmybattle.*` (außer `.admin`) → alle Spieler.
- `mobarmybattle.admin` → nur OP.
- Granular konfigurierbar via permissions.yml.

## 11. Configuration

Datei: `plugins/MobArmyBattle/config.yml`

```yaml
phases:
  farm-duration-minutes: 60
  wave-build-duration-minutes: 5
  battle-prep-seconds: 30
  battle-wave-pause-seconds: 10
  battle-hard-timeout-minutes: 10
  bracket-pause-minutes: 1

farm:
  starter-kit: "leather_full"   # "none", "leather_full" (default), "iron_full", "custom"
  custom-kit: []                # Liste von ItemStacks wenn starter-kit=custom
  death-penalty: "soft"         # "drop_items", "soft" (default), "none", "hard"
  death-penalty-percent: 10     # für soft: % des Pool-Verlusts
  difficulty: "hard"            # "easy", "normal", "hard"
  eternal-night: false
  mob-spawn-cap-multiplier: 1.0 # 1.0 = vanilla, 3.0 = 3x
  world-border-radius: 1000

waves:
  point-budget-enabled: false
  point-budget-per-wave: 100
  point-costs:
    zombie: 1
    skeleton: 2
    creeper: 3
    witch: 5
    # weitere Mobs...
  equipment-cost-modifiers:
    iron-armor: 3
    diamond-armor: 8

arena:
  schematics-folder: "arenas/"
  selection: "random"           # "fixed" oder "random"
  fixed-schematic-name: ""      # bei selection=fixed

worlds:
  lobby-world-name: "mab_lobby"
  auto-generate-lobby: true
  cleanup-orphans-on-start: true

reconnect:
  grace-period-minutes: 5

stats:
  enable-leaderboard: true

permissions:
  default-player-perms: true   # alle non-admin perms an alle
```

## 12. Error-Handling

| Fehler | Verhalten |
|---|---|
| Spieler-Disconnect mid-match | 5-min-Reconnect-Grace, Slot freigehalten. Im Battle: Spieler "tot" bis Reconnect. |
| Captain-Disconnect | Auto-Promotion: nächstes Teammitglied (sortiert nach längster Match-Membership; bei Gleichstand zufällig). |
| Tournament: alle Teams einer Runde durch Disconnect raus | Match abgebrochen, Sieger-Heuristik: Team mit wenigsten Disconnects, sonst Random. |
| Welt-Lade-Fehler | Match abbrechen, alle Spieler zurück zur Lobby, Fehler in Log + Admin-Broadcast. |
| SQLite-Fehler beim Stats-Schreiben | Match-Replay als JSON in `failed-writes/`. Plugin läuft weiter. |
| Server-Crash mid-match | Beim nächsten Start: alle aktiven `mab_farm_*` / `mab_arena_*` Verzeichnisse als Orphans erkennen → löschen. Keine Match-Recovery. |
| Schematic-Datei fehlt/ungültig | Match-Erstellung verhindert, Captain bekommt Fehlermeldung. |
| Schematic ohne Spawn-Marker | Plugin nutzt zentrale Position als Default-Spawn-Punkt, warnt im Log. |

## 13. Testing-Strategie

### 13.1 Unit Tests (JUnit 5)

Bukkit-freie Domain-Logik:
- `MobPool` — Add/Remove, Equipment-Hash-Aggregation.
- `Match.calculateWinner()` — Hierarchische Sieger-Bestimmung.
- `Tournament` — Bracket-Aufbau, Bye-Logik, Pairing.
- `Wave` — Constraints (min. 1 Mob, Punkte-Budget).
- `MobEntry.getHash()` — Determinismus, Kollision.

### 13.2 Integration Tests

Mit MockBukkit-Library:
- Phase-Übergänge im `Match`.
- Listener-Reaktion auf `EntityDeathEvent`.
- Befehl-Parsing.

### 13.3 Manuelle Tests

Lokaler Paper-Server via `runServer` Gradle-Task. Test-Szenarien:
- Komplettes 1v1-Match (alle Phasen).
- 4v4-Match mit Captain-Wechsel via Disconnect.
- Tournament mit 5 Teams (Bye-Slot).
- Arena-Schematic ohne Spawn-Marker → Default-Verhalten.
- Server-Restart mid-match → Cleanup.

## 14. Implementierungs-Reihenfolge (Empfehlung)

Reihenfolge so gewählt, dass jeder Schritt eigenständig testbar ist:

1. Domain-Modell + State-Machine-Skelett (`Match`, `Team`, `MatchPhase`-Interface, leere Phase-Klassen).
2. Welt-Management (Lobby, Farm-Welt-Erstellung mit Seed, Cleanup).
3. Pool-System mit Equipment-Snapshot + Mob-Kill-Listener.
4. Wave-Build-GUI + Constraints.
5. Battle-Engine (Arena-Struktur-Loading via `StructureManager`, Wave-Spawning, Sieger-Berechnung).
6. Tournament + Bracket.
7. SQLite-Schicht + Stats + Leaderboard.
8. Spectator-System + Sidebar-Scoreboard + UI-Polish.
9. Config-Layer + Permissions + Befehle vollständig.
10. Testing + Bug-Fixing + manueller End-to-End-Test.

## 15. Offene Punkte / Annahmen

Punkte, die nicht explizit besprochen wurden, aber im Design getroffene Annahmen sind:

- **Death-Penalty-Prozent (soft):** Default 10% Pool-Verlust, in Config änderbar.
- **WorldBorder-Radius:** Default 1000 Blöcke, in Config änderbar.
- **Mob-Punkte-Liste:** Standardwerte vom Plugin vorgegeben, vom Admin in Config überschreibbar.
- **Strukturen-Format:** Vanilla NBT-Strukturen (Paper `StructureManager`-API), keine WorldEdit-Dependency.
- **Spawn-Marker:** Banner mit Lore-Tag `[MAB_SPAWN]`. Plugin scannt Arena nach Bannern, nimmt deren Positionen, entfernt sie nach dem Loading.
- **Tournament-Teilnahme nach Match-Start:** Captains, die zu spät joinen, werden abgelehnt.
- **Edge Case "alles gleich" beim Sieger:** Bei perfektem Tie nach allen Kriterien → zufälliger Sieger oder erneutes Match (Admin-Entscheidung in Config: `tie-resolution: "random"` oder `"replay"`).
