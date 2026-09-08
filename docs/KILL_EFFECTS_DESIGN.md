# Kill Effects: Product and Technical Design

## Status

Approved design for development handoff.

This document defines the replacement of the existing Death Effects category with a new Kill Effects product for Minecraft 1.8.8. It includes the product behavior, runtime architecture, PacketEvents integration, selective EffectLib-Legacy reuse, migration, performance controls, testing policy, and initial effect catalog.

## Product Goal

Kill Effects reward the killer by playing a polished death animation at the victim's location. The selected effect belongs to the killer, while the victim and eligible nearby players see the result.

The effects must be visually memorable without affecting combat or world state. They must never damage players, change blocks, create drops, or leave real entities behind.

## Scope

- Replace Death Effects with Kill Effects.
- Trigger the killer's selected effect when that player kills another player.
- Support Minecraft 1.8.8 only.
- Make effects visible to eligible nearby players.
- Add private previews from the Kill Effects menu.
- Preserve existing Death Effect selections, unlocks, and permissions where applicable.
- Move and redesign the existing Explosion, Firework, and Lightning effects.
- Build a reusable runtime for complex, concurrent, packet-only animations.
- Deliver Freeze Kill as the first technical and visual vertical slice.

## Out of Scope

- Support or fallback implementations for Minecraft versions other than 1.8.8.
- Real block placement or rollback.
- Real Bukkit entities for visual components.
- Damage, knockback, fire, collision, drops, or other gameplay changes.
- A general-purpose YAML animation language in the first release.
- Automated server, smoke, PacketEvents, or Bukkit integration tests.
- MockBukkit, embedded servers, Testcontainers, or mocked Minecraft runtimes.

## Existing System

The current implementation is not a suitable runtime for complex Kill Effects:

- `core/src/main/java/be/isach/ultracosmetics/cosmetics/deatheffects/DeathEffect.java` registers a listener on every equipped Death Effect.
- It triggers when the cosmetic owner dies, not when the owner kills another player.
- The equipped cosmetic object and the running visual effect are the same object.
- Only Explosion, Firework, and Lightning exist.
- There is no independent execution per kill.
- There is no scene lifecycle, centralized ticker, packet budget, fake entity registry, audience snapshot, or guaranteed scene cleanup.
- There is no Death Effect preview implementation.
- Persistence currently derives stored category names from `Category`, so a direct enum rename would lose existing data.

Kill Effects must separate the persistent cosmetic selection from each temporary animation execution.

## Product Decisions

| Topic | Decision |
|---|---|
| Public name | Kill Effects |
| Internal category | `KILL_EFFECTS` |
| Configuration path | `Kill-Effects` |
| Permission prefix | `ultracosmetics.killeffects` |
| Java package | `be.isach.ultracosmetics.cosmetics.killeffects` |
| Supported server | Spigot/Paper-compatible Minecraft 1.8.8 only |
| Effect owner | Killer |
| Effect anchor | Victim's final death location, corrected when required |
| Runtime entities | Packet-only client entities |
| World modification | None |
| Packet implementation | PacketEvents |
| Scheduling | One synchronous centralized ticker |
| Nearby visibility | Fixed audience captured when the scene starts |
| Preview | Private right-click preview with cooldown |
| Existing Death Effects | Removed as a product category and migrated into Kill Effects |

## Dependencies

### PacketEvents

Use PacketEvents as an externally installed plugin, not as a bundled runtime.

```groovy
repositories {
    maven {
        url = 'https://repo.codemc.io/repository/maven-releases/'
    }
}

dependencies {
    compileOnly 'com.github.retrooper:packetevents-spigot:2.13.0'
}
```

Add PacketEvents to `softdepend` in `plugin.yml`. Kill Effects must be disabled with one clear startup warning when PacketEvents is unavailable or incompatible. The rest of UltraCosmetics must continue loading.

PacketEvents classes must be isolated behind an integration factory so they are not class-loaded when the plugin is absent.

