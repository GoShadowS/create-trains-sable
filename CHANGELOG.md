# Changelog

## 0.4.2-alpha.1 - the version number now says "alpha", the licence now requires a name

### Version numbering was wrong, and is corrected retroactively

Every release before this one was numbered without a pre-release suffix: `0.2.0`, `0.2.1`, `0.2.2`,
`0.3.0`, `0.3.1`, `0.4.0`, `0.4.1`. That was a **numbering error, not a claim about the state of the
project**: none of them was a release. The station and signal criteria are still open, derailment is
not reported through Create's `Train.derailed`, passengers and cargo are disabled while bridged,
collisions between consists are not modelled, and no run longer than one minute of game time has been
measured. A number that climbs `0.2.1 -> 0.3.0 -> 0.4.0` reads as steady progress towards a stable
release, and that reading was false.

The old headings keep their text and gain their true numbers, so history stays traceable rather than
retouched:

| Published as | Actually was |
| --- | --- |
| `0.2.0` | `0.2.0-alpha.1` |
| `0.2.1` | `0.2.1-alpha.1` |
| `0.2.2` | `0.2.2-alpha.1` |
| `0.3.0` | `0.3.0-alpha.1` |
| `0.3.1` | `0.3.1-alpha.1` |
| `0.4.0` | `0.4.0-alpha.1` |
| `0.4.1` | `0.4.1-alpha.1` |

Scheme from here on: `0.<minor>.<patch>-alpha.<n>`. The minor number moves only when a phase of the
agreed architecture closes its acceptance criteria; the patch number moves for everything else; the
suffix is mandatory. `1.0.0`-class numbering - anything without `-alpha`, `-beta` or `-rc` - is
forbidden until every acceptance criterion in `CONTRIBUTING.md` is implemented and measured. This
release therefore keeps minor 4 and moves the patch: it closes no phase.

### Licence: MIT replaced by Apache-2.0 with a mandatory NOTICE

