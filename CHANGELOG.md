# Changelog

All notable changes to Bitsmith are documented here.

---

## [Unreleased]

### Added

- **Project foundations** — NeoForge 1.21.1 project, mod entry point, specification, profiling protocol.
- **Voxel core** — Immutable bit packed 16x16x16 grid with canonical palette and content hashing, shared instances for identical carvings, greedy mesher with outer face flags, greedy collision box merger and exact voxel raycast. Covered by unit tests and a micro-benchmark task.
- **Carved block** — Any full, simple block can be carved bit by bit with the iron chisel (left click, held for a steady rate). A carved block that becomes empty turns into air, one that becomes a single material turns back into the plain block.
- **Chunk mesh rendering** — Carved blocks render inside the normal chunk mesh, with no block entity renderer. Meshes are cached by grid content and shared between identical carvings; each edit marks its render section dirty once.
- **Collision and outline** — Shapes are built from the grid in one linear pass and shared between identical carvings.
- **Network** — The server is authoritative, applies all edits of a block once per tick and sends one update per block per tick, as a delta of the changed bits or as the whole grid when that is smaller. Chisel actions are checked for reach, build permission and rate.
- **Game tests** — Headless in game tests for conversion, batching, rate limit, reach check, block reverting, save and load, and shapes (`./gradlew runGameTestServer`).
- **Chisel modes and sizes** — Single bit, cube, line, plane and sphere modes at 1, 2, 4 or 8 bits, stored on the chisel and changed with V (mode) and B (size). The outline shows exactly the box the next stroke covers.
- **Bits as items** — Chiseled bits go to the player in survival, as stackable bit items per material. Right click places a bit against the clicked face, in the same carved block or in the empty block next to it.
- **Bit bag** — Collects chiseled bits automatically (16 materials, 8 blocks worth each). Held in hand it places the selected material (V cycles it); sneak and right click in the air takes a stack out.
- **Survival rules** — The iron chisel cuts what an iron pickaxe can harvest and loses one durability per stroke. Recipes for the chisel and the bag.
- **Carved block item** — Breaking a carved block drops it with its carving; placing it restores the carving. Pick block keeps the carving too. Carved blocks and bits render in hand and in slots from the same cached meshes.
- **Client prediction** — Strokes on a carved block show at once on the client; a refused stroke is undone by the server, and a lasting mismatch triggers a resync.
- **Radial menu** — Hold Left Alt with the chisel or the bag in hand: the chisel shows its modes (outer ring) and sizes (inner ring), the bag its materials. Point and release, or click. The centre holds undo (left) and redo (right).
- **Undo and redo** — Ctrl+Z and Ctrl+Y undo and redo the last 64 carving actions of each player (strokes and bit placements). In survival the bits follow: undoing a stroke takes its bits back, undoing a placement returns the bit. A step whose block changed since is dropped instead of overwriting the newer work.
- **Patterns** — A blank pattern (3 paper and an iron nugget) records a carved block on right click. In print mode the pattern fills the empty space next to the clicked face (or in the clicked block when sneaking); in carve mode it cuts its shape out of the clicked block. The outline previews the shape and where it goes, V or the tool menu switches the mode, sneak and right click in the air wipes it. In survival printing takes the bits from the inventory and bags, and lists what is missing; carving gives the bits. Every use can be undone.
- **Mirror** — A per player mirror (off, X, Y, Z, X+Z) repeats every chisel stroke and bit placement on the other side of the block. Set with N or the outer ring of the tool menu; the outline shows the mirrored strokes in blue. In survival a mirrored placement uses one bit per placed bit.
- **Wrench** — Right click turns a carved block a quarter turn around the clicked face's axis; sneaking mirrors it. Oriented materials follow horizontal turns and mirrors. Can be undone.
- **Saw** — Right click cuts a 1, 2, 4 or 8 bit slice off the clicked side and hands it over as a carved block with its shape; the rest stays. Thickness with B or the tool menu, orange outline preview.
- **Tape measure** — Right click two bits: a yellow box shows the measure, the size, distance and volume in bits show above the hotbar. Sneak and right click in the air clears it. Client side only.
- **Tool menu sizing** — The radial menu shrinks to fit the screen at large GUI scales.
- **Compatibility** — Checked in game with Sodium, Iris (with a shader pack), ModernFix (dynamic model loading on), FerriteCore, Lithium, ImmediatelyFast, Entity Culling, WorldEdit, Create and KubeJS through `./gradlew runClientCompat`. See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
- **Turning with other tools** — Carved blocks have a horizontal facing; WorldEdit `//rotate`, structure blocks and Create rotations now turn the carving with the block.
- **Chisels & Bits import** — Worlds saved with Chisels & Bits 1.21.1 open with Bitsmith instead: chiseled blocks load as carved blocks with the same shape and materials, then are saved in Bitsmith's format. Checked against blocks carved with Chisels & Bits itself (0 voxel difference). Items are not converted. See [docs/CNB_FORMAT.md](docs/CNB_FORMAT.md).
- **Performance and endurance runs** — `runClientBench` and `runClientBenchCnb` play the same scenes with Bitsmith and with Chisels & Bits (Spark profiles saved to files), `runClientSoak` has bot players and the real client carve the same blocks. Results in [docs/benchmarks/](docs/benchmarks/): coming back to a build, 1 % low 312 fps against 32 and worst frame 83 ms against 527 ms; carving, worst frame 9.6 ms against 27.4 ms; a 1 hour soak of 72000 bot operations without a desync.
- **Client playtest** — `./gradlew runClientTest` plays the mod like a player in a fresh flat world (key presses, menus, clicks), checks each step, takes screenshots and writes `run-clienttest/playtest-report.txt`, then quits. It uses its own game directory.
- **`/bitsmith` command** — For operators. `find radius <blocks>`, `find chunks <chunks>` and `find loaded` list carved blocks nearest first, optionally only those containing a material (`/bitsmith find loaded minecraft:oak_planks`); `find loaded` also lists the chunks holding the most. `map [radius]` draws the chunks around the player in the chat: loaded or not, and how many carved blocks each holds. Every result is a link: click it to teleport on top of the block. Searches read an index of the loaded carved blocks, never the world block by block, and never load a chunk.
- **Per block profiling** — `/bitsmith perf [seconds]` measures Bitsmith's server time for each carved block (chisel strokes, applying edits, shape queries, saving, loading) and lists the costliest blocks in µs per tick, with teleport links. `/bitsmith perf stop` ends it early. Nothing is measured outside a window.
- **One item id per material** — Every carvable material gets its own carved block item at startup, `bitsmith:carved/<block>` for Minecraft and `bitsmith:carved/<mod>/<block>` for other mods (382 in vanilla, 533 with the compatibility mod set). Breaking, pick block and the saw give the item of the carving's main material; the item tag `#bitsmith:carved_blocks` holds them all. Without a carving (from `/give`, a recipe or an item list) an item shows and places an example step in its material. Models and the tag are generated in memory, so nothing is logged as missing. Placed carved blocks stay one block.
- **Carved block names and tooltips** — A carved block item is named after its main material ("Carved Oak Planks", "Carved Stone +2" when other materials are mixed in). Its tooltip lists each material with a colour swatch, its share and its bits, then how full the block is. Tooltips of Bitsmith's items use the admin panel's dark and blue colours.
- **Admin panel** — `/bitsmith` (or a key, unbound by default) opens a panel for operators with three tabs: Search (scope, radius, material with suggestions, results table with material icons), Chunk map (chunks coloured by carved block count, player position and facing, click a chunk to list its blocks) and Performance (duration, start and stop, progress, per block table in µs per tick). Every row has a TP button. All requests are checked on the server for operator rights.
- **Command playtest** — `./gradlew runClientAdmin` uses the panel with the mouse and keyboard (tabs, typing a material, clicking TP buttons and map cells, a measurement while carving), then the text commands from the chat.

