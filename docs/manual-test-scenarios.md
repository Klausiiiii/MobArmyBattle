# Manuelle Test-Szenarien — MobArmyBattle

Abhakbare Smoke-Test-Checkliste für `./gradlew.bat runServer`. Stand: 2026-06-06.

---

## 0. Wichtig vorab — bitte zuerst lesen

Du hast nach einer "bereit machen"-Phase gefragt. **Es gibt keine klassische Ready-/Bereit-Mechanik.** Stattdessen:

- In **WAVE_BUILD** ist "bereit" = der **Captain finalisiert beide Wellen** (Confirm oder Forfeit pro Welle) im Wave-Build-GUI. Erst wenn **alle aktiven Teams** beide Wellen finalisiert haben, startet die Battle-Phase. Die Tests dazu stehen unter **§7**.
- Das ist der einzige Punkt im Spiel, an dem auf Spieler-Input "gewartet" wird. Es gibt keinen Ready-Button in Lobby/Farm — dort treibt der **Host** die Übergänge per Command/GUI, bzw. der Farm-Timer.

### Bekannte Schwachstellen — hier gezielt draufschauen (Details in §16)
- ⚠️ **WAVE_BUILD hat KEINEN Timeout.** Wenn ein Captain nie bestätigt (und nicht disconnected), hängt das Match dort **ewig**. `wave-build-duration-min` aus der config wird nicht ausgewertet.
- ⚠️ **Veralteter Text:** Beim Start der Battle-Phase wird `"Alle Wellen abgeschlossen — Battle-Phase startet (Stub)"` gebroadcastet. Die Phase ist **nicht** mehr Stub — der Text ist nur alt.
- ⚠️ **config.yml-Kommentar veraltet:** `prep-duration-sec` / `wave-pause-sec` / `wave-hard-timeout-min` sind als *"Reserved for Plan 10 (currently no effect)"* markiert, laut PLAN.md ist Plan 10 aber implementiert. **Verifizieren, ob diese Werte im Battle tatsächlich wirken** (§8).

### Priorität (laut PLAN.md noch nie manuell getestet)
1. **NEU & uncommitted, nirgends im PLAN:** TeamSelectorGui, ChatInputManager, PlayerFreezeManager, WaveBuildProtectionListener, TeamVisibility → §3, §7.
2. **"Smoke-Test offen":** Plan 8 (Spectator/Sidebar, §9), Plan 9 (Config/Permissions, §13/§14), Plan 10 (Battle-Timing/Admin, §8, `/mab kick`, `/mab forcecancel`).

### Setup-Hinweise
- Mehrere Spieler: zweiter (Alt-)Account oder lokaler Bot-Client; ohne 2. Spieler sind die meisten Mehr-Spieler-Tests nicht möglich.
- Zum schnellen Testen Config-Dauern runtersetzen: `farm-duration-min: 1`, `wave-hard-timeout-min: 1`, `post-battle-view-sec: 2`, `reconnect.grace-sec: 15` (in `plugins/MobArmyBattle/config.yml`, dann `/mab reload` **vor** Match-Erstellung — laufende Matches behalten ihren Snapshot, §13).
- Op-Defaults: `mobarmybattle.lobby.bypass`, `mobarmybattle.wavebuild.bypass`, `.admin`, `.reload` sind nur für Ops. Für realistische Spieler-Tests einen **Nicht-Op**-Account verwenden.

---

## 1. Match-Erstellung (Command + GUI)

- [x] `/mab create` ohne Argument → Match mit max. Teamgröße 1, Ersteller ist Captain Team 1 + Host.
- [x] `/mab create 2` → max. Teamgröße 2.
- [x] `/mab create 0` → Fehler "max-team-size muss >= 1 sein".
- [x] `/mab create -3` → Fehler.
- [x] `/mab create abc` → Fehler "Ungültige max-team-size".
- [x] `/mab create` während man bereits in einem Match ist → wird abgelehnt (kein Doppel-Match).
- [x] Rechtsklick auf Menü-Villager in der Lobby → MabMenuGui öffnet sich.
- [x] GUI: "Match erstellen" → Größenauswahl → Config-Screen → Submit → Match existiert, Lobby-Broadcast "klick-to-join" erscheint.
- [x] Nach Erstellung per GUI **und** per Command erscheint derselbe Lobby-weite Join-Broadcast (`broadcastNewMatch`).

