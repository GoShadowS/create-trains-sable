# Create Trains Sable

Create Trains Sable is a Minecraft 1.21.1 add-on for NeoForge. It takes a Create
train out of Create's kinematic movement and gives it a Sable sub-level with a
Rapier rigid body when the train derails, crashes or exceeds a safe speed in a
curve. Once the wreck has settled on a straight piece of track, the mod hands
control back to Create's `TrackGraph` so stations, signals and schedules keep
working.

> **Status: 0.1.0, early development.** The physics model is unit-tested; the
> Sable integration is written against Sable 2.0.6's public API but has not yet
> been validated in a live game build. There is no released jar. Read
> [Known Issues / Limitations](#known-issues--limitations) before adding this to
> a world you care about.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Java | 21 |
| NeoForge | 21.1.228 or newer in the 21.1 line |
| Create | 6.0.10.x |
| Sable | 2.0.x (optional, needed for physics) |
| Sable Companion | 1.6.0 (bundled in the mod jar) |

The project is NeoForge-only. There is no Fabric version, no multiloader layout
and no plan to add one while Sable's development is centred on NeoForge.

## Installation

1. Install Minecraft 1.21.1 with NeoForge 21.1.228 or newer.
2. Put Create 6.0.10.x and Sable 2.0.x in `mods/`. Sable pulls in Veil, Flywheel,
   Ponder and Registrate; use the versions its own documentation lists.
3. Build this add-on from source with `./gradlew build` and copy
   `build/libs/createtrainssable-0.1.0.jar` into `mods/`. Sable Companion is
   embedded in that jar, so you do not install it separately.

If Sable is missing, the add-on loads, writes a warning to the log and leaves all
train movement to Create. Nothing is converted and `TrackGraph`, stations and
signals are untouched. This is why `sable` is declared as an `optional`
dependency in `neoforge.mods.toml`, not a required one.

## What it does

A server-side monitor reads the public state of every Create train each tick. It
samples the leading bogey's position, fits a circle through three samples to get
the curve radius, and compares the train's lateral acceleration with a limit
derived from track gauge, centre-of-gravity height and wheel-flange friction.
Create's own `Train.crash()` event, a sudden speed drop and a hard deceleration
are also conversion triggers.

On conversion, the mod writes a copy of each carriage's virtual blocks into the
world at the train's current pose and asks Sable to assemble those blocks into a
sub-level. The copy carries block-entity NBT, so chests and tanks in the wreck
are not empty. The original `CarriageContraptionEntity` stays alive but hidden;
it still owns the contraption's inventory, seats, schedule and signal state.
Sable's Rapier pipeline owns the body from that point on. Wheel adhesion, rolling
resistance and braking are applied as impulses on Sable's physics substeps, not
by teleporting the body.

When a body has been quiet, upright and close to a track for long enough, the mod
re-seeds the travelling points using Create's own `TrainMigration` and
`TravellingPoint.migrateTo` API, restores `Train.graph`, and makes the contraption
visible again. If the original graph no longer resolves, the mod looks for a
straight edge long enough to hold the whole train.

## Configuration

The server config lives at `config/createtrainssable-server.toml` and is regenerated
with documented defaults on first start. It is read on every tick, so a reload
takes effect without restarting the world. Key groups:

```toml
[general]
enabled = true
conversionMode = "THRESHOLD"   # THRESHOLD, ALWAYS, DISABLED
maxActiveTrains = 10
sessionTimeoutTicks = 12000

[derailment]
cantReliefFactor = 1.8        # extra lateral capacity from banked curves
flangeCoefficient = 0.45
overspeedGrace = 1.15
impactSpeedThreshold = 4.0    # m/s
hardBrakeDeceleration = 18.0  # m/s^2

[traction]
wheelRailFriction = 0.45
brakeForceMultiplier = 1.0
blockMassScale = 0.5

[rebinding]
maxSpeed = 0.25               # m/s
maxTiltDegrees = 12.0
stableTicks = 40

[debug]
showRapierHitboxes = false
```

`conversionMode = "ALWAYS"` converts every moving train, which is useful when
tuning thresholds. `DISABLED` and `enabled = false` both leave Create alone.
`showRapierHitboxes = true` writes body poses, speeds and tilt to the server log
every 20 ticks; Sable's own client debug overlay draws the actual colliders.

Mass and inertia are estimated from the blocks of each carriage using a
parallel-axis calculation. Sable remains authoritative for the body's mass
through its datapack-driven `MassTracker`. This mod ships definitions under
`data/createtrainssable/physics_block_properties/` for Create bogeys, railway
casings and train controls; server owners can override them with a datapack.

## Building and testing

See [BUILDING.md](BUILDING.md) for the full IntelliJ IDEA walkthrough. In short:

```bash
./gradlew test          # dependency-free physics unit tests
./gradlew build         # jar in build/libs/
./gradlew runClient     # development client, same as the IDE run config
```

## Known Issues / Limitations

- **Not yet validated in a live build.** The source is written against
  Create 6.0.10-281, Sable 2.0.6 and NeoForge 21.1.228, but no jar built from
  this repository has been run in a game yet. Expect to file the first
  integration bugs.
- **No released jar.** Downloadable builds will appear once the integration has
  been exercised in-game. Until then, build from source.
- **Sable's API is not semver-stable.** Types such as `ServerSubLevel`,
  `RigidBodyHandle` and `SubLevelAssemblyHelper` may move between Sable releases.
  A new Sable version can require an update here.
- **Rebinding is conservative.** It needs the original graph to still resolve the
  old travelling points, or a straight edge long enough for the whole train,
  within `maxTrackDistance`. A wreck in a tunnel, on a curve, or far from any
  track stays a wreck. After `sessionTimeoutTicks` the physics body is removed
  and Create keeps the train in its derailed state.
- **Conversion needs clear space.** Blocks are only materialised into air. A train
  that derails inside terrain produces a partial wreck, and changes made to the
  wreck's block entities are not merged back into the contraption on resync.
- **Passengers are ejected** at conversion time. They keep their inventory but do
  not ride the physical body.
- **Orientation at hand-off is upright.** The materialised blocks snap to the
  voxel grid, so a train that was pitched on a slope starts its rigid-body life
  axis-aligned. Rapier takes over rotation from there.
- **Server restarts.** Active wrecks are discarded when the server stops and are
  not re-adopted after restart. The hidden contraption is made visible again and
  the train stays derailed until a player deals with it.
- **Collisions before conversion** come from Create's own crash detection and from
  measured speed drops. Real rigid-body collisions between trains, entities and
  other sub-levels only exist after the conversion has happened.
- **No client-side prediction.** The server owns every physics decision; Sable's
  pose sync carries the result to clients. High-latency connections can show a
  short delay before a derailment becomes visible.
- **Compatibility with other Sable add-ons** (Create: Aeronautics, Offroad,
  Simulated) is limited to projecting positions through Sable Companion so that
  track lookups do not misread plot-grid coordinates. Their own physics bodies
  are not merged with train carriages.

## Why this is a separate mod

Sable's Create compatibility layer handles ordinary contraptions and rendering.
Create's trains use a different system (`Train`, `Carriage`, `CarriageContraption`,
`TravellingPoint`, `TrackGraph`), and Sable does not convert trains. Create:
Aeronautics uses Sable for aircraft, ships and offroad vehicles through its own
contraption system, not for rail transport. The abandoned Ferronautics prototype
made carriages interactive by assembling sub-levels at station assembly time, but
it drove those bodies kinematically from the carriage entity every tick, so
Rapier never owned their motion and a derailment could not be simulated.

