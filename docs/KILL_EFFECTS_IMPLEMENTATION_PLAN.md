# Kill Effects Implementation Plan

This plan organizes the Kill Effects work into three sequential blocks. It describes what must be complete in each block, not the full product or technical specification. The authoritative detail for every requirement, behavior, constraint, default, timeline, migration rule, test policy, and acceptance criterion remains in [`docs/KILL_EFFECTS_DESIGN.md`](KILL_EFFECTS_DESIGN.md).

The design document records the solution agreed during product and engineering design. It is a development baseline, not a reason to preserve a flawed implementation. If development or real-server validation reveals a problem or a materially safer or more efficient solution, stop the affected work, communicate the finding and its impact, agree on the change, and update the design and implementation together.

## Delivery Model

```text
Block 0: Development readiness
                  |
                  v
Block 1: Complete platform + Freeze Kill vertical slice
                  |
                  v
Block 2: Catalog completion + release validation
```

- Blocks are sequential. A block starts only after the previous block passes its exit gate.
- Workstreams inside a block may run in parallel once their shared contracts are agreed.
- Integration happens continuously inside the block, not in one large merge at the end.
- A workstream is not complete merely because its code compiles. Its tests, cleanup behavior, configuration, and applicable real-server checks are part of the same work.
- Do not split these blocks into separate delivery phases unless an actual dependency or ownership conflict requires it. Team tickets may be smaller, but they must remain children of one of these three blocks.

## Block 0: Development Readiness

**Outcome:** every developer can compile, test, package, and run the approved stack before product implementation begins.

All exact dependency versions, supported environments, integration restrictions, testing constraints, and local reference locations for this block are defined in [`docs/KILL_EFFECTS_DESIGN.md`](KILL_EFFECTS_DESIGN.md), primarily under **Dependencies**, **Out of Scope**, **Automated Testing Policy**, and **Manual Real-Server Validation**.

### Parallel Workstreams

| Workstream | What must be ready |
|---|---|
| Build and dependencies | Add the approved PacketEvents repository and `compileOnly` dependency, add JUnit 4.13.2 for tests, add PacketEvents as a soft dependency, and confirm PacketEvents is not shaded into the plugin artifact. |
| Local development | Establish the test source layout, documented Gradle commands, IDE import, and a reproducible build from a clean checkout. |
| Real-server environment | Prepare the Minecraft 1.8.8 test server with the production Java runtime and the approved PacketEvents version. Include ViaVersion and ViaRewind when they are part of production. |
| Validation baseline | Record how to inspect loaded plugins, startup logs, generated entities, block changes, packet behavior, performance, and cleanup during manual tests. |
| Reuse and licensing | Confirm access to the EffectLib-Legacy source and reference JAR, identify likely source files, and record the licenses that must be preserved if code is copied or substantially derived. No EffectLib runtime dependency is added. |

### Required Decisions Before Integration

- One agreed package layout for the Kill Effects domain, runtime, renderer boundary, PacketEvents adapter, effects, API event, and tests.
- One real-server test matrix owned by the team, including the exact server implementation, Java runtime, PacketEvents version, and optional protocol translation plugins.
- One short verification command set for compilation, unit tests, packaging, and checking that PacketEvents was not bundled.
- One communication path for design changes discovered during implementation.

### Exit Gate

- [x] A clean build resolves PacketEvents and JUnit successfully.
- [x] Unit tests can be discovered and executed, even if the initial suite is empty or contains only a bootstrap test.
- [x] The packaged UltraCosmetics artifact does not contain PacketEvents runtime classes.
- [ ] The plugin can be installed on the prepared 1.8.8 environment with PacketEvents present.
- [x] The team can reproduce the same build and test commands.
- [x] EffectLib reuse sources and their applicable licenses are known before extraction begins.

**Do not start Block 1 until this gate passes.** Otherwise protocol or build incompatibilities will be discovered after the runtime has already been built around invalid assumptions.

## Block 1: Platform and Freeze Kill Vertical Slice

