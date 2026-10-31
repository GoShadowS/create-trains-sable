# Create Trains Sable

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
- **The physics keeps the body.** With `TrainBridge.bridge(train, body)` the single carriage of the
  train becomes a `BridgedCarriage`. Instead of travelling the distance `Train.speed` implies, it
  travels the distance its body actually covered since the last tick, measured along the carriage's
  own forward direction and clamped at two blocks per tick so a teleport cannot drag a carriage.
- **The order still flows the other way.** `Train.speed` - set by the controls, by a schedule or by
  `Navigation` - is handed to the axles as a target speed, and the wheels turn it into force. The
  train tells the body how fast to go; it never tells it where it is.
- **No contraption entity.** A bridged carriage does not spawn a `CarriageContraptionEntity`: the
  body in the world is the carriage, and a contraption drawn on top of it would be a second copy of
  the same vehicle.

Today the bridge has no in-game entry point: a train is bridged by code (`TrainBridge.bridge`) or by
the harness. Turning a carriage's own contraption into a body, and giving a player a tool to ask for
it, is the next step and is not claimed here.

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

1. **`./gradlew build`** - the mod compiles, and the rail-contact arithmetic passes 11 unit tests
   (holding the rail, leaving it past the flange, the attach window, overspeed on a curve, the
   adhesion cap, weight and grip sharing between axles, the steering correction).
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

Each scenario prints one line of numbers and the run ends with
`CTS SELFTEST SUMMARY scenarios=5 passed=5 failed=0`; the CI job fails unless that line says
`failed=0`. The last run on `main` (the `bridge` line is wrapped for width):

```
assembly PASS blocks=17 trucks=2 mass=17.000 states=[GUIDING,GUIDING]
guide    PASS lateral_max=0.046 lateral_final=0.020 vertical=0.005 speed=0.126
traction PASS target=1.600 speed=1.424 travelled=13.050
derail   PASS horizontal_speed=4.472 dropped=52.413 saw_off_rail=true
bridge   PASS carriages=1 target=1.600 speed=1.424 travelled_body=14.234
              travelled_train=14.234 drift=0.000 lateral_max=0.000
              anchor_gap=0.011 ordered=1.600 peak=1.424 rise_ticks=34
```

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
- **A train's own contraption is not turned into a body yet.** The bridge binds a train to a body
  that already exists, and the harness supplies one it builds. Reading a carriage's contraption,
  putting its blocks into the world and assembling them is the next step; until then a train and a
  body are joined only by code.
- **One carriage per body.** `TrainBridge.bridge` refuses a train with more than one carriage: the
  couplings between bodies have to decide who pulls whom, and the spring coupling model is not wired
  into the train loop yet. A multi-carriage train stays a Create contraption.
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

## License

MIT. See [LICENSE](LICENSE). Create and Sable are separate projects under their own licenses and
are not included in this mod.

## Credits

- RyanHCode and the Sable contributors, for sub-levels and the Rapier pipeline.
- The Create team, for the track graph and the kinetic system.
- Dimforge, for the Rapier physics engine.

Minecraft is a trademark of Mojang Synergies AB. This mod is not affiliated with Mojang, Create or
Sable.
