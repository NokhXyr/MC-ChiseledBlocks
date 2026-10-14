# Error log

## [2026-10-14 20:13] — Resync packet sent to a player without the Bitsmith channel

**Context:** M2 game test `editsApplyOncePerTickWithinBudget`, where the server refuses chisel strokes over the per tick budget.
**Error:** `Payload bitsmith:grid_full may not be sent to the client!`
**Root cause:** On a refused stroke the server sends the true grid back to undo the client prediction. The game test mock player has no negotiated Bitsmith channel, and NeoForge throws when a payload is sent on a channel the connection does not have.
**Fix:** `ChiselLogic.resync` checks `player.connection.hasChannel(GridFullPayload.TYPE)` before sending.
**Prevention:** Every server to client payload sent outside the vanilla sync path to a single player is guarded by a channel check. Fake players and mock players never have mod channels.
