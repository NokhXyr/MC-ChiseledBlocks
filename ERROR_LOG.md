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

## [2026-10-14 21:46] — Tape measure box drawn out of view

**Context:** Client playtest of the tape measure: the measurement text showed, the box did not.
**Error:** No yellow box on screen.
**Root cause:** In 1.21.1 the level renderer has already applied the camera rotation to the model view stack when `RenderLevelStageEvent` fires; multiplying the event's model view matrix again rotated the box a second time, off screen.
**Fix:** Use the event's pose stack (or an identity one) and only translate by the camera position.
**Prevention:** World space drawing in level render stages translates by the camera position only. Check it on a playtest screenshot.

## [2026-10-14 21:57] — Compatibility client loaded the Fabric jar of Entity Culling

**Context:** First `runClientCompat` with mods pinned by Modrinth version number.
**Error:** Loading warning `entityculling-1.11.2.jar is a Fabric mod and cannot be loaded`; the playtest stayed stuck on that screen.
**Root cause:** Entity Culling publishes its Fabric and NeoForge files as separate Modrinth versions with the same version number; the Modrinth maven resolved `1.11.2` to the Fabric one.
**Fix:** All compatibility mods are pinned by Modrinth version id, and each jar was checked for `META-INF/neoforge.mods.toml`.
**Prevention:** Pin Modrinth maven dependencies by version id, never by version number.

## [2026-10-14 22:17] — Create refused to move carved blocks

**Context:** Compatibility playtest: a Create mechanical piston pushing a carved block.
**Error:** The piston never assembled; its data held `create.gui.assembly.exception.unmovableBlock` for the carved block.
**Root cause:** The carved block was registered with `PushReaction.BLOCK`, meant to stop vanilla pistons. Vanilla pistons already refuse any block with a block entity, so the setting only blocked Create.
**Fix:** Default push reaction. Carved blocks also gained a horizontal facing so that rotations done by WorldEdit, structure blocks and Create turn the carving too.
**Prevention:** Do not restrict movement beyond what vanilla already does; check other mods' movers in the compatibility playtest.

## [2026-10-14 22:53] — Chisels & Bits test client failed to load (missing library)

**Context:** `runClientCnbSource`, the run that carves with Chisels & Bits 21.1.33 to produce real data.
**Error:** `NoClassDefFoundError: com/communi/suggestu/saecularia/caudices/core/block/IBlockWithWorldlyProperties`, then the client stayed on the error screen.
**Root cause:** Chisels & Bits needs Saecularia Caudices, which is not bundled. Its maven jar nests the needed classes in a jar marked `FMLModType: MOD` without mod metadata, which the development loader skips.
**Fix:** Saecularia Caudices 21.1.11 is downloaded from its author's maven with a SHA-512 check; for the test run only, the nested library is repacked as a `GAMELIBRARY` in `run-cnb/mods`. Only the test client process (identified by its own args file) was stopped; the user's game was left running.
**Prevention:** Before a run that adds third party mods, read their declared dependencies and nested jars. Wait for either a report or a crash report, never for a fixed time only.

## [2026-10-14 23:00] — Chisels & Bits data read as empty

**Context:** First comparison of Bitsmith's reader against blocks saved by Chisels & Bits.
**Error:** Blocks decoded as all air, then `UTFDataFormatException` while reading the NBT.
**Root cause:** Three differences between the format as read in the source and as saved: the payload sits inside a `data` compound, the root NBT tag is unnamed (`writeAnyTag`), and palette entries nest the block state twice (`{state:{state:{Name}}}`).
**Fix:** The reader handles all three; it is checked voxel by voxel against Chisels & Bits' own shapes, and a real saved block is kept as a game test fixture.
**Prevention:** Never trust a format read from code alone: check against data saved by the real program.

## [2026-10-15 13:15] — Refused chisel strokes of fake players threw an exception

**Context:** Multiplayer soak test (`runClientSoak`): bot players carving at random, some out of reach.
**Error:** `NullPointerException: ... Connection.channel() is null` from `NetworkRegistry.hasChannel`, 2361 times in 10 minutes. Carvings stayed in sync, but the bot's action was aborted.
**Root cause:** A refused stroke sends the true grid back to correct the client's prediction. Fake players (bots, and machines of other mods such as a Create deployer holding a chisel) have a connection without a netty channel, and NeoForge's channel check dereferences it.
**Fix:** No resync for fake players (`isFakePlayer()`); a game test covers a refused stroke by a fake player.
**Prevention:** Every packet sent to a single player first checks `isFakePlayer()`. Soak tests include bot players.

## [2026-10-16 01:07] — Carved block not indexed right after placement

**Context:** game test of the new carved block index used by `/bitsmith find`.
**Error:** `indexandfindfollowcarvedblocks failed: a placed carved block is indexed`.
**Root cause:** NeoForge calls `BlockEntity.onLoad()` from `Level.tickBlockEntities` (pending fresh block entities), so after `setBlock` the block entity is only indexed at the next level tick, not during the call.
**Fix:** the test checks the index one tick after placing. The index itself is correct: searches run from commands, never in the same call as a placement.
**Prevention:** anything registered from `onLoad()` is visible one tick after the block is placed; tests and code that need it at once must wait a tick or register explicitly.

## [2026-10-16 02:30] — Push rejected after a commit message fix ran twice

**Context:** removing a forbidden attribution line from a pushed commit with `git commit --amend` and `git push --force-with-lease`.
**Error:** `! [rejected] main -> main (non-fast-forward)` on the next push.
**Root cause:** the amend command was run a second time after a new commit had been made on top, so it rewrote the newest commit's message (the admin panel) with the older one. The remote kept the right history; the local branch had a twin commit with the same tree and the wrong message.
**Fix:** checked the two commits had the same tree (`git diff --quiet`), then moved the only unpushed commit onto the remote branch with `git rebase --onto origin/main <twin> main`. No force push needed.
**Prevention:** an amend command handed over for manual use is valid once, for the commit it names; say so when giving it. Never put attribution lines in commit messages in the first place.
