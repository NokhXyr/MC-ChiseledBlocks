# Chiseled Blocks

Chisel any block into pixels (1/16 of a block), mix materials inside one block, copy and reprint your chiselings,
without losing frames. Chiseled Blocks is a NeoForge mod for Minecraft 1.21.1, built from the ground up
for performance and stability.

Inspired by [Chisels & Bits](https://github.com/ChiselsAndBits/Chisels-and-Bits). Chiseled Blocks is an
independent implementation: it contains no code or assets from Chisels & Bits.

> Status: release candidate in progress (milestone M6, measured in [docs/benchmarks/](docs/benchmarks/)). Chiseling, pixels, pixel bag, survival rules, undo, patterns, the advanced tools and the Chisels & Bits world import work; compatibility is checked with Sodium, Iris, ModernFix, Create, WorldEdit and KubeJS.

## Features

Planned for the first release (see [the specification](docs/SPECIFICATION.md)):

- Chisel with single pixel, line, plane, cube and sphere modes, at 1, 2, 4 or 8 pixel sizes.
- Several materials in one block, transparent and tinted blocks included.
- Pixels as items, pixel bag, placement of pixels on any surface.
- Blank, positive and negative patterns.
- Mirror, wrench, tape measure and saw.
- Import of blocks chiseled with Chisels & Bits.

Why it is faster: identical chiselings share one mesh and one collision shape, an edit rebuilds a
render section once, network updates carry only what changed, and nothing is computed per frame.
Details and targets are in [the specification](docs/SPECIFICATION.md).

## Usage

| Action | How |
|---|---|
| Carve | Hold the iron chisel and left click a block (hold the button to keep chiseling) |
| Tool menu | Hold `Left Alt` with a Chiseled Blocks tool: a wheel of choices around the tool (point and release, or click to keep it open), undo and redo buttons on the left, the mirror on the right, the chisel size slider below |
| Change chisel mode | Tool menu, or `V` (single pixel, cube, line, plane, sphere) |
| Change chisel size | Tool menu, or `B` (1, 2, 4, 8 pixels) |
| Undo / redo | `Ctrl+Z` / `Ctrl+Y`, or the buttons on the left of the tool menu (last 64 actions) |
| Place a pixel | Hold pixels and right click a face |
| Fill the pixel bag | Keep the bag anywhere in the inventory while chiseling: pixels go into it first |
| Place from the bag | Hold the bag and right click a face; the tool menu or `V` picks the material |
| Placement modes | Pixels or a bag in hand, tool menu: **single pixel**; **draw** (hold right click and paint on the layer of the first pixel); **line**, **plane** or **box** (first right click sets the start, the second the end, an outline previews the shape; right click in the air to cancel). A shape may cross blocks (8 blocks per side, 16,384 pixels at most) and is undone with one Ctrl+Z; in survival it is refused when pixels are missing |
| Bag pages | A bag holds up to 64 materials; the tool menu shows 12 per page, use the arrows on the right or the mouse wheel to turn pages |
| Put pixels back in the bag | In an inventory, right click pixels onto the bag, or the bag onto pixels. Pixels picked up from the ground go into the bag first |
| Take pixels out of the bag | Hold the bag, sneak and right click in the air: one stack of the selected material |
| Creative pixel bag | Creative tab or `/give` only, no recipe. Sneak and right click a block or a pixel: adds its material and selects it. Places without limit, pays for patterns, and swallows chiseled pixels while in the inventory |
| Copy a chiseling | Right click a chiseled block with a blank pattern |
| Print a pattern | Right click a face: prints next to it (sneak: into the clicked block, empty space only) |
| Carve with a pattern | Switch the pattern to carve mode (`V` or tool menu), right click the block |
| Wipe a pattern | Sneak and right click in the air |
| Mirror | `N` or the outer ring of the tool menu: off, X, Y, Z, X+Z; strokes and placements are repeated across the block |
| Turn a chiseled block | Wrench, right click: quarter turn around the clicked face (sneak: mirror) |
| Cut a slice | Saw, right click a face: the slice comes off as a chiseled block; `B` or tool menu sets 1, 2, 4, 8 pixels |
| Measure | Tape measure, right click two pixels; sneak and right click in the air to clear |
| Keep a chiseling | Break the chiseled block: it drops with its chiseling, place it back anywhere. The item is named after its main material and its tooltip lists the materials and their pixels |

Keys can be changed in Options, Controls, under Chiseled Blocks. The bag's tooltip lists its contents,
the selected material is highlighted.

### Item ids

Every carvable material has its own chiseled block item, registered at startup from the blocks of
every installed mod: `chiseledblocks:chiseled/oak_planks` for Minecraft's blocks,
`chiseledblocks:chiseled/<mod>/<block>` for the others (for example `chiseledblocks:chiseled/create/andesite_casing`).
A chiseling is held as the item of its **main material**, so recipes, KubeJS scripts, sorting
filters, item lists (JEI, EMI) and commands can tell chiseled blocks apart:

- `/give @s chiseledblocks:chiseled/stone` gives an example shape in stone, ready to place or to carve;
- `/clear @s #chiseledblocks:chiseled_blocks` removes every chiseled block, whatever its material;
- placed in the world, every chiseled block stays the one block `chiseledblocks:chiseled_block`.

The tag `#chiseledblocks:chiseled_blocks` holds all of them. Carvings held as the older generic item
`chiseledblocks:chiseled_block` become their material's item once in a player's inventory.

### Admin panel

`/chiseledblocks` opens the admin panel (a key can be bound to it in Controls, unbound by default). It
is the mod's only command. It has three tabs:

- **Search**: chiseled blocks within a radius of blocks or chunks, or in every loaded chunk,
  optionally only those containing a material (type part of its name, suggestions appear). A
  table lists the nearest first with their materials, distance and pixels. Searches never load a
  chunk.
- **Chunk map**: the chunks around you, coloured by how many chiseled blocks they hold, with your
  position and facing. Click a chunk to list its chiseled blocks. Refreshes every two seconds.
- **Performance**: measures for a chosen time the server time Chiseled Blocks spends on each chiseled
  block, in µs per tick, split into chisel, apply, shape, save and load. Close the panel and play
  while it measures; the report is there when you reopen it.

Every row has a **TP** button that teleports you on top of the block.

Access goes through NeoForge's permission API, so a permission mod such as **LuckPerms** decides
when one is installed. A node that is not set anywhere falls back to operators (level 2):

| Node | Allows |
|---|---|
| `chiseledblocks.panel` | `/chiseledblocks` and the panel key, the Search and Chunk map tabs |
| `chiseledblocks.panel.teleport` | the TP buttons |
| `chiseledblocks.panel.perf` | the Performance tab |

For example, to give the panel and teleporting to a `staff` group without profiling:
`lp group staff permission set chiseledblocks.panel true` and
`lp group staff permission set chiseledblocks.panel.teleport true`. The panel only shows the tools a
player may use, and the server checks every request again.

### Create addon: bulk pixels

Chiseled Blocks alone makes pixels by hand, with the chisel. Producing pixels in bulk is only possible with
the optional addon **Chiseled Blocks: Create** (`chiseledblocks_create`, built from `chiseledblocks-create/`), which
needs Chiseled Blocks and Create. It adds a production line of three machines that work on Create belts
and depots, like the mechanical press:

- the **Block Slicer** cuts a block into 16 slices, the **Slice Cutter** a slice into 16 rods, the
  **Pixel Crusher** a rod into 16 pixels: 4096 pixels per block, in the block's material;
- each machine sits two blocks above the belt or depot, with one block of air between them, and
  is turned by a shaft through its sides; it holds the item under it, cuts the whole stack at
  once and lets the pieces move on (2 seconds per cut at 16 rpm, faster as the shaft turns
  faster); stopped, it lets items pass; stress impact 8 per rpm each;
- each machine only takes its own input, so one belt can carry the three stages in a row;
- the tools move: the slicer lowers a spinning saw, the cutter drops a blade, the crusher strikes
  three times per cut;
- recipes: the slicer from a shaft, two iron chisels, an andesite casing and a stonecutter; the
  cutter from a shaft, two iron ingots, a copper casing and an iron chisel; the crusher from a
  shaft, two iron ingots, a brass casing and an iron block;
- JEI shows a page per machine and material, and how to build the line on each machine;
- Ponder (hold W over a machine) plays two scenes: the whole line on a belt, and one machine on
  a depot with its speed and what it does when stopped; the machines cut in them as in a world.

## Requirements

- Minecraft 1.21.1
- [NeoForge](https://neoforged.net/) 21.1.259 or newer

## Building

```
./gradlew build        # mod jar in build/libs, addon jar in chiseledblocks-create/build/libs
./gradlew test         # unit tests
./gradlew benchmark    # voxel core micro-benchmarks
./gradlew runGameTestServer  # in game tests, headless
./gradlew runClientTest      # plays the mod like a player, screenshots and report
./gradlew runClientCompat    # the same with Sodium, Iris, ModernFix, Create, WorldEdit, KubeJS...
./gradlew runClientStress    # 16,000 chiseled blocks under stress, also runClientStressOpti and runClientStressArcadia
./gradlew :chiseledblocks-create:runClientCreate  # plays the Create addon: belt, three machines, 4096 pixels, Ponder
./gradlew :chiseledblocks-create:runClientCreatePonderExport  # rebuilds the Ponder scene schematics in game
./gradlew runClient    # development client
```

Requires Java 21.

## Documentation

- [Specification](docs/SPECIFICATION.md)
- [Profiling protocol](docs/PROFILING.md)
- [Compatibility](docs/COMPATIBILITY.md)
- [Chisels & Bits import](docs/CNB_FORMAT.md)
- [Benchmarks](docs/benchmarks/)

## License

All Rights Reserved. See [LICENSE](LICENSE).

**Modpacks are allowed**: you may include the unmodified released mod file in any modpack, public or private, free or paid, without asking.

## Credits

Author: NokhXyr

---

# Chiseled Blocks (Version Française)

Ciselez n'importe quel bloc en pixels de 1/16, mélangez plusieurs matériaux dans un même bloc,
copiez et réimprimez vos sculptures, sans perdre de FPS. Chiseled Blocks est un mod NeoForge pour
Minecraft 1.21.1, conçu dès le départ pour la performance et la stabilité.

Inspiré de [Chisels & Bits](https://github.com/ChiselsAndBits/Chisels-and-Bits). Chiseled Blocks est une
implémentation indépendante : il ne contient aucun code ni aucune ressource de Chisels & Bits.

> État : release candidate en cours (jalon M6, mesuré dans [docs/benchmarks/](docs/benchmarks/)). Le ciselage, les pixels, le sac à pixels, les règles de survie, l'annulation, les patterns, les outils avancés et l'import des mondes Chisels & Bits fonctionnent ; la compatibilité est vérifiée avec Sodium, Iris, ModernFix, Create, WorldEdit et KubeJS.

## Caractéristiques

Prévu pour la première version (voir [le cahier des charges](docs/SPECIFICATION.md)) :

- Ciseau avec modes pixel seul, ligne, plan, cube et sphère, en tailles de 1, 2, 4 ou 8 pixels.
- Plusieurs matériaux dans un même bloc, blocs transparents et teintés compris.
- Pixels en items, sac à pixels, pose de pixels sur n'importe quelle surface.
- Patterns vierge, positif et négatif.
- Miroir, clé, mètre ruban et scie.
- Import des blocs ciselés avec Chisels & Bits.

Pourquoi c'est plus rapide : des sculptures identiques partagent un seul maillage et une seule
forme de collision, une édition ne reconstruit une section de rendu qu'une fois, les mises à jour
réseau ne transportent que ce qui a changé, et rien n'est recalculé à chaque frame. Le détail et
les objectifs sont dans [le cahier des charges](docs/SPECIFICATION.md).

## Utilisation

| Action | Comment |
|---|---|
| Ciseler | Ciseau en fer en main, clic gauche sur un bloc (maintenir pour continuer) |
| Menu de l'outil | `Alt gauche` maintenu avec un outil Chiseled Blocks : une roue de choix autour de l'outil (pointer et relâcher, ou cliquer pour le garder ouvert), boutons annuler et rétablir à gauche, le miroir à droite, le curseur de taille du ciseau en dessous |
| Changer le mode du ciseau | Menu de l'outil, ou `V` (pixel seul, cube, ligne, plan, sphère) |
| Changer la taille du ciseau | Menu de l'outil, ou `B` (1, 2, 4, 8 pixels) |
| Annuler / rétablir | `Ctrl+Z` / `Ctrl+Y`, ou les boutons à gauche du menu de l'outil (64 dernières actions) |
| Poser un pixel | Pixels en main, clic droit sur une face |
| Remplir le sac à pixels | Garder le sac n'importe où dans l'inventaire en ciselant : les pixels y vont en premier |
| Poser depuis le sac | Sac en main, clic droit sur une face ; le menu de l'outil ou `V` choisit le matériau |
| Méthodes de pose | Pixels ou sac en main, menu de l'outil : **pixel seul** ; **dessin** (clic droit maintenu, on peint sur la couche du premier pixel) ; **ligne**, **plan** ou **boîte** (premier clic droit : départ, second : arrivée, un contour montre la forme ; clic droit dans le vide pour annuler). Une forme peut traverser plusieurs blocs (8 blocs par côté, 16 384 pixels au plus) et s'annule d'un seul Ctrl+Z ; en survie elle est refusée s'il manque des pixels |
| Pages du sac | Un sac contient jusqu'à 64 matériaux ; le menu en montre 12 par page, flèches à droite ou molette pour changer de page |
| Remettre des pixels dans le sac | Dans un inventaire, clic droit avec des pixels sur le sac, ou avec le sac sur des pixels. Les pixels ramassés au sol vont d'abord dans le sac |
| Sortir des pixels du sac | Sac en main, accroupi + clic droit dans le vide : une pile du matériau sélectionné |
| Sac à pixels créatif | Créatif ou `/give` seulement, sans recette. Accroupi + clic droit sur un bloc ou un pixel : ajoute son matériau et le choisit. Pose sans limite, paie les patterns, et avale les pixels ciselés tant qu'il est dans l'inventaire |
| Copier une sculpture | Clic droit sur un bloc ciselé avec un pattern vierge |
| Imprimer un pattern | Clic droit sur une face : imprime à côté (accroupi : dans le bloc visé, espace vide seulement) |
| Creuser avec un pattern | Passer le pattern en mode creuser (`V` ou menu de l'outil), clic droit sur le bloc |
| Effacer un pattern | Accroupi + clic droit dans le vide |
| Miroir | `N` ou l'anneau extérieur du menu de l'outil : aucun, X, Y, Z, X+Z ; coups et poses sont répétés de l'autre côté du bloc |
| Tourner un bloc ciselé | Clé, clic droit : quart de tour autour de la face visée (accroupi : miroir) |
| Découper une tranche | Scie, clic droit sur une face : la tranche sort comme bloc ciselé ; `B` ou le menu règle 1, 2, 4, 8 pixels |
| Mesurer | Mètre ruban, clic droit sur deux pixels ; accroupi + clic droit dans le vide pour effacer |
| Garder une sculpture | Casser le bloc ciselé : il tombe avec sa sculpture, à reposer où l'on veut. L'objet porte le nom de son matériau principal et son infobulle liste les matériaux et leurs pixels |

Les touches se changent dans Options, Commandes, rubrique Chiseled Blocks. L'infobulle du sac liste son
contenu, le matériau sélectionné est en surbrillance.

### Identifiants d'objets

Chaque matériau ciselable a son propre objet de bloc ciselé, enregistré au démarrage à partir des
blocs de tous les mods installés : `chiseledblocks:chiseled/oak_planks` pour les blocs de Minecraft,
`chiseledblocks:chiseled/<mod>/<bloc>` pour les autres (par exemple `chiseledblocks:chiseled/create/andesite_casing`).
Une sculpture prend l'objet de son **matériau principal** : recettes, scripts KubeJS, filtres de
tri, listes d'objets (JEI, EMI) et commandes peuvent ainsi distinguer les blocs ciselés :

- `/give @s chiseledblocks:chiseled/stone` donne une forme d'exemple en pierre, à poser ou à ciseler ;
- `/clear @s #chiseledblocks:chiseled_blocks` retire tous les blocs ciselés, quel que soit leur matériau ;
- posés dans le monde, tous les blocs ciselés restent le même bloc `chiseledblocks:chiseled_block`.

Le tag `#chiseledblocks:chiseled_blocks` les regroupe tous. Les sculptures portées par l'ancien objet
générique `chiseledblocks:chiseled_block` deviennent l'objet de leur matériau dès qu'elles sont dans
l'inventaire d'un joueur.

### Panneau d'administration

`/chiseledblocks` ouvre le panneau d'administration (une touche peut lui être attribuée dans Commandes,
aucune par défaut). C'est la seule commande du mod. Il a trois onglets :

- **Recherche** : les blocs ciselés dans un rayon en blocs ou en chunks, ou dans tous les chunks
  chargés, éventuellement seulement ceux qui contiennent un matériau (tape une partie de son nom,
  des suggestions apparaissent). Un tableau les liste du plus proche au plus loin, avec leurs
  matériaux, leur distance et leurs pixels. Les recherches ne chargent jamais de chunk.
- **Carte des chunks** : les chunks autour de toi, colorés selon le nombre de blocs ciselés, avec
  ta position et ta direction. Un clic sur un chunk liste ses blocs ciselés. Mise à jour toutes les
  deux secondes.
- **Performance** : mesure pendant une durée choisie le temps serveur que Chiseled Blocks consacre à
  chaque bloc ciselé, en µs par tick, détaillé en ciseau, application, forme, sauvegarde et
  chargement. Ferme le panneau et joue pendant la mesure ; le rapport t'attend à la réouverture.

Chaque ligne a un bouton **TP** qui te téléporte sur le bloc.

L'accès passe par l'API de permissions de NeoForge : un mod de permissions comme **LuckPerms**
décide quand il est installé. Un nœud défini nulle part revient aux opérateurs (niveau 2) :

| Nœud | Autorise |
|---|---|
| `chiseledblocks.panel` | `/chiseledblocks` et la touche du panneau, les onglets Recherche et Carte des chunks |
| `chiseledblocks.panel.teleport` | les boutons TP |
| `chiseledblocks.panel.perf` | l'onglet Performance |

Par exemple, pour donner le panneau et la téléportation à un groupe `staff`, sans la mesure :
`lp group staff permission set chiseledblocks.panel true` et
`lp group staff permission set chiseledblocks.panel.teleport true`. Le panneau n'affiche que les outils
permis au joueur, et le serveur revérifie chaque demande.

### Addon Create : les pixels en masse

Chiseled Blocks seul fait les pixels à la main, avec le ciseau. Produire des pixels en masse n'est possible
qu'avec l'addon optionnel **Chiseled Blocks: Create** (`chiseledblocks_create`, construit depuis
`chiseledblocks-create/`), qui demande Chiseled Blocks et Create. Il ajoute une chaîne de trois machines qui
travaillent sur les tapis et les dépôts de Create, comme la presse mécanique :

- la **Trancheuse de blocs** coupe un bloc en 16 tranches, la **Découpeuse de tranches** une tranche en
  16 baguettes, la **Concasseuse à pixels** une baguette en 16 pixels : 4096 pixels par bloc, dans la
  matière du bloc ;
- chaque machine se pose deux blocs au-dessus du tapis ou du dépôt, avec un bloc d'air entre les
  deux, et tourne grâce à un arbre qui la traverse ; elle retient l'objet en dessous, coupe toute
  la pile d'un coup et laisse repartir les morceaux (2 secondes par coupe à 16 tr/min, plus vite
  si l'arbre tourne plus vite) ; à l'arrêt, elle laisse passer les objets ; impact de contrainte
  8 par tr/min chacune ;
- chaque machine ne prend que son entrée, donc un seul tapis peut porter les trois étapes à la
  suite ;
- les outils bougent : la trancheuse descend une scie qui tourne, la découpeuse abat une lame, la
  concasseuse frappe trois fois par coupe ;
- recettes : la trancheuse avec un arbre, deux ciseaux en fer, un boîtier en andésite et un
  tailleur de pierre ; la découpeuse avec un arbre, deux lingots de fer, un boîtier en cuivre et
  un ciseau en fer ; la concasseuse avec un arbre, deux lingots de fer, un boîtier en laiton et un
  bloc de fer ;
- JEI montre une page par machine et par matériau, et comment monter la chaîne sur chaque machine ;
- Ponder (W maintenu sur une machine) joue deux scènes : toute la chaîne sur un tapis, et une
  machine sur un dépôt avec sa vitesse et ce qu'elle fait à l'arrêt ; les machines y coupent comme
  dans un monde.

## Prérequis

- Minecraft 1.21.1
- [NeoForge](https://neoforged.net/) 21.1.259 ou plus récent

## Compilation

```
./gradlew build        # jar du mod dans build/libs, jar de l'addon dans chiseledblocks-create/build/libs
./gradlew test         # tests unitaires
./gradlew benchmark    # micro-benchmarks du noyau voxel
./gradlew runGameTestServer  # tests en jeu, sans fenêtre
./gradlew runClientTest      # joue le mod comme un joueur, captures et rapport
./gradlew runClientCompat    # pareil avec Sodium, Iris, ModernFix, Create, WorldEdit, KubeJS...
./gradlew runClientStress    # 16 000 blocs ciselés sous stress, aussi runClientStressOpti et runClientStressArcadia
./gradlew :chiseledblocks-create:runClientCreate  # joue l'addon Create : tapis, trois machines, 4096 pixels, Ponder
./gradlew :chiseledblocks-create:runClientCreatePonderExport  # refait en jeu les schémas des scènes Ponder
./gradlew runClient    # client de développement
```

Nécessite Java 21.

## Documentation

- [Cahier des charges](docs/SPECIFICATION.md)
- [Protocole de profilage](docs/PROFILING.md)
- [Compatibilité](docs/COMPATIBILITY.md)
- [Import Chisels & Bits](docs/CNB_FORMAT.md)
- [Benchmarks](docs/benchmarks/)

## Licence

Tous droits réservés (All Rights Reserved). Voir [LICENSE](LICENSE).

**Les modpacks sont autorisés** : vous pouvez inclure le fichier du mod publié, non modifié, dans n'importe quel modpack, public ou privé, gratuit ou payant, sans demander.

## Credits

Author: NokhXyr
