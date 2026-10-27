# Building Create Trains Sable

This guide takes you from a fresh checkout to a Minecraft test client in which
Create, Sable and this add-on are loaded, without building a jar by hand or
copying anything into a `mods/` folder. It assumes you have used Git and IntelliJ
IDEA before, but not that you have ever built a Minecraft mod.

## 1. What you need on your machine

| Requirement | Notes |
| --- | --- |
| JDK 21 | The build uses a Java 21 toolchain. If your installed JDK is older, the Foojay resolver in `settings.gradle` downloads JDK 21 on the first sync. |
| IntelliJ IDEA 2024.2 or newer | Community Edition is enough. Enable the bundled Gradle and Java plugins. |
| About 8 GB of free disk | The first sync downloads Gradle 9.5.0, Minecraft, NeoForge, Parchment mappings and several mod jars. The NeoForm decompilation step also needs memory. |
| A working internet connection | Maven repositories used: NeoForged, Create, Parchment, Sponge, BlameJared, Modrinth, Tterrag, ithundxr, Maven Central and `maven.ryanhcode.dev`. |
| A Mojang/Microsoft account | Only needed if you want to log in to a real account in the test client. Offline/dev mode works for physics testing. |

You do **not** need to install Minecraft, NeoForge, Create, Sable, Flywheel, Veil
or Sable Companion manually. `build.gradle` declares them, and
`gradle.properties` pins the versions:

```
minecraft_version        1.21.1
neo_version              21.1.228
create_version           6.0.10-281:slim
sable_version            2.0.6
sable_companion_version  1.6.0
ponder_version           1.0.82
flywheel_version         1.0.6
registrate_version       MC1.21-1.3.0+67
```

The Gradle wrapper jar is committed at `gradle/wrapper/gradle-wrapper.jar` and
`gradle-wrapper.properties` points at Gradle 9.5.0. Always use `./gradlew`
(Linux/macOS) or `gradlew.bat` (Windows) rather than a system-wide `gradle`.

## 2. Open the project in IntelliJ IDEA

1. Clone and enter the repository:

   ```bash
   git clone https://github.com/GoShadowS/create-trains-sable.git
   cd create-trains-sable
   ```

2. In IDEA, choose **File > Open** and select the repository root (the folder
   that contains `build.gradle`). If IDEA asks whether to trust the project,
   accept. When asked how to open it, choose **Open as Project**, not "Open as
   File".

3. In the **Gradle** tool window, make sure **Use Gradle wrapper** is selected in
   the Gradle settings, and that the Gradle JVM is JDK 21 (or "Project SDK"
   pointing at one).

4. Wait for the first Gradle sync. This is the long one: Gradle downloads the
   plugin `net.neoforged.moddev` 2.0.141, then NeoForm produces the Minecraft
   artifacts, then all mod dependencies are resolved. Watch the **Build** tool
   window. A sync that ends with `BUILD SUCCESSFUL` is what you want.

If IDEA offers to use a newer Gradle or a different JDK, decline. The versions in
this repository were chosen to match Sable 2.0.6, which was built against
NeoForge 21.1.228.

## 3. The task that creates the run configurations

The plugin is `net.neoforged.moddev` (ModDevGradle), declared at the top of
`build.gradle`:

```groovy
plugins {
    id 'java-library'
    id 'idea'
    id 'net.neoforged.moddev' version '2.0.141'
}
```

ModDevGradle registers a Gradle task named **`neoForgeIdeSync`** (group
`mod development/internal`). IDEA runs that task automatically at the end of a
Gradle project sync. It writes the argument files under `build/` and creates the
IntelliJ application run configurations from the `neoForge { runs { ... } }`
block. You should see these three entries in the run configuration dropdown:

| IntelliJ run configuration | Gradle task | Game directory |
| --- | --- | --- |
| `Create Trains Sable: Client` | `runClient` | `runs/client` |
| `Create Trains Sable: Server` | `runServer` | `runs/server` |
| `Create Trains Sable: Game Tests` | `runGameTestServer` | `runs/gametest` |

If the dropdown is empty, open the Gradle tool window, find the task
`neoForgeIdeSync` under **mod development/internal**, run it, then reload the
Gradle project (the circular arrows at the top of the Gradle tool window). The
task must run while IDEA is performing a Gradle sync, so a manual invocation is
only a diagnostic; reloading the project is what actually creates the entries.

These are Gradle tasks from the command line too: `./gradlew runClient` starts the
same client without IDEA.

## 4. Start the test client with one click

