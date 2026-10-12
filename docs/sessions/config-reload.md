# Session: config-reload (v0.11, playtest fixes)

**Lanes:** `common` (`Config`, debug commands; additive only), `docs/TUNING.md`. **Branch:** `claude/config-reload`.
**PR title:** `common: /bs config reload and a tuning map`.

Owner playtest (`docs/playtest/2026-10-12-owner-feedback.md`, item 5): web swinging and the Mario jump "feel off". Feel
can only be tuned in game, so give the owner a live loop.

## Do
- `Config.load` remembers every (file name, live instance) it returns.
- `/bs config reload [name]` (op only) re-reads the JSON and copies the fields into the **same** instances
  (reflection, recursively for nested config objects), so every module's `CONFIG` updates without code changes.
- After each reload, fire a common event, `ConfigEvents.RELOADED` (Fabric `Event`, args: file name and live instance), so
  anything that caches config-derived values can refresh. At minimum `PartyScaling` (party.json), `Threat` (threat.json)
  and the v2 SiegeClock cycle length listen to it. Document it in `docs/decisions/config-reload.md`.
- Synced configs (movement's client sync) are re-sent to players after a reload, from a `RELOADED` listener.
- Modules that cache derived values at init: list them, and fix the cheap ones by reading the config at use time or
  listening to `RELOADED`.
- Bad JSON keeps the old values and reports the error in chat.
- **`docs/TUNING.md`:** which config field fixes which feel complaint.
  - Swing: rope slack/length, swing boost, release boost, raycast range, gravity while swinging.
  - Jump: hold boost, hold cap, fall gravity, coyote/buffer ticks, triple-jump window.
  - Waves/pacing: breaks, waves per night, dusk cue.
  - Each with its file and default.

**Priority:** land this first in v0.11. It unblocks the owner's movement tuning session (run by the v2 orchestrator).

## Tests
- Unit: field copy, including nested objects and lists.
- Gametest: edit a config file on disk, run the reload, and the live value changes.
- Bad JSON keeps the old values.
- A `RELOADED` listener is called with the reloaded file's name.

## Done when
CI green; `docs/decisions/config-reload.md`. Tell the orchestrator: the v2 orchestrator runs a movement tuning
session with the owner on top of this.