### Fixed

- **Machines holding a chisel** — A refused stroke by a fake player (bots, machines of other mods such as a Create deployer) threw an exception. Fake players are no longer sent a correction they cannot receive.
- **Create could not move carved blocks** — Carved blocks were marked immovable, which only stopped Create. They now ride contraptions and keep their carving.
- **Empty carved blocks** — A carved block left without a carving (WorldEdit `//set`, damaged data) is removed at the end of the tick instead of staying as an invisible shell.
- **Radial menu choice** — An option picked by moving and releasing within the same frame is now taken into account.
- **Carving at the edge of a hole** — Repeated clicks on the edge of a hole kept targeting the bit already removed. The targeted bit is now found by tracing the view ray through the carving.
- **Dark inner faces** — Faces inside a carved block no longer render almost black with smooth lighting: they now use the light of the block itself instead of ambient occlusion sampled from the solid blocks around.

### Changed

- **Modpack permission** — The license now explicitly allows including the unmodified released mod file in any modpack, without asking.

### Ajouts

- **Fondations du projet** — Projet NeoForge 1.21.1, point d'entrée du mod, cahier des charges, protocole de profilage.
- **Noyau voxel** — Grille 16x16x16 immuable et compactée, palette canonique et hash du contenu, instances partagées pour les sculptures identiques, greedy mesher avec signalement des faces extérieures, fusion des boîtes de collision et raycast exact par voxel. Couvert par des tests unitaires et une tâche de micro-benchmark.
- **Bloc ciselé** — N'importe quel bloc plein et simple peut être ciselé bit par bit avec le ciseau en fer (clic gauche, en continu si on le maintient). Un bloc ciselé vidé devient de l'air, un bloc redevenu d'un seul matériau redevient le bloc d'origine.
- **Rendu dans le maillage des chunks** — Les blocs ciselés sont rendus dans le maillage normal des chunks, sans block entity renderer. Les maillages sont mis en cache selon le contenu de la grille et partagés entre sculptures identiques ; chaque édition ne marque sa section de rendu qu'une fois.
- **Collision et contour** — Les formes sont construites depuis la grille en une seule passe linéaire et partagées entre sculptures identiques.
- **Réseau** — Le serveur fait autorité, applique toutes les éditions d'un bloc une fois par tick et envoie une seule mise à jour par bloc et par tick, sous forme de delta des bits modifiés ou de grille entière si elle est plus petite. Les actions du ciseau sont vérifiées : portée, droit de construire, cadence.
- **Game tests** — Tests en jeu headless pour la conversion, le regroupement par tick, la limite de cadence, la vérification de portée, le retour au bloc d'origine, la sauvegarde et le rechargement, et les formes (`./gradlew runGameTestServer`).
- **Modes et tailles du ciseau** — Modes bit seul, cube, ligne, plan et sphère, en tailles de 1, 2, 4 ou 8 bits, enregistrés sur le ciseau et changés avec V (mode) et B (taille). Le contour montre exactement la zone que le prochain coup va toucher.
- **Bits en items** — En survie, les bits ciselés vont au joueur sous forme d'items empilables par matériau. Le clic droit pose un bit contre la face visée, dans le même bloc ciselé ou dans le bloc vide voisin.
- **Sac à bits** — Récupère automatiquement les bits ciselés (16 matériaux, l'équivalent de 8 blocs chacun). En main, il pose le matériau sélectionné (V pour changer) ; accroupi + clic droit dans le vide pour sortir une pile.
- **Règles de survie** — Le ciseau en fer coupe ce qu'une pioche en fer peut récolter et perd une durabilité par coup. Recettes pour le ciseau et le sac.
- **Item de bloc ciselé** — Un bloc ciselé cassé se ramasse avec sa sculpture ; le reposer la restaure. Le pick block garde aussi la sculpture. Blocs ciselés et bits s'affichent en main et dans les slots à partir des mêmes maillages en cache.
- **Prédiction côté client** — Les coups sur un bloc ciselé s'affichent immédiatement côté client ; un coup refusé est annulé par le serveur, et un décalage durable déclenche une resynchronisation.
- **Menu radial** — Alt gauche maintenu avec le ciseau ou le sac en main : le ciseau affiche ses modes (anneau extérieur) et ses tailles (anneau intérieur), le sac ses matériaux. On pointe et on relâche, ou on clique. Le centre contient annuler (gauche) et rétablir (droite).
- **Annuler et rétablir** — Ctrl+Z et Ctrl+Y annulent et rétablissent les 64 dernières actions de ciselage de chaque joueur (coups et poses de bits). En survie les bits suivent : annuler un coup reprend ses bits, annuler une pose rend le bit. Une étape dont le bloc a changé depuis est abandonnée au lieu d'écraser le travail plus récent.
- **Patterns** — Un pattern vierge (3 papiers et une pépite de fer) enregistre un bloc ciselé d'un clic droit. En mode imprimer, le pattern remplit l'espace vide à côté de la face visée (ou dans le bloc visé en étant accroupi) ; en mode creuser, il découpe sa forme dans le bloc visé. Le contour montre la forme et où elle va, V ou le menu de l'outil change le mode, accroupi + clic droit dans le vide l'efface. En survie, imprimer prend les bits dans l'inventaire et les sacs et liste ce qui manque ; creuser rend les bits. Chaque utilisation peut être annulée.
- **Miroir** — Un miroir par joueur (aucun, X, Y, Z, X+Z) répète chaque coup de ciseau et chaque pose de bit de l'autre côté du bloc. Réglé avec N ou l'anneau extérieur du menu de l'outil ; le contour montre les coups miroir en bleu. En survie, une pose en miroir consomme un bit par bit posé.
- **Clé** — Clic droit : le bloc ciselé tourne d'un quart de tour autour de l'axe de la face visée ; accroupi, il est retourné en miroir. Les matériaux orientés suivent les rotations et miroirs horizontaux. Annulable.
- **Scie** — Clic droit : découpe une tranche de 1, 2, 4 ou 8 bits du côté visé et la donne comme bloc ciselé avec sa forme ; le reste reste en place. Épaisseur avec B ou le menu de l'outil, aperçu en orange.
- **Mètre ruban** — Clic droit sur deux bits : une boîte jaune montre la mesure, la taille, la distance et le volume en bits s'affichent au-dessus de la barre. Accroupi + clic droit dans le vide l'efface. Uniquement côté client.
- **Taille du menu de l'outil** — Le menu radial rétrécit pour tenir à l'écran aux grandes échelles d'interface.
- **Compatibilité** — Vérifiée en jeu avec Sodium, Iris (avec un shader), ModernFix (chargement dynamique des modèles activé), FerriteCore, Lithium, ImmediatelyFast, Entity Culling, WorldEdit, Create et KubeJS via `./gradlew runClientCompat`. Voir [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
- **Rotation par d'autres outils** — Les blocs ciselés ont une orientation horizontale ; `//rotate` de WorldEdit, les blocs structure et les rotations de Create tournent maintenant la sculpture avec le bloc.
- **Import Chisels & Bits** — Les mondes sauvegardés avec Chisels & Bits 1.21.1 s'ouvrent avec Bitsmith à la place : les blocs ciselés se chargent comme blocs Bitsmith avec la même forme et les mêmes matériaux, puis sont enregistrés au format Bitsmith. Vérifié sur des blocs ciselés avec Chisels & Bits lui-même (0 voxel de différence). Les items ne sont pas convertis. Voir [docs/CNB_FORMAT.md](docs/CNB_FORMAT.md).
- **Mesures de performance et d'endurance** — `runClientBench` et `runClientBenchCnb` jouent les mêmes scènes avec Bitsmith et avec Chisels & Bits (profils Spark enregistrés en fichiers), `runClientSoak` fait ciseler les mêmes blocs à des robots et au vrai client. Résultats dans [docs/benchmarks/](docs/benchmarks/) : retour vers une construction, 1 % bas 312 FPS contre 32 et pire image 83 ms contre 527 ms ; ciselage, pire image 9,6 ms contre 27,4 ms ; une endurance d'une heure de 72000 opérations de robots sans désynchronisation.
- **Partie de test client** — `./gradlew runClientTest` joue le mod comme un joueur dans un monde plat neuf (touches, menus, clics), vérifie chaque étape, prend des captures et écrit `run-clienttest/playtest-report.txt`, puis quitte. Il utilise son propre dossier de jeu.

### Correctifs

- **Machines tenant un ciseau** — Un coup refusé d'un faux joueur (robots, machines d'autres mods comme un déployeur de Create) levait une exception. Les faux joueurs ne reçoivent plus de correction qu'ils ne peuvent pas recevoir.
- **Create ne déplaçait pas les blocs ciselés** — Les blocs ciselés étaient marqués inamovibles, ce qui ne bloquait que Create. Ils voyagent maintenant sur les contraptions et gardent leur sculpture.
- **Blocs ciselés vides** — Un bloc ciselé resté sans sculpture (`//set` de WorldEdit, données abîmées) est retiré à la fin du tick au lieu de rester une coque invisible.
- **Choix dans le menu radial** — Une option choisie en bougeant puis en relâchant dans la même image est maintenant prise en compte.
- **Ciseler au bord d'un trou** — Des clics répétés au bord d'un trou visaient toujours le bit déjà retiré. Le bit visé est désormais trouvé en suivant le rayon du regard à travers la sculpture.
- **Faces intérieures sombres** — Les faces à l'intérieur d'un bloc ciselé ne sont plus presque noires avec l'éclairage lissé : elles utilisent la lumière du bloc lui-même au lieu de l'occlusion ambiante calculée sur les blocs pleins autour.

### Modifications

- **Autorisation modpacks** — La licence autorise désormais explicitement l'inclusion du fichier du mod publié, non modifié, dans n'importe quel modpack, sans demander.

---