MIT named the author but gave forks nothing that had to survive repackaging. Apache-2.0 section 4(d)
requires the `NOTICE` file to be preserved in every distribution and 4(b) requires modified files to
say they were modified; that is the "attribution of GoShadowS is non-removable" requirement in
binding text. MPL-2.0 (file-level attribution plus per-file copyleft) and GPL/AGPL (copyleft answers
a different question, and AGPL's network clause clashes with shipping a PolyForm-licensed dependency)
were considered and rejected. Compatibility checked against the actual licences of the dependencies:
Create's code is MIT and its assets are All Rights Reserved - nothing from Create's assets is copied
here, every texture is generated in this repository; Sable is PolyForm Shield 1.0.0 - it is a
dependency only, never vendored. Details in `README.md` under "Licence and attribution" and in
`NOTICE`.

### Added

- `NOTICE` with the original author, the repository, the attribution terms and the third-party
  notices.
- `CONTRIBUTING.md` with the versioning rule, the list of criteria that must close before a
  suffix-free version may exist, and the attribution rule for forks.
- A project-status block at the top of `README.md` and an "Original project" section above it.
- `LICENSE` is now the Apache License 2.0 with the copyright line in its appendix; `CITATION.cff`
  updated to the new licence and version; `neoforge.mods.toml` declares `license = "Apache-2.0"` and
  points at the NOTICE.

### Removed

Dead code that no longer had a caller after the bridge and the soak scenario: `WheelRail.idle`,
`Vec.negate`, `Vec.toMinecraft`, `Vec.isFinite`, `TruckBlockEntity.lastLateralError`,
`TrainBridge.count`, `Bridge.train()`, `Bridge.bodies()`, `CarriageBody.lastAdvance()`,
`CarriageBody.lastCommand()`, `CarriageBody.lastMeasuredSpeed()`, `BridgedCarriage.lastMoved()`,
`SelfTest.truckNames` with its now-unused `ServerSubLevel` import, and the committed
`tools/__pycache__/` directory (now ignored). The unused `lastAdvance`, `lastCommand` and
`lastMeasuredSpeed` fields went with their getters.

### Changed

- The damping term in `TrainBridge.couple` now reads each body's speed from the pose Sable
  integrated (`ServerSubLevel.latestLinearVelocity`) instead of the solver state
  (`RigidBodyHandle.getLinearVelocity`). The two disagree by a per-body constant - measured -0.045 m/s
  on the driven body and +0.005 m/s on the passive one in the same run - and the damping term works on
  the *difference*, so those biases added up instead of cancelling. Nothing in the coupling geometry
  changed; only the input to the damper.

### Measured

The velocity question - why a solver reading disagrees with displacement by a constant - is answered
by comparing both sources Sable offers, in the same tick, for both bodies of the `couple` scenario:

| | front | rear |
| --- | --- | --- |
| solver state, m/s | 0.834 | 0.884 |
| pose derivative, m/s | 0.879 | 0.879 |
| difference | -0.045 | +0.005 |

Locked bodies must share a speed, and the pose-derived one does; the solver readings straddle it. The
cause is documented in `README.md` under "Two speeds, and which one to trust": Sable calls
`sable$physicsTick` for every actor before `pipeline.physicsTick`, so a read there is the state before
the substep's impulses are resolved. `scenarios=8 passed=8 failed=0`, 18 unit tests, same geometry:
`gap_end=10.240 gap_drift=0.000`.


## 0.4.1-alpha.1 (published as `0.4.1`) - the coupling settles, and a bridged train costs nothing that grows

No behaviour changes. Two questions that 0.4.0 raised but did not answer are now measured, and one
number that looked like a defect is shown to be a reading artefact.

### The trailing body looked faster than the one pulling it

`v_front=0.834` against `v_rear=0.884` in 0.4.0 looked like a wagon overtaking its locomotive. It is
not. Measured over 600 ticks:

```
gap_at_20=10.134 gap_at_40=10.209 gap_at_80=10.240
gap_min=10.240  gap_max=10.240  gap_drift=0.000
rel_speed_mean=0.000 rel_speed_worst=0.000
front_v_bias=-0.045 rear_v_bias=0.005
```

The gap grows for four seconds - the powered vehicle pulls away until the spring is stretched enough to
carry the wagon - and then stops changing: from tick 400 to tick 600 it is 10.240 in every tick, and the
relative speed measured from the distance each body actually covered is 0.000 m/s. The apparent
difference between the two reported speeds is an instrument artefact: the front body's handle velocity
reads 0.045 m/s below its own displacement rate on every tick (worst equal to mean, so a constant
offset), while the rear's reads 0.005 above. Position is the ground truth here, and it says the pair is
locked.

### `spacing=6.000` against `rest=10.000` is geometry, not a mismatch

```
spacing_i = | P_trailing(i) - P_leading(i+1) |   on the track, fixed when the train is built
rest_i    = | C_i - C_(i+1) |                    centres of mass, fixed at coupling
rest_i    = spacing_i + delta_i                  delta = 4.000 for the harness rig
```

`delta` is how far each vehicle's centre sits from its carriage's reference points. Both numbers are used
only as differences from their own zero (the spring pulls on `distance - rest`, Create computes
`stress = spacing - anchorDistance`), so the offset cancels: 0.240 of extension against -0.240 of stress,
sum 0.000. The consequence is worth stating plainly: **the coupling enforces the gap the bodies had at
coupling, not the spacing the train was built with.** A consist laid out very differently from its
spacing now gets a warning in the log at bridge time.

### A bridged train was measured for a minute of game time

New `soak` scenario, 1200 ticks:

```
soak PASS ticks=1200 travelled=20.982 carriage_travelled=20.982 drift=0.000
         entities_start=7 entities_end=7 contraption_entities=0
         heap_start_mb=607 heap_end_mb=495 lateral_max=0.000
         ticks_holding=1200 bridged=true
```

The suppressed contraption is never instantiated, so there is no entity to tick, save or leak; the level
holds the same seven item entities from earlier scenarios before and after. Heap went 607 MB to 495 MB,
with telemetry reading 751 MB early in the run: reclaimed, not monotonic, and reported as a shape rather
than as a leak claim. What remains allocated is bounded: one anchor object per carriage per dimension,
one serialised tag, two body objects.

**Open:** an hours-long session with chunk unload, reload and a train deleted while bridged is not
covered. It is recorded in `README.md` as open until phase D, not as tested.


## 0.4.0-alpha.1 (published as `0.4.0`) - a player can bridge a train, and a coupling pulls a wagon

### What this version adds

Two halves of phase B, both measured on a dedicated server.

**The in-game entry point.** The debug assembly tool has a second action: sneak and right-click any
part of an assembled vehicle. If the vehicle already carries a train, the train is unbridged; if not,
the tool finds the nearest unbridged Create train within 64 blocks in the same dimension and pairs it
with the clicked vehicle and the other free bodies near it, ordered the way the train faces. One body
per carriage, and if the counts do not match it refuses and says both numbers. Every click reports its
result in the action bar and the log, with a search summary (`trains=1, usable=1, nearest=2.0 blocks`)
when it finds nothing, because "no train" has three different causes and the log has to say which.

**Couplings between carriages.** `TrainCouplings` is the spring between two neighbouring bodies:
`stiffness * deformation + damping * separation rate`, capped at `maxAcceleration` of the effective
mass, applied to both bodies at their centres as one impulse and its opposite. Its arithmetic is pure
and has seven unit tests. With it, one traction truck can move a train of unpowered wagons - the wagon
has no motor, so everything it does arrives through the spring.

**Several bodies per train.** `TrainBridge.bridge(train, bodies)` takes a list and requires exactly one
body per carriage. The gaps the train was coupled at are recorded as rest lengths, so hooking up never
yanks the bodies together.

### Three things the harness found, and what they cost

- **A carriage without a contraption cannot be handed back to Create.** `Carriage.manageEntities`
  creates the carriage's `CarriageContraptionEntity` from its serialised data; with empty data
  `createEntity` sets `Train.invalid` and Create removes the train from the railway manager on the next
  tick. Unbinding therefore keeps the carriage object and only changes where its distance comes from:
  the suppression of the contraption entity lasts as long as the carriage does. Measured: the train
  survives unbridge and re-bind, and its carriage is still the same object with the same id.
- **A bridged carriage could not be saved.** `MountedStorageManager is uninitialized` was thrown the
  first time the carriage was written to NBT, because Create initialises the cargo storage while
  setting up the contraption, which a bridged carriage never gets. `BridgedCarriage` now initialises
  it. The storage is empty - cargo is disabled while bridged - but a train that cannot be saved is not
  a train.
- **A train built by the harness has to be bridged in the tick it is created.** Not a mod bug: the
  harness builds carriages without contraption data, and Create kills such a train on its first tick.
  The scenario does both actions in one tick and says why in a comment.

### Measured, not claimed

`./gradlew runSelfTest`, dedicated server, all 7 scenarios pass, 18 unit tests, 0 failed:

```
entry  PASS bridge=BRIDGED unbridge=UNBRIDGED rebridge=BRIDGED active_after_rebind=true
           moved_bridged=4.390 moved_while_unbound=0.512 speed_after_unbind=0.039
           train_moved_unbound=1.600 moved_after_rebind=1.569
           nbt_class=Carriage nbt_spacing=2 nbt_ok=true
couple PASS spacing=6.000 rest=10.000 gap_start=10.000 gap_end=10.240 extension=0.240
           peak_extension=0.246 train_deformation=-0.240 v_front=0.834 v_rear=0.884
           front_moved=10.132 rear_moved=9.892 trailer_driven=false derailed=false
```

Target, actual, deviation, in the same shape the other scenarios use:

| Quantity | Target | Actual | Deviation |
| --- | --- | --- | --- |
| coupling gap (blocks) | 10.000 (rest, measured at coupling) | 10.240 | +0.240 (2.4 %) |
| spring deformation (blocks) | 0 | +0.240, peak 0.246 | 0.240 |
| the train's own deformation (blocks) | -0.240 (same number, opposite sign) | -0.240 | 0.000 |
| wagon speed vs leader (m/s) | 0.834 | 0.884 | the wagon is ahead, and cannot pull |
| body distance while bridged (blocks) | - | 4.390 | - |
| body distance while unbound (blocks) | 0 (it is not driven) | 0.512, down to 0.039 m/s | - |
| train distance while unbound (blocks) | 1.600 (20 ticks at 0.08/tick) | 1.600 | 0.000 |

The sign convention is not a typo: Create counts a positive stress as "these carriages should move
apart", the spring counts a positive extension as "they are too far apart", so the two agree when
their sum is zero. The scenario asserts `|stress + extension| <= 0.5` and reports both.

### Known limits of this phase

- Pairing is by distance and direction, not by identity: a consist assembled in a different order gets
  paired wrongly, and the springs will fight.
- `Train.stress` is package-private in Create, so the harness recomputes the same formula from the
  travelling points; the number in the train's status message is not asserted directly.
- A bridged carriage still has no passengers and no cargo, and no station or signal behaviour: see
  0.3.1 for why, unchanged by this version.


## 0.3.1-alpha.1 (published as `0.3.1`) - phase A said what it did not do

### Why this version exists

0.3.0 shipped the bridge and reported it as done. It was not done against its own acceptance
criteria: the plan for phase A asked for a train visible in Create's train interface, a station that
brakes it and a red signal that stops it, and the release only measured that a train accelerates,
travels the distance its body travelled, and keeps its carriage over its axle. The station and the
signal were never exercised, and 0.3.0 did not say so.

This version does not add behaviour. It records the gap, measures the one part of the criterion that
can be measured headless, and corrects a sentence that was wrong.

### Fixed, in the documentation

- **"Passengers and cargo stay on the contraption side" was incorrect.** A bridged carriage suppresses
  its `CarriageContraptionEntity`, so there is no contraption in the world at all: no seat, no
  inventory, no entity. Only disconnected data survives on the `Carriage` (`TrainCargoManager`,
  `serialisedPassengers`), and nothing creates anything that could read it. Passengers and cargo are
  **disabled while a carriage is bridged**, not relocated, and the README now says exactly that.
- **Phase A is recorded as a partial pass.** `README.md` has a section on what phase A did not close
  and why: `Navigation.tick` cancels when the train has no conductor, `hasForwardConductor()` reads
  `presentConductors` which is filled from contraption entities, a bridged carriage has none, and the
  signal listener is only armed while navigation waits for a signal. A station stop cannot be faked
  headless without measuring something other than what a player would experience.

### Added

- The `bridge` scenario reports `in_railway_manager`, the registration that Create's train screen and
  HUD read. It is data-level visibility on a dedicated server, and it is labelled as such rather
  than as a rendering test.

### Measured

```
bridge PASS carriages=1 target=1.600 speed=1.424 travelled_body=14.234 travelled_train=14.234
            drift=0.000 lateral_max=0.000 anchor_gap=0.011 ordered=1.600 peak=1.424 rise_ticks=34
            in_railway_manager=true
```

### The road, not a promise

The in-game entry point for the bridge - the debug assembly tool gaining "bridge this train to that
body" - is the first item of phase B, because a player has to be able to activate the bridge by hand
before multi-carriage behaviour can be judged in the world. Station and signal scenarios belong to
phase D, which is where the train loop is touched again.


## 0.3.0-alpha.1 (published as `0.3.0`) - a Create train rides a body

### What this version adds

The seam the mod exists for. Until now a physical vehicle and a Create train were two unrelated
things: the train moved its own contraption, the body held itself on the rail, and nothing was
shared between them. A train can now be bridged to a body (`bridge/`), and the division of labour is
one sentence: **Create keeps the route, the physics keeps the body.**

- `TrainBridge.bridge(train, body)` replaces the carriage of a single-carriage train with a
  `BridgedCarriage` and binds it to a `CarriageBody`. The train stays in Create's railway manager;
  its travelling points, anchors, edge points and signal handling remain Create's code, unpatched.
- `BridgedCarriage` overrides `Carriage.travel` and nothing else. The distance Create computed for
  that step is replaced by the distance the body covered since the previous call, measured along the
  carriage's own forward direction. A carriage that is asked to follow a body must not follow a
  teleport, so the step is clamped at two blocks per tick - above Create's own top speed.
- `CarriageBody` hands `Train.speed` to the axles as a target speed, and answers two questions: how
  far the body went, and how fast it is going. Nothing on the train side writes a position, a
  velocity or an angular velocity of the body.
- `TruckBlockEntity.driveAlong` is an axle's second input: a speed in m/s and a world direction,
  projected onto the rail the axle is on. The projection resolves the sign for an axle mounted any
  way round, so the train's forward is the train's forward and not the block's.
- A bridged carriage does not spawn a `CarriageContraptionEntity`. The body in the world is the
  carriage; a contraption entity drawn over it would be a second copy of the same vehicle.

### Why a subclass and not a patch

The engine only has to own one number - how far a carriage travels. A subclass can supply that
number without a mixin, without editing Create, and without a second representation of the train:
every other behaviour of `Carriage` is still `Carriage`'s.

### Measured, not claimed

`./gradlew runSelfTest`, scenario `bridge`, dedicated server, 220 ticks (the log line is wrapped):

```
bridge PASS carriages=1 target=1.600 speed=1.424 travelled_body=14.234 travelled_train=14.234
            drift=0.000 lateral_max=0.000 anchor_gap=0.011 ordered=1.600 peak=1.424 rise_ticks=34
```

The harness builds a real `Train` with a real `Carriage` on the same track graph the trucks ride,
registers it in Create's railway manager and bridges it to the rig. Create's own train loop then
drives it. The order reaches the commanded 1.6 m/s (0.08 blocks per tick), the body settles at
1.424 m/s after 34 ticks of wheel-limited ramp, the carriage travels 14.234 blocks against the body's
14.234 with zero drift in 220 ticks, and the carriage's leading anchor sits 0.011 blocks from the
leading axle. A train that dragged its body would be at full speed on the first tick and would hold
a constant offset from it; `rise_ticks=34` and `drift=0.000` are the two numbers that say otherwise.

The remaining 1.42 against 1.6 is the same shortfall the `traction` scenario reports and has the same
cause: the idling axle rolls and takes energy out. It is the model, not a miss.

### Known limits of this phase

- The bridge has no in-game entry point yet: it is called by code, or by the harness.
- A carriage's own contraption is not converted into a body. The bridge joins a train to a body that
  already exists; reading the contraption, placing its blocks and assembling them is the next step.
- One carriage per body. `TrainBridge.bridge` refuses a train with more than one carriage, because
  the couplings between bodies have to decide who pulls whom.
- Passengers and cargo stay on the contraption side.


## 0.2.2-alpha.1 (published as `0.2.2`) - a physics harness, and the direction of 0.2.0 called what it was

### Why the next version changes direction again

0.2.0 replaced the mod's connection to Create's trains with a vehicle of its own. The reasoning was
recorded as "integration with a Create train is not possible", and that was wrong. The failure of
0.1 had exactly one cause: the body was teleported after a `CarriageContraptionEntity` that Create
moves itself, so the physics engine never owned the motion. The correct conclusion was "do not
teleport the body", not "do not touch the train". This changelog does not present that turn as the
plan from the start: it was a wrong inference, and the mod is being rebuilt on Create's own
`Train`, `Carriage` and `CarriageContraption` from 0.3.0.

### Fixed in the physics, all found by the harness

- **Weight, grip and steering were divided between axles twice.** Each axle reports an acceleration
  of the whole vehicle, which becomes a force once; three of them were divided by the axle count
  again, and the driven axle used the *driven* count for its share of the weight. A two-axle vehicle
  with one driven axle pushed itself upwards at half a g and climbed off the rail.
- **The axle could ride the wrong one of a rail's two edges.** Create stores every rail as an edge
  from A to B and another from B to A; they are geometrically identical and point opposite ways.
  Two axles that picked different ones pushed the body apart. The contact now takes the direction the
  truck faces and rejects the edge that disagrees.
- **The contact wrote angular velocity to the body.** With inertia bypassed, the harness measured the
  vehicle yawing from 0 to -180 degrees in twenty ticks and tumbling. The steering term is gone: a
  structure whose axles are pushed sideways in opposite directions yaws by itself.
- **A sideways force at the wheel rolled the body.** The wheel sits half a block below the centre of
  mass, so the flange force had a rolling lever arm, and with no suspension to catch it the body
  reached sixty degrees of roll after a 0.5 m/s push. The force is now applied in the plane of the
  centre of mass; the fore/aft offset that steers is kept.
- **The vertical contact was too soft to hold a wheel on a rail.** At the flange stiffness of 18 the
  body floated half a block above the rail head. `contactStiffness` (220) and `contactDamping` (24)
  are separate settings now, and the harness measures 0.005 blocks of offset.
- **The harness itself had two bugs worth recording**: a rail with one node at each end is invisible
  to the contact, which only looks near nodes; and a plot with no player unloads within a second,
  which removes the body. Both are fixed, the second with Sable's own forced-loading ticket.

### Added

- **A physics harness that runs on a dedicated server** (`./gradlew runSelfTest`, property
  `cts.selftest=true`). Four scenarios - `assembly`, `guide`, `traction`, `derail` - build a
  vehicle, drive it on a straight rail and print measurements: mass, lateral and vertical error,
  speed, distance travelled, and the state of every axle. The run ends with
  `CTS SELFTEST SUMMARY scenarios=4 passed=4 failed=0`.
- A `selftest` job in CI. It fails if the summary is missing or reports a failure, so "the server
  started" is no longer accepted as evidence that the mod works.
- `TruckBlockEntity.selfTestSpeed`, so the harness can give a truck a shaft speed on a server with
  no kinetic network.

### Known limits of the harness

- The rail is a synthetic two-node `TrackGraph` with a straight edge: no switches, curves or
  slopes.
- The shaft speed is set by the test rather than read from a kinetic network.
- There is no curve in the harness yet, so overspeed derailment is covered by arithmetic only.

### Measured, not claimed

```
assembly PASS blocks=17 trucks=2 mass=17.000 states=[GUIDING,GUIDING]
guide    PASS lateral_max=0.046 lateral_final=0.020 vertical=0.005 speed=0.126
traction PASS target=1.600 speed=1.424 travelled=13.050
derail   PASS horizontal_speed=4.472 dropped=52.413 saw_off_rail=true
```

## 0.2.1-alpha.1 (published as `0.2.1`) - fixes found by reading the rebuilt code

0.2.0 built and booted, so the next pass went through the arithmetic and the pipeline instead.

### Fixed

- **The gravity the contact carries is the dimension's, not a config guess.** Sable builds its
  pipeline with `DimensionPhysicsData.getGravity(level)`, so the engine applies the gravity of the
  dimension; the contact used a constant. In any dimension whose gravity is not 9.81 the vehicle
  sank through the track or floated off it. The contact now takes gravity as an argument and the
  config value is only a fallback.
- **Weight, grip and steering were divided between axles twice.** Each truck reports an
  acceleration of the whole vehicle, which becomes a force once, by `mass / axleCount`; three of
  those accelerations were divided by the axle count *again*. A four-axle vehicle was therefore
  held up by a quarter of its weight, had a quarter of its grip, and its steering correction was
  added four times. Now one `g` for the vehicle, `mu*g` of grip whatever the axle count, and the
  steering shared. Four unit tests pin the scaling for 1, 2, 4 and 6 axles.

### Added

- **Vehicle Assembly Tool.** Sable ships no item that assembles a structure, so the only way to
  make a vehicle physical was a command. The tool does what the command does, on right click, and
  wears out.
- **Readable block models.** Every face now uses a UV window sized from its own box, so the wheels
  are round and the frame keeps its rivets; the driven truck has a motor housing and the idler a
  bolted cover, so the two are distinguishable.
- **`tools/preview_models.py`** renders the models with the real textures (back-face culling,
  painter's algorithm, inventory camera) so the models can be checked without launching the game.
- **Russian names** for the blocks, the tool and the config sections.

### Changed

- The workflow reports on every commit: `tools/test_summary.py` puts the number of tests and the
  name of any failure into the commit comment, and the boot job quotes Minecraft's `Done (` line
  and the mod's own load line - and fails if that line is missing.
- `rail/RailPath.java`, the unused second implementation of "find the rail under a point", removed.

### Still not verified

Nothing here has been driven in a world. The unit tests pin the arithmetic and the server boots
with the mod loaded; handling, brake distance and the choice of constants are untested.

## 0.2.0-alpha.1 (published as `0.2.0`) - full architecture revision

The previous versions did not work and are not carried over. This entry says what was wrong,
what was removed, and what replaced it.

### Why the previous version was rewritten

- The mod replaced a running Create train **after the fact**: it copied a carriage's blocks into
  the world next to the train and assembled *that* copy into a Sable sub-level. Blocks that did
  not land in air were silently skipped, so the body was a partial copy of the train, with the
  wrong mass and the wrong centre of mass, floating next to the original.
- The physical path was **off by default** (`convertCreateTrains = false`) and the switch that
  enabled it was named after the implementation, not the behaviour.
- Even when enabled, the body was **dragged by a position controller** towards a hidden Create
  contraption that kept being teleported by Create every tick. Rapier never owned the motion, the
  train's own speed never came from the physics, and a crash could only be detected after ten
  ticks of a growing error - the same dead end a kinematic follow-up of a moving entity always
  reaches.
- The rail contact was implemented **twice** (one path for assembled Create trains, one for the
  rail truck blocks), with two config sections, two derailment models and two kinds of state.
- Nearly all of the code was unverifiable: the project had no continuous integration, so it was
  never compiled outside a developer's machine, and nothing was ever run.
- Rendering, collision and Flywheel were patched with `require = 0` mixins that silently did
  nothing when their target moved - a hidden duplicate carriage rather than a visible failure.

### Removed

The whole contraption-replacement pipeline: `derailment/`, `conversion/`, `integration/`,
`physics/` (the controller models), the crash/hide mixins, the optional-backend loader and the
degraded mode, and their configuration. Sable is a hard requirement, so pretending it is optional
was dead weight.

### Rebuilt

- A vehicle becomes a Sable sub-level and is **owned by the physics engine** from the first tick.
  Nothing is teleported, nothing is copied into the world: the structure is the train.
- Rail contact is **per axle**: each truck projects its axle onto Create's `TrackGraph`, and the
  wheel/rail interaction is applied as an impulse at the axle, with the reaction returned to the
  track. A truck that leaves the flange limit stops holding the vehicle - that is the derailment,
  and the body keeps its momentum.
- Spring couplings exchange **equal and opposite impulses**, so a locomotive is actually held back
  by the wagons it pulls.
- One configuration section, one set of numbers, one place where the physics lives.

### Influences

This version is a hybrid of working solutions from three independent projects:

- **Create: Simurail** - the approach of driving a Sable structure with per-axle rail joints and
  adhesion-limited traction instead of a spline animation, and doing it from block entities that
  live inside the structure.
- **Ferronautics** - converting a carriage at the moment the station removes its blocks, keeping
  the contraption as the owner of the schedule, and the explicit warning that a body driven
  kinematically is not a physical body.
- **Create: Loconautics** - the per-train choice between a contraption and a physics object, and
  treating derailment and uncoupling as physical events rather than scripted ones.