## 2. Spieleranzahl & Team-Bildung

Decke gezielt **1 / 2 / 3 / 5** Spieler ab.

- [x] **1 Spieler**, `/mab create 1`, dann `/mab start` → abgelehnt: nur 1 Team aktiv, `canStart()` = false ("beide Teams brauchen mind. 1 Spieler").
- [ ] **2 Spieler**, maxTeamSize 1 (1v1): P2 joint → neues Team 2, P2 ist Captain. `/mab start` → klappt.
- [ ] **3 Spieler**, maxTeamSize 1 → 3 Solo-Teams. Start mit 3 aktiven Teams möglich (FFA).
- [ ] **4 Spieler**, maxTeamSize 2 (2v2): Auto-Balance füllt Team 1 (2), dann Team 2 (2). Start klappt.
- [ ] **3 Spieler**, maxTeamSize 2 (ungerade): Team 1 voll (2), Team 2 hat 1 → Start-Verhalten prüfen (Team 2 darf noch jemanden aufnehmen; mit 2 aktiven Teams sollte Start gehen).
- [ ] **5 Spieler**, maxTeamSize 2: erwartet Team1=2, Team2=2, Team3=1 (drittes Team wird neu erstellt). FFA-Start mit 3 Teams prüfen.
- [ ] **5 Spieler**, maxTeamSize 5: alle landen in Team 1 → nur 1 aktives Team → Start abgelehnt (kein Gegner).
- [ ] **10 Spieler**, maxTeamSize 5 (5v5): zwei volle Teams. Start klappt. (Falls 10 Accounts machbar.)
- [x] Join in volles Team (über Command mit explizitem Index) → Fehler "Team ist voll".
- [ ] Sidebar-Footer zeigt bei FFA korrekt "Teams aktiv: X/Y".

## 3. Beitreten — TeamSelectorGui / ChatInput (NEU, ungetestet)

- [x] `/mab join <captain>` bei maxTeamSize 1 → direkter Beitritt, keine GUI.
- [x] `/mab join <captain>` bei maxTeamSize > 1 → **TeamSelectorGui** öffnet sich.
- [x] `/mab join` ohne Captain-Arg → Usage-Fehler.
- [x] `/mab join <offline-spieler>` → "Spieler nicht online".
- [x] `/mab join <captain-ohne-match>` → "Dieser Captain hat kein Match".
- [x] `/mab join <eigener-name>` → "Du kannst nicht deinem eigenen Match joinen".
- [ ] Bereits in Match A, dann `/mab join` in Match B → wird aus A entfernt, in B aufgenommen.
- [x] **TeamSelector:** Klick auf öffentliches Team (nicht voll) → sofortiger Beitritt.
- [x] **TeamSelector:** Klick auf volles Team → "Team ist voll", kein Beitritt.
- [x] **TeamSelector:** neues **öffentliches** Team erstellen → man wird Captain, andere sehen Broadcast.
- [x] **TeamSelector:** neues **privates** Team erstellen → man wird Captain; fremder Spieler kann nur per Invite rein.
- [x] **TeamSelector:** privates Team beitreten **ohne** Einladung → "du brauchst eine Einladung".
- [x] **TeamSelector:** privates Team beitreten **mit** Einladung → klappt.
- [x] **TeamSelector → Passwort-Team erstellen:** Chat-Prompt erscheint; Passwort eingeben → Team erstellt.
- [x] **Passwort-Team beitreten:** Chat-Prompt; **richtiges** Passwort → Beitritt.
- [x] **Passwort-Team beitreten:** **falsches** Passwort → abgelehnt.
- [x] **ChatInput abbrechen:** im Prompt `cancel` tippen → "Abgebrochen", kein Beitritt/keine Team-Erstellung.
- [x] **ChatInput Timeout:** Prompt offen lassen (~30 s) → "Eingabe abgelaufen", Aktion abgebrochen.
- [x] **ChatInput Disconnect:** während offenem Prompt ausloggen → kein Crash, sauberer Abbruch.
- [x] **ChatInput doppelt:** zweiten Prompt auslösen, während erster offen ist → alter wird abgebrochen, neuer aktiv.
- [x] Während eines aktiven ChatInput-Prompts getippter Text taucht **nicht** im normalen Chat auf (wird abgefangen).
- [ ] Passwort-Team per Command beitreten (`/mab join captain <index>`) → Hinweis "bitte über das Menü beitreten".

