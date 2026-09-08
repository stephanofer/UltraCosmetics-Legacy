# Block 1: Implementation and Validation

The Kill Effects platform and Freeze implementation are integrated and packaged for manual testing. **The Block 1 exit gate remains open until the new rendering and lifecycle behavior passes the real-server checks.** Compilation and unit tests do not establish visual correctness.

The product requirements remain in [the design](KILL_EFFECTS_DESIGN.md); the delivery boundary remains in [the implementation plan](KILL_EFFECTS_IMPLEMENTATION_PLAN.md).

## Build and Use

Run from the repository root:

```powershell
.\gradlew.bat :core:test verifyPacketEventsNotBundled --offline --console=plain
```

Omit `--offline` when dependencies are not already cached. Install the root artifact, `build/libs/UltraCosmetics-3.5.1-RELEASE.jar`, not a module JAR. PacketEvents 2.13.0 remains an external plugin.

1. Open `/uc menu kill_effects` on the approved 1.8.8 server.
2. Left-click Freeze to equip it, or right-click to preview without equipping or unlocking it.
3. Kill another real player. The killer's selection, not the victim's, determines the effect.
4. Toggle received effects with the menu control or `/uc killeffects on|off`.

Freeze uses `ultracosmetics.killeffects.freeze`. Existing menu and command permission conventions apply. Locked previews do not bypass the category menu's access permission or its configured owned-item filter.

## Integration Map

| Responsibility | Implementation |
|---|---|
| Equipped selection | `KillEffect`, `KillEffectType`; no selection listener or animation task |
| Attribution | `KillEffectManager`: early immutable death capture, final attribution at `MONITOR` |
| API and cancellation | Both `play` overloads converge on the same pipeline and `UCKillEffectTriggerEvent` |
| Execution lifecycle | `AbstractKillEffectExecution`, `KillEffectTicker`, `KillEffectScene` |
| Capacity | Global/world/chunk admission, full/lite/skip decisions, entity and duration caps |
| Delivery | Fixed audience, per-viewer entities/profile leases, near/far particle density |
| World policy | Enabled worlds, location-aware WorldGuard, bounded anchor search without chunk loading |
| Persistence | Explicit category storage IDs, historical alias parsing, preservation of unresolved file unlocks and selections |
| Preference | `View-Kill-Effects` in flat files and `viewKillEffects` in MySQL |

The `killeffects-packetevents` module is deliberately outside core's Shadow relocation. PacketEvents methods use unrelocated Adventure signatures. The packaging verification checks that the adapter is included, its Adventure references are not relocated, and PacketEvents runtime classes are absent.

The adapter observes outgoing player-info packets to track known profiles per connection. This observer only tracks identifiers on network callbacks; scene creation, world access, and effect sends remain synchronous. Unknown profiles use synthetic UUIDs, including after reload when existing profile knowledge cannot be reconstructed safely. Only synthetic leases are removed from the tab list.

## Public API

All manager calls must run on the server thread. A missing/disabled integration returns `null` from `UltraCosmetics.getKillEffectManager()`.

```java
KillEffectManager manager = ultraCosmetics.getKillEffectManager();
if (manager != null) {
    manager.play(killer, victim);
}
```

For a minigame that removes its victim before playing the effect, capture first:

```java
VictimSnapshot snapshot = manager.captureVictim(victim);
Location deathLocation = victim.getLocation();
// The minigame may now remove or disconnect its victim.
manager.play(killer, snapshot, deathLocation);
```

The snapshot's visibility allowlist is captured before removal. Custom snapshots supplied by trusted integrations must preserve this policy. API and vanilla triggers for the same victim are deduplicated within two ticker ticks. Cancelled or rejected scenes are not queued.

Read-only counters are available through `getActiveScenes()`, `getAllocatedEntityCount()`, `getTotalPacketSends()`, and `getTotalGeneratedPoints()`. Packet counts measure attempted adapter sends, including cleanup, not client acknowledgements. IDs whose destroy send fails are quarantined rather than recycled; they remain visible in the allocation counter.

## Performance Controls

Numeric settings are clamped before use, including oversized and non-finite numeric input. Freeze defaults to 60 ticks; its duration setting is bounded to 60-80 ticks, with the central maximum duration always taking precedence. Its lite signature lasts 12 ticks and creates no entities.

Internal per-tick safety limits are 128 points/2,048 sends per scene and 1,024 points/12,000 sends globally. Optional particles and sounds leave 4,096 sends reserved for entity setup and maintenance. Full-scene admission also considers audience fanout; excessive fanout degrades to lite or skips according to policy. Cleanup is not blocked by visual budgets.

Freeze's circle coordinates are calculated once and kept private. No EffectLib source was copied or derived for this slice, and no EffectLib runtime dependency was introduced.

## Compatibility Boundary

`KILL_EFFECTS` keeps `death_effects` as its storage identifier to preserve existing SQL relationships. The deprecated, always-disabled `DEATH_EFFECTS` enum value is retained only for historical WorldGuard enum flags. It has no registered effects or menu; historical command/menu aliases resolve to Kill Effects.

Only category enable/menu settings are copied when their new keys are absent. Old configuration is not deleted. Legacy Explosion, Firework, and Lightning replacements, permissions, treasure settings, and complete cross-backend migration remain Block 2 work. Do not use the existing cross-backend migration command as a migration of the unfinished catalog.

New messages have shared English defaults in `core/src/main/resources/messages/killeffects.yml`; existing per-language overrides take precedence. Catalog localization completion remains part of Block 2.

## Validation Status

Local checks performed during implementation:

- Compilation of core, the PacketEvents adapter, and both WorldGuard modules passed.
- 17 JUnit tests passed (16 new tests plus the existing bootstrap test), with no failures, errors, or skipped tests. New coverage includes execution lifecycle, independent state, ID allocation, point/send budgets, capacity boundaries, configuration clamping, snapshots, timeline boundaries, and migration decisions.
- Root packaging and PacketEvents exclusion passed.
- No mocks, automated server tests, or PacketEvents serialization tests were added.

The final local verification on 2026-09-08 used `:core:test verifyPacketEventsNotBundled --offline --console=plain --rerun-tasks`; all 20 Gradle tasks executed successfully. Bytecode inspection of the packaged Freeze execution confirmed it has no outer selection field.

Manual validation of this implementation has **not** been performed in this session. The prepared environment's prior validation does not substitute for testing the new code.

| Pending check | Required observation |
|---|---|
| Identity | Correct skin and nametag; no removal or corruption of the real victim's tab entry |
| Capsule | Two full ice blocks at feet/head, traversable and visually stable |
| Timeline | Impact, freeze, hold, fracture, shatter, and final dissolve match the reference |
| Audience/preview | Correct full/reduced/private delivery, preference, vanish, region and world exclusions |
| Interruption | Respawn, all participant quits, world change/unload, reload and disable leave no visual resources |
| Capacity | Overlapping kills, same-chunk pressure and large audiences produce full/lite/skip without queuing |
| Dependency | Missing/incompatible PacketEvents disables only Kill Effects; test required ViaVersion/ViaRewind stack |
| World safety | No Bukkit visual entities, changed blocks, drops, damage or gameplay effects |

The ice correction interval is currently **two ticks with velocity reset**. This is a provisional implementation value, not a measured optimum. Establish the lowest stable frequency on the real client/server stack before closing Block 1. Record those results alongside the full manual checklist in the design, including packet/tick/memory measurements.
