# Contributing

## Where an engineering report lives

Not in this repository. `README.md`, `CHANGELOG.md` and this file are for people who install the mod
or want to change it, and they say what the mod does and what is required to work on it. Measurements,
comparisons against a target, the reasoning behind a fix and the post-mortem of a defect belong to the
pull request that changed the behaviour, and to the conversation with the maintainer.

The rule of thumb: if a paragraph would be out of place in the mod's own documentation page, it goes
in the PR description instead. A README full of numbers from last month is worse than a README with
none.

## A change is only accepted with numbers

A change is only accepted with numbers. "It builds" and "the server started" are not evidence. Every
PR that touches motion, the rail contact, the bridge, the couplings or the train loop has to show, in
its description, a run of `./gradlew runSelfTest` with the scenarios it affects, each measurement
against the target it was compared with, and what happened in between. If a scenario cannot be
measured honestly, that is said in the PR and listed as an open point in `README.md` - it is never
quietly dropped.

## Version numbering: frozen for the alpha

The version is **`0.1.0-alpha`** and it does not change from pull request to pull request. Builds are
told apart by their commit.

- **A release number is assigned only when a whole phase is closed** - the phases are listed in
  `README.md` under "What comes next" - or when the project leaves alpha. Closing phase B gives
  `0.2.0-alpha`, phase C `0.3.0-alpha`, phase D `0.4.0-alpha`.
- **Inside a phase, nothing increments.** Not for a feature, not for a fix, not for a patch. If it
  helps a player to know what is in their jar, the answer is the changelog and the commit, not a new
  number.
- **A version without a pre-release suffix may not be assigned** until all of these are closed, each
  with a measurement in the PR that closes it:
  1. derailment reported to Create's own derailed state;
  2. stations stopping a bridged train, with the stop distance measured;
  3. signals stopping a bridged train, with the reaction time measured;
  4. the train visible and driven from Create's own interface;
  5. passengers and cargo carried inside the physical body;
  6. collisions between two consists;
  7. stability over a long session, with chunk unload and reload.

## Building and running

- `./gradlew build` - compile and run the 18 unit tests of the rail contact and the coupling
  arithmetic.
- `./gradlew runSelfTest` - a dedicated server with `-Dcts.selftest=true`: it builds a vehicle by code,
  drives it, bridges it to a Create train and prints one line of numbers per scenario.
- `./gradlew runServer` - a dedicated server, which must reach `Done (`.
- `python3 tools/preview_models.py` - renders the block models with the real textures into PNGs, so a
  stretched face is visible without launching the game.

Java 21 is required. There is no Fabric build, no Loom, and no second source set to keep in sync.

## Drawing and replacing a texture

The textures are All Rights Reserved (`ASSETS_LICENSE`), so a texture you add has to be drawn by you:
copying, tracing or recolouring a Create texture - or an existing one from this mod - is not
acceptable. The full procedure:

1. **Open the existing model.** Blockbench → *File → Open Model* →
   `src/main/resources/assets/createtrainssable/models/block/traction_truck.json`.
2. **Resolution.** Keep 16x16, Minecraft's native density. A 32x32 pass is a separate resource pack
   for 1.21.1 and must not replace the base set.
3. **UV.** Leave *Auto UV* off and keep the explicit rectangles: a 16x6 px side face keeps a 16x6
   window. Changing them stretches the face - that is the only way to break the model.
4. **Palette.** Five ramps: dark steel `#2E3236`, mid steel `#53595F`, light steel `#7C848B`,
   highlight `#A9B1B7`, and one accent for the drive `#C08A3E`. At most 12 colours per texture.
5. **Shading.** Light from top-left, one ramp step per surface, a one-pixel dark line where two boxes
   meet. No gradients and no anti-aliasing - Minecraft's filtering blurs both.
6. **Export.** *Export Block/Item Model* over the JSON in `models/block/`, *Export Texture* over the
   PNG in `textures/block/` or `textures/item/`. Exact paths under
   `src/main/resources/assets/createtrainssable/`:
   ```
   textures/block/truck_frame.png      textures/block/truck_wheel.png
   textures/block/truck_drive.png      textures/block/coupling_plate.png
   textures/block/coupling_coil.png    textures/item/assembly_tool.png
   models/block/traction_truck.json    models/block/rolling_truck.json
   models/block/spring_coupling.json   models/item/{assembly_tool,traction_truck,
                                                rolling_truck,spring_coupling}.json
   blockstates/{traction_truck,rolling_truck,spring_coupling}.json
   ```
7. **Verify headless.** `python3 tools/preview_models.py` renders the models with the new textures; if
   a face looks wrong there, it is wrong in game.
8. **Say where it came from.** In the PR: who drew it and under what terms. The assets are All Rights
   Reserved in this repository, which only works if every asset in it is either the maintainer's or
   explicitly licensed for it.

## Branches, commits, merges

`main` is protected. Work happens on a branch, in Conventional Commits (`feat:`, `fix:`, `test:`,
`docs:`), in a PR whose description carries the measurements, and it is squash-merged. Do not commit
to `main` directly and do not merge a phase into another phase's PR.

## Dead code

Unused classes, methods, fields, constants and resources are removed in the PR that makes them
unused, or in the next cleanup pass if they were already dead. "Might be needed later" is not a
reason to keep them: git remembers.

## Attribution and licence

The code is Apache-2.0 and the assets are All Rights Reserved; the split is spelled out in
`ASSETS_LICENSE` and in the "Licence and attribution" section of `README.md`. Keep the `NOTICE` file,
the "Original project" section of the README and the copyright line in every fork and every
redistribution, and if you modify a file, say so in that file, as Apache-2.0 section 4(b) requires.

## Language and layout

- Every document lives in the repository root.
- Code comments and identifiers are English.
- No generated artefacts in git: `build/`, `runs/`, `__pycache__/` and the like are ignored, and if
  one slips in it is removed in the next PR rather than left.