Version `2.13.0` is the approved target. Before completing the Freeze Kill spike, verify this exact combination on the real test environment:

- Spigot/Paper-compatible Minecraft 1.8.8 server.
- The production Java runtime.
- PacketEvents 2.13.0.
- ViaVersion and ViaRewind when those plugins are part of the production stack.

If 2.13.0 fails that environment, select the newest PacketEvents release that passes the same validation and update this document and dependency together. Do not add an NMS fallback.

Local PacketEvents documentation is available at:

```text
docs/PacketEvents/
```

### JUnit

JUnit is the only approved automated testing dependency:

```groovy
dependencies {
    testImplementation 'junit:junit:4.13.2'
}
```

Do not add another testing framework or Minecraft test runtime.

### EffectLib-Legacy

Do not depend on or ship the EffectLib runtime JAR.

Available sources:

```text
C:\Users\vendi\Documents\software-workspace\java-projects\jack\EffectLib-Legacy
or
libs/EffectLib-Legacy
```

Available local JAR, for reference only:

```text
libs/EffectLib-5.10-SNAPSHOT.jar
```

The source code is an implementation reference and an approved source for selective code reuse. Copy only useful mathematics, geometry, and transformation algorithms into the Kill Effects implementation, then adapt and improve them for this runtime.

Do not copy or use:

- `EffectManager` scheduling.
- EffectLib's reflection-based particle transport.
- EffectLib's `ParticleEffect` runtime.
- Asynchronous Bukkit or packet operations.
- Real-entity behavior from EffectLib effects.
- Configuration reflection that writes directly into public fields.

Primary reuse candidates:

- Axis and yaw/pitch vector rotations.
- Circle, arc, line, helix, sphere, cylinder, donut, star, wave, vortex, and DNA geometry.
- Dragon-style trajectories.
- Random circle and sphere vectors.
- Selected transform composition where it materially simplifies an effect.

Required improvements during extraction:

- Use a random generator owned and seeded by each execution instead of a shared global `Random`.
- Precompute immutable geometry templates when the shape does not change.
- Avoid repeated `Location` and `Vector` allocation inside tick loops.
- Make mutating and non-mutating operations explicit.
- Reveal precomputed trajectory segments over time instead of regenerating complete shapes every tick.
- Cap all point counts before packet rendering.
- Remove unused general-purpose methods.
- Cover extracted pure mathematics with JUnit tests.

Licensing must be reviewed per copied file. The EffectLib project root is MIT, while `EffectLib-Legacy/src/main/java/de/slikey/effectlib/util/MathUtils.java` contains an Apache License 2.0 header. Preserve all required copyright and license notices for copied or substantially derived code. Do not replace those notices with one generic EffectLib attribution.

## Runtime Architecture

```text
PlayerDeathEvent or public API
            |
            v
KillAttributionResolver
            |
            v
KillEffectManager.trigger(...)
            |
            +--> validate category and selected effect
            +--> apply world, region, vanish, and capacity policies
            +--> capture immutable victim and audience snapshots
            |
            v
KillEffect.createExecution(context, scene)
            |
            v
KillEffectExecution
            |
            v
KillEffectTicker
            |
            v
KillEffectScene
            |
            v
PacketEventsKillEffectRenderer
```

### Persistent Selection

`KillEffect` represents the cosmetic equipped by a player. It must not contain mutable state belonging to one kill and must not register its own death listener.

```java
public abstract class KillEffect extends Cosmetic<KillEffectType> {

    protected abstract KillEffectExecution createExecution(
            KillEffectContext context,
            KillEffectScene scene
    );
}
```

The equipped selection remains active when its owner dies because `KILL_EFFECTS` is not a clear-on-death category.

### Per-Kill Execution

Every qualifying kill creates a separate execution:

```java
public interface KillEffectExecution {

    void start();

    void tick(int elapsedTicks);

    boolean isComplete();

    void stop();
}
```

