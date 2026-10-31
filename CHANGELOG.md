# Changelog

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
