# Create Trains Sable

Create Trains Sable is a Minecraft 1.21.1 add-on for NeoForge. It makes a **Sable vehicle
ride Create's rails on physics**, the way Create: Simurail does: put bogeys on your
build, assemble it into a sub-level, drive it with a shaft, and it rolls along the
track, takes curves, slips, couples to wagons and leaves the rails when it is going
too fast for the curve it is in.

There is no train object being converted, no hidden contraption and nothing that is
teleported, because the vehicle *is* the thing you built.

> **Status: 0.1.0, early development.** The physics code is unit-tested; the Sable
> integration is written against Sable 2.0.6's public API but has not yet been run in a
> live game. There is no public release yet. Read
> [Known Issues / Limitations](#known-issues--limitations) before adding this to a world
> you care about.

## Compatibility

Targets Minecraft 1.21.1 on NeoForge 21.1.228 or newer in the 21.1 line, with
Create 6.0.10.x. **Sable 2.0.x and Create: Aeronautics 1.3.x are required**: a bogey is a
block entity that Sable ticks on a sub-level, and the vehicle has to be assembled with
Aeronautics' tooling before a bogey does anything. Sable Companion 1.6.0 comes as a
bundled library. Java 21 is the language level. There is no Fabric version and no
multiloader layout while Sable's development is centred on NeoForge.

## Building a train

1. Build the vehicle out of ordinary blocks, on the ground or on a schematic.
2. Put **physics bogeys** where the wheels should be. A powered bogey takes rotary power
   on its axle axis - a shaft or a cogwheel from a motor, a boiler or a motorised
   bearing - and the speed of that rotation is the speed it tries to reach. Unpowered
   bogeys carry weight and roll.
3. Assemble the whole thing into a sub-level with Create: Aeronautics' assembly tooling,
   the same way a ship or an airship is assembled.
4. Give the powered bogey rotation and watch it go. Point the bogeys in the direction the
   vehicle should move when the shaft turns forwards.
5. Add an **automatic coupler** to the end of the vehicle, and another coupler facing it on
   a second vehicle, and the two hook up on their own within `couplerRange`. A locomotive
   then actually drags its wagons, and a shove at the back is felt at the front.

## What it does

Each bogey holds its axle on the nearest piece of Create track and pulls the vehicle along
it. Everything is applied as a change of velocity on Sable's physics substeps, so:

- the vehicle can be pushed, rammed, loaded with cargo and tipped over;
- a curve taken too fast makes the flange limit trip and the bogey stops guiding, which is
  a derailment you can see coming - the vehicle keeps its momentum and becomes a wreck;
- a heavy train is not slower than a light one, because the model works in accelerations
  rather than forces;
- the vehicle never gets a position teleported into it, so it can never jump back to where
  it was built.

The guidance is a position/velocity controller on the rail point plus a gravity
feed-forward, capped at `maxGuideAcceleration` - the grip of the wheel/rail contact. That
cap is what makes the difference between a train and a ghost: when it is exceeded, the
bogey reports that it is off the rail, stops applying guidance, and physics takes over.

### Blocks

| Block | What it does |
| --- | --- |
| Physics Bogey | Driven axle. Speed follows the rotation on its axle, so 32 RPM is about 1.6 blocks/s and a full speed shaft is about 12.8 blocks/s. |
| Unpowered Physics Bogey | Carries weight and rolls; braking only. |
| Automatic Coupler | Hooks up mouth to mouth with another coupler within `couplerRange`; the spring keeps the distance it had when it linked. |

### The old train conversion

Earlier versions replaced Create trains with Sable structures, with the Create contraption
hidden behind them. That is still in the mod, but `[general] convertCreateTrains` is now
**off by default**: it needed three render/collision mixins to keep one of the two copies
out of sight, and any future Create change to those internals would bring the duplicate
back. The bogey system does not need any of that, so it is the default.

`[rails]` and `[rebinding]` only affect that legacy path.

## Configuration

The server config lives at `config/createtrainssable-server.toml` and is regenerated
with documented defaults on first start. It is read on every tick, so a reload
takes effect without restarting the world. Key groups:

```toml
[bogey]
speedPerRpm = 0.05           # m/s per RPM of the driving shaft
tractionAcceleration = 1.2   # m/s^2 the driven wheels can put down
brakeAcceleration = 1.6      # m/s^2 the brakes can remove
guideStiffness = 18.0        # how firmly the axle is held on the rail
guideDamping = 6.0
maxGuideAcceleration = 26.0  # grip limit, m/s^2
alignGain = 3.0              # how strongly the vehicle is turned along the rail
maxLateralError = 1.2        # blocks off the rail before the bogey gives up
maxVerticalError = 0.9
curveLateralLimit = 2.6      # m/s^2 of flange load before a curve derails the vehicle
scanRadius = 24.0            # how far to look for track
trackRecheckTicks = 2        # track lookups are cheap, but not free
couplerRange = 2.5           # blocks within which two couplers link
couplerStiffness = 6.0
couplerDamping = 4.0
couplerMaxAcceleration = 14.0

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

`[bogey] curveLateralLimit` is the number that decides how a train feels: raise it and
trains hold absurd curves, lower it and a normal turn at speed throws the vehicle off.

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

## Relationship to Create: Simurail and Create: Loconautics

**Create: Simurail** (MIT, source available) is the direct reference for this add-on, and
the bogey system here is the same idea implemented against this project's own code:
physics bogey blocks that hold axles on Create track, adhesion-limited traction and
braking, slip and derailment when the curve cannot be held, and couplers between
vehicles. No Simurail code is used; its public API calls and its model were studied and
reimplemented.

Where this implementation differs from it:

- Simurail constrains each axle with a Sable `GenericConstraint` joint with linear limits
  and a limited rotation range, so the vehicle is kinematically *attached* to the rail and
  only breaks free when a limit is exceeded. Here the axle is held by a capped
  acceleration instead: no joint, no limit special case, and the body stays a free rigid
  body at every moment. It is simpler to reason about and cannot fight the solver, but it
  is also a servo, not a flange: it cannot model wheel lift or axle load transfer.
- Simurail has a full bogey UI (control modes, brake strength, coupling through a
  connector item, probes, a remote controller, ComputerCraft). Here the bogey only reads
  rotation and the coupler links automatically, which is the minimum that makes a train
  drivable.
- Simurail tracks the rail by scanning track blocks in the sub-level's local space as well
  as the global graph, so a vehicle can ride rails that are themselves on another
  sub-level. Here only the global `TrackGraph` is searched.

**Create: Loconautics** (unreleased) takes the other route: a Create train is assembled at
a station and *becomes* a Sable body. This add-on did that first, and it is why the
previous version had the duplicate-contraption and teleport problems: keeping Create's
train as the route authority means two objects for one train. That path is now
`convertCreateTrains = false` by default. If it is ever finished properly, it means
reimplementing station stops, coupling, seat assignment and signal lookups on top of the
structure - which is Loconautics' main body of work.

## Known Issues / Limitations

- **Not yet validated in a live game.** The source is written against Create 6.0.10-281,
  Sable 2.0.6 and NeoForge 21.1.228, but no jar built from this repository has been run in
  a game yet. Expect to file the first integration bugs.
- **No model or texture for the blocks yet.** Bogeys and couplers are drawn as plain
  blocks (brass, andesite and copper casing). Their physics is real; their looks are not.
- **Bogeys need an assembled sub-level.** They do nothing lying in the world, and this mod
  does not ship an assembly tool: assemble with Create: Aeronautics, exactly like a ship.
- **The rail contact is a servo, not a flange.** Guidance is a capped acceleration toward
  the rail point, so there is no wheel lift, no axle load transfer and no slip curve per
  axle. Tight S-bends are the place where this shows first.
- **Couplers are mass independent.** Both ends are pushed by the same acceleration rather
  than by equal and opposite forces, so a locomotive is not slowed by a wagon it outweighs
  ten to one. On a mixed train the locomotive feels lighter than it is.
- **One track lookup per bogey every `trackRecheckTicks`.** A lookup walks the graphs near
  the vehicle; many bogeys in one place cost more than few, and a very long straight with
  few nodes is searched node by node.
- **The legacy train conversion** (`convertCreateTrains`) still exists and is still the
  part that needed three mixins to hide a duplicate train. It is off by default and is not
  the direction this mod is going.
- **Sable's API is not semver-stable.** Types such as `ServerSubLevel`, `RigidBodyHandle`
  and `BlockEntitySubLevelActor` may move between Sable releases.

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
- Crystaelix's Create: Simurail (MIT) for the physics-bogey model this add-on now
  implements: axles held on the rail with a finite grip, adhesion-limited traction,
  derailment as a consequence of curvature and speed, and couplers between vehicles.
  No code from it is used; its API calls and design were studied and reimplemented
  against this project's own abstractions.
- The Create: Loconautics author for the clearest public statement of what a
  physical train should be: the train is the object, not a contraption that is
  drawn somewhere else.
- Dimforge for the Rapier physics engine.

## License

Apache-2.0. See [LICENSE](LICENSE).
