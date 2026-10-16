# Compatibility

[English](#english) | [Français](#français)

---

## English

Every mod below is loaded together in `./gradlew runClientCompat`, which plays the whole
Chiseled Blocks playtest in game (real key presses, menus and clicks) and adds the checks listed for
each mod. Results come from that run, with screenshots read after each change. ModernFix runs
with its dynamic model loading turned on, and Iris with a shader pack enabled.

| Mod | Version tested | Result | What is checked |
|---|---|---|---|
| Sodium | 0.8.13 | Works | Every chiseling, inner faces, items and outlines render the same as without Sodium |
| Iris | 1.8.14-beta.1 | Works | Chiseled blocks render under a shader pack (Complementary Reimagined r5.9.3) |
| ModernFix | 5.27.24 | Works | Dynamic model loading on: chiseled block, pixel and chiseled item models still resolve |
| FerriteCore | 7.0.3 | Works | Loaded with the full playtest |
| Lithium | 0.15.4 | Works | Loaded with the full playtest (collisions, shapes) |
| ImmediatelyFast | 1.6.14 | Works | Loaded with the full playtest (radial menu, outlines, item rendering) |
| Entity Culling | 1.11.2 | Works | Loaded with the full playtest |
| WorldEdit | 7.3.8 | Works, one limit | `//copy`, `//paste` and `//rotate` keep and turn chiselings; `//set chiseledblocks:chiseled_block` leaves no empty ghost block. `//flip` does not mirror chiselings (see below) |
| Create | 6.0.10 | Works | A mechanical piston moves a chiseled block: it renders with its chiseling on the contraption and is placed back unchanged. Bulk pixel production with Create is a separate addon (Chiseled Blocks: Create): a belt laid with the belt connector carries one stone block under its Block Slicer, Slice Cutter and Pixel Crusher, and 4096 stone pixels come out at the end; the addon's Ponder scenes open on each machine and play the line |
| KubeJS | 2101.7.2 | Works | Recipes can use Chiseled Blocks items, including pixels with their material component |
| AE2 | 19.2.18 | Works | A chiseled block, a stack of pixels and a filled pixel bag go into a 1k storage cell, the cell is saved and reopened, and they come back identical |
| Refined Storage | 2.0.9 | Works | The same three items go through its storage and its save codec and come back identical |
| JEI | 19.57.0.451 | Works, integrated | Every chiseled block item is listed; pixels have an information page. With the Create addon, a page per line machine and material |
| Jade | 15.10.6 | Works, integrated | Looking at a chiseled block shows its materials and how full it is |
| Pehkui | 3.8.3 | Works | Loaded in every development client |

### How it works

- **Rendering** goes through the normal chunk mesh with NeoForge model data, which Sodium and
  Iris support. Mods that draw blocks outside that pipeline (Create contraptions) are covered by
  a fallback: the model reads the chiseling from the block entity of the level it is drawn in.
- **Turning builds**: chiseled blocks have a horizontal orientation. WorldEdit, structure blocks and
  Create turn block states only; the block entity compares the state with the orientation its
  chiseling was saved in and turns the chiseling to match.
- **Movable**: chiseled blocks can be moved by Create. Vanilla pistons still refuse them, like any
  block with a block entity.

### Known limits

- **WorldEdit `//flip`** turns a chiseling instead of mirroring it: an orientation cannot tell a
  mirror from a half turn, and WorldEdit does not expose block entity data to mods.

### Running the checks

```
./gradlew runClientCompat
```

The report is written to `run-compat/playtest-report.txt`, screenshots to
`run-compat/screenshots/`. Mods are pinned by Modrinth version id in `gradle.properties`, all
published at least 7 days before being pinned.

---

## Français

Tous les mods ci-dessous sont chargés ensemble dans `./gradlew runClientCompat`, qui joue toute la
partie de test de Chiseled Blocks en jeu (vrais appuis de touches, menus et clics) et ajoute les
vérifications indiquées pour chaque mod. Les résultats viennent de cette partie, captures relues
après chaque modification. ModernFix tourne avec son chargement dynamique des modèles activé, et
Iris avec un shader actif.

| Mod | Version testée | Résultat | Ce qui est vérifié |
|---|---|---|---|
| Sodium | 0.8.13 | Fonctionne | Sculptures, faces intérieures, items et contours rendus comme sans Sodium |
| Iris | 1.8.14-beta.1 | Fonctionne | Les blocs ciselés s'affichent sous un shader (Complementary Reimagined r5.9.3) |
| ModernFix | 5.27.24 | Fonctionne | Chargement dynamique des modèles activé : les modèles du bloc ciselé, des pixels et des items restent corrects |
| FerriteCore | 7.0.3 | Fonctionne | Chargé avec toute la partie de test |
| Lithium | 0.15.4 | Fonctionne | Chargé avec toute la partie de test (collisions, formes) |
| ImmediatelyFast | 1.6.14 | Fonctionne | Chargé avec toute la partie de test (menu radial, contours, rendu des items) |
| Entity Culling | 1.11.2 | Fonctionne | Chargé avec toute la partie de test |
| WorldEdit | 7.3.8 | Fonctionne, une limite | `//copy`, `//paste` et `//rotate` gardent et tournent les sculptures ; `//set chiseledblocks:chiseled_block` ne laisse pas de bloc fantôme vide. `//flip` ne retourne pas les sculptures en miroir (voir plus bas) |
| Create | 6.0.10 | Fonctionne | Un piston mécanique déplace un bloc ciselé : il s'affiche avec sa sculpture sur la contraption et est reposé intact. La production de pixels en masse avec Create est un addon séparé (Chiseled Blocks: Create) : un tapis posé avec le connecteur de tapis passe un bloc de pierre sous sa Trancheuse, sa Découpeuse et sa Concasseuse à pixels, et 4096 pixels de pierre sortent au bout ; les scènes Ponder de l'addon s'ouvrent sur chaque machine et jouent la chaîne |
| KubeJS | 2101.7.2 | Fonctionne | Les recettes peuvent utiliser les items Chiseled Blocks, y compris les pixels avec leur matériau |
| AE2 | 19.2.18 | Fonctionne | Un bloc ciselé, une pile de pixels et un sac rempli entrent dans une cellule de stockage 1k, la cellule est sauvegardée puis rouverte, et ils ressortent identiques |
| Refined Storage | 2.0.9 | Fonctionne | Les trois mêmes objets passent par son stockage et son format de sauvegarde et ressortent identiques |
| JEI | 19.57.0.451 | Fonctionne, intégré | Tous les objets de blocs ciselés sont listés ; les pixels ont une page d'information. Avec l'addon Create, une page par machine de la chaîne et par matériau |
| Jade | 15.10.6 | Fonctionne, intégré | Regarder un bloc ciselé affiche ses matériaux et son remplissage |
| Pehkui | 3.8.3 | Fonctionne | Chargé dans tous les clients de développement |

### Fonctionnement

- **Le rendu** passe par le maillage normal des chunks avec les model data de NeoForge, que Sodium
  et Iris prennent en charge. Les mods qui dessinent des blocs hors de ce pipeline (contraptions
  de Create) sont couverts par un repli : le modèle lit la sculpture dans la block entity du monde
  où il est dessiné.
- **Rotation des constructions** : les blocs ciselés ont une orientation horizontale. WorldEdit,
  les blocs structure et Create ne tournent que les états de bloc ; la block entity compare l'état
  avec l'orientation dans laquelle sa sculpture a été enregistrée et tourne la sculpture.
- **Déplaçable** : Create peut déplacer les blocs ciselés. Les pistons vanilla les refusent
  toujours, comme tout bloc avec une block entity.

### Limites connues

- **`//flip` de WorldEdit** tourne une sculpture au lieu de la retourner en miroir : une
  orientation ne distingue pas un miroir d'un demi-tour, et WorldEdit n'expose pas les données des
  block entities aux mods.

### Lancer les vérifications

```
./gradlew runClientCompat
```

Le rapport est écrit dans `run-compat/playtest-report.txt`, les captures dans
`run-compat/screenshots/`. Les mods sont épinglés par identifiant de version Modrinth dans
`gradle.properties`, tous publiés au moins 7 jours avant d'être épinglés.