**Outcome:** Freeze Kill works end to end as a production-quality Kill Effect, and every shared capability required by the remaining catalog is complete and proven.

This is the architecture block and the highest-risk block. All behavioral and implementation detail is in [`docs/KILL_EFFECTS_DESIGN.md`](KILL_EFFECTS_DESIGN.md), especially **Runtime Architecture**, **Audience and Visibility**, **Packet-Only Visuals**, **Freeze Kill Vertical Slice**, **GUI and Preview**, **Player Visibility Preference**, **Region, World, and Vanish Policy**, **Performance and Capacity**, **Configuration**, **Migration and Compatibility**, **Cleanup and Failure Safety**, and the testing sections.

### Integration Order Inside the Block

The team may work in parallel, but shared contracts must stabilize in this order:

1. Domain contracts and compatibility identifiers.
2. Scene, ticker, capacity, and renderer interfaces.
3. PacketEvents adapter and trigger/policy integration against those interfaces.
4. Freeze Kill and private preview on the integrated platform.
5. Lifecycle, migration-foundation, and real-server validation before the block closes.

### Parallel Workstreams

| Workstream | What must be complete |
|---|---|
| Product model and compatibility foundation | Introduce `KILL_EFFECTS`, create the Kill Effect type and immutable selection model, keep per-kill execution state separate, introduce stable storage identifiers and alias parsing, and ensure the selection is not cleared when its owner dies. Build and test the migration decision mechanism now, but do not finalize legacy effect mappings before their Block 2 target effects exist. |
| Runtime lifecycle | Implement immutable contexts and snapshots, per-kill executions, the abstract execution safeguards, one synchronous central ticker, idempotent scenes, fake entity ID allocation, profile leases, safe mutation queues, duration enforcement, failure isolation, and cleanup for completion, failure, reload, disable, quit, world change, and world unload. Plugin shutdown must clean scenes before scheduler cancellation and before PacketEvents becomes unavailable. |
| Rendering boundary | Define the renderer interface used by effects and implement the isolated PacketEvents factory and adapter. Cover fake players, profiles, falling blocks, metadata, teleports, status/animations, particles, sounds, and destruction without exposing PacketEvents wrappers to effect implementations. Missing or incompatible PacketEvents must disable only Kill Effects with one clear warning. |
| Triggering and public API | Implement late `PlayerDeathEvent` attribution, all qualification rules, manager entry points for vanilla and minigame use, immutable victim snapshots, the cancellable trigger event, and victim/tick deduplication. No trigger path may mutate the death event. |
| Audience and policies | Capture fixed eligible audiences, full/reduced detail groups, visibility preferences, vanish visibility, enabled worlds, location-aware WorldGuard checks, anchor resolution, unloaded-world/chunk behavior, and viewer removal. No operation may force-load a chunk. |
| Capacity and configuration | Load and safely clamp shared settings, enforce global/world/chunk/entity/duration limits, account for generated points and actual viewer sends, select full/lite/skip without queuing, and expose only the approved settings. |
| Geometry foundation | Extract only the mathematics and transformations needed by Freeze Kill or the planned catalog, preserve file-specific licenses, use deterministic per-execution randomness, precompute immutable templates, cap points, and avoid unnecessary tick-loop allocations. |
| Freeze Kill | Implement the complete 60-tick visual timeline, normal and reduced airborne behavior, victim skin and nametag handling, traversable packet-only ice, stable falling blocks, restrained sound, full and lite signatures, supported final animation, and guaranteed cleanup. The implementation must follow the visual reference and constraints in the design. |
| Menus, preview, and preference | Replace the Death Effects menu entry, add the dedicated Kill Effect button behavior, left-click equip/unequip, right-click preview including locked effects, explicit lore, private preview placement, one-preview limit, cooldown, invalid-space handling, persistent `View-Kill-Effects`, and matching menu/command controls. |
| Verification | Add all applicable deterministic JUnit coverage and execute the complete Freeze Kill and lifecycle manual checks required by the design on the real server matrix. Do not introduce mocks or automated Minecraft integration tests. |

