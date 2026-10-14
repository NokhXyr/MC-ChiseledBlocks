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

### Modifications

- **Autorisation modpacks** — La licence autorise désormais explicitement l'inclusion du fichier du mod publié, non modifié, dans n'importe quel modpack, sans demander.

---
