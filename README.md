# Create Trains Sable

**Physical rail vehicles for Create.** A vehicle is a Sable structure, and its axles are the only
thing that holds it on the rails: real wheel/rail contact, adhesion-limited traction, curves that
can throw it off, and spring couplings that hold two vehicles together with equal and opposite
force.

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
3. **Steering.** The axle turns the body so its facing follows the tangent of the rail.

The force is applied at the axle, not at the centre of mass, so a truck under the front of a vehicle
turns it and a truck under the back holds it straight - which is what a bogey does.

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

## Configuration

`config/createtrainssable-server.toml`, applied immediately:

```toml
[trucks]
speedPerRpm = 0.05           # m/s of target speed per RPM of the driving shaft
maxSpeed = 60.0
traction = 1.2               # m/s^2 the driven wheels can put down
brake = 1.6                  # m/s^2 the brakes can remove
friction = 0.45              # adhesion of steel wheel on steel rail
guideStiffness = 18.0        # how hard the flange pushes back, 1/s^2
guideDamping = 6.0
maxGuideAcceleration = 26.0  # grip limit of the contact, m/s^2
alignGain = 3.0              # how strongly the body is turned along the rail
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

Verified on every push, by the workflow in `.github/workflows/build.yml`:

- the mod compiles (Gradle, Java 21, NeoForge 1.21.1);
- the rail-contact arithmetic passes its unit tests - 12 cases covering holding the rail, leaving
  it past the flange, the attach window, overspeed on a curve, the adhesion cap, weight and grip
  sharing between axles, and the steering correction;
- a dedicated server starts, loads the mod and reaches `Done (`.

**Not verified:** nothing in this repository has been driven in a world with a player. The feel -
acceleration, brake distances, how tight a curve can be taken, whether the trucks sit where the
build expects - is untuned, and every number in the configuration section is an estimate from
physics, not a measurement from play. Treat the mod as a working mechanism, not a finished ride.

## Known limitations

- **Assembly takes the whole connected structure.** The assembly tool gathers everything touching
  the block that was clicked, so a vehicle that is connected to the ground or to a building will
  take that with it. Disconnect it first, or click a part that is not touching the world.
- **Create trains are not converted.** A Create station train stays a Create contraption. The
  physical vehicle is the one the player builds and assembles; converting station trains would mean
  keeping two representations of the same train in sync, which is exactly what the previous
  architecture got wrong.
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