This separation is mandatory. One killer may produce multiple overlapping executions, and no mutable animation state may be shared between them.

An abstract base implementation should provide duration idempotent stop behavior, duration enforcement, error isolation, and access to the scene and immutable context.

### Kill Context

`KillEffectContext` is immutable and contains:

- Killer UUID and name.
- Victim UUID and name.
- Victim skin properties required by the fake-player renderer.
- Victim yaw and pitch.
- Exact death location.
- Resolved visual anchor.
- Fixed audience snapshot.
- Per-execution deterministic seed.
- Preview flag.
- Initial server tick.

Do not retain live `Player` references inside long-lived execution state when a UUID or immutable snapshot is sufficient.

### Attribution

The vanilla listener uses `PlayerDeathEvent` and resolves `event.getEntity().getKiller()`.

A kill qualifies when:

- The victim is a real player, not an NPC exposed as an offline Bukkit player.
- A killer is available.
- Killer and victim are different players.
- The killer is online when the context is created.
- Minecraft version is exactly 1.8.8.
- The category and selected type are enabled.
- The killer has a Kill Effect equipped.
- The death world is enabled.
- The victim's effect location is allowed by region policy.
- Runtime capacity permits a full or lite execution.

Run the final trigger after other death handlers have had an opportunity to establish attribution, without mutating the death event.

### Public Integration API

Minigames may implement eliminations without a normal attributed `PlayerDeathEvent`. Expose both forms:

```java
killEffectManager.play(killer, victim);
killEffectManager.play(killer, victimSnapshot, location);
```

Publish a cancellable `UCKillEffectTriggerEvent` before creating the execution. It must expose the killer, victim snapshot, location, selected type, preview state, and proposed audience.

The manager must guard against a minigame invoking the API and then producing the same vanilla death event. Use a short-lived deduplication key based on victim UUID and server tick.

### Central Ticker

Use one synchronous Bukkit task that ticks all active executions.

The ticker must:

- Add and remove executions safely outside active iteration.
- Catch failures per execution.
- Stop failed executions and clean their scenes.
- Enforce the maximum duration even when an implementation never reports completion.
- Stop all executions on reload and disable before scheduler cancellation.
- Never run Bukkit world access asynchronously.

Do not create one scheduler per effect or one listener per equipped player.

### Scene Lifecycle

`KillEffectScene` owns every visual resource created by one execution:

- Fixed viewer UUIDs.
- Allocated fake entity IDs.
- Temporary player-profile leases.
- Packet-only players and falling blocks.
- Per-viewer spawned entity state.
- Current packet and point usage.
- Cleanup state.

Scene cleanup must be idempotent. Calling it after normal completion, an exception, reload, disable, viewer quit, or world change must never leak or double-release resources.

### Renderer Boundary

Effect implementations must not instantiate PacketEvents wrappers directly.

```java
public interface KillEffectRenderer {

    void spawnPlayer(...);

    void spawnFallingBlock(...);

    void teleportEntity(...);

    void playEntityStatus(...);

    void spawnParticle(...);

    void playSound(...);

    void destroyEntities(...);
}
```

`PacketEventsKillEffectRenderer` is the only PacketEvents implementation. It handles protocol-specific metadata, block data encoding, profile packets, wrapper construction, and packet sending.

## Audience and Visibility

Capture the audience once when the execution begins.

A viewer is eligible when:

- The viewer is online.
- The viewer is in the death world.
- The viewer is within the configured range.
- The viewer can see the victim according to Bukkit visibility.
- The viewer has not disabled Kill Effect visibility.

The fixed audience prevents late viewers from receiving entities without the scene's setup packets. Remove disconnected or world-changing viewers from further sends.

Default visible range: 32 blocks.

Level of detail:

- Up to 16 blocks: full effect.
- More than 16 and up to 32 blocks: reduced particle density.
- More than 32 blocks: no packets.