## 4. Einladen & Kicken

- [x] `/mab invite <player>` als Captain → Ziel bekommt klickbare "[Annehmen]"-Nachricht.
- [x] Klick auf "[Annehmen]" → Ziel tritt korrektem Team bei.
- [x] `/mab invite` als **Nicht-Captain** → "Nur der Captain darf einladen".
- [x] `/mab invite` bei vollem Team → "Dein Team ist voll".
- [x] `/mab invite <offline>` → "Spieler nicht online".
- [x] `/mab invite <bereits-in-match>` → "ist bereits in einem Match".
- [x] `/mab kick <member>` als Captain → Member raus + Lobby-Teleport, beide bekommen Meldung.
- [x] `/mab kick` als Nicht-Captain → "Nur der Captain kann kicken".
- [x] `/mab kick <self>` → "Du kannst dich nicht selbst kicken — nutze /mab leave".
- [x] `/mab kick <fremdes-team-member>` → "Spieler ist nicht in deinem Team".
- [x] `/mab kick <offline>` → "nicht online".

## 5. Verlassen & Host-Cascade

- [ ] **Normaler Member** `/mab leave` → nur er raus, Team schrumpft, Match läuft weiter.
- [ ] **Captain (nicht Host) mit Member** verlässt → Member wird zum neuen Captain promotet.
- [ ] **Captain (nicht Host) allein** verlässt → Team wird disbanded (captainId null), Match läuft weiter wenn ≥1 anderes Team da.
- [ ] **Host verlässt** (`/mab leave`) → **alle** anderen Spieler werden in die Lobby evakuiert ("Der Host hat das Match verlassen"), Match wird aufgelöst (`cascadeIfHostLeaving` **vor** `leaveMatch`).
- [ ] Host verlässt via **GUI**-Leave-Button → gleiches Cascade-Verhalten wie per Command.
- [ ] `/mab leave` ohne Match und ohne Spectator-Status → "Du bist in keinem Match".
- [ ] Nach Host-Leave: kein Spieler hängt in "Geister"-Match (neues `/mab create` möglich, kein "bereits in Match"-Fehler).

## 6. Phasen-Übergänge (LOBBY → FARM → WAVE_BUILD → BATTLE → FINISHED)

- [ ] `/mab start` als **Host** in LOBBY mit ≥2 Teams → FARM-Phase, Farm-Welten erzeugt, BossBar/Sidebar zeigen FARM.
- [ ] `/mab start` als **Nicht-Host** → "Nur der Host darf starten".
- [ ] `/mab start` zweimal / nicht in LOBBY → "Match ist bereits gestartet".
- [ ] `/mab endfarm` als Host in FARM → WAVE_BUILD.
- [ ] `/mab endfarm` als Nicht-Host → abgelehnt.
- [ ] `/mab endfarm` außerhalb FARM → "Match ist nicht in Farm-Phase".
- [ ] **Farm-Auto-Timeout:** `auto-farm-transition: true`, kurze `farm-duration-min` → nach Ablauf automatisch WAVE_BUILD.
- [ ] **Farm-Timeout aus:** `auto-farm-transition: false` → Farm endet **nie** automatisch, nur `/mab endfarm` hilft.
- [ ] Battle endet → FINISHED: Spieler zurück in Lobby, GameMode SURVIVAL, Arena-Welten werden gelöscht.
- [ ] FINISHED ist terminal: keine weiteren Phasenwechsel (kein Re-Trigger von Battle o.ä.).
- [ ] Während BATTLE/FINISHED: `/mab start`, `/mab endfarm`, `/mab join` werden korrekt abgelehnt.

