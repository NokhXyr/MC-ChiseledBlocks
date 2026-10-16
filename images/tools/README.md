# Page images

Everything the project page (`curseforge_page.md`) shows lives in `images/`, and everything that
generates it lives here, in `images/tools/`.

| Folder | Holds |
|---|---|
| `images/` | Animated banners, title ribbons, badges, language labels, the divider |
| `images/icons/` | The small icons shown next to the features |
| `images/screenshots/` | Pictures for the CurseForge gallery (uploaded by hand) |
| `images/preview/` | Pictures of the page as CurseForge will show it (not committed) |
| `images/tools/` | The scripts and the files below |

| File | Role |
|---|---|
| `page.toml` | The page itself: links, badges, sections, texts in every language |
| `theme.json` | The colors, the ornament and the shape shared by every image |
| `make_banners.py` | The animated banners, made from footage recorded in game |
| `footage/` | The set (`build_scene.py`, a datapack of the recording world) and the Just Replay shot plans |
| `build_page.py` | Writes `curseforge_page.md` from `page.toml` |
| `titles.py`, `export_icons.py`, `shots.py` | Title ribbons, badges, language labels, divider, feature icons, gallery pictures |
| `check_page.py`, `preview.py` | Check the page and show it the way CurseForge will |
| `kit.py`, `iso.py`, `pixelfont.py`, `icons.py`, `theme.py`, `gifsheet.py`, `heat.py` | The drawing, animation and encoding library behind the others |

Requirements: Python 3.11 or newer with Pillow and numpy, and ffmpeg on the PATH for the banners.

## Rebuilding

```
python images/tools/export_icons.py images/tools/page.toml     # images/icons
python images/tools/titles.py images/tools/page.toml           # images/title-*.gif, lang-*.gif, divider.gif, badge-*.png
python images/tools/make_banners.py                            # images/*.gif (needs the footage)
python images/tools/build_page.py images/tools/page.toml       # curseforge_page.md
python images/tools/check_page.py curseforge_page.md
python images/tools/preview.py curseforge_page.md
```

To change a text, edit `page.toml` and run `build_page.py`. Then paste the new `curseforge_page.md`
into the description of the project on CurseForge.

## Why the page looks the way it does

CurseForge filters the HTML of a description: it removes `align="center"` and most style
properties, and shows headings as small plain text. It keeps `text-align`, `color`, `font-size`,
`font-weight`, `display`, `margin-left`, `margin-right` and the width of images. So the page is
centered by wrappers that carry `text-align`, section titles are images, and feature names are
colored with `span` tags. `preview.py` imitates that filter and CurseForge's dark stylesheet.

Every image weighs less than 2 MB, the limit of CurseForge for an uploaded image, and banners are
800 pixels wide, the width of its description column.

The generated pictures are not committed here: the page loads them from the public repository
NokhXyr/MC-Images (folder `MC-ChiseledBlocks/`, through jsDelivr), because this repository is
private. After a rebuild, copy the files listed by `check_page.py curseforge_page.md --uploads
images/upload` there, with `images/screenshots/` in `screenshots/`, then run `check_page.py
--online`.

## Footage

The banners are made from scenes recorded with the Just Replay mod in a development client, then
filmed afterwards with the camera moves of the shot plans. `footage/build_scene.py` writes the set
(a colonnade of chiseled blocks and a block carved stroke by stroke) as a datapack;
`footage/colonnade.json` and `footage/create-line.json` are the shot plans. The carving is staged by
that datapack and an actor, not by real clicks. Recordings, exports and frames stay local
(`run-capture/`, ignored), and the `replay` run is added to `build.gradle` only for a recording.

## The Create addon's page

Chiseled Blocks: Create has its own CurseForge page, `chiseledblocks-create/curseforge_page.md`. Its
description, theme and banner script are in `chiseledblocks-create/images/tools/` and are built with
the scripts of this folder, given that `page.toml`; its logo is `chiseledblocks-create/Branding/`, its
pictures are hosted in NokhXyr/MC-Images under `MC-ChiseledBlocks-Create/`, and its footage comes from
`footage/create-line.json`.

## Credits

Author: NokhXyr

---

# Images de la page (Version Française)

Tout ce que montre la page du projet (`curseforge_page.md`) se trouve dans `images/`, et tout ce
qui le génère se trouve ici, dans `images/tools/`.

