# Changelog

All notable changes to this project. Newest first.

## `0.2.0-alpha` - a consist, not a pair

The version moved because a phase closed, not because a jar was built. The alpha below was one body,
then two; this one is a train of several vehicles that holds together and can be trusted with a load
behind it.

### What works now

- **A chain of vehicles.** A Create train with three carriages bridged to three bodies runs as one
  consist: one traction truck in front, two unpowered vehicles behind it, a spring between each pair.
  Both couplings hold their gap while the train runs, and the leading one stretches about twice as far
  as the one behind it - it is carrying both vehicles, and a coupling that ignored that would be a
  rigid bar rather than a coupling.
- **Create reacts when the couplings are pulled apart.** For a bridged train, Create had no way of
  knowing how far a coupling was stretched, so nothing happened however hard the consist was pulled.
  It now sees the real deformation and applies its own limit: a coupling stretched past four blocks
  stops the train and puts it in the derailed state that the train screen shows.
- **A minute of a consist, not of a single body.** The long-run check now runs a three-vehicle train
  and watches every carriage against its body, the entity count and every axle's contact with the
  rail.

### Still open, and it is the next phase

Stations and signals: Create's navigation needs a conductor, and a bridged carriage still has no
contraption to carry one. Then riders, then derailment reported to Create when a vehicle leaves the
rail - see the roadmap in `README.md`.

## `0.1.0-alpha` - the alpha

This is the whole alpha up to now, in one entry. The number is frozen: it moves when a phase of the
roadmap is closed, not when a jar is rebuilt, so a build is identified by its commit. The tag on the
jar tells you one thing only - that this is alpha, and that the API, the physics, the data format and
the saves can change without warning.

Earlier these changes carried numbers of their own - `0.2.0`, `0.2.1`, `0.2.2`, `0.3.0`, `0.3.1`,
`0.4.0`, `0.4.1` and a short-lived `0.4.2-alpha.x` - as if each were a release. They were not. They
were intermediate iterations inside this same alpha, published without a pre-release suffix, which
read as steady progress the project had not made. They are kept here as history, not as releases:

| Iteration marker | What it actually was |
| --- | --- |
| `0.2.0` | the rewrite: a vehicle became a Sable body owned by the physics, with per-axle rail contact, instead of a copy of a Create train dragged by a position controller. Everything before it did not work and was removed. |
| `0.2.1` | fixes that came from reading the rewritten code: contact and coupling arithmetic, the assembly path. |
| `0.2.2` | the physics self-test: a headless harness that assembles a vehicle, drives it and measures what happened - and the defects it immediately found. |
| `0.3.0` | the bridge: a Create train can be tied to a physical body, so Create keeps the route and the physics keeps the body. One carriage. |
| `0.3.1` | the same bridge, documented honestly: what it does not do (stations, signals, riders) written down instead of implied. |
| `0.4.0` | more than one carriage: spring couplings between bodies, a physical body pulled by a traction truck through a coupling, and the debug tool that bridges and unbridges a train in game. |
| `0.4.1` | convergence and soak measurements for the coupled pair, and the distinction between a solver velocity and a distance actually travelled. |
| `0.4.2-alpha.x` | licensing and metadata: Apache-2.0 with a mandatory `NOTICE`, the alpha marker in the version, dead code removed. |

### What the alpha contains

**Blocks and items**

- **Traction Truck** - a driven axle. Takes rotation, pulls the vehicle along the rail, capped by
  adhesion.
- **Rolling Truck** - an idler axle. Carries its share of the weight, rolls and brakes.
- **Spring Coupling** - links two vehicles that face each other; the link is a spring and a damper
  applied to both bodies.
- **Vehicle Assembly Tool** - turns a built vehicle into a Sable sub-level, and is also the debug
  bridge/unbridge action (sneak + right-click an assembled vehicle).

**Physics**

- Per-axle wheel/rail contact: flange spring with a clamp, vertical contact, yaw produced by applying
  force at the axle rather than at the centre of mass.
- Adhesion-limited traction and braking, shared between axles by the weight each carries.
- Derailment as a physical event: an axle beyond the flange limits or a curve taken too fast stops
  guiding, and the vehicle keeps its momentum. In this version Create is not told about it.
- Spring couplings between vehicles, with equal and opposite force at the coupling faces.

**Create integration**

- A Create train can be bridged to one physical body per carriage: the train keeps its route, its
  travelling points, its schedules and its screen, and each carriage travels the distance its body
  actually covered.
- The train's commanded speed is handed to the axles as a target speed; nothing on the train side
  writes a body's position, velocity or angular velocity.
- A bridged carriage spawns no contraption entity - the body in the world *is* the carriage.

### Not in this alpha

Stated here as well as in `README.md`, because a changelog that omits it would be the wrong kind of
short:

- no passengers and no cargo while a train is bridged;
- no derailed state in Create for a vehicle that leaves the rail, and no station stops and no signal
  stops while bridged;
- no collisions between two consists;
- no support for long sessions with chunk unloading.

### Licensing

- Code: Apache License 2.0 (`LICENSE-APACHE`), with a `NOTICE` that must travel with every copy.
- Assets - textures, models, blockstates, language files under
  `src/main/resources/assets/createtrainssable/`: All Rights Reserved (`LICENSE-ARR`).
- The two files are named after their licences, so which one covers what is visible without opening
  either of them. Both were MIT before `0.1.0-alpha`; the change is recorded here rather than
  quietly.

### Influences

The design is a hybrid of working solutions from three independent projects, credited in full in the
`0.2.0` iteration above: **Create: Simurail** (per-axle rail joints and adhesion-limited traction
instead of a spline animation), **Ferronautics** (the train keeps the schedule while the vehicle
becomes physical, and the warning that a kinematically driven body is not a physical one) and
**Create: Loconautics** (derailment and uncoupling as physical events rather than scripted ones).