### Coordination Boundaries

- Effect authors depend only on the context, scene, renderer, timeline, geometry, and budget contracts. They do not construct packets or own schedulers.
- The PacketEvents workstream owns protocol encoding. Runtime and effect workstreams do not duplicate protocol logic.
- The persistence workstream owns canonical storage identifiers, alias parsing, and migration decisions. No other workstream derives persisted names directly from enum names.
- The menu workstream invokes the manager preview path. It does not create a second animation lifecycle.
- The vanilla listener and public API converge on the same manager trigger pipeline.
- Capacity policy is decided before an execution starts; effects do not independently invent queueing or overload behavior.

### Exit Gate

- [x] A killer's selected Freeze Kill triggers at the victim's resolved death location; the victim's own selection does not trigger on death.
- [x] Multiple overlapping executions from the same killer have isolated mutable state.
- [x] Eligible viewers receive the correct full or reduced scene; ineligible viewers receive nothing.
- [x] The fake victim has the correct supported identity presentation and does not corrupt the real victim's tab-list entry.
- [x] Ice and body visuals are packet-only, traversable, stable, and leave no blocks, entities, drops, damage, or gameplay changes.
- [x] Preview is private, reusable through the same runtime, limited, cooled down, and cleaned before replacement.
- [x] Capacity produces Freeze Kill's lite signature or safely skips; it never queues.
- [x] Every required completion and interruption path destroys scene resources exactly once from the runtime's perspective.
- [x] PacketEvents absence or incompatibility disables only Kill Effects and does not prevent UltraCosmetics from loading.
- [x] Stable storage identifiers, category aliases, cosmetic alias parsing, and idempotent migration decision logic are covered by unit tests without modifying or losing legacy data prematurely.
- [x] Applicable JUnit tests pass and all Freeze Kill, lifecycle, compatibility, and protocol-stack manual checks in the design are recorded as passing.

**Do not begin production work on the other seven effects until this gate passes.** Small exploratory prototypes are acceptable only when they answer a documented platform question and are not merged as production effect implementations.

## Block 2: Catalog Completion and Release Validation

**Outcome:** all eight polished effects ship on the proven platform, with complete resources, compatibility, performance validation, and acceptance evidence.

The exact catalog, visual direction, quality rules, compatibility aliases, settings, limits, and acceptance requirements are defined in [`docs/KILL_EFFECTS_DESIGN.md`](KILL_EFFECTS_DESIGN.md), primarily under **Effect Catalog**, **Existing Effect Migration**, **Visual Design Rules**, **Performance and Capacity**, **Configuration**, **Manual Real-Server Validation**, and **Acceptance Criteria**.

### Parallel Workstreams

After Block 1 closes, effect implementations can be distributed across the team because they now share a validated runtime and renderer.

| Workstream | What must be complete |
|---|---|
| Effect production | Implement Squid Missile, Head Rocket, Frostfire, Soul Vortex, Bloodburst, Divine Judgment, and Firework Finale with distinct timelines, silhouettes, climax, clean exits, restrained audio, bounded durations, and no gameplay/world state changes. |
| Legacy replacements | Map Explosion, Firework, and Lightning identities to their approved redesigned effects or aliases while retaining existing access, unlock, configuration, and permission behavior. |
| Migration completion | Activate and validate the idempotent migration for category/effect configuration, custom main-menu entries, treasure chest settings and messages, flat-file equipped/unlocked data, MySQL category values, and legacy permissions now that all destination effects exist. Preserve cosmetic IDs and foreign-key relationships where possible; prefer aliases over destructive SQL rewrites. |
| Lite variants and budgets | Give every effect a recognizable lite signature, enforce per-scene entity and point limits, precompute reusable geometry, and verify full/lite/skip decisions under concurrent load. |
| Shared visual utilities | Add only geometry, easing, trajectory, and transform code materially required by the catalog. Keep APIs narrow, deterministic, allocation-conscious, capped, licensed correctly, and covered by JUnit where pure. |
| Product resources | Complete effect definitions, safe per-effect configuration, menu items, names, descriptions, control lore, purchase and treasure chest behavior, permissions, commands, and maintained message resources consistent with the existing plugin localization process. |
| Hardening | Exercise simultaneous kills, same-chunk pressure, all interruption paths, invalid anchors, vanished victims, blocked regions/worlds, viewer preferences, protocol translation plugins, missing/incompatible PacketEvents, reload, and disable across the completed catalog. |
| Performance and acceptance | Measure packet sends, tick cost, scene counts, entity use, and memory on the real server. Tune within the designed limits and execute every acceptance criterion without expanding the automated test scope beyond approved isolated logic. |

