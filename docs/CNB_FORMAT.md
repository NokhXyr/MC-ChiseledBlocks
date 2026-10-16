# Chisels & Bits save format and import

[English](#english) | [Français](#français)

---

## English

How Chiseled Blocks reads blocks saved by Chisels & Bits 21.1.33 (Minecraft 1.21.1, NeoForge), as
observed in worlds saved by that version. Chiseled Blocks's reader is
`compat/cnb/ChiselsAndBitsImport.java`; it contains no Chisels & Bits code.

### Registry names

| Chisels & Bits | Chiseled Blocks |
|---|---|
| block `chiselsandbits:chiseled` (no state properties) | `chiseledblocks:chiseled_block` |
| block entity `chiselsandbits:chiseled_block` | `chiseledblocks:chiseled_block` |
| block entity `chiselsandbits:chiseled` (old conversion blocks) | `chiseledblocks:chiseled_block` |

When Chisels & Bits is **not** installed, Chiseled Blocks registers these names as registry aliases, so
the blocks load as carved blocks. When it is installed, no alias is added and both mods keep their
own blocks.

### Block entity data

```
{ data: { payload: <byte array> } }
```

- `payload` is an **LZ4 frame**. Decompressed, it holds one **unnamed** NBT tag: a type id byte
  (10, compound) followed directly by the compound, without a name.
- The compound is `{ storage: {...}, statistics: {...} }`. Only `storage` is needed.
- `storage.palette.entries` is a list of `{ state: { state: <block state> } }`: a block
  information wrapping the block state (`{Name, Properties}`).
- `storage.data` is a bit set saved as a long array (`BitSet.toLongArray`); older saves may use a
  byte array.
- Width of an entry: `w = ceil(log2(palette size))` bits (0 when the palette has one entry).
- Entry `i` is the palette index stored in bits `[i*w, i*w+w)`, lowest bit first.
- Voxel `(x, y, z)` (0 to 15) is entry `x*256 + y*16 + z`. Chiseled Blocks stores voxels as
  `y*256 + z*16 + x`, so the reader converts the order.

Older data under `data` as a byte array (an LZ4 frame holding `{chiseledData: {palette, data}}`)
is read the same way, on a best effort basis.

### How it was checked

`./gradlew runClientCnbSource` plays a world with Chisels & Bits installed: blocks of stone, oak
planks and glass are carved with the Chisels & Bits chisel, and a stone bit is placed inside the
planks (three materials). Each block is decoded by Chiseled Blocks and compared, voxel by voxel, with the
collision shape Chisels & Bits computes itself: 0 differences on every block.
`./gradlew runClientCnbMigrate` opens a copy of that world with Chiseled Blocks alone: every block comes
back as a carved block with the same shape and materials. The planks block is kept as a fixture in
a game test, so the reader is checked on real data at every build.

### What is not converted

- **Items**: carved blocks, bits, chisels and patterns of Chisels & Bits in inventories and
  containers are not converted; their data components are unknown to Chiseled Blocks and they disappear.
- **Other Chisels & Bits blocks**: bit storage, printer and the like.
- **Very old conversion blocks** (`chiselsandbits:chiseled<material>` from much older versions).

### Migrating a world

1. Back the world up.
2. Remove Chisels & Bits (and Scena, Saecularia Caudices if nothing else needs them), add Chiseled Blocks.
3. Open the world. Chiseled blocks load as carved blocks and are saved in Chiseled Blocks's format the
   next time their chunk is saved.

---

## Français

Comment Chiseled Blocks lit les blocs sauvegardés par Chisels & Bits 21.1.33 (Minecraft 1.21.1, NeoForge),
tel qu'observé dans des mondes sauvegardés par cette version. Le lecteur de Chiseled Blocks est
`compat/cnb/ChiselsAndBitsImport.java` ; il ne contient aucun code de Chisels & Bits.

### Noms de registre

| Chisels & Bits | Chiseled Blocks |
|---|---|
| bloc `chiselsandbits:chiseled` (sans propriété d'état) | `chiseledblocks:chiseled_block` |
| block entity `chiselsandbits:chiseled_block` | `chiseledblocks:chiseled_block` |
| block entity `chiselsandbits:chiseled` (anciens blocs de conversion) | `chiseledblocks:chiseled_block` |

Quand Chisels & Bits n'est **pas** installé, Chiseled Blocks déclare ces noms comme alias de registre :
les blocs se chargent comme blocs ciselés Chiseled Blocks. Quand il est installé, aucun alias n'est ajouté
et chaque mod garde ses propres blocs.

### Données de la block entity

```
{ data: { payload: <tableau d'octets> } }
```

- `payload` est une **trame LZ4**. Décompressée, elle contient un tag NBT **sans nom** : un octet
  d'identifiant de type (10, compound) suivi directement du compound, sans nom.
- Le compound est `{ storage: {...}, statistics: {...} }`. Seul `storage` est utile.
- `storage.palette.entries` est une liste de `{ state: { state: <état de bloc> } }` : une
  information de bloc qui enveloppe l'état de bloc (`{Name, Properties}`).
- `storage.data` est un bit set enregistré en tableau de longs (`BitSet.toLongArray`) ; les
  anciennes sauvegardes peuvent utiliser un tableau d'octets.
- Largeur d'une entrée : `w = ceil(log2(taille de la palette))` bits (0 si la palette n'a qu'une
  entrée).
- L'entrée `i` est l'index de palette stocké dans les bits `[i*w, i*w+w)`, bit de poids faible
  d'abord.
- Le voxel `(x, y, z)` (0 à 15) est l'entrée `x*256 + y*16 + z`. Chiseled Blocks range ses voxels en
  `y*256 + z*16 + x` : le lecteur convertit l'ordre.

Les données plus anciennes sous `data` en tableau d'octets (une trame LZ4 contenant
`{chiseledData: {palette, data}}`) sont lues de la même façon, au mieux.

### Comment c'est vérifié

`./gradlew runClientCnbSource` joue un monde avec Chisels & Bits installé : des blocs de pierre, de
planches de chêne et de verre sont ciselés avec le ciseau de Chisels & Bits, et un bit de pierre est
posé dans les planches (trois matériaux). Chaque bloc est décodé par Chiseled Blocks et comparé, voxel par
voxel, à la forme de collision calculée par Chisels & Bits lui-même : 0 différence sur chaque bloc.
`./gradlew runClientCnbMigrate` ouvre une copie de ce monde avec Chiseled Blocks seul : chaque bloc revient
en bloc ciselé avec la même forme et les mêmes matériaux. Le bloc de planches est gardé comme
donnée de référence dans un game test, donc le lecteur est vérifié sur des données réelles à chaque
build.

### Ce qui n'est pas converti

- **Les items** : blocs ciselés, bits, ciseaux et patterns de Chisels & Bits dans les inventaires et
  les coffres ne sont pas convertis ; leurs data components sont inconnus de Chiseled Blocks et ils
  disparaissent.
- **Les autres blocs de Chisels & Bits** : stockage de bits, imprimante, etc.
- **Les très anciens blocs de conversion** (`chiselsandbits:chiseled<matériau>` de versions bien plus
  anciennes).

### Migrer un monde

1. Sauvegarder le monde.
2. Retirer Chisels & Bits (et Scena, Saecularia Caudices si rien d'autre n'en a besoin), ajouter
   Chiseled Blocks.
3. Ouvrir le monde. Les blocs ciselés se chargent comme blocs Chiseled Blocks et sont enregistrés au
   format Chiseled Blocks à la prochaine sauvegarde de leur chunk.