## 7. WAVE_BUILD — Finalisierung (= "bereit"), Freeze, Bauschutz

**Das ist die "Bereit"-Phase, nach der du gefragt hast.**

- [ ] `/mab build` als Captain in WAVE_BUILD → Wave-Build-GUI öffnet.
- [ ] `/mab build` als **Nicht-Captain** → "Nur der Captain darf Wellen bauen".
- [ ] `/mab build` außerhalb WAVE_BUILD → "geht nur in der Wave-Build-Phase".
- [ ] Pool-Mob anklicken → +1 in aktuelle Welle; Shift-Klick → +5. Klick auf Wellen-Item → -1; Shift → -5.
- [ ] Verteilung respektiert Pool-Gesamtmenge (3 Mobs total → max. 3 über beide Wellen verteilbar).
- [ ] Welle "Bestätigen" (Confirm) → Welle finalisiert, danach **nicht** mehr editierbar.
- [ ] Welle "Aufgeben" (Forfeit) → Welle gilt als finalisiert mit 0 Mobs; Broadcast "hat Welle X aufgegeben".
- [ ] Leere Welle bestätigen → abgelehnt (Welle braucht mind. 1 Mob) bzw. nur Forfeit möglich.
- [ ] **Beide** Wellen finalisiert → GUI schließt, Meldung "beide Wellen abgeschlossen".
- [ ] **Übergang:** Sobald **alle** aktiven Teams beide Wellen finalisiert haben → BATTLE startet automatisch.
- [ ] ⚠️ **Ein Captain bestätigt NIE:** Match bleibt in WAVE_BUILD hängen (kein Timeout). **Erwartetes Fehlverhalten bestätigen** — dann entscheiden, ob Timeout gebaut werden muss.
- [ ] **Leerer Pool:** Team das 0 Mobs gefarmt hat → wird beim WAVE_BUILD-Eintritt eliminiert ("euer Team scheidet aus und schaut zu").
- [ ] **Nur 1 Mob im Pool:** Captain-Hinweis "nur Welle 1 baubar, Welle 2 muss aufgegeben werden".
- [ ] **< 2 aktive Teams** nach Eliminierung → Match springt direkt zu FINISHED ("Zu wenige aktive Teams").
- [ ] **Freeze (NEU):** Nicht-Captain-Mitglieder können sich in WAVE_BUILD nicht bewegen.
- [ ] **Freeze:** auch **Kamera drehen** (Yaw/Pitch) ist geblockt — Blick schnappt zurück.
- [ ] **Freeze:** Captain selbst ist ebenfalls eingefroren, baut aber im GUI.
- [ ] **Freeze + Disconnect:** in WAVE_BUILD ausloggen und wieder einloggen → **nicht** erneut an alter Position eingefroren (Anchor wird beim Join verworfen).
- [ ] **Freeze endet** beim Übergang zu BATTLE (alle wieder beweglich).
- [ ] **Bauschutz (NEU):** in WAVE_BUILD Block abbauen/setzen → gecancelt.
- [ ] **Bauschutz:** Eimer leeren/füllen → gecancelt.
- [ ] **Bauschutz:** Mob mit Schwert schlagen → gecancelt; Mob mit Pfeil treffen → gecancelt.
- [ ] **Bauschutz greift phasenbasiert**, nicht weltbasiert (auch außerhalb Farm-Welt aktiv).
- [ ] **Bauschutz-Bypass:** Op mit `mobarmybattle.wavebuild.bypass` darf bauen/schlagen.
- [ ] Mobs in der Farm-Welt sind während WAVE_BUILD eingefroren (keine AI, keine neuen Spawns).

## 8. BATTLE — Timing, Spawns, Sieger (Plan 10, Smoke-Test offen)