## Licensing & Compatibility

The code in this repository is licensed under **Apache-2.0**. See [LICENSE](LICENSE)
for the full text and [NOTICE](NOTICE) for the attribution requirement. In plain
terms: you may fork, modify and redistribute this mod, including commercially, as
long as you keep the copyright and licence notices, mark changed files, preserve
the `NOTICE` information, and visibly credit **Create Trains Sable**,
**GoShadowS** and <https://github.com/GoShadowS/create-trains-sable> in your
README, mod page description or in-game credits. Forks must say that they are
forks. This is a legal obligation, not a technical lock: no licence can stop
someone from deleting a line of text, but doing so breaches the licence.

This add-on **does not include or redistribute the source code of Create, Sable,
Sable Companion or Create: Aeronautics**. It depends on their published jars as
runtime libraries and compiles against their public APIs. Create and Sable
Companion are MIT-licensed. Sable is licensed under the
[PolyForm Shield License 1.0.0](https://polyformproject.org/licenses/shield/1.0.0/),
which allows use and modification but restricts competing commercial products.
That restriction applies to Sable itself; if you distribute a modpack or a fork
that bundles Sable, check Sable's licence separately. The assets, names and
trademarks of Minecraft, Create and Sable belong to their respective owners.

## Credits

- RyanHCode and the Sable contributors for sub-levels and the Rapier pipeline.
- The Create team for the train system and its migration API.
- Zortifer's Ferronautics prototype, whose source made the limits of a
  kinematically driven sub-level obvious.
- Dimforge for the Rapier physics engine.

## License

Apache-2.0. See [LICENSE](LICENSE).