The killer and victim may be included when they satisfy the same rules. A victim can continue seeing the scene after respawn if the client remains in the same world and location range.

## Packet-Only Visuals

All scene entities are client-side packet entities. Do not register them with Bukkit or add them to the server world.

Use a global allocator that issues unique active entity IDs from a controlled high range. IDs must not be reused until the previous scene has destroyed them for all remaining viewers.

The renderer may use the following PacketEvents capabilities:

- Spawn player.
- Player info and profile properties.
- Spawn entity, including falling blocks.
- Entity metadata.
- Entity equipment when an effect explicitly needs it.
- Entity teleport and head rotation.
- Entity status and supported animations.
- Particles.
- Sounds.
- Entity destruction.

Never use fake block-change packets to build solid-looking effect structures unless a future design explicitly proves collision and restoration safety. Packet-only falling blocks are the approved representation for traversable full block visuals.

## Freeze Kill Vertical Slice

The visual reference is:

```text
reference.png
```

Freeze Kill is the first implementation because it validates all high-risk technical requirements: player replication, skin, nametag, fake falling blocks, traversability, synchronized animation, nearby audiences, and cleanup.

### Intended Result

1. The real victim dies normally.
2. A stationary visual replica appears immediately with the victim's skin and nametag.
3. Two fake ice falling blocks form a transparent capsule from feet to head.
4. Ice fragments and cold particles sell the freezing impact.
5. The victim remains suspended for a short hold.
6. Cracks and sound communicate the upcoming release.
7. The ice shatters radially.
8. The fake body receives a final supported animation and dissolves.
9. Every fake entity and profile resource is removed.

### Timeline

| Ticks | Phase | Visual and audio direction |
|---:|---|---|
| 0 | Impact | Compact cold burst, snow fragments, low glass impact |
| 1-6 | Freeze | Lower and upper ice blocks form with ascending block-crack particles |
| 7-12 | Seal | Cyan pulse and final crystal particles around the silhouette |
| 13-42 | Hold | Sparse snow, restrained frost breath, occasional glass creak |
| 43-52 | Fracture | Increasing cracks and subtle capsule vibration |
| 53-58 | Shatter | Glass break, radial ice shards, staggered capsule destruction |
| 59-60 | Cleanup | Final body dissolve and destruction of remaining resources |

Target duration: 60 ticks, or 3 seconds.

### Fake Victim

Spawn a packet-only player with a unique fake entity ID and the victim's captured UUID, name, location, yaw, pitch, and skin context.

Nearby eligible viewers should already know the real victim's profile at death time. Reuse that known profile without adding or removing the connected victim from the tab list.

When a profile is not known, such as a custom API invocation after the victim has left, use a synthetic UUID and a temporary profile lease:

```text
PLAYER_INFO ADD
SPAWN_PLAYER
skin load safety interval
DESTROY_ENTITY
PLAYER_INFO REMOVE
```

Never remove the real connected victim's UUID from a viewer's player list.

Minecraft 1.8 does not provide modern per-limb player poses. The approved visual is a standing or crouching replica with the basic damage/death status animations supported by the 1.8 protocol.

### Traversable Ice

Create the capsule from two packet-only `FALLING_BLOCK` entities using 1.8 ice block data. Place one at the lower body and one at the upper body.

Falling blocks may simulate gravity client-side. Keep them visually fixed with the minimum teleport frequency proven stable during the real-server spike. Do not blindly teleport every tick if a lower frequency is visually stable.

Because neither the fake player nor the ice exists in the server world, they do not provide server-side collision, alter blocks, interact with anticheat as world obstacles, or require rollback.

Do not implement the capsule with `BLOCK_CHANGE`: client-side fake blocks can create movement prediction, ghost-block, chunk refresh, and restoration problems.

### Anchor Resolution

Use the exact victim position when it is visually valid. For airborne deaths, search down only a small configured distance for a presentation surface. Do not load chunks or move the real victim.

