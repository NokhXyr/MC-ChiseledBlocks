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

### Modifications

- **Autorisation modpacks** — La licence autorise désormais explicitement l'inclusion du fichier du mod publié, non modifié, dans n'importe quel modpack, sans demander.

---
