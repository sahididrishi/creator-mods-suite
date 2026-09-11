**What this changes**

One or two sentences, and the issue it closes if there is one.

**Which feature**

`toolkit` / `colossus` / `powers` / `rules` / `evolve` / `events` / `arsenal` / `vault`, or
build/docs. Everything you touched must be inside that feature's paths - see
[CONTRIBUTING.md](../CONTRIBUTING.md) and CONTRACT.md section 2.

**Tested on**

- Loader(s) you actually ran the game on, with versions (e.g. NeoForge 21.1.250, Fabric Loader
  0.19.5):
- Minecraft version:
- Log or GameTest output, if something is worth showing:

**Checklist**

- [ ] `./gradlew build` is green
- [ ] `./gradlew :common:test` passes
- [ ] `./gradlew :fabric:runGameTest` and `./gradlew :neoforge:runGameTestServer` both pass
- [ ] No datagen, no `common/src/generated/`, no files under `fabric|neoforge/src/main/resources/`
- [ ] No shared or build files edited (Gradle files, `core/**`, `platform/**`, `Constants.java`)