### Effect Completion Rule

An effect is complete only when all of the following are true:

- [ ] Its full timeline has a readable impact, recognizable motion language, deliberate climax, and clean exit.
- [ ] Its lite signature remains recognizable under capacity pressure.
- [ ] It uses only the shared ticker, scene, renderer, geometry, policy, and budget mechanisms.
- [ ] It creates no real visual entity, block modification, damage, collision, drop, or other gameplay change.
- [ ] It respects fixed audiences, level of detail, viewer preference, vanish, world, region, and anchor policies.
- [ ] It remains bounded by duration, entity, point, and packet-send controls.
- [ ] Normal completion and every forced stop path clean all resources.
- [ ] Applicable pure logic has JUnit coverage and rendered behavior passes manual real-server validation.

### Exit Gate

- [ ] The initial catalog contains exactly the eight approved effects; future candidates remain out of scope.
- [ ] Every effect passes the effect completion rule in full and lite modes.
- [ ] All GUI, configuration, message, command, treasure chest, permission, persistence, and alias paths expose Kill Effects consistently and hide the retired Death Effects product name where required.
- [ ] The full automated unit suite passes using JUnit 4.13.2 only.
- [ ] The complete manual validation matrix and every acceptance criterion in the design pass on the approved real-server environment.
- [ ] Measured concurrency remains within configured scene, entity, point, packet-send, tick-time, and memory limits.
- [ ] Reload, disable, dependency failure, and runtime exceptions leave no packet-only resources visible and do not destabilize other UltraCosmetics features.
- [ ] Any design changes discovered during development have been communicated and reflected in both the design document and implementation.

## Coverage Map

This map prevents requirements from falling between blocks. It does not replace the referenced design sections.

| Design area | Delivery block |
|---|---|
| Scope, constraints, approved dependencies, test environment, EffectLib access/licensing preparation | Block 0 |
| Kill Effect category, selection/execution separation, context, manager, API event, attribution, deduplication | Block 1 |
| Ticker, scenes, renderer, PacketEvents isolation, fake IDs/profiles/entities, cleanup | Block 1 |
| Audience, level of detail, preference, vanish, worlds, regions, anchors | Block 1 |
| Capacity, packet budgets, configuration clamping, full/lite/skip policy | Block 1 |
| Freeze Kill, menu interaction, private preview | Block 1 |
| Stable storage identifiers, alias parsing, and migration decision foundation | Block 1 |
| Activation and end-to-end validation of every Death Effects migration path | Block 2 |
| Remaining seven effects and legacy visual replacements | Block 2 |
| Additional catalog geometry and effect-specific pure tests | Block 2 |
| Full catalog hardening, performance measurements, manual matrix, and final acceptance | Block 2 |

## Team Operating Rules

- Assign ownership by workstream, not by individual class. A workstream owner is responsible for integration and evidence, not merely code production.
- Keep one integration branch or equivalent continuous integration path per active block. Avoid maintaining parallel alternative runtimes.
- Merge shared contracts before implementations that consume them. Contract changes after that point require communication to all affected workstreams.
- Demonstrate the integrated product at least once during Block 1 before its exit gate and regularly during Block 2.
- Record manual server evidence beside the work item it validates so the final release check does not depend on memory.
- When implementation evidence contradicts the design, do not silently improvise. Communicate the issue, compare safety, performance, compatibility, and maintenance tradeoffs, then update the decision explicitly.
