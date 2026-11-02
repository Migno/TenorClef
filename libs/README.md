Pinned Ostinato Baritone jars. These are authoritative: `build.gradle` uses exactly
the jar named below for each module, and fails if it is missing or its `.sha256` does
not match. Sibling `../Ostinato/dist` directories are never read. To try another jar
without re-pinning, pass `-PostinatoJar.<module>=<path>` (for example `-PostinatoJar.26.3=...`), or `-PostinatoJar=<path>` to apply it to every module.

| File | Module | Ostinato source | `fabric.mod.json` version |
| --- | --- | --- | --- |
| `baritone-unoptimized-fabric-ostinato-26.3.jar` | 26.3 (primary) | `main` @ `1aed5cc2` (tag `v1.26.0`), `:fabric:build` unoptimized jar, built locally with the Windows x64 native library only | `1.26.0` |
| `baritone-unoptimized-fabric-ostinato-1.21.11.jar` | 1.21.11 | branch `1.21.11` @ `c622bbe4` (driver API backports #110, #113), `dist/` unoptimized jar | `1.20.0-7-gc622bbe4` |
| `baritone-unoptimized-fabric-1.21.4.jar` | 1.21.4 | branch `1.21.4` @ `d9320bcf` (driver API backports #111, #114), `dist/` unoptimized jar | `1.0.2-11-gd9320bcf` |
| `baritone-unoptimized-fabric-1.16.1.jar` | 1.16.1 | branch `1.16.1` @ `c1360347` (driver API backports #112, #115), CI artifact `baritone-unoptimized-fabric-1.16.5.jar` of run 40182679176; commit in `ostinato-1.16.1.commit` | `1.16.1-ostinato` |

Re-pin: replace the jar, run `sha256sum <jar> > <jar>.sha256`, update the table, commit
all three together. See [docs/OSTINATO_WIRING.md](../docs/OSTINATO_WIRING.md).

Do not drop a 1.21.11 Ostinato jar into a 1.21.4 TenorClef build, or the reverse.

## Vibe coding / AI use

Large parts of this repository were written or edited with AI assistants
(Claude, Grok, and similar). That is vibe coding: a person set the
direction; a model produced a lot of the text. A green CI run or a
commit message is not proof that a human understood every line.

Read the diff before you run or merge it. Do not treat this as audited
software. File bugs. Do not assume the model already considered your
case.