When no safe anchor exists, including void deaths, run a reduced airborne Freeze signature instead of creating a misplaced capsule.

## Effect Catalog

Quality is more important than quantity. The initial release contains eight distinct, polished effects.

| Effect | Direction |
|---|---|
| Freeze Kill | Victim replica is frozen inside traversable ice, held, cracked, and shattered |
| Squid Missile | A fake squid captures the victim's essence and launches upward as a missile before bursting |
| Head Rocket | The victim's head rises on a rocket trail and explodes into a compact firework finale |
| Frostfire | Interlocking cold and fire helices consume the body and collapse into one final pulse |
| Soul Vortex | A dark portal and spiral disassemble the victim's silhouette and pull it inward |
| Bloodburst | Stylized redstone and block fragments create a radial, non-gory impact signature |
| Divine Judgment | Layered lightning, electrical rings, and a vertical disintegration strike the death point |
| Firework Finale | The victim transforms into a short, controlled multi-stage firework sequence |

Future candidates, not part of the first delivery:

- Black Hole.
- Dragon Consumption.
- Graveyard.
- Atomic Collapse.
- Pixel Disintegration.

### Existing Effect Migration

| Existing type | New implementation | Compatibility requirement |
|---|---|---|
| Explosion | Bloodburst or redesigned Explosion alias | Preserve old `Explosion` identity as an alias |
| Firework | Firework Finale | Preserve old `Firework` identity as an alias |
| Lightning | Divine Judgment | Preserve old `Lightning` identity as an alias |

The final display names can improve while stable aliases continue resolving old configuration, unlock, and permission data.

## Visual Design Rules

Every effect must have:

- A readable anticipation or immediate impact.
- One recognizable silhouette or motion language.
- A short hold only when it improves recognition.
- A deliberate climax.
- A clean visual exit.
- A recognizable lite variant.
- Layered audio with restraint rather than one repeated sound every tick.
- A normal duration between 1.5 and 4 seconds.
- No prolonged combat obstruction.

Particle count alone is not quality. Timing, silhouette, contrast, motion, sound, and cleanup define the product quality.

## GUI and Preview

Add a Kill Effects main-menu category and category menu using established UltraCosmetics menu behavior.

Interaction:

- Left click equips or unequips.
- Right click previews.
- Lore states both controls explicitly.
- The currently equipped effect has a clear visual state.
- Enabled but locked effects may be previewed to support purchase decisions.
- Preview does not equip, unlock, or persist anything.

Preview behavior:

- Private to the requesting player.
- Uses that player's skin and name.
- Anchored approximately three blocks in front of the player when valid.
- Maximum one active preview per player.
- Starting another preview stops and cleans the previous one.
- Default cooldown is 5 seconds.
- Invalid preview space produces a concise message and no partial scene.

The existing generic right-click path currently performs no action for Death Effects. Implement a dedicated Kill Effect menu button instead of introducing category-specific branching throughout unrelated buttons.

## Player Visibility Preference

Add a persistent player preference:

```text
View-Kill-Effects: true
```

Disabling it prevents that viewer from receiving other players' Kill Effect packets. It does not prevent the viewer's equipped Kill Effect from being shown to other eligible players.

Expose the preference through the appropriate menu control and command behavior consistent with the existing plugin UX.

## Region, World, and Vanish Policy

The effect manifests at the victim's location, so region policy must be evaluated there. The current WorldGuard integration primarily evaluates a `Player` at the player's current position and must be extended to support location-aware category checks.

A Kill Effect must not reveal a vanished victim. Capture audience visibility with `viewer.canSee(victim)` before the victim is removed or hidden by respawn logic.

Do not force-load the victim's chunk. If the chunk or world becomes unavailable, stop and clean the scene.

## Performance and Capacity

### Default Limits

