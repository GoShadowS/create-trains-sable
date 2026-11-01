# Contributing

## The one rule about answering questions

A change is only accepted with numbers. "It builds" and "the server started" are not evidence. Every
PR that touches motion, the rail contact, the bridge, the couplings or the train loop has to show a
run of `./gradlew runSelfTest` with the scenarios it affects, their measurements, and the target each
measurement was compared against. If a scenario cannot be measured honestly, that is said in the PR
and recorded in `README.md` as an open point - it is never quietly dropped.

## Version numbering: alpha until it is not

The version is `0.<minor>.<patch>-alpha.<n>` and the suffix is part of the number, not a note in the
changelog.

- **`minor`** increments when a phase of the agreed architecture closes its acceptance criteria.
- **`patch`** increments for any other release: fixes, tests, documentation, licensing.
- **`-alpha.<n>`** is mandatory today. `-beta.<n>` may be used only when every item in the list below
  is implemented and measured, but not yet survived a long session. `-rc.<n>` may be used only after
  a beta has run for hours on a server with chunk unloading.
- **A version without a pre-release suffix may not be assigned** until all of the following are
  closed, each with a measurement in `README.md`:
  1. derailment reported through Create's own `Train.derailed` and `TrainStatus`;
  2. stations stopping a bridged train, with the stop distance measured;
  3. signals stopping a bridged train, with the reaction time measured;
  4. the train visible and driven from Create's own interface;
  5. passengers and cargo carried inside the physical body;
  6. collisions between two consists;
  7. stability over a long session, with chunk unload and reload.

If in doubt, the suffix stays. Shipping `0.4.1` and calling it a release was a numbering mistake, and
`CHANGELOG.md` records that in the entry for every version that did it.

## Branches, commits, merges

`main` is protected. Work happens on a branch, in Conventional Commits (`feat:`, `fix:`, `test:`,
`docs:`), in a PR whose description carries the measurements, and it is squash-merged. Do not commit
to `main` directly and do not merge a phase into another phase's PR.

## Attribution

This project is Apache-2.0. Keep the `NOTICE` file, the "Original project" section of `README.md` and
the copyright line in every fork and every redistribution. If you modify a file, say so in that file,
as Apache-2.0 section 4(b) requires.

## Language and layout

- Every document lives in the repository root.
- Code comments and identifiers are English. Chat and reports are Russian.
- No generated artefacts in git: `build/`, `runs/`, `__pycache__/` and the like are ignored, and if
  one slips in it is removed in the next PR rather than left.
