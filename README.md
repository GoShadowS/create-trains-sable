# Create Trains Sable

Create Trains Sable is a Minecraft 1.21.1 add-on for NeoForge. It replaces the
object a Create train is built from: instead of a contraption entity, every
carriage becomes a Sable sub-level with a Rapier rigid body. A train can therefore
derail, crash, collide and be walked on like any other Sable structure. A wreck
stays where it fell; if the server owner enables `[rebinding] autoResync`, a
settled wreck is put back on a nearby track instead, so stations, signals and
schedules keep working.

> **Status: 0.1.0, early development.** The physics model is unit-tested; the
> Sable integration is written against Sable 2.0.6's public API but has not yet
> been validated in a live game. There is no public release yet. Read
> [Known Issues / Limitations](#known-issues--limitations) before adding this to
> a world you care about.

## Compatibility

Targets Minecraft 1.21.1 on NeoForge 21.1.228 or newer in the 21.1 line, with
Create 6.0.10.x. Sable 2.0.x is optional and is required for the structure and
physics modes; Sable Companion 1.6.0 is bundled. Create: Aeronautics 1.3.x is
compiled against - it is the reference for what a Sable structure feels like in
game - but it is neither required nor bundled. Java 21 is the language level. There is no
Fabric version and no multiloader layout while Sable's development is centred on
NeoForge.

Without Sable the add-on loads, writes a warning to the log and leaves all train
movement to Create. Nothing is converted and `TrackGraph`, stations and signals
are untouched. This is why `sable` is declared as an `optional` dependency in
`neoforge.mods.toml`, not a required one.

## What it does

The add-on replaces the object a train is made of. A Create train is a
`CarriageContraption` entity that the game draws and collides for you; here every
carriage is turned into a **Sable sub-level** instead, so the train is a real
structure in Sable's plot grid: it is rendered by Sable, it collides with terrain,
entities and other sub-levels, and tools that understand Sable can see it.

The replacement happens as soon as a train exists - built in a station or loaded
from a save - unless `convertOnAssembly` is turned off. Create's
`CarriageContraptionEntity` stays alive but invisible: it still owns the schedule,
the seats, the contraption inventory and the train maths. Create therefore keeps
choosing the route, while what the player sees and bumps into is Sable.

The running train is then **simulated, not teleported**. Each tick the mod asks
Create where every carriage should be, and pushes the body there with forces
(`[rails]` below): a position/velocity controller holds it on the sleeper line, a
torque controller keeps it pointing along the track, gravity is cancelled
feed-forward, and a spring-damper coupler ties each carriage to the next one so the
chain transfers real momentum. The force is capped by the grip of the wheels, and
a capped controller that cannot follow its carriage is how a train leaves the rails
in this mod: it is not a trigger that decides to derail, it is physics that runs
out of grip.

A server-side monitor reads the public state of every train each tick. It samples
the leading bogey's position, fits a circle through three samples to get the curve
radius, and compares the train's lateral acceleration with a limit derived from
track gauge, centre-of-gravity height and wheel-flange friction. Create's own
`Train.crash()` event, a sudden speed drop and a hard deceleration are also
derailment triggers.

When a trigger fires, the mod stops following the train: the sub-levels are
released to Rapier with the train's momentum, passengers are ejected, and wheel
adhesion, rolling resistance and braking are applied as impulses on Sable's
physics substeps. From that point the wreck moves only because Rapier says so.

By default the wreck simply stays where Rapier put it: it is a normal Sable
structure, it can be walked on, pushed, broken or left in the tunnel it fell into.
With `[rebinding] autoResync = true` a body that has been quiet, upright and close
to a track for long enough is put back instead: the mod re-seeds the travelling
points using Create's own `TrainMigration` and `TravellingPoint.migrateTo` API,
restores `Train.graph`, and rebuilds the sub-levels at the wreck's pose. If the
original graph no longer holds the train, the mod looks for the closest straight
edge long enough to hold it - and gives up rather than teleporting the wreck
somewhere else.

## Configuration

The server config lives at `config/createtrainssable-server.toml` and is regenerated
with documented defaults on first start. It is read on every tick, so a reload
takes effect without restarting the world. Key groups:

```toml
[general]
enabled = true
conversionMode = "THRESHOLD"   # THRESHOLD, ALWAYS, DISABLED
convertOnAssembly = true       # a train is a Sable structure from the moment it exists
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

[rails]
physical = true              # forces; false = teleport the structure onto the carriage
positionGain = 10.0          # 1/s^2
velocityGain = 4.0           # 1/s
maxAcceleration = 24.0       # m/s^2 of grip available at the wheel/rail contact
angularGain = 5.0
angularVelocityGain = 2.0
coupling = true              # spring-damper between neighbouring carriages
couplingStiffness = 8.0      # 1/s^2 per block of extension
couplingMaxAcceleration = 20.0
lateralTolerance = 1.5       # blocks away from the ideal pose that counts as leaving the rails
toleranceTicks = 10

[rebinding]
autoResync = false            # put a settled wreck back on the nearest track
maxSpeed = 0.25               # m/s
maxTiltDegrees = 12.0
stableTicks = 40

[debug]
showRapierHitboxes = false
```

`[rails] physical = false` restores the old kinematic behaviour - the structure is
teleported onto the carriage every tick - which is cheaper and cannot derail but
also cannot be pushed, so it is only meant as an escape hatch.
`[traction] gravity` must match the gravity Sable applies to sub-levels in your
dimension, or a train will hang slightly above or below its rails.

`conversionMode = "ALWAYS"` converts every moving train, which is useful when
tuning thresholds. `DISABLED` and `enabled = false` both leave Create alone.
`showRapierHitboxes = true` writes body poses, speeds and tilt to the server log
every 20 ticks; Sable's own client debug overlay draws the actual colliders.

Mass and inertia are estimated from the blocks of each carriage using a
parallel-axis calculation. Sable remains authoritative for the body's mass
through its datapack-driven `MassTracker`. This mod ships definitions under
`data/createtrainssable/physics_block_properties/` for Create bogeys, railway
casings and train controls; server owners can override them with a datapack.

## Relationship to Create: Loconautics and Create: Simurail

Two projects define what a "physical Create train" should feel like, and both are
references for this add-on. Neither is used here: no code, API or dependency is
shared, and neither needs to be installed.

**Create: Loconautics** (unreleased, Create: Aeronautics add-on) reimplements
Create trains as Sable physics objects assembled at a train station. From its
published material: real derailments and crash deformation, physical coupling,
trains that need real rotational power, blocks on board staying interactive
(furnaces, chests, machinery), building on an assembled train, and a per-train
choice between a Create contraption and a Sable body. Vanilla features that do not
survive the change - portals, operation in unloaded chunks, schedules - are being
added one by one after release.

**Create: Simurail** (MIT, source available) takes the other route: the train is a
Sable sub-level the player assembles, and it is moved by *physics bogeys* - axles
constrained to the Create track graph, applying traction limited by wheel
adhesion, slipping when the demand exceeds it, and losing the track when the curve
is taken too fast. It also adds automatic couplers that form real joints between
vehicles, gangway frames, and a physics probe/controller set.

What this add-on takes from them:

- a train is a Sable structure, not a contraption (both);
- the running train is held on its rails by forces, and a derailment is the rails
  running out of grip rather than a scripted trigger (Simurail's bogey model);
- carriages are coupled with a spring-damper and exchange momentum (both);
- a wreck stays where it fell and is a normal Sable structure; putting it back on
  a track is opt-in (Loconautics' "the train is the object" stance).

What is still missing:

1. **Create's train is still the route authority.** The hidden contraption exists
   so schedules, stations and signals keep working. Removing it entirely means
   reimplementing station stops, coupling, seat assignment and signal lookups, which
   is Loconautics' main body of work and its stated reason for shipping vanilla
   features one by one.
2. **Real bogey blocks.** Simurail's axles are blocks with per-axle traction,
   adhesion and slip; here the rail forces act on the whole carriage body. Per-axle
   constraint joints (Sable `GenericConstraint` with linear limits) would give
   flange contact, wheel lift and a much better curve feel.
3. **Rotational power.** Trains here keep Create's speed control; a train does not
   need kinetic stress or a boiler to move.
4. **Blocks on board.** The structure's blocks are real Sable blocks, but Create
   contraption behaviour (drills, deployers, funnels processing on the move) is
   tied to the contraption, not the sub-level.
5. **Per-train opt-out**, automatic coupling/uncoupling as a physical action, and
   unloaded-chunk operation.

## Known Issues / Limitations

- **Not yet validated in a live game.** The source is written against
  Create 6.0.10-281, Sable 2.0.6 and NeoForge 21.1.228, but no jar built from
  this repository has been run in a game yet. Expect to file the first
  integration bugs.
- **No public release yet.** A release will be published once the integration has
  been exercised in-game.
- **Sable's API is not semver-stable.** Types such as `ServerSubLevel`,
  `RigidBodyHandle` and `SubLevelAssemblyHelper` may move between Sable releases.
  A new Sable version can require an update here.
- **Rebinding is off by default and conservative.** When enabled it needs the
  original graph to still resolve the travelling points near the wreck, or a
  straight edge long enough for the whole train within `maxTrackDistance`. A wreck
  in a tunnel, on a curve, or far from any track stays a wreck. After
  `sessionTimeoutTicks` the mod stops trying; the body is never removed behind the
  player's back.
- **Conversion needs clear space.** Blocks are only materialised into air. A train
  that derails inside terrain produces a partial wreck, and changes made to the
  wreck's block entities are not merged back into the contraption on resync.
- **Passengers are ejected** when a train derails. While it runs normally they stay
  on the hidden contraption entity, so they are not pushed by the sub-level.
- **The route is still Create's.** A running train is held on its rails by forces,
  so it can be pushed and can run out of grip, but where the rails go is still
  Create's travelling points. `[rails] physical = false` switches back to teleporting
  the structure onto the carriage.
- **The rail controller is a servosystem, not rail contact.** It cannot model wheel
  flange contact, axle load transfer or slip per axle; the grip limit is one number
  per carriage. Expect a softer, more "arcade" feel than Simurail's per-axle
  constraints, especially in tight S-bends.
- **The carriage entity is hidden on purpose.** Its renderer and its entity
  collisions are suppressed while the structure exists, because otherwise the train
  would be drawn and would push players twice.
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
runtime libraries and uses their public APIs. Create and Sable
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
- Crystaelix's Create: Simurail (MIT) for the physics-bogey reference model: rails
  as a constraint with a finite grip, adhesion-limited traction and coupling as a
  joint. No code from it is used here; the design was studied and reimplemented
  against this project's own abstractions.
- The Create: Loconautics author for the clearest public statement of what a
  physical train should be: the train is the object, not a contraption that is
  drawn somewhere else.
- Dimforge for the Rapier physics engine.

## License

Apache-2.0. See [LICENSE](LICENSE).