| Limit | Default |
|---|---:|
| Visible range | 32 blocks |
| Full-detail range | 16 blocks |
| Maximum active scenes globally | 12 |
| Maximum active scenes per world | 6 |
| Maximum full scenes per chunk | 2 |
| Maximum duration per scene | 100 ticks |
| Maximum packet-only entities per scene | 12 |
| Active previews per player | 1 |
| Preview cooldown | 5 seconds |

All configurable numeric values must be clamped to safe ranges during loading.

### Packet Budget

Measure cost as actual sends:

```text
geometry points x eligible viewers
```

One particle point sent to twenty viewers is twenty sends. The scene and global ticker must track both generated points and expected viewer sends.

Precompute reusable geometry templates and avoid regenerating static shapes for each viewer or tick.

### Capacity Degradation

Do not queue Kill Effects. A delayed animation would no longer correspond to the kill that caused it.

When full-detail capacity is exhausted, play the selected effect's lite signature:

- One recognizable particle burst or short trajectory.
- One restrained signature sound.
- No fake player unless the lite design explicitly budgets it.
- No long-lived fake entities.

If even lite capacity is unavailable, skip the scene safely without affecting the death event.

## Configuration

Keep shared runtime controls separate from cosmetic definitions:

```yaml
Kill-Effects-Settings:
  Visible-Range: 32
  Full-Detail-Range: 16
  Max-Active-Global: 12
  Max-Active-Per-World: 6
  Max-Full-Effects-Per-Chunk: 2
  Max-Duration-Ticks: 100
  Max-Entities-Per-Scene: 12
  Preview-Cooldown: 5
  Sounds: true
  Respect-Vanish: true
  Lite-Mode-On-Capacity: true
```

Expose only safe, meaningful per-effect options:

```yaml
Kill-Effects:
  Freeze:
    Enabled: true
    Duration: 60
    Show-Description: true
    Treasure-Chest-Weight: 1
    Purchase-Price: 500
```

Do not expose raw packet counts, arbitrary class names, reflection targets, or an unrestricted timeline DSL.

## Migration and Compatibility

A direct rename from `DEATH_EFFECTS` to `KILL_EFFECTS` would break persisted values such as:

```text
DEATH_EFFECTS:Explosion
enabled.death_effects
death_effects
```

Introduce a stable storage identifier separate from the enum and public configuration name. `KILL_EFFECTS` may continue recognizing `death_effects` as its historical storage alias without exposing that old name in the new UI or API.

Migration must cover:

- `Categories-Enabled.Death-Effects` to `Categories-Enabled.Kill-Effects`.
- `Categories.Death-Effects` to `Categories.Kill-Effects`.
- Existing Explosion, Firework, and Lightning configuration.
- Custom main-menu entries using `DEATH_EFFECTS`.
- Treasure chest category settings and messages.
- Flat-file `enabled.death_effects` selections.
- Flat-file `DEATH_EFFECTS:*` unlock entries.
- MySQL rows whose stored category is `death_effects`.
- Legacy permission nodes for Explosion, Firework, and Lightning.

Prefer storage aliases over destructive SQL rewrites. Existing foreign-key relationships and cosmetic IDs should remain stable where possible.

Migration must be idempotent. Running it again must not duplicate entries, overwrite explicit new settings, or lose unlocks.

## Cleanup and Failure Safety

- Scene cleanup is safe to call multiple times.
- Reload stops and cleans scenes before cancelling plugin tasks.
- Disable destroys all visible packet entities before PacketEvents becomes unavailable.
- Viewer quit removes that viewer from future sends.
- Viewer world change destroys that viewer's scene entities when possible and removes the viewer.
- Killer quit does not invalidate an already captured scene.
- Victim quit or respawn does not invalidate immutable snapshot data.
- World unload stops all scenes anchored in that world.
- One effect exception cannot stop the central ticker.
- No chunk is force-loaded for cleanup.
- No real entity or block rollback is required.

## Automated Testing Policy

Use JUnit 4.13.2 only for deterministic, isolated unit tests that do not require a Minecraft server.

Approved JUnit subjects:

- Circle, arc, line, helix, sphere, curve, and other extracted geometry.
- Axis and yaw/pitch rotations.
- Transform and easing functions.
- Deterministic geometry generated from a seed.
- Point-count caps.
- Packet-budget calculations.
- Timeline phase transitions.
- Abstract execution start, completion, timeout, and idempotent stop state.
- Entity ID allocation and release logic independent of PacketEvents.
- Category and cosmetic alias parsing.
- Migration decision logic independent of Bukkit storage.
- Configuration clamping.
- Full-to-lite capacity decisions.

Do not create automated tests for:

- Bukkit event dispatch.
- PacketEvents packet serialization or injection.
- Fake player spawning.
- Skin and nametag rendering.
- Ice movement or traversability.
- WorldGuard or vanish integration.
- Respawn, quit, world changes, or reload on a server.
- Sounds and particles as rendered by the client.
- ViaVersion or ViaRewind compatibility.
- Real server performance.

Do not add mocks to force server-dependent behavior into unit tests. Those behaviors are verified manually in the real test environment.

## Manual Real-Server Validation

All Minecraft behavior is tested manually on the real 1.8.8 test server.

Required Freeze Kill validation:

- The real victim dies normally.
- The fake victim uses the correct skin.
- The fake victim shows the correct nametag.
- The real victim's tab-list entry is not corrupted.
- The capsule appears as two complete transparent ice blocks.
- The capsule remains visually stable.
- Players can pass through the body and ice.
- The freeze, hold, fracture, and shatter timing is coherent.
- Nearby eligible players see the same scene.
- Distant and ineligible players receive nothing.
- Preview is private.
- Viewer preference is respected.
- No Bukkit entity is created.
- No block is modified.
- No item drops are created by the effect.
- All visual entities disappear after completion.

Required lifecycle validation:

- Victim respawns during the scene.
- Victim disconnects.
- Killer disconnects.
- Viewer disconnects.
- Viewer changes world.
- World unloads.
- UltraCosmetics reloads with active scenes.
- Server disables with active scenes.
- Several kills occur simultaneously.
- Several kills occur in the same chunk.
- Capacity degradation uses the correct lite effect.
- PacketEvents is missing.
- PacketEvents is present but incompatible.
- WorldGuard blocks the death location.
- Victim is vanished from some viewers.
- ViaVersion and ViaRewind are installed when required by production.

Performance and memory measurements are also performed on the real server. They are not implemented as automated smoke tests in this repository.

## Acceptance Criteria

- Killing a player triggers the Kill Effect selected by the killer.
- Dying no longer triggers a Death Effect owned by the victim.
- Kill Effects are available only on Minecraft 1.8.8.
- Freeze Kill reproduces the victim's skin and nametag.
- Freeze Kill's body and ice are traversable.
- Eligible nearby players see the effect.
- Viewer visibility preferences, vanish, worlds, and region restrictions are respected.
- No effect changes real blocks or gameplay state.
- No effect creates a real Bukkit entity for a visual component.
- Every scene cleans up after completion, failure, quit, world change, reload, disable, or world unload.
- Multiple executions from the same killer can overlap without sharing mutable state.
- Runtime limits prevent unbounded entity, particle, or packet growth.
- Capacity pressure produces the selected effect's lite signature rather than a delayed scene.
- Existing Explosion, Firework, and Lightning owners retain access through migration aliases.
- PacketEvents absence disables only Kill Effects and produces a clear startup warning.
- Automated tests use only JUnit 4.13.2 and cover only isolated unit-testable logic.
- All Minecraft rendering and integration behavior is validated manually on the real 1.8.8 test server.

## Development Principle

Build the hardest protocol behavior first. Freeze Kill must prove fake-player identity, traversable ice, audience delivery, and cleanup before the team invests in the remaining catalog. Once that vertical slice is stable, every additional effect becomes a controlled composition of a tested scene lifecycle, PacketEvents renderer, and selectively reused geometry.
