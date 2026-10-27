# Contributing

Thanks for taking the time to work on this project. The code is small enough to
read in an afternoon, and the physics model is deliberately separated from
Minecraft so that most changes can be tested without launching the game.

## Before you start

The project targets Java 21. Changes must keep the existing unit tests green;
they cover the configuration snapshot, mass and inertia, curve geometry,
derailment thresholds, wheel adhesion and resync hysteresis.

## Branches and commits

`main` is the stable branch. Do not push to it directly. Create one branch per
logical change from the current `main`:

| Prefix | Use |
| --- | --- |
| `feature/` | new behaviour, for example `feature/impact-damage` |
| `fix/` | a bug fix |
| `docs/` | documentation-only changes |
| `chore/` | build, CI, dependency and housekeeping changes |
| `refactor/` | behaviour-preserving restructuring |
| `test/` | test-only changes |

Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) for
commit messages (`feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`).
Write the subject in the imperative mood and keep it under 72 characters. Each
commit should build on its own; do not bundle an unrelated formatting pass with a
physics change.

## Pull requests

One pull request per logical change, from your branch into `main`. The
description should answer:

- What changed and why.
- Which files are affected.
- How to reproduce or verify the change (unit tests, in-game steps, log lines).
- Risks and known limitations, especially anything touching Sable's internal API.

Include a short acceptance checklist when it makes sense, for example
"stations still stop trains with the add-on disabled". Link the related issue.
Squash merges are preferred so that one pull request becomes one commit on
`main`.

Review may ask for changes. That is normal; please update the branch with new
commits instead of force-pushing during review unless a maintainer asks for it.

## Code style

- Java 21, four spaces, no tabs, UTF-8, no wildcard imports.
- Keep `physics/` free of Minecraft, NeoForge, Create and Sable imports. It is
  the part that runs in plain JUnit tests.
- Comments explain why a decision was made, not what the next line does. Write
  Javadoc for public API when it documents parameters, return values, side
  effects or edge cases; skip it when the signature says everything.
- Name things after the domain: `carriage`, `subLevelId`, `safeSpeed`,
  `derailmentThreshold`. Avoid `data`, `result`, `temp`, `handleStuff`.
- Handle errors you can explain. Do not wrap ordinary control flow in try/catch
  or add null checks for values an API guarantees.
- Do not copy source from Create, Sable, Sable Companion or Create: Aeronautics.
  Depend on published artifacts and describe the behaviour you need in your own
  words. Their licenses differ from ours.
- Add a unit test for each change to the physics model. For game-side changes,
  describe the in-game verification in the pull request; a game test is welcome
  but not required yet.

## Mixins

A mixin is a last resort. Prefer Create's public fields and methods, NeoForge
events, datapack definitions and Sable's `api` package. If you add a mixin,
explain in a comment which state would otherwise be lost, keep the injection at
`HEAD` or `TAIL` with a single callback, and do not cancel the original method
unless the feature cannot work otherwise. Register the class in
`createtrainssable.mixins.json`.

## Licensing

By submitting a pull request you agree that your contribution is licensed under
Apache-2.0, the same as the rest of the repository. Preserve the `NOTICE` file
and the attribution requirement in `LICENSE` if you publish a fork.

## Conduct

All participants follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
