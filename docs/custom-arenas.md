# Eigene Arenen einbinden

Die Kampf-Arenen werden **nicht über die Config gewählt**, sondern über `.nbt`-Strukturdateien
im Ordner `plugins/MobArmyBattle/arenas/`. Beim Dev-Server (`./gradlew.bat runServer`) ist das
`run/plugins/MobArmyBattle/arenas/`.

- Liegt dort **mindestens eine** `.nbt`-Datei, wird **pro Kampf eine zufällig** ausgewählt
  (`ArenaLoader#loadInto`).
- Liegt dort **nichts**, baut das Plugin die prozedurale Fallback-Arena:
  30×30 Bedrock-Plattform auf Y 63, vier rote Banner als Mob-Spawn-Marker in den Ecken,
  Spieler-Spawn auf (0, 64, 0).
- Der Ordner wird beim Plugin-Enable automatisch angelegt. Dateien können hineinkopiert werden,
  ohne den Server neu zu starten — gescannt wird beim Start jedes Kampfes. Die Dateiendung muss
  `.nbt` sein.

## 1. Map als `.nbt` exportieren

Es muss das **Vanilla-Strukturformat** sein (`org.bukkit.structure.StructureManager` /
Strukturblock), **nicht** das WorldEdit-`.schem`-Format.

Praktischster Weg:

1. Map im Spiel mit einem Vanilla-**Strukturblock** im *Save*-Modus markieren und speichern.
2. Die Datei landet als `<welt>/generated/minecraft/structures/<name>.nbt`.
3. Diese Datei nach `plugins/MobArmyBattle/arenas/` kopieren (Name egal, Hauptsache `.nbt`).

> **Größenlimit:** Strukturblöcke speichern max. **48×48×48**. Größere Maps gehen so nicht direkt —
> dann splitten oder einen externen Converter nutzen (z. B. Litematica/WorldEdit-Schematic →
> `.nbt`).

## 2. Mob-Spawn-Punkte setzen

Vor dem Speichern in der Map **Banner** platzieren und im Amboss umbenennen auf **exakt**
`[MAB_SPAWN]` (Banner-Typ ist egal).

`ArenaSpawnScanner` sucht nach dem Kampfstart alle Banner mit diesem Custom-Name, merkt sich die
Position (+0.5 / +0.5 zentriert) und **entfernt das Banner**, bevor die gegnerische Welle dort
spawnt. Findet er kein einziges → alle Mobs spawnen am Welt-Spawn (also auf einem Haufen).

> Der Scanner sieht nur Banner in **geladenen Chunks**. Bei Arenen rund um den Welt-Spawn ist das
> kein Problem (Structure-Placement lädt die betroffenen Chunks). Bei sehr weitläufigen Maps mit
> Bannern weit weg vom Ursprung kann es klemmen — Marker möglichst nah am Strukturursprung halten.

## 3. Platzierung verstehen

- Die Struktur wird mit **Ursprung = `world.getSpawnLocation()`** der Arena-Welt platziert
  (Rotation `NONE`, Mirror `NONE`, zufällige Palette, Integrität 1.0).
- **Entities werden mitkopiert** (`includeEntities = true`) — Armor Stands, Item Frames etc. aus
  der Map landen in der Arena. Ungewollte Entities vorher entfernen.
- **Spieler-Spawn = Welt-Spawn + (0.5, 1, 0.5)** — also genau 1 Block über der Ursprungsecke der
  Struktur. **Alle Spieler eines Teams spawnen auf diesem einen Punkt.** An der relativen Position
  `(0, 0, 0)` der Struktur muss ein fester Bodenblock liegen.
- Die Arena-Welt ist eine **flache VOID-Welt** (`LobbyChunkGenerator`, `WorldType.FLAT`) — alles
  außerhalb der Struktur ist Leere. Die Map muss ihren eigenen Boden/Rand mitbringen.
- `time` ist fest auf Tag, `ADVANCE_TIME` ist aus.
- Beim Teleport in die Arena: Gamemode `SURVIVAL`, volle HP, volles Essen.

## 4. World-Border anpassen

In `config.yml`:

```yaml
arena:
  border:
    enabled: true
    radius: 50      # Border-Größe = radius * 2  →  100 × 100 Blöcke um den Welt-Spawn
```

Für größere Maps `radius` erhöhen oder `enabled: false` setzen, sonst landen Teile der Map
außerhalb der Border.

## Bekannte Einschränkungen

- **Kein In-Game-Befehl** zum Speichern/Verwalten von Arenen — Dateien manuell in den Ordner legen.
- **Keine gezielte Zuordnung** Arena ↔ Match/Turnier — es wird immer zufällig aus dem Ordner
  gewählt.
- Spieler-Spawn ist ein einzelner fester Punkt, kein Spawn-Bereich pro Team.

Wenn etwas davon gebraucht wird (z. B. `/mab arena save`, feste Arena pro Turnier-Runde,
mehrere Spieler-Spawnpunkte), muss es erst implementiert werden.