- [ ] Battle startet: beide Teams werden in ihre Arena-Welten teleportiert.
- [ ] **Prep-Phase:** vor Welle 1 läuft `prep-duration-sec`-Countdown; Sidebar zeigt "Bauphase - W1" + "Spawn in: MM:SS", Title "Bauphase" + Sound. **(config.yml-Kommentar behauptet 'no effect' — prüfen, ob es wirklich wirkt!)**
- [ ] Welle 1 spawnt korrekt aus dem Pool — **inkl. Equipment** der Mobs (Signatur-Rückwandlung).
- [ ] Mobs sind anfangs gespawnt, kämpfen gegeneinander.
- [ ] Nach Welle 1: `wave-pause-sec`-Pause, dann Prep + Welle 2.
- [ ] **Hard-Timeout:** Welle räumt sich nach `wave-hard-timeout-min` nicht auf → Mobs werden gelöscht, Team gilt als nicht überlebt; Title "Zeit abgelaufen". (Mit `wave-hard-timeout-min: 1` testbar.)
- [ ] Sieger-Ermittlung korrekt (mehr überlebte Wellen / mehr Kills laut BattleResult-Hierarchie).
- [ ] Spieler stirbt im Battle → Spectator-GameMode, Death-Spectate-GUI öffnet (→ §9).
- [ ] In Arena: kein Item-Drop, kein XP-Drop, Inventar bleibt (KeepInventory).
- [ ] Bei FFA mit >2 Teams: Pairings bilden 2er-Gruppen, ungerades Team bekommt **Bye** (wird als Spectator in eine zufällige Arena gesetzt).
- [ ] `post-battle-view-sec` greift: Verlierer schauen kurz zu, bevor sie in die Lobby teleportiert werden.

## 9. Spectator (Plan 8, Smoke-Test offen)

- [ ] **Death-Spectate:** im Battle sterben → GUI mit zusehbaren Captains; Auswahl wechselt Blickwinkel.
- [ ] Death-Spectate-Ende → zurück in die **Lobby** (keine alte Position gespeichert).
- [ ] `/mab spectate` (ohne Arg) → DeathSpectateGui mit zulässigen Captains.
- [ ] `/mab spectate <captain>` als **Pair-Partner**, dessen eigene Wellen **beide fertig** sind → erlaubt, Gegner-Arena zuschaubar.
- [ ] `/mab spectate <captain>` während eigene Wellen **noch laufen** → abgelehnt.
- [ ] **Bye-Team** darf jeden aktiven Captain im selben Match zuschauen.
- [ ] **Tournament-eliminierter** Spieler darf Captains **desselben** Tournaments zuschauen.
- [ ] **Cross-Match-Schutz:** Spieler aus Match A darf Match B **nicht** zuschauen ("Du bist in einem anderen Match").
- [ ] `/mab spectate <offline>` → "ist nicht online".
- [ ] **Spectator disconnect** → `endSpectate`, beim Rejoin zurück an returnLocation (Command-Spectate) bzw. Lobby (Death-Spectate).
- [ ] `/mab leave` als Spectator → Spectator-Modus beendet ("Spectator-Mode beendet").
- [ ] Bei Match-Ende werden alle Spectators evakuiert (`evictAll`).
- [ ] Sidebar zeigt in jeder Phase die richtigen Infos (FARM Pool-Top-7, WAVE_BUILD, BATTLE Pair-Captain).

## 10. Lobby-Schutz

- [ ] Block abbauen/setzen in der Lobby → gecancelt (als Nicht-Op).
- [ ] Eimer leeren/füllen in der Lobby → gecancelt.
- [ ] Block rechtsklicken (Interaktion) in der Lobby → gecancelt.
- [ ] Anderen Spieler in der Lobby schlagen (PvP) → gecancelt.
- [ ] Menü-Villager schlagen → kein Schaden (invulnerable).
- [ ] Unter y=0 fallen → Teleport zurück zum Lobby-Spawn.
- [ ] Keine natürlichen Mob-Spawns in der Lobby.
- [ ] Op mit `mobarmybattle.lobby.bypass` → darf bauen/interagieren (außer Villager-Schaden).

## 11. Respawn-Routing

