# Error log

## [2026-10-14 20:13] — Resync packet sent to a player without the Bitsmith channel

**Context:** M2 game test `editsApplyOncePerTickWithinBudget`, where the server refuses chisel strokes over the per tick budget.
**Error:** `Payload bitsmith:grid_full may not be sent to the client!`
**Root cause:** On a refused stroke the server sends the true grid back to undo the client prediction. The game test mock player has no negotiated Bitsmith channel, and NeoForge throws when a payload is sent on a channel the connection does not have.
**Fix:** `ChiselLogic.resync` checks `player.connection.hasChannel(GridFullPayload.TYPE)` before sending.
**Prevention:** Every server to client payload sent outside the vanilla sync path to a single player is guarded by a channel check. Fake players and mock players never have mod channels.

## [2026-10-14 20:40] — Inner faces of carved blocks rendered almost black

**Context:** In game test of M2: a grass block carved into a pit.
**Error:** Every face inside the carving was much darker than the surrounding terrain.
**Root cause:** Vanilla smooth lighting computes ambient occlusion from the blocks around the block position. For a face inside the block, those are the solid blocks around the carving, so every inner face was shaded as if it sat in a closed corner.
**Fix:** Inner quads are baked with ambient occlusion disabled (`BakedQuad` AO flag false); NeoForge then lights them flat with the light of the block's own position. Boundary quads keep vanilla AO.
**Prevention:** Any quad that does not lie on the block boundary must not use vanilla AO. Check carvings visually with smooth lighting on, inside a pit and against a wall.

## [2026-10-14 20:50] — Stopping the dev client also closed the user's own game

**Context:** Checking that the development client starts, then stopping it.
**Error:** Two Minecraft processes were stopped; the client log could not be rotated because another client was already using `run/logs`.
**Root cause:** The client was stopped by matching every `java.exe` whose command line looked like a NeoForge dev client, which also matched a client the user had started from the same project.
**Fix:** None possible after the fact; the user was told.
**Prevention:** Only stop a process started in the same step, identified by its own PID. Before launching, check whether a dev client is already running for this project; if one is, do not start a second one and do not stop it.

## [2026-10-14 21:00] — Game test server crashed once Pehkui was added to development runs

**Context:** Running `runGameTestServer` after adding Pehkui as a development runtime mod.
**Error:** `UnsupportedOperationException: Payload pehkui:scale may not be sent to the client!` while ticking the world.
**Root cause:** `localRuntime` puts a mod in every run. Pehkui then ran in the game test server and sent its packets to the mock players, which have no Pehkui channel. The game test server also shared `run/` with the client, so a jar in `run/mods` reached it too.
**Fix:** Pehkui moved to a `devClientMods` configuration copied into the mods folder of the client runs only, and the game test server got its own directory `run-gametest/`.
**Prevention:** Third party mods for manual testing go in client run mods folders, never on the shared runtime classpath. Each automated run keeps its own game directory.

## [2026-10-14 21:10] — Radial menu ignored an option chosen within the same frame

**Context:** Client playtest: move the cursor onto an option and release the menu key right away.
**Error:** The previous option stayed selected.
**Root cause:** The hovered option was only computed while drawing; a move and a release between two frames used the stale hover.
**Fix:** `RadialMenuScreen` recomputes the hovered option from the real cursor position on release and on click.
**Prevention:** Input handlers never rely on state computed during rendering.

## [2026-10-14 21:15] — Repeated chisel clicks at a hole's edge did nothing

**Context:** Client playtest: click the same spot several times, aiming at the exact centre of a face.
**Error:** Only the first click removed a bit.
**Root cause:** The targeted voxel was derived from the hit point stepped behind the face. On an edge shared with a voxel already removed, rounding picked that empty voxel every time.
**Fix:** In a carved block the target is found by tracing the view ray through the grid with `BitRaycast`; the hit point method is kept only for plain blocks, where every voxel is solid.
**Prevention:** Never derive a voxel from a hit point lying on voxel boundaries when the exact grid is available.