1. Select **`Create Trains Sable: Client`** in the run configuration dropdown.
2. Press the green **Run** button (Shift+F10).

ModDevGradle passes a `MOD_CLASSES` argument that points at this project's
compiled classes and resources, so the add-on is loaded directly from your
source set. `Create`, `Ponder`, `Registrate`, `Flywheel`, `Sable`, Veil and the
bundled Sable Companion are on the run classpath through the `implementation`,
`localRuntime` and `jarJar` configurations. Nothing is copied into
`runs/client/mods`.

On the first launch Minecraft downloads its assets into `runs/client`. When the
main menu appears:

1. Create a new world in Creative mode. Cheats can stay on.
2. Open the log console in IDEA and check the startup lines for
   `Create Trains Sable ready; physics backend: Sable / Rapier (server
   authoritative, Sable pose sync)`. If it says `degraded (vanilla Create)`,
   Sable did not load and no physics will run.
3. Check that Create, Sable and this add-on appear in the mod list.

For a dedicated server run, use `Create Trains Sable: Server`. NeoForge may ask
you to accept `eula.txt` in `runs/server` the first time.

## 5. What to build in the test world

A minimal physics test needs:

- A track loop with one clearly curved section. A turn with a radius of about
  8 to 12 blocks makes the curve criterion easy to reach.
- A train station block on a straight section, connected to the track.
- A small train assembled at the station: two bogeys, a railway casing body, a
  train controls block, and optionally a chest or fluid tank to check that
  block-entity data is copied into the wreck.
- A schedule or manual controls to accelerate the train.

Then set `conversionMode = "ALWAYS"` in
`runs/client/config/createtrainssable-server.toml` and restart the world, or keep
`THRESHOLD` and lower `cantReliefFactor` / `flangeCoefficient` / `overspeedGrace`
until the train derails on the curve. `ALWAYS` is the faster way to confirm that
the conversion pipeline works at all.

Drive the train into the curve at full speed. Watch the IDEA console for these
lines (the run configuration sets the log level to DEBUG):

```
Create Trains Sable ready; physics backend: Sable / Rapier (server authoritative, Sable pose sync)
Train <uuid> handed to Rapier: trigger=CURVE_OVERSPEED, speed=... m/s, bodies=...
Converted carriage 0 of train <uuid> into sub-level <uuid>
Released sub-level <uuid> (RESYNC)
```

The `[CTS]` prefix appears when `showRapierHitboxes = true`; it prints each
body's position, speed, angular speed and tilt every 20 ticks. Sable's own
commands (for example `/sable`) list the active sub-levels if you want to inspect
the wreck from inside the game. The full log is saved at
`runs/client/logs/latest.log`.

If the train stays on the rails, check in order: `sable` is loaded, the config is
enabled, the trigger thresholds are low enough, and the console is not printing
`Physics is enabled but no Sable backend is available`. The monitor only sees
trains whose `Train.graph` is not null, so a train that Create itself already
marked as derailed will not produce a `CURVE_OVERSPEED` line.

## 6. Iterating on the code

- **Java changes:** stop the client and press Run again. IntelliJ recompiles the
  changed classes before launch because the run configuration depends on the
  Gradle-prepared argument files and the compiled source-set output. A full
  restart takes roughly 20 to 60 seconds after the first build.
- **Config changes:** `config/createtrainssable-server.toml` is read on every
  server tick, so you can edit it while the game runs and watch the behaviour
  change. NeoForge may re-write the file on reload; edit the values, not the
  comments.
- **Resources** (lang, datapack JSON, `neoforge.mods.toml`, mixin config): rebuild
  the resources with `./gradlew processResources` and restart the client.
  Datapack definitions are read by Sable when the world loads; use `/reload` in
  game for the datapack files.
- **`build.gradle` or `gradle.properties` changes:** reload the Gradle project.
  Repeat the `neoForgeIdeSync` step if run configurations disappear.

Hot reload is limited. Attaching the debugger and using **Run > Debugging Actions
> Reload Changed Classes** can replace method bodies in a running game. It cannot
add or remove fields, change method signatures, alter annotations, or reapply a
mixin. Sable's Rapier natives and Minecraft's transforming class loader do not
support structural redefinition. Treat a restart as the normal workflow and use
HotSwap only for quick changes to a calculation.

## 7. Tests and jars

```bash
./gradlew test                 # JUnit 5 physics tests, no game launch needed
./gradlew build                # compile + test + jar
./gradlew runGameTestServer    # game tests, if any have been added
```