- [ ] Tod in **FARM** → Respawn in der eigenen Farm-Welt (sicherer höchster Block).
- [ ] Tod in **Arena (Battle)** → bleibt Spectator in der Arena (kein Lobby-Respawn).
- [ ] Tod in Lobby / Default-Welt → Respawn am Lobby-Spawn.
- [ ] Death-Penalty in FARM greift je nach `death-penalty.mode` (SOFT/HARD/NONE/DROP_ITEMS): Pool-Anteil wird abgezogen / Items gedroppt — Verhalten je Modus prüfen.

## 12. Reconnect-Grace

- [ ] In **FARM** ausloggen → Spieler bleibt im Match (Grace läuft, default 300 s; zum Test kürzen).
- [ ] **Rejoin vor Ablauf** → zurück in die Farm-Welt teleportiert, weiter im Team.
- [ ] **Rejoin nach Ablauf** → aus Match entfernt, in der Lobby.
- [ ] `grace-sec: 0` → kein Aufschub, sofortiges `leaveMatch` beim Quit.
- [ ] Mehrfaches Quit→Rejoin→Quit → Tasks werden sauber gecancelt/neu gesetzt, kein Doppel-Leave.
- [ ] **Host** disconnectet in FARM → Verhalten prüfen: Cascade greift bei Disconnect (vs. nur bei `/mab leave`)? Gezielt verifizieren, ob andere evakuiert werden oder im Grace hängen.
- [ ] **Captain** disconnectet während WAVE_BUILD → Wave-Build-Session schließt; bei Rejoin nicht eingefroren; aber: kann er hängende Finalisierung noch abschließen? (verknüpft mit ⚠️ §7-Timeout)
- [ ] Beim `onDisable` (Plugin-Reload/Server-Stop) werden alle Grace-Tasks gecancelt (kein Zombie-State).

## 13. Config — global vs. per-Match & Reload

- [ ] Per-Match-Config im Erstell-Flow (CONFIG-Screen) ändern → Match nutzt die geänderten Werte, andere Matches nicht.
- [ ] Host editiert Config in LOBBY über das Menü → `match.setMabConfig` greift für dieses Match.
- [ ] Config-Edit-Button ist **host-only** und nur in **LOBBY** sichtbar/aktiv.
- [ ] `/mab reload` während ein Match läuft → laufendes Match behält seinen Snapshot, nur neue Matches sehen neue Werte.
- [ ] Jeder CONFIG-Slot: Linksklick +, Rechtsklick −, Shift = größere Schritte; Werte werden auf erlaubte Grenzen geclamped.
- [ ] CONFIG-Felder testen: Farm-Dauer, Wave-Build-Dauer, Prep, Wave-Pause, Hard-Timeout, Auto-Farm-Toggle, Starter-Kit-Cycle, Death-Penalty-Cycle, Farm-Border (an/aus + Radius), Arena-Border (an/aus + Radius), Mob-Multiplier, Post-Battle-View.
- [ ] **Ungültige config.yml** (z.B. `farm-duration-min: 0`, `mob-spawn-multiplier: 0`) → Plugin fällt auf Defaults zurück, kein Crash, Warnung im Log.
- [ ] Starter-Kit-Typen NONE / LEATHER_FULL / IRON_FULL / CUSTOM → korrektes Equipment beim Farm-Start.
- [ ] Farm-Border (Radius 200 default) und Arena-Border (50 default) sind tatsächlich aktiv.
- [ ] Mob-Spawn-Multiplier (2.0 default) erhöht sichtbar die Spawnrate in der Farm-Welt.

## 14. Permissions

- [ ] Nicht-Op **ohne** `mobarmybattle.reload` → `/mab reload` abgelehnt.
- [ ] Nicht-Op **ohne** `mobarmybattle.admin` → `/mab forcecancel` abgelehnt.
- [ ] `/mab forcecancel <matchId>` als Admin → Match sofort beendet, alle raus, Arenen aufgeräumt (Warnung bei Tournament-Match).
- [ ] `/mab forcecancel <ungültige-id>` → "Kein Match mit ID ...".
- [ ] Default-true-Permissions (`.create.match`, `.join`, `.spectate`, `.stats`) → normaler Spieler kann diese Commands nutzen.
- [ ] Permission explizit entziehen (z.B. `.join` auf false) → entsprechende Commands abgelehnt.
- [ ] Backward-Compat-Alias `mobarmybattle.create` funktioniert noch.

