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
- **Client playtest** — `./gradlew runClientTest` plays the mod like a player in a fresh flat world (key presses, menus, clicks), checks each step, takes screenshots and writes `run-clienttest/playtest-report.txt`, then quits. It uses its own game directory.

### Fixed

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
- **Partie de test client** — `./gradlew runClientTest` joue le mod comme un joueur dans un monde plat neuf (touches, menus, clics), vérifie chaque étape, prend des captures et écrit `run-clienttest/playtest-report.txt`, puis quitte. Il utilise son propre dossier de jeu.

### Correctifs

- **Choix dans le menu radial** — Une option choisie en bougeant puis en relâchant dans la même image est maintenant prise en compte.
- **Ciseler au bord d'un trou** — Des clics répétés au bord d'un trou visaient toujours le bit déjà retiré. Le bit visé est désormais trouvé en suivant le rayon du regard à travers la sculpture.
- **Faces intérieures sombres** — Les faces à l'intérieur d'un bloc ciselé ne sont plus presque noires avec l'éclairage lissé : elles utilisent la lumière du bloc lui-même au lieu de l'occlusion ambiante calculée sur les blocs pleins autour.

### Modifications

- **Autorisation modpacks** — La licence autorise désormais explicitement l'inclusion du fichier du mod publié, non modifié, dans n'importe quel modpack, sans demander.

---
