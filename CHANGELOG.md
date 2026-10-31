# Changelog

## 0.3.0 - a Create train rides a body

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


## 0.2.2 - a physics harness, and the direction of 0.2.0 called what it was

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

## 0.2.1 - fixes found by reading the rebuilt code

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

## 0.2.0 - full architecture revision

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
