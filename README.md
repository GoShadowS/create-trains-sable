# Create Trains Sable

## Original project

**Author and original repository: GoShadowS - https://github.com/GoShadowS/create-trains-sable**

Copyright 2026 GoShadowS. The code is licensed under the Apache License, Version 2.0 (`LICENSE`); the
textures, models and other assets are All Rights Reserved (`ASSETS_LICENSE`). The `NOTICE` file must
travel with every copy, fork and build. If you fork this mod, keep this section, the `NOTICE` file and
the copyright line: attribution to the original author is a licence condition, not a courtesy, and it
may not be removed, hidden or rewritten. See [Licence and attribution](#licence-and-attribution).

## Project status: ALPHA

**Status: alpha, version `0.1.0-alpha`. The API, the physics, the data format and the save
compatibility can all change without warning. Not ready for production servers.**

The version number is frozen for the whole alpha period and does not move with individual updates; a
build is identified by its commit, and the number changes only when a whole phase of the plan below
is closed. `CHANGELOG.md` says what is in this alpha, `CONTRIBUTING.md` says when the mod is allowed
to leave it.

What is still missing, in plain terms, because a version suffix should not be the only signal:

- derailment is not reported to Create - a vehicle that leaves the rail simply stops being driven;
- stations and signals do not stop a train that is bridged to a physical body;
- passengers and cargo are disabled while a train is bridged;
- collisions between two consists are not modelled;
- nothing beyond a minute of running has been measured for long-session stability.

**Physical rail vehicles for Create.** A vehicle is a Sable structure, and its axles are the only thing
that holds it on the rails: real wheel/rail contact, adhesion-limited traction, curves that can throw
it off, and spring couplings that hold two vehicles together with equal and opposite force. A Create
train can be bridged to such a body, so the route stays Create's while the motion becomes the physics
engine's.

Minecraft 1.21.1 · NeoForge · Create 6.0.10 · Sable 2.0 · Java 21

---

## What the mod is

There is exactly one representation of a vehicle: the Sable sub-level the player assembled. The mod
adds three blocks that go on that structure and one rule that governs them - **the physics owns the
motion, the blocks only push**.

| Block / item | What it does |
| --- | --- |
| **Traction Truck** | A driven axle. Takes rotation on its axle axis and pulls the vehicle along the rail. |
| **Rolling Truck** | An idler axle. Carries its share of the weight, rolls, and brakes. |
| **Spring Coupling** | Links two vehicles that face each other. The link is a spring and a damper, applied to both bodies. |
| **Vehicle Assembly Tool** | Turns a built vehicle into a Sable sub-level. Right-click it; no command needed. |

Build the vehicle, put trucks under the frame where the axles belong, power the driven ones, add
couplings to the ends, then right-click the build with the assembly tool. From the first physics step
the vehicle is a rigid body held on Create's rails by force - there is no second copy of it, no hidden
contraption, and nothing that teleports it.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.228 or newer
- Create 6.0.10.x
- Sable 2.0.x (required: a truck is a block entity Sable ticks on a sub-level)
- Java 21

Install all of the above into `mods/`, plus this mod's jar. Nothing else is needed: there is no
resource pack, no datapack and no command.

## How it works

Every physics substep, every truck does three things and reports them as a force:

1. **Flange.** A spring-damper in the plane across the rail pushes the axle back onto the rail head.
   It is clamped: the rail can only push so hard before the flange climbs.
2. **Traction.** A driven axle tries to reach the speed its shaft asks for; an idler only rolls and
   brakes. Both are capped by the adhesion of the wheel, so a heavy vehicle accelerates slowly and a
   locomotive cannot pull what it has no grip for.
3. **Yaw, from the contact itself.** The force is applied at the axle, not at the centre of mass, so
   two axles pushed sideways in opposite directions turn the structure the way a bogie does.

### Derailment

An axle is either holding the rail or it is not, and it stops holding for a physical reason: it is
further from the rail head than the flange allows, or the curve needs more lateral force than the
flange can supply.

When no axle holds the vehicle any more, nothing pushes it any more and the physics engine does the
rest: it keeps its momentum, hits what is in the way, and comes to rest where it lands. There is no
scripted crash and no wreck entity. What does *not* happen yet is Create noticing: the train is not
marked as derailed, so the train screen shows a train whose carriages have stopped following it.

### Couplings

Two couplings facing each other within range link on their own. The distance at the moment of linking
is the rest length, so hooking up never yanks the vehicles together. The spring force is applied to
one body and the opposite force to the other, at the coupling faces: a locomotive is genuinely held
back by the wagons it pulls. A coupling pulls and pushes along one line only - it transmits no torque
and does not bend.

## Putting a Create train on a physical body

A body on its own has no route. Create's trains have the route - the track graph, the travelling
points, the signals, the stations, the schedule and the train screen - and no physics. Bridging a
train joins the two without merging them:

- **Create keeps the route.** The train stays a real Create train and appears in the train screen and
  the HUD; nothing about Create's train screen, schedules or track graph is replaced.
- **The physics keeps the body.** Each carriage is tied to one physical body. Instead of travelling
  the distance its speed implies, the carriage travels the distance its body actually covered, so a
  train cannot slide along the track independently of the vehicle carrying it.
- **The couplings hold the consist together.** Between two neighbouring bodies a spring and a damper
  push and pull along the line between their centres. That is what lets one traction truck move a
  train of unpowered wagons: the wagon has no motor, so all of its motion arrives through the
  coupling.
- **The order still flows the other way.** The train's speed - set by the controls, by a schedule or
  by its own navigation - is handed to the axles as a target speed, and the wheels turn it into force.
  The train tells the body how fast to go; it never tells it where it is.

### What a bridged carriage does not have

Suppressing the contraption entity is not free, and the cost is stated plainly instead of phrased as
"passengers stay on the contraption side". A bridged carriage has **no contraption in the world at
all** - there is no seat to sit on, no inventory to open and no entity to carry. What survives is data
on the carriage object: its cargo and its list of riders, which nothing reads while the carriage is
bridged.

So **passengers and cargo are disabled functionality in this alpha**, not relocated functionality. A
player who tries to board a bridged carriage has no carriage to board; a train inventory is not
rendered and not accessible. Carrying riders into the physical body is phase C below, and until it
exists a bridged train is a freight-only, empty-vehicle feature.

### Bridging a train by hand

The bridge is a debug action on the assembly tool, and it needs no command:

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
carriage cannot go back to Create's contraption - its blocks live in a physical body, and Create's
entity creation would mark the train invalid from the empty contraption data. That is why the toggle
keeps the carriage object and only switches where its distance comes from.

## What comes next

Not implemented; listed so the roadmap is not a guess. The version number moves when one of these
phases is closed, not before.

- **B - a whole consist.** Wiring a train to its bodies from inside the game, and `Train.stress`
  treated as real deformation instead of a number to clamp.
- **C - riders.** Passengers and cargo moved into the physical body, which is also when the bridged
  carriage stops being an empty shell.
- **D - derailment in the train.** A body that leaves the rail puts the train into Create's own
  derailed state, and stations and signals finally stop a bridged train.

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

[debug]
gravity = 9.81               # fallback gravity, used when Sable has none for the dimension
logTrucks = false            # log the state of every truck once a second
```

## Getting help

Bugs, questions and requests go to
[the issue tracker](https://github.com/GoShadowS/create-trains-sable/issues). When reporting a
crash, attach the log - a bridged train's problems are almost always visible in the physics lines
before they are visible in the world.

## Is it tested?

Every push runs three things, and the build fails if any of them fails: the mod compiles and 18 unit
tests of the rail contact and the coupling arithmetic pass; a dedicated server loads the mod and
reaches `Done (`; and a physics session runs eight scenarios - assembly, guidance, traction,
derailment, bridging, bridging on and off by hand, a pulled wagon, and a minute-long soak - and prints
a line per scenario.

Those sessions are the project's own measurement, and their numbers belong to the pull request that
changed the behaviour, not to this file: a README full of measurements from last month is worse than
no measurements. If you want to see them, look at the self-test job of the latest commit or at the
descriptions of the pull requests on GitHub.

## Textures

Every block and item has its own texture, and none of them is a missing-texture placeholder - but none
of them is final art either. They are 16x16, generated by the scripts in `tools/`, readable at a
glance and flat: enough to play with, not enough to call finished. `CONTRIBUTING.md` has the
step-by-step procedure for redrawing a texture without stretching its faces, and the asset licence is
in `ASSETS_LICENSE` (All Rights Reserved, so a redraw has to be yours).

## Known limitations

- **Assembly takes the whole connected structure.** The assembly tool gathers everything touching the
  block that was clicked, so a vehicle that is connected to the ground or to a building will take that
  with it. Disconnect it first, or click a part that is not touching the world.
- **A train's own contraption is not turned into a body yet.** The bridge binds a train to bodies that
  already exist: the player assembles each vehicle by hand and then bridges the train to them.
- **A bridge needs as many free bodies as the train has carriages.** The tool pairs them by distance
  and direction, not by identity, so a consist assembled in a different order will be paired wrongly
  and the springs will fight. There is no per-carriage selection yet.
- **A pair that deforms by more than four blocks derails the train, by Create's own rule.** The spring
  is capped at 14 m/s^2 so this is hard to reach, but a body wedged against a wall will do it.
- **A bridge is not covered for hours of play.** One minute of game time is measured; chunk unload,
  reload, and deleting a train while it is bridged are not. Open until phase D.
- **No passengers, no cargo while bridged.** A bridged carriage has no contraption entity, so there is
  nothing to ride in and nothing to open. See "What a bridged carriage does not have" above.
- **No station stops and no signal stops while bridged.** Create's navigation needs a conductor from
  the contraption and a station to drive to; neither exists for a bridged carriage yet, so a bridged
  train can be driven with its controls but cannot run a schedule to a station.
- **One contact point per axle.** There is no wheel lift, no axle load transfer and no per-wheel slip
  curve; tight S-bends are where this shows first.
- **Couplings do not transmit torque or bend.** They pull and push along one line, which is what a
  screw coupling does, and nothing more.
- **A vehicle cannot be turned back into blocks.** Sable exposes assembly and removal but no
  disassembly, so an assembled vehicle is edited inside its sub-level or removed with Sable's own
  command.
- **Sable's API is not semver-stable.** A Sable update can require an update here.

## Licence and attribution

**Two licences, split by path.**

| Path | Licence |
| --- | --- |
| code: `src/main/java/`, `src/test/java/`, `build.gradle`, `tools/`, the documents in the repository root | **Apache License 2.0** ([LICENSE](LICENSE)) |
| assets: `src/main/resources/assets/createtrainssable/` - textures, models, blockstates, language files - and `pack.mcmeta` | **All Rights Reserved** ([ASSETS_LICENSE](ASSETS_LICENSE)) |

The Apache-2.0 side requires the `NOTICE` file to be preserved in every distribution (section 4(d))
and modified files to say they were modified (section 4(b)). That is what makes attribution to
GoShadowS non-removable rather than a request. The assets side is stricter on purpose: they may not be
copied, recoloured or extracted into another project, mod or pack, and a fork that renames the project
must replace them. Using the unmodified mod - in a modpack, in a video, on a server - is allowed and
needs no permission.

This is the same split Create itself uses (MIT code, All Rights Reserved assets), applied to this
project's own art.

### Compatibility with the projects this mod builds on

- **Create - MIT for code, All Rights Reserved for its assets.** Compatible: Apache-2.0 code may be
  combined with MIT code, and Create's notice is preserved for the parts that come from Create. No
  Create asset is copied here: every texture in this repository is generated by the scripts in
  `tools/`. Create is a dependency, not a component.
- **Sable - PolyForm Shield License 1.0.0.** Compatible as a dependency: nothing from Sable is
  redistributed here or in the built jar. Because that licence is not permissive, Sable can never be
  vendored into this mod, and its notice stays visible in `NOTICE`.
- **The rest of the mod ecosystem.** Apache-2.0 is one-way compatible with MIT: MIT code can go into an
  Apache-2.0 work with attribution, which is what happens here in a compiled mod environment.

### Forks

Change the name if you like - then replace the assets. Keep `NOTICE`, keep this section, keep the
copyright line. Removing them is a licence violation, not a style choice.

## Credits

- RyanHCode and the Sable contributors, for sub-levels and the Rapier pipeline.
- The Create team, for the track graph and the kinetic system.
- Dimforge, for the Rapier physics engine.

Minecraft is a trademark of Mojang Synergies AB. This mod is not affiliated with Mojang, Create or
Sable.
