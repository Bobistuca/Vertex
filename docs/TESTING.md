# Testing and platform verification

## Unit tests

```bash
./gradlew :core:test      # server-independent tests (no server API needed)
./gradlew build           # also compiles the plugin against Spigot/Paper APIs and shades Vertex.jar
```

The build needs network access to Maven Central, the Spigot snapshot repository
(`hub.spigotmc.org`), the PaperMC repository (`repo.papermc.io`) and the CodeMC repository (`repo.codemc.io`).
If you only have Maven Central, set `VERTEX_MAVEN_MIRROR` to a Central mirror and run `:core:test`.

## Smoke tests on real servers

The `Smoke` workflow (`.github/workflows/smoke.yml`, started with *Run workflow*) tests every combination of
CraftBukkit, Spigot and Paper with Minecraft 26.1.2, 26.2 and 26.3:

1. `Vertex.jar` and the test-only helper plugin `VertexSmokeHelper` (never shipped) are built.
2. CraftBukkit and Spigot are built with Spigot's official **BuildTools** (`--rev <version> --compile craftbukkit,spigot`);
   Paper is downloaded from the official **PaperMC download API**. Nothing is committed to the repository.
3. `smoke/scripts/run_smoke.py` starts the server with RCON enabled and checks, on the real process:
   plugin load and enable, absence of load errors, packet-listener state (`/vertex status`), the commands and the
   `/vx` alias, localized output, aggregation (`2x` exactly once) and duplicate suppression through the real
   pipeline, safe reload (invalid file refused, previous configuration kept, language switch), evidence
   persistence with accurate totals, clean shutdown and absence of Vertex stack traces.
4. On Minecraft **26.1.x only**, a scripted protocol client (mineflayer, test-only) joins and checks permissions
   (ordinary players refused, Creative players can toggle their alerts but cannot use administrative commands,
   operators can), alert delivery rules (Creative receives, Survival non-operator does not, operator does) and that
   a real malformed packet sent by the client is observed by the packet listener. No public protocol test client
   supports 26.2/26.3 yet; those cells report the check as `skipped`, never as passed.
5. Results are published as the `smoke-results` branch and in the run summary.

### Reproducing locally

```bash
# 1. Build Vertex.jar and the helper
./gradlew :plugin:shadowJar :smoke:helper:jar

# 2. Obtain a server (examples)
java -jar BuildTools.jar --rev 26.1.2 --compile craftbukkit,spigot --output-dir servers
curl -L "$(curl -s https://fill.papermc.io/v3/projects/paper/versions/26.2/builds/latest | jq -r '.downloads["server:default"].url')" -o paper-26.2.jar

# 3. Run the smoke test (Python 3, Java 25; add --bot-version 26.1.2 after `npm install` in smoke/scripts)
python3 smoke/scripts/run_smoke.py --impl paper --version 26.2 --server-jar paper-26.2.jar \
  --vertex-jar plugin/build/libs/Vertex.jar --helper-jar smoke/helper/build/libs/VertexSmokeHelper.jar \
  --workdir /tmp/vertex-smoke --out /tmp/vertex-smoke-result.json
```

The script accepts the Minecraft EULA on your behalf by writing `eula=true`; only run it if you accept
https://aka.ms/MinecraftEULA.
