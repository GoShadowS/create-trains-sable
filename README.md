# Create Trains Sable

**Physics trains for Create.** Assemble a train and it is already a physical object:
every carriage becomes a Sable sub-level the moment it is built, held on the rails by
wheel forces until the rails lose it. Build your own vehicle instead and it works the
same way - rail trucks under the frame, a shaft for power, and it rolls as one rigid
body that takes curves, slips, couples to wagons and derails when it is going too fast
for the bend it is in.

Minecraft 1.21.1 · NeoForge · Create 6.0.10 · Sable 2.0

---

## Features

- **Physical on assembly.** A train assembled in a station, or loaded from a save, is a
  Sable structure within a second - not a contraption that waits for a crash to become
  one. It stays attached to the rail under its wheels until the wheels leave it.
- **Trains that are objects, not animations.** The vehicle you built *is* the train: one
  body, one collision shape, one thing to walk on, push, load with cargo and crash.
  Create still owns the schedule, the seats and the route; the physics owns the motion.
- **Real traction.** Speed comes from rotation, not from a number in a menu. Wheel grip
  caps acceleration, brakes cap deceleration, and a heavy train accelerates like a heavy
  train.
- **Real curves.** Guidance is limited by the flange load the rail can take. Take a bend
  too fast and the trucks stop holding the vehicle - it keeps its momentum and becomes a
  wreck where physics put it.
- **Spring couplings.** Two couplings facing each other hook up on their own and hold the
  vehicles with a spring and a damper, so a locomotive pulls its wagons and a shunt at the
  back is felt at the front.
- **No teleporting, ever.** Nothing snaps the vehicle back onto a track, because nothing
  writes a position into it: every tick only velocity and angular velocity change.

## Blocks

| Block | What it does |
| --- | --- |
| **Traction Truck** | Driven axle. Takes rotary power on its axle axis; the shaft's speed is the speed it tries to reach. |
| **Rolling Truck** | Carries weight and rolls. Use as many as the train needs. |
| **Spring Coupling** | Links two vehicles that are close enough and facing each other. |

The trucks are drawn as a low steel frame on a visible wheel; the traction truck carries
its drive housing on top, so a powered axle and an idler axle never look the same. The
coupling is a plate with a coil spring reaching towards the vehicle in front of it.

All three are in the mod's own creative tab.

## Building a train

1. Build the vehicle out of ordinary blocks.
2. Put **traction trucks** and **rolling trucks** where the wheels belong, pointing the
   way the vehicle should move when the shaft turns forwards. At least one must be driven.
3. Give the driven trucks rotation - a shaft or a cogwheel from a motor, a boiler or a
   mechanical bearing.
4. Assemble the whole thing into a sub-level with Create: Aeronautics' assembly tooling,
   exactly like a ship or an airship.
5. For a multi-vehicle train, add a **spring coupling** to the end of each vehicle.

A full speed shaft (256 RPM) is about 12.8 blocks per second with the default gearing;
32 RPM is about 1.6.

## How it works

Each truck finds the nearest piece of Create track under its axle, with the tangent, the
up direction and the curvature of the rail at that point - straight track and Bezier
turns alike. On every physics substep it then:

1. holds its axle on the rail with a limited acceleration, plus gravity compensation
   shared between the vehicle's trucks;
2. turns the body so its facing follows the rail;
3. pushes along the rail with traction up to the grip of the wheels, and brakes up to the
   brake rating - shared between the driven trucks.

If the axle ends up further from the rail than the flange allows, or the curvature times
speed squared exceeds the flange limit, the truck simply stops guiding. That is the whole
derailment model: a train derails because the physics ran out of grip, not because a
threshold decided it should.

## Configuration

`config/createtrainssable-server.toml`, applied immediately:

```toml
[general]
physicalTrains = true        # trains are Sable sub-levels, not contraptions
physicalAtAssembly = true    # ...from the moment they are assembled, not only after a crash
```

```toml
[trucks]
speedPerRpm = 0.05           # m/s of target speed per RPM of the driving shaft
tractionAcceleration = 1.2   # m/s^2 the driven wheels can put down
brakeAcceleration = 1.6      # m/s^2 the brakes can remove
guideStiffness = 18.0        # how firmly the axle is held on the rail
guideDamping = 6.0
maxGuideAcceleration = 26.0  # grip limit of the wheel/rail contact, m/s^2
alignGain = 3.0              # how strongly the vehicle is turned along the rail
maxLateralError = 1.2        # blocks off the rail before the truck gives up
maxVerticalError = 0.9
curveLateralLimit = 2.6      # m/s^2 of flange load a curve may take
scanRadius = 24.0            # how far to look for track around a truck
trackRecheckTicks = 2        # physics runs faster than track lookups
couplingRange = 2.5          # blocks within which two spring couplings link
couplingStiffness = 6.0
couplingDamping = 4.0
couplingMaxAcceleration = 14.0
```

`curveLateralLimit` is what sets the feel of the mod: raise it and trains hold absurd
curves, lower it and a normal bend at speed throws the vehicle off.

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.228 or newer
- Create 6.0.10.x
- Sable 2.0.x
- Create: Aeronautics 1.3.x (the assembly tooling)
- Java 21

## Known limitations

- **Trains are held by a servo, not by axles.** Create's spline still says where a carriage
  should be and a force controller takes the body there; the body can lose that argument -
  that is a derailment - but it cannot yet steer itself along a switch that Create would not
  have taken.
- **Art is a first pass.** The models and textures are made for this mod but they are
  simple: a steel frame, a wheel, a brass drive housing, a coil spring. They are meant to be
  replaced by a proper art pass, not to be the final look.
- **Trucks need an assembled sub-level.** They do nothing lying in the world, and this mod
  does not ship an assembly tool - assemble with Create: Aeronautics.
- **The rail contact is a servo, not a flange.** There is no wheel lift, no axle load
  transfer and no per-axle slip curve; tight S-bends are where this shows first.
- **Couplings are mass independent.** Both ends are pushed by the same acceleration rather
  than by equal and opposite forces, so a locomotive is not slowed by a wagon it outweighs.
- **One track lookup per truck every `trackRecheckTicks`.** Fine for a handful of vehicles,
  measurable with dozens of trucks in one place.
- **Sable's API is not semver-stable.** A Sable update can require an update here.
- **Early development.** The physics is unit-tested, but this build has not yet been
  exercised in a long survival world.

## License

MIT. See [LICENSE](LICENSE). Create and Sable are separate projects with their own
licenses and are not included in this mod.

## Credits

- RyanHCode and the Sable contributors, for sub-levels and the Rapier pipeline.
- The Create team, for the train graph, track and kinetic systems.
- Dimforge, for the Rapier physics engine.

Minecraft is a trademark of Mojang Synergies AB. This mod is not affiliated with Mojang,
Create, Sable or Create: Aeronautics.
