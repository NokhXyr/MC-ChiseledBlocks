# Bitsmith: Specification

Author: NokhXyr
Status: draft for validation

[English](#english) | [Français](#français)

---

## English

### 1. Purpose

Bitsmith lets players carve any block into a 16x16x16 grid of bits, combine materials inside one
block, copy and reprint carvings, and use precision tools, like Chisels & Bits does. Its reason to
exist is performance: carving and displaying carved blocks must not cost frames, must not stutter
while editing, and must stay stable on servers.

Bitsmith is an independent implementation. No code, texture, model, sound or language file is
taken from Chisels & Bits. The README credits Chisels & Bits as the inspiration.

| Item | Value |
|---|---|
| Name / mod id | Bitsmith / `bitsmith` |
| Package | `com.nokhxyr.bitsmith` |
| Target | Minecraft 1.21.1, NeoForge 21.1.x, Java 21 |
| License | All Rights Reserved |
| Author | NokhXyr |

### 2. Where the lag comes from in the reference mod

Read from the source of Chisels & Bits (branch `version/1.21.1`). These are code findings, not
measurements; section 8 describes how they get measured with Spark.

| # | Finding | Effect in game |
|---|---|---|
| F1 | The cache key of a block model (`ChiseledBlockBakedModelManager`) contains the render seed of the block position and the full neighbourhood. In vanilla 1.21.1 the seed is derived from the position for every block, so two identical carvings at two positions never share a model. | Every carved block builds its own model. The cache size barely matters. |
| F2 | A model is built on a worker thread, then `sendBlockUpdated` is called (`ChiseledBlockModelDataExecutor`). The block change that started the work had already marked the section dirty. | Each edit rebuilds the 16x16x16 render section twice. |
| F3 | On chunk load every carved block entity schedules its own model build (`ChiseledBlockModelUpdateHandler`), each ending in `sendBlockUpdated`. | N carved blocks in a section cause up to N rebuilds of that section: stutter when flying into a build. |
| F4 | Every `setChanged` runs a light check, a full block update (`UPDATE_ALL`) and a neighbour update, then sends the whole block payload (storage plus statistics, including collision bit sets) to every player tracking the chunk (`UpdateBlockEntityPacket`). | Heavy server ticks and network bursts while carving. |
| F5 | Building a shape cache key copies the whole storage (`Identifier` takes a snapshot). | Allocation on every shape cache lookup. |
| F6 | A collision shape is built by OR-ing one box shape at a time (`VoxelShapeCalculator`, `Shapes.joinUnoptimized` in a reduce). Each join walks the whole accumulated shape. | Quadratic cost on complex carvings, paid again after every edit. |

Greedy meshing already exists in Chisels & Bits (`GreedyMeshBuilder`). The cost is therefore not
the meshing itself but how often it runs, how little is shared, and the extra rebuilds around it.
Bitsmith's design targets F1 to F6 directly.

### 3. Performance goals

Measured with the protocol of section 8, on the same machine and scene as the Chisels & Bits
baseline. A goal is met only when measured.

| Id | Goal | Target |
|---|---|---|
| P1 | Static scene: 2000 carved blocks in view | Average FPS at least 90 % of the same scene built from vanilla blocks |
| P2 | No per frame cost for static carved blocks | 0 block entity renderers, 0 shape computations per frame |
| P3 | Editing | No frame above 2x the median frame time while carving continuously for 30 s |
| P4 | Section rebuilds per edit | Exactly 1 per affected section per tick, whatever the number of bits changed |
| P5 | Chunk load | 1 rebuild per section, never one per carved block |
| P6 | Mesh build, one block, worst realistic case | Under 0.3 ms on the build machine (pure core benchmark) |
| P7 | Network, single bit edit | Under 128 bytes on the wire |
| P8 | Memory, two material block | At most 600 bytes of voxel payload; identical carvings shared |
| P9 | Server tick, one player carving at full speed | Under 1 ms per tick spent in Bitsmith |
| P10 | Stability | No crash, no desync, no lost bits after 1 h of carving in multiplayer |

### 4. Architecture

```
core.voxel      pure Java, no Minecraft classes, unit tested and benchmarked
  BitGrid         immutable 16^3 grid, sorted palette, bit packed, content hash
  BitGridBuilder  mutable working copy for edits, packed once per batch
  BitGridInterner one shared instance per distinct content
  GreedyMesher    rectangles per face and material, outer faces flagged
  BoxMerger       greedy 3D boxes for collision and outline
  BitRaycast      exact voxel picking by grid traversal
block           CarvedBlock, CarvedBlockEntity (holds an interned BitGrid)
client.render   CarvedBakedModel, MeshCache, item model
shape           ShapeCache: BitGrid -> VoxelShape
network         action packets (client to server), delta packets (server to client)
item            chisel, bit, bit bag, patterns, wrench, mirror, tape measure, saw
compat.cnb      Chisels & Bits importer
```

**4.1 Storage.** A carved block entity holds one reference to an interned `BitGrid`. Material ids
map to block states through the vanilla block state id registry. The encoding is canonical
(sorted palette), so equality and hashing are by content: identical carvings share one grid, one
mesh and one shape (answers F1 and F5). A grid that becomes a single material turns back into the
plain vanilla block; an empty grid becomes air.

**4.2 Rendering.** No block entity renderer. The carved block uses a custom baked model that reads
the grid from the block entity's model data and returns quads during the normal chunk section
build, which already runs on worker threads.
- The mesh is cached by grid content only: neighbours are not part of the key (answers F1).
- Faces on the block boundary are emitted with their cull face, so vanilla culls them when the
  neighbour is a full block. Against a carved neighbour, outer quads are filtered with a 16x16
  occupancy mask of the touching neighbour face: a cheap pass, outside the cache.
- Texture, tint and render type come from each material's own model. The texture variant is
  fixed per material and not per position, which keeps the cache shared (a deliberate trade-off).
- No extra block update after meshing: one dirty mark per section per tick (answers F2 and F3).
- Compatibility with Sodium and Iris on NeoForge is a requirement, tested at every milestone.

**4.3 Collision, outline and picking.** The shape is built once per distinct grid from the boxes of
`BoxMerger` (or directly from a bit set discrete shape) and cached by content (answers F6). Picking
a bit uses `BitRaycast`, never the shape. The outline drawn around the target covers only the
voxels the current tool mode will affect.

**4.4 Editing and network.** The server is authoritative.
- The client sends an action (tool, mode, size, target block, target voxel, face, sequence id).
- The client applies the same deterministic operation at once (prediction) so the result shows
  with no delay; the server confirms or corrects it.
- The server applies all edits of a block within one tick to a builder, packs once, then sends
  one delta packet per block per tick (changed region and its voxels), or the whole grid when that
  is smaller (answers F4).
- Light is rechecked only when emission or light blocking changes. Neighbours are notified only
  when the occupancy of a boundary face changes.

**4.5 Items.** Carved block items carry their grid in a data component. Item models are cached by
content with a bounded LRU cache.

**4.6 Adaptive detail.** Tools work at 1, 2, 4 or 8 pixel sizes. Storage cost follows complexity:
one material costs nothing per voxel, two materials cost one bit per voxel, and so on.

### 5. Features for the first release

**Core**
- Chisel with modes: single bit, line, plane, cube, sphere; selectable size 1, 2, 4, 8.
- Chiseled bits drop as bit items (one item per material; one bit is one voxel, 1/4096 of a block).
- Placing bits from the hand onto any surface, including onto vanilla blocks.
- Several materials inside one block, including transparent ones and blocks with tint.
- Bit bag: stores bits by material, fed automatically while carving.

**Patterns**
- Blank pattern to copy a carved block.
- Positive pattern (print the shape), negative pattern (carve the shape out).
- Printing uses bits from the inventory or the bag; creative mode needs none.

**Advanced tools**
- Mirror / symmetry plane applied to carving and placing.
- Wrench: rotate and flip a carved block.
- Tape measure: distances and volumes in bits.
- Saw: split a carved block into slices along an axis.

**Chisels & Bits import**
- Converts carved blocks saved by Chisels & Bits 1.21.1 into Bitsmith blocks, on chunk load,
  through registry aliases, so a world can be migrated without Chisels & Bits installed.
- The save format is studied from saved data and documented in `docs/`; the reader is written
  from that documentation.

**Out of scope for the first release:** Fabric, other Minecraft versions, the bit tank and other
storage blocks, Create contraption support, a chiseled printer block.

### 6. Milestones

| Milestone | Content | Exit criteria |
|---|---|---|
| M0 Foundations | Project, voxel core, tests, benchmarks | Build green, core benchmarks published, Spark baseline of Chisels & Bits recorded |
| M1 Carved block | Block, block entity, storage, render, shape, single bit chisel, network | P2, P4, P5, P6, P7 met; Sodium renders correctly |
| M2 Core gameplay | Modes and sizes, bits, placement, bag, survival rules | P3, P9 met; feature checklist passes |
| M3 Patterns | Blank, positive, negative patterns | Feature checklist passes |
| M4 Advanced tools | Mirror, wrench, tape measure, saw | Feature checklist passes |
| M5 Import | Format study, importer, migration test world | A test world converts with no lost block |
| M6 Release candidate | Full Spark comparison, Iris, multiplayer soak test | P1 to P10 met and documented |

**Progress (2026-10-15).** M0 to M5 done. M6: measured against Chisels & Bits in
`docs/benchmarks/2026-10-15-m6.md` and `2026-10-15-soak.md`. P1, P2, P4, P6, P7, P8 and P9 met;
P5 met by design; P10 met over a 10 minute soak, the 1 hour run remains; P3 not met as written (a
2x median frame rule is unreachable above 1000 fps), a new wording is proposed in the report and
waits for approval.

### 7. Engineering rules

- `core.voxel` never imports Minecraft classes; every algorithm there has unit tests.
- No allocation per frame in render paths. No block entity ticking.
- Every cache is bounded and cleared on resource reload.
- Every network packet is validated on the server: reach distance, permissions, item counts.
- Each milestone ends with a Spark comparison saved under `docs/benchmarks/`.

### 8. Measurement protocol

See `docs/PROFILING.md`. Summary: the same instance settings and the same scenes for Chisels &
Bits and for Bitsmith; Spark client and server profiles plus average and 1 % low FPS for four
scenes (static view, continuous carving, chunk loading, fly over).

### 9. Risks

| Risk | Mitigation |
|---|---|
| Sodium or Iris bypass part of the model pipeline | Test at M1; fall back to Sodium's model API if needed |
| Smooth lighting looks wrong on inner faces | Compare with flat lighting at M1; disable AO on inner quads if needed |
| Chisels & Bits save format is compressed and undocumented | Time boxed format study before M5; import stays optional |
| Worst case carvings (checkerboard) produce many quads | Measured in the core benchmark; acceptable because rare and cached |
| All Rights Reserved blocks pack makers from including the mod | Resolved: modpack exception in LICENSE and README |

---

## Français

### 1. Objet

Bitsmith permet de ciseler n'importe quel bloc en une grille de 16x16x16 bits, de mélanger
plusieurs matériaux dans un même bloc, de copier et réimprimer des sculptures et d'utiliser des
outils de précision, comme Chisels & Bits. Sa raison d'être est la performance : ciseler et
afficher des blocs ciselés ne doit pas coûter de FPS, ne doit pas saccader pendant l'édition et
doit rester stable sur serveur.

Bitsmith est une implémentation indépendante. Aucun code, texture, modèle, son ou fichier de
langue n'est repris de Chisels & Bits. Le README cite Chisels & Bits comme source d'inspiration.

| Élément | Valeur |
|---|---|
| Nom / mod id | Bitsmith / `bitsmith` |
| Package | `com.nokhxyr.bitsmith` |
| Cible | Minecraft 1.21.1, NeoForge 21.1.x, Java 21 |
| Licence | All Rights Reserved (tous droits réservés) |
| Auteur | NokhXyr |

### 2. D'où vient le lag dans le mod de référence

Constats tirés du code source de Chisels & Bits (branche `version/1.21.1`). Ce sont des constats
de code, pas des mesures ; la section 8 décrit comment ils sont mesurés avec Spark.

| # | Constat | Effet en jeu |
|---|---|---|
| F1 | La clé de cache d'un modèle de bloc (`ChiseledBlockBakedModelManager`) contient la graine de rendu de la position du bloc et tout le voisinage. En vanilla 1.21.1, cette graine dépend de la position pour tous les blocs : deux sculptures identiques à deux endroits ne partagent jamais leur modèle. | Chaque bloc ciselé construit son propre modèle. La taille du cache ne change presque rien. |
| F2 | Le modèle est construit sur un thread de travail, puis `sendBlockUpdated` est appelé (`ChiseledBlockModelDataExecutor`). La modification qui a lancé ce travail avait déjà marqué la section comme à refaire. | Chaque édition reconstruit deux fois la section de rendu 16x16x16. |
| F3 | Au chargement d'un chunk, chaque block entity ciselée lance sa propre construction de modèle (`ChiseledBlockModelUpdateHandler`), chacune terminée par `sendBlockUpdated`. | N blocs ciselés dans une section provoquent jusqu'à N reconstructions de cette section : saccades en arrivant près d'une construction. |
| F4 | Chaque `setChanged` déclenche une vérification de lumière, une mise à jour complète du bloc (`UPDATE_ALL`) et une mise à jour des voisins, puis envoie tout le contenu du bloc (stockage et statistiques, bit sets de collision compris) à tous les joueurs qui suivent le chunk (`UpdateBlockEntityPacket`). | Ticks serveur lourds et pics réseau pendant le ciselage. |
| F5 | Construire une clé de cache de forme copie tout le stockage (`Identifier` prend un snapshot). | Allocation à chaque consultation du cache de formes. |
| F6 | La forme de collision est construite en unissant une boîte à la fois (`VoxelShapeCalculator`, `Shapes.joinUnoptimized` dans un reduce). Chaque union reparcourt toute la forme déjà accumulée. | Coût quadratique sur les sculptures complexes, payé à nouveau après chaque édition. |

Le greedy meshing existe déjà dans Chisels & Bits (`GreedyMeshBuilder`). Le coût ne vient donc pas
du maillage lui-même, mais de sa fréquence, du peu de partage et des reconstructions en trop
autour. L'architecture de Bitsmith vise directement F1 à F6.

### 3. Objectifs de performance

Mesurés avec le protocole de la section 8, sur la même machine et la même scène que la référence
Chisels & Bits. Un objectif n'est atteint que s'il est mesuré.

| Id | Objectif | Cible |
|---|---|---|
| P1 | Scène statique : 2000 blocs ciselés à l'écran | FPS moyen d'au moins 90 % de la même scène construite en blocs vanilla |
| P2 | Aucun coût par frame pour les blocs ciselés statiques | 0 block entity renderer, 0 calcul de forme par frame |
| P3 | Édition | Aucune frame au-dessus de 2 fois la frame médiane pendant 30 s de ciselage continu |
| P4 | Reconstructions de section par édition | Exactement 1 par section touchée et par tick, quel que soit le nombre de bits modifiés |
| P5 | Chargement de chunk | 1 reconstruction par section, jamais une par bloc ciselé |
| P6 | Construction du maillage, un bloc, pire cas réaliste | Moins de 0,3 ms sur la machine de build (benchmark du noyau) |
| P7 | Réseau, édition d'un seul bit | Moins de 128 octets transmis |
| P8 | Mémoire, bloc à deux matériaux | Au plus 600 octets de données voxel ; sculptures identiques partagées |
| P9 | Tick serveur, un joueur qui cisèle à pleine vitesse | Moins de 1 ms par tick passée dans Bitsmith |
| P10 | Stabilité | Aucun crash, aucune désynchronisation, aucun bit perdu après 1 h de ciselage en multijoueur |

### 4. Architecture

```
core.voxel      Java pur, aucune classe Minecraft, testé et mesuré
  BitGrid         grille 16^3 immuable, palette triée, données compactées, hash du contenu
  BitGridBuilder  copie modifiable pour l'édition, compactée une fois par lot
  BitGridInterner une seule instance partagée par contenu distinct
  GreedyMesher    rectangles par face et par matériau, faces extérieures signalées
  BoxMerger       boîtes 3D fusionnées pour la collision et le contour
  BitRaycast      visée exacte d'un voxel par parcours de grille
block           CarvedBlock, CarvedBlockEntity (référence une BitGrid partagée)
client.render   CarvedBakedModel, MeshCache, modèle d'item
shape           ShapeCache : BitGrid -> VoxelShape
network         paquets d'action (client vers serveur), paquets delta (serveur vers client)
item            ciseau, bit, sac à bits, patterns, clé, miroir, mètre ruban, scie
compat.cnb      import Chisels & Bits
```

**4.1 Stockage.** Une block entity ciselée garde une seule référence vers une `BitGrid` partagée.
Les identifiants de matériau correspondent aux block states via le registre vanilla des block
states. L'encodage est canonique (palette triée), donc l'égalité et le hash portent sur le
contenu : des sculptures identiques partagent une grille, un maillage et une forme (répond à F1
et F5). Une grille qui ne contient plus qu'un matériau redevient le bloc vanilla ; une grille vide
devient de l'air.

**4.2 Rendu.** Aucun block entity renderer. Le bloc ciselé utilise un modèle baked personnalisé
qui lit la grille dans les model data de la block entity et fournit ses quads pendant la
construction normale de la section de chunk, qui tourne déjà sur des threads de travail.
- Le maillage est mis en cache selon le seul contenu de la grille : les voisins ne font pas
  partie de la clé (répond à F1).
- Les faces au bord du bloc portent leur face de culling, donc vanilla les masque quand le voisin
  est un bloc plein. Face à un voisin ciselé, les quads extérieurs sont filtrés avec un masque
  d'occupation 16x16 de la face voisine : une passe légère, hors du cache.
- Texture, teinte et type de rendu viennent du modèle de chaque matériau. La variante de texture
  est fixe par matériau et non par position, ce qui garde le cache partagé (compromis assumé).
- Aucune mise à jour de bloc supplémentaire après le maillage : une seule marque de section à
  refaire par tick (répond à F2 et F3).
- La compatibilité avec Sodium et Iris sur NeoForge est une exigence, testée à chaque jalon.

**4.3 Collision, contour et visée.** La forme est construite une fois par grille distincte, à
partir des boîtes de `BoxMerger` (ou directement depuis une forme discrète en bit set), et mise en
cache selon le contenu (répond à F6). La visée d'un bit utilise `BitRaycast`, jamais la forme. Le
contour dessiné autour de la cible ne couvre que les voxels que le mode d'outil actuel va toucher.

**4.4 Édition et réseau.** Le serveur fait autorité.
- Le client envoie une action (outil, mode, taille, bloc visé, voxel visé, face, numéro de
  séquence).
- Le client applique immédiatement la même opération déterministe (prédiction), donc le résultat
  s'affiche sans délai ; le serveur la confirme ou la corrige.
- Le serveur applique toutes les éditions d'un bloc pendant un tick sur un builder, compacte une
  seule fois, puis envoie un paquet delta par bloc et par tick (région modifiée et ses voxels), ou
  la grille entière si elle est plus petite (répond à F4).
- La lumière n'est revérifiée que si l'émission ou l'opacité change. Les voisins ne sont prévenus
  que si l'occupation d'une face extérieure change.

**4.5 Items.** Les items de blocs ciselés portent leur grille dans un data component. Les modèles
d'items sont mis en cache selon le contenu, dans un cache LRU borné.

**4.6 Détail adaptatif.** Les outils travaillent en tailles de 1, 2, 4 ou 8 pixels. Le coût de
stockage suit la complexité : un seul matériau ne coûte rien par voxel, deux matériaux coûtent un
bit par voxel, et ainsi de suite.

### 5. Fonctionnalités de la première version

**Cœur**
- Ciseau avec modes : bit seul, ligne, plan, cube, sphère ; taille 1, 2, 4 ou 8.
- Les bits ciselés tombent en items de bits (un item par matériau ; un bit vaut un voxel, soit 1/4096 de bloc).
- Pose de bits depuis la main sur n'importe quelle surface, y compris sur des blocs vanilla.
- Plusieurs matériaux dans un même bloc, y compris transparents et blocs teintés.
- Sac à bits : range les bits par matériau, rempli automatiquement pendant le ciselage.

**Patterns**
- Pattern vierge pour copier un bloc ciselé.
- Pattern positif (imprime la forme), pattern négatif (creuse la forme).
- L'impression consomme les bits de l'inventaire ou du sac ; en créatif, aucun bit n'est requis.

**Outils avancés**
- Miroir / plan de symétrie appliqué au ciselage et à la pose.
- Clé : rotation et retournement d'un bloc ciselé.
- Mètre ruban : distances et volumes en bits.
- Scie : découpe un bloc ciselé en tranches selon un axe.

**Import Chisels & Bits**
- Convertit les blocs ciselés sauvegardés par Chisels & Bits 1.21.1 en blocs Bitsmith, au
  chargement des chunks, via des alias de registre, pour migrer un monde sans que Chisels & Bits
  soit installé.
- Le format de sauvegarde est étudié à partir de données sauvegardées et documenté dans `docs/` ;
  le lecteur est écrit à partir de cette documentation.

**Hors périmètre de la première version :** Fabric, autres versions de Minecraft, réservoir à
bits et autres blocs de stockage, support des contraptions Create, bloc imprimante.

### 6. Jalons

| Jalon | Contenu | Critère de sortie |
|---|---|---|
| M0 Fondations | Projet, noyau voxel, tests, benchmarks | Build vert, benchmarks du noyau publiés, mesure Spark de référence de Chisels & Bits enregistrée |
| M1 Bloc ciselé | Bloc, block entity, stockage, rendu, forme, ciseau bit à bit, réseau | P2, P4, P5, P6, P7 atteints ; rendu correct sous Sodium |
| M2 Gameplay cœur | Modes et tailles, bits, pose, sac, règles survie | P3, P9 atteints ; checklist fonctionnelle validée |
| M3 Patterns | Patterns vierge, positif, négatif | Checklist fonctionnelle validée |
| M4 Outils avancés | Miroir, clé, mètre ruban, scie | Checklist fonctionnelle validée |
| M5 Import | Étude du format, importeur, monde de test de migration | Un monde de test se convertit sans bloc perdu |
| M6 Release candidate | Comparaison Spark complète, Iris, test d'endurance multijoueur | P1 à P10 atteints et documentés |

**Avancement (2026-10-15).** M0 à M5 terminés. M6 : mesuré face à Chisels & Bits dans
`docs/benchmarks/2026-10-15-m6.md` et `2026-10-15-soak.md`. P1, P2, P4, P6, P7, P8 et P9 atteints ;
P5 atteint par conception ; P10 atteint sur une endurance de 10 minutes, le passage d'une heure
reste à faire ; P3 non atteint tel qu'écrit (une règle de 2x la médiane est inatteignable au-delà
de 1000 FPS), une nouvelle formulation est proposée dans le rapport et attend validation.

