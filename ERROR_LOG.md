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

## [2026-10-16 02:42] — Compatibility run did not start, an old report was read as a pass

**Context:** `./gradlew runClientCompat` after adding one item per material.
**Error:** `Execution failed for task ':writeCompatConfig'. Invocation of 'configurations' references a Gradle script object from a Groovy closure at execution time, which is unsupported with the configuration cache.` The report in `run-compat/` still said "all steps passed": it was the one from 2026-10-14.
**Root cause:** the configuration cache was turned on in M6, after the last compatibility run; the task read `configurations` inside `doLast`. The check only grepped the report, not the build result or the report's date.
**Fix:** the task captures the shader pack configuration as a `FileCollection` at configuration time. The old report was moved away before running again: 87 steps passed, report dated 2026-10-16 02:45.
**Prevention:** before each playtest run, move the previous report away, and always check the Gradle exit code as well as the report.

## [2026-10-16 03:00] — Players kicked on join with LuckPerms: "Invalid player data"

**Context:** permission test on a dedicated server with LuckPerms, after moving `/bitsmith` to NeoForge's permission API.
**Error:** `Couldn't place player in world` / `IllegalStateException: Capability has not been initialised`, from `BitsmithPermissions.has` inside the `/bitsmith` command requirement, called by `Commands.sendCommands` during `PlayerList.placeNewPlayer`. The client showed "Connection Lost: Invalid player data".
**Root cause:** the command list is sent to a joining player before LuckPerms has loaded that player, and LuckPerms throws when asked a permission then. Any exception in a command requirement aborts the login.
**Fix:** `BitsmithPermissions.has` never throws: it answers with the operator rule when the permission mod is not ready.
**Prevention:** code reached from command requirements or login must never throw; test permission integrations on a dedicated server with the real permission mod, not only in single player.

## [2026-10-16 03:09] — LuckPerms 5.4.140 itself kicks every player on NeoForge 21.1.259

**Context:** same test, after the fix above.
**Error:** the same `Capability has not been initialised`, now from `NeoForgeConnectionListener.onPlayerLoggedIn` with no Bitsmith frame in the trace.
**Root cause:** a LuckPerms bug on NeoForge 1.21.1 (LuckPerms issues 3963 and 4235). 5.4.140 is the only build published for 1.21.1; LuckPerms also refuses to start in single player ("not supported on the client").
**Fix:** the test server uses LuckPerms 5.4.150 (built for 1.21.4), with which players join and the permission checks work. Bitsmith's own code needs nothing more.
**Prevention:** when a third party mod fails, read the whole trace to tell whether our code is in it before changing anything.

## [2026-10-16 03:05] — Two Gradle builds at once broke the compiled classes

**Context:** starting `runServerPerms` in the background, then `runClientPerms` right after.
**Error:** `error: cannot access Scope`, `cannot find symbol ... Kind` in classes whose sources were intact; the client never started a test.
**Root cause:** both builds compiled the same sources into the same output directory at the same time.
**Fix:** compile once first (`compileJava`, `prepareClientPermsRun`), then start the server and wait for it, then the client: neither build recompiles.
**Prevention:** never start two Gradle builds of this project at once unless everything is already compiled.

## [2026-10-16 10:20] — The belt line playtest produced no bits

**Context:** laying a Create belt with the belt connector in the Create addon playtest, then feeding one stone block at its start.
**Error:** first `belt at x 3: minecraft:air`, then `stone bits out = 0`; the stone was lying at x -2, past the start of the belt.
**Root cause:** the shafts were about 5.1 blocks from the player, just past reach, so the connector clicks did nothing; once laid, the belt ran towards the motor end because of the side the motor was on.
**Fix:** the player walks up to the shafts first; the belt motor sits on the other side of the shaft, facing north, which turns the belt towards the machines.
**Prevention:** check reach (5 blocks from the eyes) before a playtest click, and check where items go (a diagnostic step that lists items on the belt, on the ground and in containers) before blaming the machines.

## [2026-10-16 10:40] — Line machines idle in the close up bench

**Context:** feeding a second item onto each depot under the machines to photograph them mid cut.
**Error:** `running false, progress 0.0` for all three machines.
**Root cause:** the depots still held the pieces from the first cut, so the new item could not be inserted.
**Fix:** the depot is broken and placed again (empty) before each feed.
**Prevention:** read a block entity's saved state in the report when an animation does not show, instead of guessing from screenshots.

## [2026-10-16 11:00] — Create's textures and models used by the addon

**Context:** the line machines were modelled with Create's casings, saw, press and millstone textures.
**Error:** the user pointed out that Create's assets are All Rights Reserved and that the design must be the addon's own; a top view also showed a broken saw top texture with see-through holes.
**Root cause:** reusing another mod's textures by reference was taken as acceptable; some of those textures have transparent parts meant for other geometry.
**Fix:** 18 original textures generated by a script (`textures/block/line`), a new body design, and no `create:` texture or model left in the addon's assets. Create still draws its own shaft at runtime through its API.
**Prevention:** never point an addon's models at another mod's textures or models; draw our own.

## [2026-10-16 11:10] — Faces clipping on the line machines

**Context:** the new machine bodies and the stretched poles of the cutter and the crusher.
**Error:** flickering surfaces reported by the user.
**Root cause:** coplanar faces facing the same way: the undersides of the core and the corner posts at y 2, and the stretched pole drawn over the tool's own pole.
**Fix:** posts end at y 1.75 (a height no other part uses), the extension starts at the top of the tool's pole (13/16). A script now lists overlapping coplanar faces in every model, alone and combined with the tool at rest: none left.
**Prevention:** run the overlap check after every model change.
