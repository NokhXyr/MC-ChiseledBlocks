# Bitsmith

Carve any block into 1/16 bits, mix materials inside one block, copy and reprint your carvings,
without losing frames. Bitsmith is a NeoForge mod for Minecraft 1.21.1, built from the ground up
for performance and stability.

Inspired by [Chisels & Bits](https://github.com/ChiselsAndBits/Chisels-and-Bits). Bitsmith is an
independent implementation: it contains no code or assets from Chisels & Bits.

> Status: early development (milestone M5). Carving, bits, bit bag, survival rules, undo, patterns, the advanced tools and the Chisels & Bits world import work; compatibility is checked with Sodium, Iris, ModernFix, Create, WorldEdit and KubeJS.

## Features

Planned for the first release (see [the specification](docs/SPECIFICATION.md)):

- Chisel with single bit, line, plane, cube and sphere modes, at 1, 2, 4 or 8 pixel sizes.
- Several materials in one block, transparent and tinted blocks included.
- Bits as items, bit bag, placement of bits on any surface.
- Blank, positive and negative patterns.
- Mirror, wrench, tape measure and saw.
- Import of blocks carved with Chisels & Bits.

Why it is faster: identical carvings share one mesh and one collision shape, an edit rebuilds a
render section once, network updates carry only what changed, and nothing is computed per frame.
Details and targets are in [the specification](docs/SPECIFICATION.md).

## Usage

| Action | How |
|---|---|
| Carve | Hold the iron chisel and left click a block (hold the button to keep carving) |
| Tool menu | Hold `Left Alt` with the chisel or the bag: point at an option and release (or click) |
| Change chisel mode | Tool menu, or `V` (single bit, cube, line, plane, sphere) |
| Change chisel size | Tool menu, or `B` (1, 2, 4, 8 bits) |
| Undo / redo | `Ctrl+Z` / `Ctrl+Y`, or the centre of the tool menu (last 64 actions) |
| Place a bit | Hold bits and right click a face |
| Fill the bit bag | Keep the bag anywhere in the inventory while carving: bits go into it first |
| Place from the bag | Hold the bag and right click a face; the tool menu or `V` picks the material |
| Take bits out of the bag | Hold the bag, sneak and right click in the air: one stack of the selected material |
| Copy a carving | Right click a carved block with a blank pattern |
| Print a pattern | Right click a face: prints next to it (sneak: into the clicked block, empty space only) |
| Carve with a pattern | Switch the pattern to carve mode (`V` or tool menu), right click the block |
| Wipe a pattern | Sneak and right click in the air |
| Mirror | `N` or the outer ring of the tool menu: off, X, Y, Z, X+Z; strokes and placements are repeated across the block |
| Turn a carved block | Wrench, right click: quarter turn around the clicked face (sneak: mirror) |
| Cut a slice | Saw, right click a face: the slice comes off as a carved block; `B` or tool menu sets 1, 2, 4, 8 bits |
| Measure | Tape measure, right click two bits; sneak and right click in the air to clear |
| Keep a carving | Break the carved block: it drops with its carving, place it back anywhere |

Keys can be changed in Options, Controls, under Bitsmith. The bag's tooltip lists its contents,
the selected material is highlighted.

## Requirements

- Minecraft 1.21.1
- [NeoForge](https://neoforged.net/) 21.1.259 or newer

## Building

```
./gradlew build        # mod jar in build/libs
./gradlew test         # unit tests
./gradlew benchmark    # voxel core micro-benchmarks
./gradlew runGameTestServer  # in game tests, headless
./gradlew runClientTest      # plays the mod like a player, screenshots and report
./gradlew runClientCompat    # the same with Sodium, Iris, ModernFix, Create, WorldEdit, KubeJS...
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

# Bitsmith (Version Française)

Ciselez n'importe quel bloc en bits de 1/16, mélangez plusieurs matériaux dans un même bloc,
copiez et réimprimez vos sculptures, sans perdre de FPS. Bitsmith est un mod NeoForge pour
Minecraft 1.21.1, conçu dès le départ pour la performance et la stabilité.

Inspiré de [Chisels & Bits](https://github.com/ChiselsAndBits/Chisels-and-Bits). Bitsmith est une
implémentation indépendante : il ne contient aucun code ni aucune ressource de Chisels & Bits.

> État : début de développement (jalon M5). Le ciselage, les bits, le sac à bits, les règles de survie, l'annulation, les patterns, les outils avancés et l'import des mondes Chisels & Bits fonctionnent ; la compatibilité est vérifiée avec Sodium, Iris, ModernFix, Create, WorldEdit et KubeJS.

## Caractéristiques

Prévu pour la première version (voir [le cahier des charges](docs/SPECIFICATION.md)) :

- Ciseau avec modes bit seul, ligne, plan, cube et sphère, en tailles de 1, 2, 4 ou 8 pixels.
- Plusieurs matériaux dans un même bloc, blocs transparents et teintés compris.
- Bits en items, sac à bits, pose de bits sur n'importe quelle surface.
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
| Menu de l'outil | `Alt gauche` maintenu avec le ciseau ou le sac : pointer une option et relâcher (ou cliquer) |
| Changer le mode du ciseau | Menu de l'outil, ou `V` (bit seul, cube, ligne, plan, sphère) |
| Changer la taille du ciseau | Menu de l'outil, ou `B` (1, 2, 4, 8 bits) |
| Annuler / rétablir | `Ctrl+Z` / `Ctrl+Y`, ou le centre du menu de l'outil (64 dernières actions) |
| Poser un bit | Bits en main, clic droit sur une face |
| Remplir le sac à bits | Garder le sac n'importe où dans l'inventaire en ciselant : les bits y vont en premier |
| Poser depuis le sac | Sac en main, clic droit sur une face ; le menu de l'outil ou `V` choisit le matériau |
| Sortir des bits du sac | Sac en main, accroupi + clic droit dans le vide : une pile du matériau sélectionné |
| Copier une sculpture | Clic droit sur un bloc ciselé avec un pattern vierge |
| Imprimer un pattern | Clic droit sur une face : imprime à côté (accroupi : dans le bloc visé, espace vide seulement) |
| Creuser avec un pattern | Passer le pattern en mode creuser (`V` ou menu de l'outil), clic droit sur le bloc |
| Effacer un pattern | Accroupi + clic droit dans le vide |
| Miroir | `N` ou l'anneau extérieur du menu de l'outil : aucun, X, Y, Z, X+Z ; coups et poses sont répétés de l'autre côté du bloc |
| Tourner un bloc ciselé | Clé, clic droit : quart de tour autour de la face visée (accroupi : miroir) |
| Découper une tranche | Scie, clic droit sur une face : la tranche sort comme bloc ciselé ; `B` ou le menu règle 1, 2, 4, 8 bits |
| Mesurer | Mètre ruban, clic droit sur deux bits ; accroupi + clic droit dans le vide pour effacer |
| Garder une sculpture | Casser le bloc ciselé : il tombe avec sa sculpture, à reposer où l'on veut |

Les touches se changent dans Options, Commandes, rubrique Bitsmith. L'infobulle du sac liste son
contenu, le matériau sélectionné est en surbrillance.

## Prérequis

- Minecraft 1.21.1
- [NeoForge](https://neoforged.net/) 21.1.259 ou plus récent

## Compilation

```
./gradlew build        # jar du mod dans build/libs
./gradlew test         # tests unitaires
./gradlew benchmark    # micro-benchmarks du noyau voxel
./gradlew runGameTestServer  # tests en jeu, sans fenêtre
./gradlew runClientTest      # joue le mod comme un joueur, captures et rapport
./gradlew runClientCompat    # pareil avec Sodium, Iris, ModernFix, Create, WorldEdit, KubeJS...
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