## 15. Stats & Leaderboard

- [ ] Nach abgeschlossenem Match: `/mab stats` zeigt aktualisierte W/L + Mob-Kills.
- [ ] `/mab stats <player>` für anderen Spieler → dessen Stats.
- [ ] `/mab stats <offline>` → "nicht online".
- [ ] `/mab leaderboard` / `/mab top` → Top 10.
- [ ] Server ohne funktionierende DB (z.B. `data.db` schreibgeschützt) → Plugin läuft weiter, nur Log-Warnung, kein Crash.
- [ ] Solo: Mob-Kills landen voll beim Spieler; Team: Kills werden gleichmäßig aufgeteilt.

## 16. Tournament (Solo-Only v1)

- [ ] `/mab tournament create <name>` → REGISTERING.
- [ ] Captains `/mab tournament join <name>` → registriert (nicht, wenn schon in Match/Tournament).
- [ ] `/mab tournament start` als **Master** mit ≥2 Captains → Runde 1 gepaart, Matches starten in FARM.
- [ ] Start als Nicht-Master → abgelehnt.
- [ ] **2 Captains:** A vs B → Sieger = Champion.
- [ ] **3 Captains:** ein Bye; Bye-Captain rollt in Runde 2 ("direkt in der nächsten Runde").
- [ ] **5 Captains:** Runde 1 = 2 Matches + 1 Bye; danach korrekte Bracket-Fortsetzung.
- [ ] **Forfeit:** Captain disconnectet während laufender Runde → Gegner gewinnt automatisch, Match → FINISHED.
- [ ] Captain verlässt während REGISTERING → einfach deregistriert.
- [ ] Round-Advance erst nach 15-Sek-Pause.
- [ ] Tournament-Match ist immer maxTeamSize 1 (Multi-Player-Team-Join nicht möglich).

## 17. Kombinierte / Robustheits-Szenarien

- [ ] Voller Durchlauf 1v1: create → join → start → farmen → endfarm → beide Wellen bauen → Battle → Sieger → FINISHED → Stats aktualisiert.
- [ ] Voller Durchlauf 2v2 mit Team-Mitgliedern (nicht nur Captains).
- [ ] FFA mit 3 Teams kompletter Durchlauf inkl. Bye im Battle.
- [ ] Spieler stirbt in jeder Phase einmal (Farm/Battle) → korrektes Respawn/Spectate.
- [ ] `/mab forcecancel` mitten in FARM, WAVE_BUILD und BATTLE — jeweils sauberer Abbruch ohne hängende Spieler/Welten.
- [ ] Plugin-Reload (`onDisable`/`onEnable`) während aktivem Match → Orphan-Welten (`mab_farm_*`, `mab_arena_*`) werden beim nächsten Start gelöscht.
- [ ] Zwei parallele Matches gleichzeitig → keine Interferenz (Welten, Sidebars, BossBars, Pools getrennt).

---

## Verdächtige Stellen, die ich beim Lesen des Codes gefunden habe

Diese gezielt prüfen — hier ist ein echtes Problem wahrscheinlich:

1. **WAVE_BUILD ohne Timeout** (`WaveBuildPhase.tick`): hängt, wenn ein Captain nie finalisiert. Realistisches AFK-/Trotz-Szenario. → Test in §7.
2. **Veralteter "(Stub)"-Broadcast** beim Battle-Start (`WaveBuildPhase` Zeile ~142): nur kosmetisch, aber irritierend.
3. **config.yml-Kommentar** sagt `prep`/`wave-pause`/`hard-timeout` hätten "no effect" — laut PLAN.md (Plan 10) tun sie das aber. Einer von beiden ist falsch → in §8 verifizieren, ob das Battle-Timing real greift.
4. **Host-Disconnect vs. Host-`/mab leave`**: Cascade ist für den freiwilligen Leave-Pfad belegt; beim reinen Disconnect (Grace) gezielt prüfen, ob die anderen Mitglieder ebenfalls korrekt behandelt werden. → §12.
