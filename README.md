# Create Trains Sable

## Original project

**Author and original repository: GoShadowS - https://github.com/GoShadowS/create-trains-sable**

Copyright 2026 GoShadowS. Licensed under the Apache License, Version 2.0 (`LICENSE`). The `NOTICE`
file must travel with every copy, fork and build. If you fork this mod, keep this section, the
`NOTICE` file and the copyright line: attribution to the original author is a licence condition
under Apache-2.0 section 4(d), not a courtesy, and it may not be removed, hidden or rewritten.
See [Licence and attribution](#licence-and-attribution) for the full terms and for the licences of
the projects this one builds on.

## Project status: ALPHA

**Status: alpha (`0.4.2-alpha.1`). The API, the physics, the data format and the save compatibility
can all change without warning. Not ready for production servers.**

What is still missing, in plain terms, because the version number alone should not be the only
signal: derailment is not yet reported through Create's own `Train.derailed` / `TrainStatus`;
stations and signals do not stop a bridged train; passengers and cargo are disabled while a train is
bridged; collisions between two consists are not modelled; and nothing beyond one minute of running
has been measured for long-session stability. `CHANGELOG.md` records this reading of the version
number and the rule for leaving alpha (`CONTRIBUTING.md`).

**Physical rail vehicles for Create.** A vehicle is a Sable structure, and its axles are the only
thing that holds it on the rails: real wheel/rail contact, adhesion-limited traction, curves that
can throw it off, and spring couplings that hold two vehicles together with equal and opposite
force. A Create train can be bridged to such a body, so the route stays Create's while the motion
becomes the physics engine's.

Minecraft 1.21.1 · NeoForge · Create 6.0.10 · Sable 2.0 · Java 21

---

## What the mod is

There is exactly one representation of a vehicle: the Sable sub-level the player assembled. The
mod adds three blocks that go on that structure and one rule that governs them - **the physics owns
the motion, the blocks only push**.

| Block | What it does |
| --- | --- |
| **Traction Truck** | A driven axle. Takes rotation on its axle axis and pulls the vehicle along the rail. |
| **Rolling Truck** | An idler axle. Carries its share of the weight, rolls, and brakes. |
| **Spring Coupling** | Links two vehicles that face each other. The link is a spring and a damper, applied to both bodies. |
| **Assembly Tool** | Turns a built vehicle into a Sable sub-level. Right-click it; no command needed. |

Build the vehicle, put trucks under the frame where the axles belong, power the driven ones, add
couplings to the ends, then right-click the build with the assembly tool. From the first physics
step the vehicle is a rigid body held on Create's rails by force - there is no second copy of it,
no hidden contraption, and nothing that teleports it.

## How it works

Every physics substep, every truck does three things and reports them as a force:

1. **Flange.** A spring-damper in the plane across the rail pushes the axle back onto the rail head.
   It is clamped: the rail can only push so hard before the flange climbs.
2. **Traction.** A driven axle tries to reach the speed its shaft asks for; an idler only rolls and
   brakes. Both are capped by the adhesion of the wheel, so a heavy vehicle accelerates slowly and a
   locomotive cannot pull what it has no grip for.
3. **Yaw, from the contact itself.** The force is applied at the axle, not at the centre of mass, so
   two axles pushed sideways in opposite directions turn the structure the way a bogie does. Nothing
   writes an angular velocity: an earlier version did, and the physics harness measured the body
   spinning a half turn and tumbling.

The contact is applied in the plane of the rail head *and* of the centre of mass: the fore/aft offset
of the axle is what steers the vehicle, but the vertical offset would roll a body that has no
suspension to catch it.

### Derailment

An axle is either holding the rail or it is not, and it stops holding for a physical reason:

- the axle is further from the rail head than the flange allows (`maxLateralError`,
  `maxVerticalError`), or
- the curve needs more lateral force than the flange can supply
  (`curvature * speed^2 > curveLateralLimit`).

When no axle holds the vehicle any more, nothing pushes it and the physics engine does the rest:
it keeps its momentum, hits what is in the way, and comes to rest where it lands. There is no
scripted crash and no wreck entity.

### Couplings

Two couplings facing each other within range link on their own. The distance at the moment of
linking is the rest length, so hooking up never yanks the vehicles together. The spring force is
applied as an impulse to one body and the opposite impulse to the other, at the coupling faces: a
locomotive is genuinely held back by the wagons it pulls.

### A Create train on a body

A body on its own has no route. Create's trains have the route - the track graph, the travelling
points, the signals, the stations, the schedule and the train screen - and no physics. The bridge
(`bridge/`) joins the two without merging them:

- **Create keeps the route.** The train stays a real `Train` in Create's railway manager. Nothing
  about `Train`, `Carriage`, `TrackGraph` or the train screen is patched or replaced.
- **The physics keeps the body.** With `TrainBridge.bridge(train, bodies)` each carriage of the train
  becomes a `BridgedCarriage` tied to one body. Instead of travelling the distance `Train.speed`
  implies, it travels the distance its body actually covered since the last tick, measured along the
  carriage's own forward direction and clamped at two blocks per tick so a teleport cannot drag a
  carriage.
- **The couplings hold the train together.** Between two neighbouring bodies a spring and a damper
  push and pull along the line between their centres, with equal and opposite impulses. That is what
  lets one traction truck move a train of unpowered wagons: the wagon has no motor, so all of its
  motion arrives through the coupling.
- **The order still flows the other way.** `Train.speed` - set by the controls, by a schedule or by
  `Navigation` - is handed to the axles as a target speed, and the wheels turn it into force. The
  train tells the body how fast to go; it never tells it where it is.
- **No contraption entity.** A bridged carriage does not spawn a `CarriageContraptionEntity`: the
  body in the world is the carriage, and a contraption drawn on top of it would be a second copy of
  the same vehicle.

### What a bridged carriage does not have

Suppressing the contraption entity is not free, and the cost has to be stated plainly rather than
phrased as "passengers stay on the contraption side". A bridged carriage has **no contraption in the
world at all** - there is no seat to sit on, no inventory to open and no entity to carry. What
survives is data on the `Carriage` object: `TrainCargoManager` for the cargo of the carriage and
`serialisedPassengers` for the list of riders. Nothing reads them while the carriage is bridged, and
nothing creates something to read them.

So **passengers and cargo are disabled functionality in 0.3.x**, not relocated functionality. A
player who boards a bridged carriage has no carriage to board; the baggage of a train inventory is
not rendered and not accessible. Carrying riders into the physical body (`pushEntityLocal`) is phase
C, and until it exists a bridged train is a freight-only, empty-vehicle feature.

### What phase A did not close, and why

The acceptance criteria agreed for phase A were: the train is visible in Create's train interface, a
station brakes it to a stop, and a red signal stops it. **Only the first is measured, and only at the
data level**: the harness asserts that the train is in `Create.RAILWAYS.trains`, which is the map the
train screen and the train HUD read (`in_railway_manager=true`). No rendering is measured on a
dedicated server, and no station or signal behaviour is exercised at all.

That is a real gap, not a wording problem:

- **A station needs a conductor.** `Navigation.tick` cancels navigation when the train has no
  conductor ahead of it, and `hasForwardConductor()` reads `Carriage.presentConductors`, which is set
  from the entities inside the contraption. A bridged carriage has no contraption, so it has no
  conductor, so Create's own navigation refuses to drive it to a station. The harness cannot fake
  that honestly for a player-visible result.
- **A red signal needs navigation as well.** The signal listener that sets the speed to zero is only
  armed while `Navigation` is waiting for a signal, and `Navigation` only runs with a destination - a
  station. No station, no signal test.
- **A station edge point needs a station block entity.** The test would have to build a real
  `StationBlockEntity` and register it in `Create.RAILWAYS`, which is a world-building problem, not a
  physics one, and the harness builds its rails by node rather than by block.

Conclusion: **phase A is a partial pass against its own criteria.** The bridge is measured and holds;
the station and signal criteria are open and are listed as open rather than presented as done. The
next phase that touches the train loop is where they get closed, with scenarios `station` and
`signal`, and with a conductor path that does not depend on a contraption entity.

### Bridging a train by hand

The bridge is a debug action on the debug assembly tool, and it needs no command:

1. Build a vehicle, put trucks under the frame, and right-click it with the **assembly tool** to make
   it a body. Do the same for every vehicle of the consist.
2. Build or place a Create train with as many carriages as there are bodies. The train may be standing
   still; it does not have to be near the bodies at the moment they are assembled.
3. **Sneak and right-click any part of an assembled vehicle with the assembly tool.** The tool looks
   for the nearest unbridged train within 64 blocks whose route is in the same dimension, and pairs it
   with the vehicle that was clicked plus the other free bodies near it, ordered the way the train
   faces. One body per carriage; if the counts do not match it refuses and tells you both numbers.
4. **Sneak and right-click again to unbridge.** The bodies stay where they are, stop being driven and
   brake to a halt; the train keeps its route and moves on its own.

Every click prints its result in the action bar and in the log (`Bridge tool: BRIDGED (...)`, with a
search summary such as `trains=1, usable=1, nearest=2.0 blocks` when it finds nothing). The action is
deliberately crude: it picks the nearest train rather than asking which one you meant, because it is a
developer tool for reaching the physics quickly, not the mod's main feature.

What "unbridge" honestly means: the carriage stops using its body's measured distance, so the train
travels along its track at its own commanded speed while the bodies are left behind. A bridged
carriage cannot go back to Create's contraption - its blocks live in a Sable body, and Create's
`createEntity` would mark the train invalid from the empty contraption data. That is why the debug
toggle keeps the carriage object and only switches where its distance comes from.

## What comes next

Not implemented; listed so the roadmap is not a guess.

- **B, first item - the in-game entry point.** The debug assembly tool gains the action "bridge this
  train to that body", so a player can bridge and unbridge a train by hand without a command.
- **B, rest - more than one carriage.** Several bodies, one train, the spring couplings deciding who
  pulls whom, and `stress` treated as deformation instead of a number to clamp.
- **C - riders.** Passengers and cargo moved into the physical body with `pushEntityLocal`, and the
  `CarriageContraptionEntity` finally out of the picture for good.
- **D - derailment in the train.** A body that leaves the rail puts the train into `Train.derailed`
  and `TrainStatus`, with the station and signal scenarios above closed as part of that work.

## Configuration

`config/createtrainssable-server.toml`, applied immediately:

```toml
[trucks]
speedPerRpm = 0.05           # m/s of target speed per RPM of the driving shaft
maxSpeed = 60.0
traction = 1.2               # m/s^2 the driven wheels can put down
brake = 1.6                 # m/s^2 the brakes can remove
friction = 0.45              # adhesion of steel wheel on steel rail
guideStiffness = 18.0        # how hard the flange pushes back, 1/s^2
guideDamping = 6.0
contactStiffness = 220.0     # vertical contact: steel on steel does not sag half a block
contactDamping = 24.0
maxGuideAcceleration = 26.0  # grip limit of one axle's contact, m/s^2
maxLateralError = 1.2        # blocks off the rail before the flange gives up
maxVerticalError = 0.9
curveLateralLimit = 2.6      # m/s^2 of lateral load a curve may take
scanRadius = 24.0            # how far to look for track
trackRecheckTicks = 2        # physics runs faster than track searches
attachTicks = 40             # window after assembly to settle onto the rail
attachRadius = 3.0

[couplings]
range = 2.5                  # blocks within which two couplings link
stiffness = 6.0
damping = 4.0
maxAcceleration = 14.0
```

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.228 or newer
- Create 6.0.10.x
- Sable 2.0.x (required: a truck is a block entity Sable ticks on a sub-level)
- Java 21

## What is verified, and what is not

Three things run on every push, in this order, and each has to pass:

1. **`./gradlew build`** - the mod compiles, and the rail-contact and coupling arithmetic passes 18
   unit tests
   (holding the rail, leaving it past the flange, the attach window, overspeed on a curve, the
   adhesion cap, weight and grip sharing between axles, the steering correction; and for the
   couplings: rest length, stretch, compression, damping, closing, the cap and coincident bodies).
2. **`./gradlew runSelfTest`** - the physics session described below. A dedicated server builds a
   vehicle by code, lets the engine drive it, and prints what happened.
3. **`./gradlew runServer`** - a dedicated server loads the mod and reaches `Done (`.

### The physics self-test

`./gradlew runSelfTest` starts a dedicated server with `-Dcts.selftest=true`. The harness
(`selftest/`) places a five by three deck with a traction and a rolling truck under it, registers a
straight rail in Create's own track graph, assembles the whole thing into a Sable sub-level, and
then measures it over several hundred ticks:

| Scenario | What it proves | Reported numbers |
| --- | --- | --- |
| `assembly` | the structure becomes a body, and its axles find the rail | blocks, trucks, mass, per-axle state |
| `guide` | a sideways push is absorbed by the flange and the vehicle settles | worst and final lateral error, vertical offset, speed |
| `traction` | 32 RPM really moves the vehicle at 1.6 m/s | speed, distance travelled, states |
| `derail` | a 14 m/s sideways push leaves the rail, keeps momentum and falls | speed after the push, height lost, whether the flange ever gave up |
| `bridge` | a real Create train with one carriage follows the body it is bridged to | commanded and measured speed, distance travelled by the body and by the carriage, drift, lateral error, anchor gap, ramp |
| `entry` | the debug tool bridges, unbinds and re-binds a train, and the train survives it | the three statuses, distance moved in each state, speed after unbinding, the train's own travel while unbound, and a carriage NBT round trip |
| `couple` | an unpowered vehicle is pulled by a powered one through the coupling | spacing, rest and actual gap, deformation both from the spring and from the train, both speeds, both distances, and whether the pair has settled |
| `soak` | a bridged train left running for a minute of game time costs nothing that grows | entities before and after, contraption entities, heap, drift between body and carriage, ticks every axle held its rail |

Each scenario prints one line of numbers and the run ends with
`CTS SELFTEST SUMMARY scenarios=5 passed=5 failed=0`; the CI job fails unless that line says
`failed=0`. The last run on `main` (long lines are wrapped for width):

```
assembly PASS blocks=17 trucks=2 mass=17.000 states=[GUIDING,GUIDING]
guide    PASS lateral_max=0.046 lateral_final=0.020 vertical=0.005 speed=0.126
traction PASS target=1.600 speed=1.424 travelled=13.050
derail   PASS horizontal_speed=4.472 dropped=52.413 saw_off_rail=true
bridge   PASS carriages=1 target=1.600 speed=1.424 travelled_body=14.234
              travelled_train=14.234 drift=0.000 lateral_max=0.000
              anchor_gap=0.011 ordered=1.600 peak=1.424 rise_ticks=34
              in_railway_manager=true
entry    PASS bridge=BRIDGED unbridge=UNBRIDGED rebridge=BRIDGED
              active_after_rebind=true moved_bridged=4.390
              moved_while_unbound=0.512 speed_after_unbind=0.039
              train_moved_unbound=1.600 moved_after_rebind=1.569
              nbt_class=Carriage nbt_spacing=2 nbt_ok=true
couple   PASS spacing=6.000 rest=10.000 rest_minus_spacing=4.000
              gap_start=10.000 gap_end=10.240
              gap_at_20=10.134 gap_at_40=10.209 gap_at_80=10.240
              gap_min=10.240 gap_max=10.240 gap_drift=0.000
              extension=0.240 peak_extension=0.246 train_deformation=-0.240
              rel_speed_mean=0.000 rel_speed_instant=0.000 rel_speed_worst=0.000
              front_v_bias=-0.045 rear_v_bias=0.005
              v_front=0.834 v_rear=0.884
              front_moved=25.073 rear_moved=24.833
              trailer_driven=false derailed=false
soak     PASS ticks=1200 travelled=20.982 carriage_travelled=20.982 drift=0.000
              entities_start=7 entities_end=7 contraption_entities=0
              heap_start_mb=607 heap_end_mb=495
              lateral_max=0.000 ticks_holding=1200 bridged=true
```

The `entry` scenario calls the same function the tool calls, with the position of a truck - the path a
player takes, not a shortcut into `TrainBridge`. Bridging moves the body 4.390 blocks (the body is
driven); unbinding leaves the body to coast, so it covers only 0.512 more and is down to 0.039 m/s
twenty ticks later, while the train itself travels 1.600 blocks on its own (20 ticks at 0.08 blocks per
tick, exactly the commanded speed). Re-binding drives it again: 1.569 blocks. The carriage is written
to NBT and read back at the end and comes back as a plain `Carriage` with its spacing intact, which is
the thing that would silently break a train after a save.

The `couple` scenario assembles a second vehicle with two **idling** trucks behind the powered one, so
it cannot pull itself: `trailer_driven=false`. The train's spacing is 6.000 blocks between the facing
carriage points, while the bodies start 10.000 blocks apart; the gap opens to 10.134 by tick 20, 10.209
by tick 40, and stops at 10.240 by tick 80 - four seconds of spin-up in which the powered vehicle pulls
away from the wagon it has not stretched the spring enough to carry yet. From tick 400 to tick 600 the
gap is 10.240 in every single tick (`gap_min = gap_max`), it drifts 0.000 blocks, and the relative speed
measured from the distance the bodies actually covered is 0.000 m/s, worst tick included. The two bodies
are locked together; the spring's deformation is a constant 0.240 blocks (peak 0.246) and it is what the
wagon is being towed by.

Two readings in this scenario need a warning label, because both were mistaken for defects at first:

- **`v_front=0.834` against `v_rear=0.884` is not relative motion.** The gap is constant to the third
  decimal in the same run, so the two bodies cannot be separating. Comparing each reported velocity with
  the distance its own body covered in that tick explains it: the front's handle velocity reads 0.045
  m/s *below* its own displacement rate, every tick, worst equal to mean; the rear's reads 0.005 above.
  A constant offset like that is an ordering artefact of reading the velocity either side of the last
  impulse of a step, not a speed. Position is the ground truth here, and position says both bodies move
  at 0.879 m/s once settled.
- **`spacing=6.000` and `rest=10.000` are two different measurements of a gap**, and the 4.000 between
  them is geometry, not a bug. See the next section.

### Why the gap has two numbers

`Train.carriageSpacing` and the coupling's rest length both describe the distance between two
carriages, but they are not the same distance:

```
spacing_i = | P_trailing(i) - P_leading(i+1) |     on the track, fixed when the train is built
rest_i    = | C_i - C_(i+1) |                      between centres of mass, fixed at coupling
rest_i    = spacing_i + delta_i                    delta is a constant of the consist
```

`P` are the carriages' facing travelling points, `C` are the bodies' centres of mass. `delta` is how far
each vehicle's centre sits from its carriage's reference point; for the harness rig it is exactly 4.000
blocks, because each five-block body carries its carriage's two reference points two blocks inboard of
its centre.

Both numbers are used only as *differences from their own zero*, which is why the offset cancels: the
spring pulls with `stiffness * (distance - rest)` and Create computes `stress = spacing - anchorDistance`,
so a 0.240-block change in the bodies produces +0.240 of extension and -0.240 of stress, and the sum is
0.000 (`|stress + extension| <= 0.5` is asserted). The spring's zero is set at the moment of coupling and
Create's is set when the train was built; neither is derived from the other, so **the coupling does not
enforce the train's spacing** - it enforces whatever gap the bodies had when they were bridged. A consist
whose bodies are laid out very differently from the train's spacing gets a warning in the log at bridge
time (`the bodies are ... apart but the train's spacing is ...`), because then Create's stress and the
spring's force are describing different geometry.

### What a bridged train costs over time

The `soak` scenario runs a bridged train for 1200 ticks - one minute of game time - and watches the
things a long session would expose:

- **entities:** 7 before, 7 after - the same seven item entities the earlier scenarios left behind - and
  **0 `CarriageContraptionEntity`** at any point. The suppressed contraption is never instantiated: there
  is no entity to tick, no entity to save and none to leak.
- **heap:** 607 MB at the start, 495 MB at the end, with telemetry reading 751 MB early in the run. It is
  not monotonic and the JVM reclaimed more than the run allocated; this is a shape, not a leak claim.
- **bridge integrity:** the carriage still follows the body (`drift=0.000` over the minute), every axle
  held its rail for 1200 of 1200 ticks, and the lateral error never exceeded 0.000 blocks.

What still holds memory is small and bounded: one `DimensionalCarriageEntity` per carriage per
dimension (anchors and a pivot, no entity), one serialised tag per carriage, and the two `CarriageBody`
objects. The per-tick work the mod adds is one pass over the bridged trains and their gaps.

**Open, not answered:** an hours-long session with chunk unload and reload, trains deleted while
bridged, and bodies unloaded in far chunks is not covered by a one-minute run. It is recorded as an open
question for the phase that touches unloading (D), rather than presented as tested.

The bridge scenario builds a real `Train` with a real `Carriage` on the same track graph, registers
it in Create's railway manager and bridges it to the rig. It then lets Create's own train loop drive:
the order reaches the commanded 1.6 m/s, the body settles at 1.42 m/s after 34 ticks of wheel-limited
ramp, the carriage travels 14.234 blocks in the same 220 ticks as the body with no drift, and its
leading anchor sits 0.011 blocks from the leading axle. A body dragged along by the train would be at
full speed on the first tick and would keep a constant offset; both numbers say it is not.

The driven axle settles at 1.42 m/s against a commanded 1.6 m/s because the idling axle is rolling
and taking energy out; that is the model working, not a target being missed.

Four honest limitations of the harness:

- **the rail is synthesised**, not built from track blocks: a two-node `TrackGraph` with a straight
  `TrackEdge`. The contact sees exactly the same thing it would see on a placed straight track, but
  switches, curves and slopes are not covered by it yet;
- **the shaft speed is forced** (`TruckBlockEntity.selfTestSpeed`), because a scripted server has
  no kinetic network to take rotation from. The block's force output and the physics are real; only
  the RPM it reads is set by the test;
- **there is no curve in the harness yet**, so `curveLateralLimit` and the overspeed derailment are
  covered only by the arithmetic tests. A Bezier edge in the synthetic graph is the next step;
- **the bridged train is built by code, and its body is the rig.** The scenario proves the seam -
  order in, measured distance out, carriage following the body - but the carriage's contraption,
  the blocks a player would have built, is not part of it.

**Not verified:** nothing here has been driven in a world with a player. The feel - acceleration,
brake distances, how tight a curve can be taken, whether the trucks sit where the build expects -
is untuned, and every number in the configuration section is an estimate from physics, not a
measurement from play.

## Known limitations

- **Assembly takes the whole connected structure.** The assembly tool gathers everything touching
  the block that was clicked, so a vehicle that is connected to the ground or to a building will
  take that with it. Disconnect it first, or click a part that is not touching the world.
- **A train's own contraption is not turned into a body yet.** The bridge binds a train to bodies
  that already exist: the player assembles each vehicle by hand and then bridges the train to them.
  Reading a carriage's contraption, putting its blocks into the world and assembling them is still
  not implemented.
- **A bridge needs as many free bodies as the train has carriages.** The tool pairs them by distance
  and direction, not by identity, so a consist assembled in a different order will be paired wrongly
  and the springs will fight. There is no per-carriage selection yet.
- **`Train.stress` is not public.** The harness recomputes Create's deformation formula from the
  travelling points to compare it against the spring; the number in the train's own status message
  comes from Create's private field and is not asserted directly.
- **A pair that deforms by more than four blocks derails the train, by Create's own rule.** The spring
  is capped at 14 m/s^2 so this is hard to reach, but a body wedged against a wall will do it.
- **A bridge is not covered for hours of play.** One minute of game time is measured (`soak`); chunk
  unload, reload, and deleting a train while it is bridged are not. Open until phase D.
- **Handle velocities can lag positions by a fraction of a tick.** The front body's velocity reads
  0.045 m/s below the distance it covered, constantly; anything that needs a true speed should use
  displacement, as the bridge does.
- **No passengers, no cargo while bridged.** A bridged carriage has no contraption entity, so there
  is nothing to ride in and nothing to open. See "What a bridged carriage does not have" above.
- **No station stops and no signal stops while bridged.** Create's navigation needs a conductor from
  the contraption and a station edge point; neither exists for a bridged carriage yet, so a bridged
  train can be driven with its controls but cannot run a schedule to a station.
- **One contact point per axle.** There is no wheel lift, no axle load transfer and no per-wheel
  slip curve; tight S-bends are where this shows first.
- **Couplings do not transmit torque or bend.** They pull and push along one line, which is what a
  screw coupling does, and nothing more.
- **The art is a first pass.** The textures are drawn in 16x16 by `tools/make_textures.py` and the
  models by `tools/make_models.py`; every face gets a UV window sized from its own box, and
  `tools/preview_models.py` renders the three models with the real textures so a stretched face or
  a box inside another box is visible without launching the game. They are readable and consistent,
  not final art.
- **A vehicle cannot be turned back into blocks.** Sable exposes assembly and removal but no
  disassembly, so an assembled vehicle is edited inside its sub-level or removed with Sable's own
  command. Writing a disassembler means writing blocks back into the world on top of terrain that
  may now be occupied, and that is not something to ship without playing it first.
- **Sable's API is not semver-stable.** A Sable update can require an update here.

## Licence and attribution

This project is licensed under the **Apache License, Version 2.0**. The full text is in
[LICENSE](LICENSE); the required notices are in [NOTICE](NOTICE).

### Why Apache-2.0 and not something else

| Option | Attribution of the original author | Obligations it puts on forks |
| --- | --- | --- |
| MIT (what 0.2.x-0.4.x used) | a copyright line that forks must "include" - enforceable, but silent about a NOTICE file and about marking modified files | almost none; the line is easy to drop without noticing |
| **Apache-2.0 (chosen)** | section 4(d) requires the NOTICE file to be kept in any distribution, section 4(b) requires modified files to say they were modified, section 4(c) forbids reusing the project's name without permission | keep NOTICE and mark changes; patent grant included |
| MPL-2.0 | attribution at file level: every modified file stays MPL and must be published | per-file copyleft; heavier for a mod that mixes files with Create's MIT code |
| GPL-3.0 / AGPL-3.0 | copyleft is not attribution - a fork may keep the copyright line and still be a different product | whole-work copyleft; AGPL adds a network-use clause that clashes with shipping a mod that links a PolyForm-licensed dependency |

Apache-2.0 was chosen because the requirement here is **non-removable attribution to GoShadowS**,
not copyleft. It states that requirement in binding text (4(d) plus this repository's NOTICE), it
allows the mod to be shipped alongside MIT and PolyForm code, and it does not force a fork to
publish anything. MIT alone was rejected because "include the copyright notice" has no file that
must survive repackaging; MPL-2.0 and the GPLs were rejected because they answer a different
question (who must publish source) than the one that was asked (who must be named).

### Compatibility

- **Create - MIT for code, All Rights Reserved for assets.** Compatible: Apache-2.0 code may be
  combined with MIT code, and MIT's notice is preserved for the parts that come from Create. No
  Create asset is copied here: every texture in this repository is generated by
  `tools/make_textures.py` and `tools/make_item_textures.py`. Create is a dependency, not a
  component.
- **Sable - PolyForm Shield License 1.0.0.** Compatible as a dependency: nothing from Sable is
  redistributed in this repository or in the built jar, and PolyForm Shield's non-compete clause
  concerns providing a competing product, which a physics add-on is not. Because that licence is
  not permissive, Sable can never be vendored into this mod, and its notices must be visible where
  Sable is described as a requirement (see `NOTICE`).
- **Apache-2.0 and the rest of the mod ecosystem.** Apache-2.0 is one-way compatible with MIT: MIT
  code can go into an Apache-2.0 work with attribution, which is exactly what happens here for
  Create's MIT portions in a compiled mod environment.

### Forks

Change the name if you like. Keep `NOTICE`, keep this section, keep the copyright line. Removing
them is a licence violation, not a style choice.

## Credits

- RyanHCode and the Sable contributors, for sub-levels and the Rapier pipeline.
- The Create team, for the track graph and the kinetic system.
- Dimforge, for the Rapier physics engine.

Minecraft is a trademark of Mojang Synergies AB. This mod is not affiliated with Mojang, Create or
Sable.
