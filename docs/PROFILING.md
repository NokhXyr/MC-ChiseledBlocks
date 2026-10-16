# Profiling protocol

[English](#english) | [Français](#français)

---

## English

The same protocol measures Chisels & Bits (the baseline) and Chiseled Blocks, so the numbers compare.
Change one thing only between the two runs: the chiseling mod.

**Automated.** `./gradlew runClientBench` and `./gradlew runClientBenchCnb` play these scenes by
themselves (same settings, same scene built with each mod's own chisel) and write their results
to `run-bench*/playtest-report.txt`; Spark profiles are saved as files in
`run-bench*/config/spark/`. `./gradlew runClientSoak` is the multiplayer endurance run. Results:
`docs/benchmarks/`. The manual steps below remain valid for measuring on another setup.

### 1. Instance

- NeoForge 21.1.x for Minecraft 1.21.1, Java 21, 6 GB of memory.
- Mods: the chiseling mod under test (with its dependencies), and Spark for NeoForge 1.21.1.
  Nothing else. A second pass adds Sodium to check compatibility.
- Video settings: render distance 12, simulation distance 8, max framerate unlimited, VSync off,
  graphics Fancy, smooth lighting on, fullscreen at the monitor's native resolution.

### 2. Test world

1. Create a Superflat world, creative, peaceful, cheats on, fixed seed `chiseledblocks`.
2. `/gamerule doDaylightCycle false`, `/gamerule doWeatherCycle false`, `/time set noon`.
3. Carve eight reference blocks on one row at `x=0..7, y=-60, z=0`: a half slab, a stair, a
   sphere, a hollow cube, a slab of stone under glass, three materials mixed, a 1 pixel
   checkerboard area of 4x4x4, and a block with only one pixel removed.
4. Fill the scene by cloning that row:
   `/clone 0 -60 0 7 -60 0 8 -60 0` then repeat with growing ranges until the area
   `x=0..39, y=-60..-56, z=0..9` is filled (2000 chiseled blocks).
5. Build the control scene with vanilla blocks at `x=100..139, y=-60..-56, z=0..9` with
   `/fill 100 -60 0 139 -56 9 stone`.
6. Save the world and keep a copy of it. Every run starts from this copy.

### 3. Scenes

| Scene | What to do | Duration |
|---|---|---|
| S1 static | `/tp @s 20 -50 -15 0 30`, do not move | 60 s |
| S2 chiseling | Stand at the scene and carve pixels without stopping | 30 s |
| S3 chunk loading | `/tp @s 2000 -50 2000`, wait 10 s, `/tp @s 20 -50 -15 0 30`, wait 20 s | 30 s |
| S4 fly over | Fly in a straight line over the scene and back, creative flight, no sprint | 30 s |
| Control | `/tp @s 120 -50 -15 0 30`, do not move | 60 s |

### 4. What to record

For each scene:
- Client profile: `/sparkc profiler start --timeout <duration>`. Spark prints a link at the end.
- Server profile (S2 only): `/spark profiler start --timeout 30 --thread *`.
- FPS: open the debug screen (F3) and the frame time chart (F3 + 2). Note the average FPS and the
  lowest FPS seen.
- Memory after S1: `/sparkc heapsummary`.

Spark links are public pages. They show the player name and the hardware; do not share them
outside the project if that matters to you.

### 5. Where the results go

Write the links and the FPS numbers in `docs/benchmarks/<date>-<mod>.md`, one table per run,
with the instance details (mod versions, Sodium yes or no).

---

## Français

Le même protocole mesure Chisels & Bits (la référence) et Chiseled Blocks, pour que les chiffres soient
comparables. Entre les deux passages, une seule chose change : le mod de ciselage.

**Automatisé.** `./gradlew runClientBench` et `./gradlew runClientBenchCnb` jouent ces scènes tout
seuls (mêmes réglages, même scène construite avec le ciseau de chaque mod) et écrivent leurs
résultats dans `run-bench*/playtest-report.txt` ; les profils Spark sont enregistrés en fichiers
dans `run-bench*/config/spark/`. `./gradlew runClientSoak` est le test d'endurance multijoueur.
Résultats : `docs/benchmarks/`. Les étapes manuelles ci-dessous restent valables pour mesurer sur
une autre configuration.

### 1. Instance

- NeoForge 21.1.x pour Minecraft 1.21.1, Java 21, 6 Go de mémoire.
- Mods : le mod de ciselage testé (avec ses dépendances) et Spark pour NeoForge 1.21.1. Rien
  d'autre. Un second passage ajoute Sodium pour vérifier la compatibilité.
- Réglages vidéo : distance de rendu 12, distance de simulation 8, FPS max illimités, VSync
  désactivée, graphismes Détaillés, éclairage lissé activé, plein écran en résolution native.

### 2. Monde de test

1. Créer un monde Superflat, créatif, paisible, commandes activées, graine fixe `chiseledblocks`.
2. `/gamerule doDaylightCycle false`, `/gamerule doWeatherCycle false`, `/time set noon`.
3. Ciseler huit blocs de référence sur une ligne en `x=0..7, y=-60, z=0` : une demi-dalle, un
   escalier, une sphère, un cube creux, une dalle de pierre sous du verre, trois matériaux
   mélangés, un damier de 1 pixel sur une zone de 4x4x4, et un bloc auquel il manque un seul pixel.
4. Remplir la scène en clonant cette ligne :
   `/clone 0 -60 0 7 -60 0 8 -60 0`, puis recommencer avec des zones de plus en plus grandes
   jusqu'à remplir `x=0..39, y=-60..-56, z=0..9` (2000 blocs ciselés).
5. Construire la scène témoin en blocs vanilla en `x=100..139, y=-60..-56, z=0..9` avec
   `/fill 100 -60 0 139 -56 9 stone`.
6. Sauvegarder le monde et en garder une copie. Chaque passage repart de cette copie.

### 3. Scènes

| Scène | Action | Durée |
|---|---|---|
| S1 statique | `/tp @s 20 -50 -15 0 30`, ne pas bouger | 60 s |
| S2 ciselage | Se placer devant la scène et ciseler sans s'arrêter | 30 s |
| S3 chargement de chunks | `/tp @s 2000 -50 2000`, attendre 10 s, `/tp @s 20 -50 -15 0 30`, attendre 20 s | 30 s |
| S4 survol | Voler en ligne droite au-dessus de la scène et revenir, vol créatif, sans sprint | 30 s |
| Témoin | `/tp @s 120 -50 -15 0 30`, ne pas bouger | 60 s |

### 4. Ce qu'il faut relever

Pour chaque scène :
- Profil client : `/sparkc profiler start --timeout <durée>`. Spark affiche un lien à la fin.
- Profil serveur (S2 seulement) : `/spark profiler start --timeout 30 --thread *`.
- FPS : ouvrir l'écran de debug (F3) et le graphique des temps de frame (F3 + 2). Noter le FPS
  moyen et le FPS le plus bas observé.
- Mémoire après S1 : `/sparkc heapsummary`.

Les liens Spark sont des pages publiques. Ils affichent le pseudo du joueur et le matériel ; ne
les partage pas hors du projet si cela compte pour toi.

### 5. Où ranger les résultats

Noter les liens et les FPS dans `docs/benchmarks/<date>-<mod>.md`, un tableau par passage, avec
les détails de l'instance (versions des mods, Sodium oui ou non).