| Dossier | Contenu |
|---|---|
| `images/` | Bannières animées, rubans de titre, badges, étiquettes de langue, séparateur |
| `images/icons/` | Les petites icônes affichées à côté des fonctionnalités |
| `images/screenshots/` | Les images pour la galerie CurseForge (à envoyer à la main) |
| `images/preview/` | Des images de la page telle que CurseForge l'affichera (non versionnées) |
| `images/tools/` | Les scripts et les fichiers ci-dessous |

| Fichier | Rôle |
|---|---|
| `page.toml` | La page elle-même : liens, badges, sections, textes dans chaque langue |
| `theme.json` | Les couleurs, l'ornement et la forme communs à toutes les images |
| `make_banners.py` | Les bannières animées, faites à partir de séquences enregistrées en jeu |
| `footage/` | Le décor (`build_scene.py`, un datapack du monde d'enregistrement) et les plans de caméra Just Replay |
| `build_page.py` | Écrit `curseforge_page.md` à partir de `page.toml` |
| `titles.py`, `export_icons.py`, `shots.py` | Rubans de titre, badges, étiquettes de langue, séparateur, icônes, images de galerie |
| `check_page.py`, `preview.py` | Vérifient la page et la montrent comme CurseForge l'affichera |
| `kit.py`, `iso.py`, `pixelfont.py`, `icons.py`, `theme.py`, `gifsheet.py`, `heat.py` | La bibliothèque de dessin, d'animation et d'encodage utilisée par les autres |

Prérequis : Python 3.11 ou plus récent avec Pillow et numpy, et ffmpeg dans le PATH pour les bannières.

## Reconstruire

```
python images/tools/export_icons.py images/tools/page.toml     # images/icons
python images/tools/titles.py images/tools/page.toml           # images/title-*.gif, lang-*.gif, divider.gif, badge-*.png
python images/tools/make_banners.py                            # images/*.gif (demande les séquences)
python images/tools/build_page.py images/tools/page.toml       # curseforge_page.md
python images/tools/check_page.py curseforge_page.md
python images/tools/preview.py curseforge_page.md
```

Pour changer un texte, modifiez `page.toml` puis lancez `build_page.py`. Collez ensuite le nouveau
`curseforge_page.md` dans la description du projet sur CurseForge.

## Pourquoi la page est faite ainsi

CurseForge filtre le HTML d'une description : il retire `align="center"` et la plupart des
propriétés de style, et affiche les titres comme du petit texte ordinaire. Il conserve
`text-align`, `color`, `font-size`, `font-weight`, `display`, `margin-left`, `margin-right` et la
largeur des images. La page est donc centrée par des blocs qui portent `text-align`, les titres de
section sont des images, et les noms des fonctionnalités sont colorés avec des balises `span`.
`preview.py` imite ce filtre et la feuille de style sombre de CurseForge.

Chaque image pèse moins de 2 Mo, la limite de CurseForge pour une image envoyée, et les bannières
font 800 pixels de large, la largeur de la colonne de description.

Les images générées ne sont pas versionnées ici : la page les charge depuis le dépôt public
NokhXyr/MC-Images (dossier `MC-ChiseledBlocks/`, via jsDelivr), car ce dépôt est privé. Après une
reconstruction, copiez-y les fichiers listés par `check_page.py curseforge_page.md --uploads
images/upload`, avec `images/screenshots/` dans `screenshots/`, puis lancez `check_page.py
--online`.

## Séquences

Les bannières sont faites à partir de scènes enregistrées avec le mod Just Replay dans un client de
développement, puis filmées après coup avec les mouvements de caméra des plans. `footage/build_scene.py`
écrit le décor (une colonnade de blocs ciselés et un bloc taillé coup après coup) sous forme de
datapack ; `footage/colonnade.json` et `footage/create-line.json` sont les plans de caméra. La taille
est mise en scène par ce datapack et un acteur, pas par de vrais clics. Les enregistrements, exports
et images restent en local (`run-capture/`, ignoré), et le run `replay` n'est ajouté à
`build.gradle` que le temps d'un enregistrement.

## La page de l'extension Create

Chiseled Blocks: Create a sa propre page CurseForge, `chiseledblocks-create/curseforge_page.md`. Sa
description, son thème et son script de bannières sont dans `chiseledblocks-create/images/tools/` et se
construisent avec les scripts de ce dossier, en leur donnant ce `page.toml` ; son logo est
`chiseledblocks-create/Branding/`, ses images sont hébergées dans NokhXyr/MC-Images sous
`MC-ChiseledBlocks-Create/`, et ses séquences viennent de `footage/create-line.json`.

## Credits

Author: NokhXyr