### 7. Règles d'ingénierie

- `core.voxel` n'importe jamais de classe Minecraft ; chaque algorithme y est testé.
- Aucune allocation par frame dans le rendu. Aucune block entity qui tick.
- Chaque cache est borné et vidé au rechargement des ressources.
- Chaque paquet réseau est validé côté serveur : distance, permissions, nombre d'items.
- Chaque jalon se termine par une comparaison Spark enregistrée dans `docs/benchmarks/`.

### 8. Protocole de mesure

Voir `docs/PROFILING.md`. En résumé : mêmes réglages d'instance et mêmes scènes pour Chisels &
Bits et pour Bitsmith ; profils Spark client et serveur, plus FPS moyen et 1 % low, sur quatre
scènes (vue statique, ciselage continu, chargement de chunks, survol).

### 9. Risques

| Risque | Parade |
|---|---|
| Sodium ou Iris contournent une partie du pipeline de modèles | Test dès M1 ; passage par l'API de modèles de Sodium si besoin |
| L'éclairage lissé rend mal sur les faces intérieures | Comparaison avec l'éclairage plat à M1 ; AO désactivée sur les quads intérieurs si besoin |
| Le format de sauvegarde de Chisels & Bits est compressé et non documenté | Étude du format limitée dans le temps avant M5 ; l'import reste optionnel |
| Les sculptures pire cas (damier) produisent beaucoup de quads | Mesuré dans le benchmark du noyau ; acceptable car rare et mis en cache |
| All Rights Reserved empêche les créateurs de modpacks d'inclure le mod | Résolu : exception modpacks dans la LICENSE et le README |