The result of `build` is `build/libs/createtrainssable-0.1.0.jar`. The
`jarJar` configuration embeds Sable Companion 1.6.0 in that jar. To install it
in a normal Minecraft instance, copy the jar into `mods/` alongside Create and
Sable. The development client does not need that step.

## 8. When something breaks, check these first

**Gradle sync fails**

- Is the Gradle JVM a JDK 21? `Help > About` in IDEA and `java -version` on the
  command line. A JDK 8/17 selected for Gradle produces obscure toolchain
  errors.
- Is `maven.ryanhcode.dev` reachable? Sable, Sable Companion and the Rapier
  native artifact come from that maven. A 502/503 or a corporate proxy shows up
  as "Could not resolve `dev.ryanhcode.sable:sable-common-1.21.1:2.0.6`".
- Did you change a version in `gradle.properties`? Sable 2.0.6 expects
  Sable Companion 1.6.0 and Create 6.0.10.x. Sable's own `mods.toml` rejects
  newer companion builds, and Create 6.0.11 changes some APIs.
- Try `./gradlew --refresh-dependencies --stacktrace`. Then
  `./gradlew clean` if cached transformed jars are stale. Delete `build/` and
  `.gradle/` only as a last resort; that forces the full NeoForm decompilation
  again.
- Out of memory during NeoForm: increase `org.gradle.jvmargs` in
  `gradle.properties` from `-Xmx3G` to `-Xmx4G`.
- Duplicate Create jar in the run: this project uses the `:slim` classifier and
  excludes Create from Sable's transitive runtime dependencies. If you add
  another mod dependency, check `./gradlew dependencies --configuration runtimeClasspath`.

**`runClient` or the IDEA run configuration does not start**

- The run configuration is missing: re-run the Gradle sync so
  `neoForgeIdeSync` executes. Do not create a plain "Application" run config by
  hand; it will not have the NeoForge argument files.
- `ClassNotFoundException` for mixin classes: make sure the
  `org.spongepowered:mixin` compile dependency was resolved and that
  `createtrainssable.mixins.json` sits in `src/main/resources`.
- A mixin apply error: `TrainMixin` targets `com.simibubi.create.content.trains.entity.Train#crash()V`
  with `remap = false`. If Create changed that method, update the injection
  target rather than disabling the mixin config.
- A crash before the main menu mentioning sodium, iris or Veil: those are Sable
  dependencies. Use the versions listed by Sable 2.0.6, and do not add OptiFine.
- A crash about Rapier natives on startup: the
  `dev.ryanhcode.sable:sable-sable_rapier-1.21.1:2.0.6` artifact contains the
  platform-specific native library. Check that it was resolved and that your
  OS/architecture is supported by Sable.
- The game starts but no physics runs: read the `Create Trains Sable ready;
  physics backend:` line. A degraded backend means the `sable` mod id was not
  loaded or Companion's default implementation was still active.
- `runs/client` is stuck in a bad state (broken world, bad config): close the
  game and delete that folder. It is generated and ignored by Git.

**Physics does not fire**

- `enabled = false` or `conversionMode = "DISABLED"` in the server config.
- The train has `Train.graph == null` (Create already derailed it) or its
  carriages have no valid travelling points yet.
- Curve radius samples are degenerate. The monitor needs three distinct
  leading-bogey positions, so a stationary or very slow train never produces a
  radius.
- Thresholds are too generous: `safeSpeed = sqrt(accelerationLimit * radius)`,
  where `accelerationLimit = cantReliefFactor * min(g * halfGauge / cogHeight,
  g * flangeCoefficient)`. Lower `cantReliefFactor` or `flangeCoefficient` to
  make derailments happen sooner.
- The conversion is capped at `maxActiveTrains` simultaneous sessions. Release
  or abandon old wrecks before testing more trains.

The `physics` unit tests are the fastest way to check a threshold change: they
run in seconds and do not need Minecraft. Use them before launching the client.

## 9. Where the generated files live

| Path | Content | In Git |
| --- | --- | --- |
| `build/` | compiled classes, generated argument files, the jar | no |
| `runs/client/`, `runs/server/`, `runs/gametest/` | game directories with worlds and configs | no |
| `gradle/wrapper/gradle-wrapper.jar` | the wrapper binary | yes, intentionally |
| `src/main/resources/` | mod metadata, lang and datapack definitions | yes |

If you are about to open a pull request, read [CONTRIBUTING.md](CONTRIBUTING.md)
for the branch and commit conventions.
