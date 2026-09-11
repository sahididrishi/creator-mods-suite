# CONTRACT.md — the rules for the 8 parallel feature agents

Single source of truth for everyone working on `creator-mods`. If this file and a plan in
`../plans/` disagree about *where a file goes* or *which API to call*, **this file wins**; the plans
win on *what the feature should do*.

Repo: `/Users/pega/developer/Minecraft/creator-mods`
Mod id: `creatormods` · Group: `dev.riftal` · MC 1.21.1 · Java 21 · Fabric + NeoForge.

---

## 0. The three rules that matter most

1. **Do not run Gradle.** Not `./gradlew build`, not `runClient`, not `compileJava`. One build agent
   owns the daemon and the build directories; eight agents running Loom/ModDevGradle concurrently
   corrupt each other's caches and burn ten minutes each. Write code, re-read it, hand it over.
2. **Only touch the files your row in §2 lists.** Everything else is shared. A one-line "helpful"
   edit to `fabric.mod.json` is a merge conflict for seven other people.
3. **Everything you need is already registered.** Your mixin config, your lang file, your resource
   namespace, your GameTest stubs and your `<Cap>Feature` entry in `CreatorMods.FEATURES` all exist
   and are wired in. You never add a file to a shared list.

---

## 1. Build and test commands (verified working)

The build agent runs these. They are listed so you know what your code has to survive.

```bash
cd /Users/pega/developer/Minecraft/creator-mods
export JAVA_HOME=$(/usr/libexec/java_home -v 21)      # Temurin 21.0.8

./gradlew build                        # 3 modules + JUnit + jars       (~4 s warm, ~2.5 min cold)
./gradlew :common:test                 # JUnit 5 unit tests             (~2 s)
./gradlew :fabric:runGameTest          # Fabric GameTest server         (~7 s warm)
./gradlew :neoforge:runGameTestServer  # NeoForge GameTest server       (~8 s warm)
./gradlew clean build                  # also green
```

Dev runs (configured, for the human): `:fabric:runClient`, `:fabric:runServer`,
`:neoforge:runClient`, `:neoforge:runServer`, `:neoforge:runData`.

Shipping jars land at `fabric/build/libs/` and `neoforge/build/libs/`.
`common/build/libs/` is build-time only — never ship it.

---

## 2. File ownership

`<id>` is your feature id; `<Cap>` is it capitalised. **Create or edit only these paths.**

| # | id | `<Cap>` | namespace | mixin config |
|---|----|---------|-----------|--------------|
| 1 | `toolkit`  | `Toolkit`  | `creator_toolkit`  | `creatormods-toolkit.mixins.json` |
| 2 | `colossus` | `Colossus` | `creator_colossus` | `creatormods-colossus.mixins.json` |
| 3 | `powers`   | `Powers`   | `creator_powers`   | `creatormods-powers.mixins.json` |
| 4 | `rules`    | `Rules`    | `creator_rules`    | `creatormods-rules.mixins.json` |
| 5 | `evolve`   | `Evolve`   | `creator_evolve`   | `creatormods-evolve.mixins.json` |
| 6 | `events`   | `Events`   | `creator_events`   | `creatormods-events.mixins.json` |
| 7 | `arsenal`  | `Arsenal`  | `creator_arsenal`  | `creatormods-arsenal.mixins.json` |
| 8 | `vault`    | `Vault`    | `creator_vault`    | `creatormods-vault.mixins.json` |

### Paths you own (create freely, edit freely)

```
common/src/main/java/dev/riftal/creator/features/<id>/**            # all your logic
common/src/test/java/dev/riftal/creator/features/<id>/**            # your JUnit tests
common/src/main/resources/creatormods-<id>.mixins.json              # append class names only
common/src/main/resources/assets/creator_<id>/**                    # lang, textures, models, sounds.json
common/src/main/resources/data/creator_<id>/**                      # recipes, loot, tags, structures
fabric/src/main/java/dev/riftal/creator/features/<id>/**            # Fabric-only glue, if any
fabric/src/gametest/java/dev/riftal/creator/features/<id>/**        # Fabric GameTest stubs
neoforge/src/main/java/dev/riftal/creator/features/<id>/**          # NeoForge-only glue, if any
```

Two files in those trees already exist and are yours to fill in:

* `common/.../features/<id>/<Cap>Feature.java` — the four lifecycle methods.
* `common/.../features/<id>/gametest/<Cap>GameTests.java` — GameTest bodies.

### Files NOBODY may edit

Ask the build agent if you think one of these genuinely has to change. Do not edit it yourself.

```
build.gradle  settings.gradle  gradle.properties  gradlew  gradle/**  buildSrc/**
common/build.gradle   fabric/build.gradle   neoforge/build.gradle
common/src/main/java/dev/riftal/creator/Constants.java
common/src/main/java/dev/riftal/creator/CreatorMods.java          # does not exist; see core/CreatorMods.java
common/src/main/java/dev/riftal/creator/core/**                   # the whole core library
common/src/main/java/dev/riftal/creator/platform/**
common/src/main/java/dev/riftal/creator/gametest/**
common/src/main/java/dev/riftal/creator/mixin/**
common/src/main/resources/creatormods.mixins.json
common/src/main/resources/pack.mcmeta
common/src/main/resources/creatormods.png
common/src/main/resources/assets/creatormods/**
common/src/main/resources/data/creatormods/**
common/src/test/java/dev/riftal/creator/ConstantsTest.java
common/src/test/java/dev/riftal/creator/core/**
fabric/src/main/resources/fabric.mod.json                         # <- all 10 mixin configs already listed
fabric/src/main/resources/creatormods.fabric.mixins.json
fabric/src/main/resources/META-INF/services/**
fabric/src/gametest/resources/fabric.mod.json                     # <- all 9 test classes already listed
fabric/src/main/java/dev/riftal/creator/CreatorModsFabric*.java
fabric/src/main/java/dev/riftal/creator/core/**
fabric/src/main/java/dev/riftal/creator/platform/**
neoforge/src/main/resources/META-INF/neoforge.mods.toml           # <- all 10 mixin configs already listed
neoforge/src/main/resources/creatormods.neoforge.mixins.json
neoforge/src/main/resources/META-INF/services/**
neoforge/src/main/java/dev/riftal/creator/CreatorModsNeoForge.java
neoforge/src/main/java/dev/riftal/creator/core/**
neoforge/src/main/java/dev/riftal/creator/platform/**
CONTRACT.md  README.md  LICENSE  .gitignore  .gitattributes
```

One more shared file, flagged because it is tempting: **`common/src/generated/resources/`** is
datagen output (`./gradlew :neoforge:runData`). Do not hand-write files there; write the provider in
your own package and let the build agent run datagen.

---

## 3. Your feature's lifecycle

`CreatorMods` holds all eight features and calls, in this order, **only for features enabled in
`config/creatormods.json`**:

```java
public interface Feature {
    String id();                                  // "toolkit"
    default String namespace();                   // "creator_toolkit"
    default ResourceLocation rl(String path);     // creator_toolkit:<path>
    default Component displayName();              // feature.creator_toolkit.name
    default <T> Registrar<T> registrar(ResourceKey<Registry<T>> registry);

    default void registerContent() {}   // 1. declare content. BOTH sides. During mod construction.
    void initCommon();                  // 2. wiring.          BOTH sides.
    default void initClient() {}        // 3. client only.     HUD, renderers, key mappings.
    default void initServer() {}        // 4. dedicated server only.
}
```

Hard rules:

* **All declaring happens in `registerContent()`.** `Registrar`, `PlayerData.register`,
  `Payloads.register*`, `EntityAttributes.register`, `CommandHelper.register`. On NeoForge these are
  flushed inside mod-bus events that fire straight after construction — declare later and your
  content silently does not exist.
* **Never touch a client-only class outside `initClient()`.** `HudLayers`, `HudText`,
  `ClientRenderers`, anything under `net.minecraft.client.*`. Loading one of those on a dedicated
  server is a hard `NoClassDefFoundError`. `initClient()` is idempotent — NeoForge calls it from
  several registration events whose order is not fixed.
* **Never read a `RegistryEntry` from a static initialiser.** `RegistryEntry.get()` throws until the
  loader has flushed. Store the entry, call `.get()` inside a method body.
* A feature that throws in any phase is logged and skipped; the other seven keep running. Do not
  rely on that — it is a safety net, not a design.

---

## 4. The core API, with a compiling example of each

Everything below is in `dev.riftal.creator.core`. Every snippet is copied from a file that was
compiled against this repo.

### 4.1 Blocks, items, entity types, sounds, particles — `core.registry.Registrar`

```java
private final Registrar<Block> blocks = registrar(Registries.BLOCK);
private final Registrar<Item> items = registrar(Registries.ITEM);
private final Registrar<EntityType<?>> entityTypes = registrar(Registries.ENTITY_TYPE);
private final Registrar<SoundEvent> sounds = registrar(Registries.SOUND_EVENT);
private final Registrar<ParticleType<?>> particles = registrar(Registries.PARTICLE_TYPE);

public final RegistryEntry<Block> altar =
        blocks.register("altar", () -> new Block(BlockBehaviour.Properties.of().strength(3.0F)));

public final RegistryEntry<Item> altarItem =
        items.register("altar", () -> new BlockItem(altar.get(), new Item.Properties()));

public final RegistryEntry<EntityType<Keeper>> keeper =
        entityTypes.register("keeper", () -> EntityType.Builder
                .<Keeper>of(Keeper::new, MobCategory.MONSTER)   // the <Keeper> witness is required
                .sized(0.9F, 2.4F)
                .build("keeper"));

public final RegistryEntry<SoundEvent> roar =
        sounds.register("roar", () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(NAMESPACE, "roar")));

public final RegistryEntry<SimpleParticleType> ash =
        particles.register("ash", () -> new SimpleParticleType(false) {});  // ctor is protected
```

`Registrar` works for *every* vanilla registry key in `net.minecraft.core.registries.Registries`:
block entity types, mob effects, creative tabs, recipe serializers, menu types, structure types,
attributes, and so on. Signatures:

```java
static <T> Registrar<T> of(ResourceKey<Registry<T>> registryKey, String namespace);
<R extends T> RegistryEntry<R> register(String path, Supplier<R> value);

final class RegistryEntry<T> implements Supplier<T> {
    ResourceLocation id();
    boolean isBound();
    T get();            // throws before the flush
}
```

**Entity attributes** (`core.registry.EntityAttributes`) — mandatory for any custom `LivingEntity`:

```java
EntityAttributes.register(keeper, () -> Monster.createMonsterAttributes()
        .add(Attributes.MAX_HEALTH, 300.0D)
        .add(Attributes.MOVEMENT_SPEED, 0.25D));
```

### 4.2 Commands — `core.command.CommandHelper` + `SilentMode`

```java
CommandHelper.register(dispatcher -> dispatcher.register(CommandHelper.op("toolkit")
        .then(CommandHelper.literal("take")
                .then(CommandHelper.arg("seconds", IntegerArgumentType.integer(1, 600))
                        .executes(ctx -> {
                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                            TickScheduler.runLater(seconds * 20, () -> { /* cut */ });
                            return CommandHelper.success(ctx.getSource(),
                                    Component.translatable("commands.creator_toolkit.take", seconds));
                        })))));
```

```java
static LiteralArgumentBuilder<CommandSourceStack> op(String name);            // requires level 2
static LiteralArgumentBuilder<CommandSourceStack> op(String name, int level);
static LiteralArgumentBuilder<CommandSourceStack> literal(String name);
static <T> RequiredArgumentBuilder<CommandSourceStack, T> arg(String name, ArgumentType<T> type);
static void register(Consumer<CommandDispatcher<CommandSourceStack>> registrar);

static int feedback(CommandSourceStack source, Component message);  // chat, or action bar if silent
static int success (CommandSourceStack source, Component message);  // feedback(), green
static int error   (CommandSourceStack source, Component message);  // red, returns 0
```

**Never call `source.sendSuccess(msg, true)`.** The `true` broadcasts `[Player: ...]` to every other
op — that is exactly the chat noise this suite exists to remove. `feedback`/`success`/`error` route
through `SilentMode`, which the human toggles with `/creator silent true`.

Root command name = your feature's own word (`/toolkit`, `/colossus`, `/power`, `/rule`, `/evolve`,
`/event`, `/arsenal`, `/vault`). `/creator` belongs to core — do not add nodes under it.

### 4.3 HUD — `core.hud.HudLayers` + `HudText` (client only)

```java
@Override
public void initClient() {
    HudLayers.register(rl("take_timer"), (graphics, delta) -> {
        if (HudLayers.hudHidden()) return;       // always honour F1
        HudText.drawShadowed(graphics, "REC 0:12", 8, 8, 0xFFFF5555);
    });
}
```

```java
static void register(ResourceLocation id, HudLayer layer);                       // above the whole HUD
static void registerBelow(ResourceLocation vanillaLayer, ResourceLocation id, HudLayer layer);
static boolean hudHidden();

// HudText
static void drawShadowed(GuiGraphics g, String text, int x, int y, int argb);
static void drawShadowed(GuiGraphics g, Component text, int x, int y, int argb);
static void drawCentered(GuiGraphics g, String text, int x, int y, int argb);
static int  width(String text);
static void drawBar(GuiGraphics g, int x, int y, int w, int h, float progress, int bgArgb, int fillArgb);
```

`HudLayer` is vanilla `LayeredDraw.Layer`: `render(GuiGraphics, DeltaTracker)`.
NeoForge maps it to `RegisterGuiLayersEvent#registerAboveAll`; Fabric to `HudRenderCallback.EVENT`
(1.21.1 has **no** `HudElementRegistry` — that API arrived in a later Fabric line). Fabric therefore
ignores relative ordering: layers draw in registration order. Do not depend on ordering across
features.

### 4.4 Client renderers — `core.client.ClientRenderers` (client only)

```java
@Override
public void initClient() {
    ClientRenderers.entityRenderer(keeper, KeeperRenderer::new);          // GeckoLib renderers too
    ClientRenderers.modelLayer(KEEPER_LAYER, KeeperModel::createBodyLayer);
}
```

```java
static <T extends Entity> void entityRenderer(Supplier<? extends EntityType<T>> type,
                                              EntityRendererProvider<T> provider);
static void modelLayer(ModelLayerLocation location, Supplier<LayerDefinition> definition);
```

### 4.5 Payloads — `core.net.Payloads`

```java
public record StartTake(int seconds) implements CustomPacketPayload {
    public static final Type<StartTake> TYPE = Payloads.type(NAMESPACE, "start_take");
    public static final StreamCodec<RegistryFriendlyByteBuf, StartTake> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, StartTake::seconds, StartTake::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

// registerContent(), both sides:
Payloads.registerC2S(StartTake.TYPE, StartTake.CODEC, (payload, sender) -> {
    if (payload.seconds() < 1 || payload.seconds() > 600) return;   // ALWAYS validate
    takes.update(sender, n -> n + 1);
});

// initClient():
Payloads.registerS2C(StartTake.TYPE, StartTake.CODEC, payload -> { /* client cache */ });
```

```java
static <T extends CustomPacketPayload> Type<T> type(String namespace, String path);
static <T extends CustomPacketPayload> void registerC2S(Type<T>, StreamCodec<? super RegistryFriendlyByteBuf,T>, ServerHandler<T>);
static <T extends CustomPacketPayload> void registerS2C(Type<T>, StreamCodec<? super RegistryFriendlyByteBuf,T>, ClientHandler<T>);
static void sendToServer(CustomPacketPayload payload);                    // client only
static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
static void sendToAll(MinecraftServer server, CustomPacketPayload payload);
static void sendToTracking(Entity entity, CustomPacketPayload payload);   // + the entity itself
```

`ServerHandler<T>.handle(T payload, ServerPlayer sender)` runs on the server thread.
`ClientHandler<T>.handle(T payload)` runs on the client thread.
**Never trust the payload**: check distance, cooldown, permission and ownership server-side.
Register C2S in `registerContent()` (both sides) and S2C receivers in `initClient()` — the S2C
*type* is registered on both sides automatically.

### 4.6 Per-player persistence — `core.data.PlayerData<T>`

```java
public static PlayerData<Integer> takes;                 // assign in registerContent()

takes = PlayerData.register(rl("takes"), Codec.INT, () -> 0, /* copyOnDeath */ true);

int n = takes.get(player);
takes.set(player, 0);
takes.update(player, v -> v + 1);
```

```java
static <T> PlayerData<T> register(ResourceLocation id, Codec<T> codec,
                                  Supplier<T> defaultValue, boolean copyOnDeath);
T get(Entity holder);  void set(Entity holder, T value);
T update(Entity holder, UnaryOperator<T> mutator);  boolean has(Entity holder);
```

Works on any `Entity`, not just players. Backed by NeoForge `AttachmentType` / Fabric
`AttachmentRegistry`. **Nothing syncs to the client** — mirror what the HUD needs with an S2C
payload. For per-*world* state use vanilla `SavedData` inside your own package.

### 4.7 Scheduling — `core.sched.TickScheduler`

```java
TickScheduler.runLater(40, () -> level.explode(null, x, y, z, 4.0F, Level.ExplosionInteraction.NONE));
TickScheduler.runRepeating(5, 20, this::spawnRingParticle).tag(rl("meteor"));
TickScheduler.runRepeating(1, -1, task -> { if (done) task.cancel(); }).tag(rl("meteor"));
TickScheduler.cancelAll(rl("meteor"));
```

```java
static ScheduledTask runLater(int delayTicks, Runnable task);
static ScheduledTask runRepeating(int periodTicks, int times, Runnable task);       // times -1 = forever
static ScheduledTask runRepeating(int periodTicks, int times, Consumer<ScheduledTask> task);
static int cancelAll(ResourceLocation owner);
static void tickForTest(int ticks);     // unit tests
static int size();
static MinecraftServer server();

// ScheduledTask: tag(ResourceLocation) · cancel() · isDone() · remainingTicks() · remainingRuns()
```

Tasks run at end of server tick, on the server thread. **Always `.tag()`** so you can cancel your
own work without touching anyone else's. Nothing is persisted; the queue is cleared on server stop —
re-schedule from your `SavedData` on load. A task that throws is logged and cancelled.

### 4.8 Utilities — `core.util`

```java
MathUtil.boxAround(Vec3 centre, double r) · boxAround(BlockPos, double) · horizontalDistance(Vec3, Vec3)
MathUtil.look(Entity) · horizontalLook(Entity) · ring(Vec3, double, int) · randomInSphere(RandomSource, Vec3, double)
MathUtil.clamp(..) · lerp(double t, double from, double to) · formatTicks(int) -> "1:23"

Fx.particles(ServerLevel, ParticleOptions, Vec3, int count, double spread, double speed)
Fx.particleRing(ServerLevel, ParticleOptions, Vec3 centre, double radius, int points)
Fx.sound(Level, Vec3, SoundEvent, SoundSource, float volume, float pitch) · sound(Level, Vec3, SoundEvent)

Selection.around(Level, Class<T>, Vec3, double radius, Predicate<? super T>)
Selection.livingAround(Level, Vec3, double, Entity except) · playersAround(ServerLevel, Vec3, double)
Selection.nearest(Level, Class<T>, Vec3, double, Predicate<? super T>)

new CooldownTracker<String>()  →  start(key, now, ticks) · ready(key, now) · remaining(key, now)
                                  progress(key, now, fullLength) · clear(key) · clearAll()

Titles.show(ServerPlayer, Component title, Component subtitle, int fadeIn, int stay, int fadeOut)
Titles.show(ServerPlayer, Component title, Component subtitle)     // 10 / 70 / 20
```

`CooldownTracker` and `MathUtil` are pure logic — unit-test against them freely.

### 4.9 Feature toggles — `core.config.CreatorConfig`

`config/creatormods.json` is written on first run:

```json
{
  "_comment": "Set a feature to false to keep it out of the game entirely.",
  "features": { "toolkit": true, "colossus": true, "powers": true, "rules": true,
                "evolve": true, "events": true, "arsenal": true, "vault": true }
}
```

A `false` feature gets **none** of its lifecycle calls and registers nothing at all — no blocks, no
commands, no HUD, no payloads. That is the recording selling point: one key, one restart, a clean
capture of exactly one feature. `/creator features` lists the state; `/creator feature <id> <bool>`
writes the key. Read it from code with `CreatorMods.isEnabled("<id>")`.

---

## 5. Naming conventions

| Thing | Rule | Example |
|---|---|---|
| Registry id | `creator_<id>:snake_case` | `creator_vault:cursed_altar` |
| Java package | `dev.riftal.creator.features.<id>[.sub]` | `dev.riftal.creator.features.vault.block` |
| Feature class | `<Cap>Feature` | `VaultFeature` |
| Block / item translation key | `block.creator_<id>.<path>` / `item.creator_<id>.<path>` | `block.creator_vault.cursed_altar` |
| Entity translation key | `entity.creator_<id>.<path>` | `entity.creator_colossus.ashen_colossus` |
| Command feedback key | `commands.creator_<id>.<verb>` | `commands.creator_toolkit.take` |
| HUD / misc key | `hud.creator_<id>.<thing>` / `<feature>.creator_<id>.<thing>` | `hud.creator_powers.cooldown` |
| Lang file | `assets/creator_<id>/lang/en_us.json` | one file per feature, never shared |
| Block texture | `assets/creator_<id>/textures/block/<path>.png` | |
| Item texture | `assets/creator_<id>/textures/item/<path>.png` | |
| Entity texture | `assets/creator_<id>/textures/entity/<path>.png` | |
| Block model | `assets/creator_<id>/models/block/<path>.json` | |
| Blockstate | `assets/creator_<id>/blockstates/<path>.json` | |
| Sound definitions | `assets/creator_<id>/sounds.json` | one per namespace, yours alone |
| Sound file | `assets/creator_<id>/sounds/<category>/<name>.ogg` | mono, 44.1 kHz |
| Sound event id | `creator_<id>:<category>.<name>` | `creator_colossus:colossus.roar` |
| GeckoLib model | `assets/creator_<id>/geo/<path>.geo.json` | |
| GeckoLib animation | `assets/creator_<id>/animations/<path>.animation.json` | |
| Loot table | `data/creator_<id>/loot_table/<kind>/<path>.json` | 1.21 uses the **singular** folder |
| Recipe | `data/creator_<id>/recipe/<path>.json` | singular |
| Tag | `data/creator_<id>/tags/<registry>/<path>.json` | |
| Structure / GameTest template | `data/creator_<id>/structure/<name>.nbt` | singular since 1.21 |
| Payload id | `creator_<id>:<snake_case>` | `creator_powers:use_power` |
| Attachment id | `creator_<id>:<snake_case>` | `creator_evolve:stage` |
| `TickScheduler` tag | `rl("<what>")` → `creator_<id>:<what>` | `creator_events:bloodmoon` |
| Mixin class | `dev.riftal.creator.features.<id>.mixin.<Target>Mixin` | `ZombieMixin` |

Mod-level ids (`creatormods:*`) belong to core. Never register anything in the `creatormods`
namespace or in `minecraft`.

---

## 6. Mixins

Your config already exists and is already listed in `fabric.mod.json` and `neoforge.mods.toml`.
Append class names (relative to the declared package) to it and nothing else:

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "dev.riftal.creator.features.vault.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": ["ChestBlockEntityMixin"],
  "client": ["GuiMixin"],
  "server": [],
  "injectors": { "defaultRequire": 1 }
}
```

* `"mixins"` = both sides, `"client"` = client only, `"server"` = dedicated server only.
* Mixin classes live in `common`, in **your** `features/<id>/mixin` package.
* There is **no `"refmap"` key** and there must not be one: Loom ≥ 1.17 dropped the mixin annotation
  processor and remaps annotations at `remapJar` time instead.
* Loader-specific mixins (targeting Fabric API or NeoForge classes) are the exception — those go in
  the shared `creatormods.fabric.mixins.json` / `creatormods.neoforge.mixins.json`, which you may
  not edit. Ask the build agent; usually there is a non-mixin hook and you do not need one.
* Prefer an existing event/hook over a mixin. Every mixin is a porting cost for 1.20.1 later.

---

## 7. Tests

### 7.1 JUnit — `common/src/test/java/dev/riftal/creator/features/<id>/`

The game is **not** bootstrapped: Minecraft classes are on the classpath (so `ResourceLocation`,
`Component`, records and pure math work) but every registry is empty. Anything that touches
`BuiltInRegistries`, `ItemStack` or a live level throws — that belongs in a GameTest.

Working example to copy: `common/src/test/java/dev/riftal/creator/core/CooldownTrackerTest.java`.

```java
package dev.riftal.creator.features.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VaultKeyTest {

    @Test
    void keyChargesCapAtThree() {
        assertEquals(3, VaultKey.addCharge(3));
    }
}
```

Run by the build agent with `./gradlew :common:test`. JUnit 5.14.4 (Jupiter), `useJUnitPlatform()`.

### 7.2 GameTest — a body in `common`, a stub per loader (all three files already exist)

Both stub files are pre-registered: the Fabric one is listed in the `fabric-gametest` entrypoint,
the NeoForge one is auto-scanned and `creator_<id>` is already in
`neoforge.enabledGameTestNamespaces`. You just add methods.

**1. Body** — `common/src/main/java/dev/riftal/creator/features/vault/gametest/VaultGameTests.java`:

```java
public static void altarAcceptsTheKey(GameTestHelper helper) {
    BlockPos pos = new BlockPos(1, 1, 1);
    helper.setBlock(pos, VaultFeature.ALTAR.get());
    helper.succeedWhen(() -> helper.assertBlockPresent(VaultFeature.ALTAR.get(), pos));
}
```

**2. Fabric stub** — `fabric/src/gametest/java/.../VaultFabricGameTests.java`. Fabric uses
`template()` **verbatim**, so it must be a full `namespace:path`:

```java
@GameTest(template = EMPTY)                 // EMPTY = VaultFeature.NAMESPACE + ":empty"
public void altarAcceptsTheKey(GameTestHelper helper) {
    VaultGameTests.altarAcceptsTheKey(helper);
}
```

**3. NeoForge stub** — `neoforge/src/main/java/.../VaultNeoForgeGameTests.java`. The class carries
`@GameTestHolder("creator_vault") @PrefixGameTestTemplate(false)`, so the template is a bare path:

```java
@GameTest(template = "empty")
public void altarAcceptsTheKey(GameTestHelper helper) {
    VaultGameTests.altarAcceptsTheKey(helper);
}
```

`creator_<id>:empty` is a 9×9×9 polished-andesite floor and already ships in your namespace at
`common/src/main/resources/data/creator_<id>/structure/empty.nbt`. Need a bigger arena? Add another
`.nbt` next to it (`/test create <name> 20 10 20` then `/test export` in a dev client) and reference
it by name; never overwrite `empty.nbt`.

Useful `GameTestHelper` methods: `setBlock`, `assertBlockPresent`, `assertBlockState`, `spawn`,
`assertEntityPresent`, `assertEntityNotPresent`, `assertTrue`, `assertValueEqual`,
`makeMockPlayer(GameType)`, `getLevel()`, `runAfterDelay(long, Runnable)`, `runAtTickTime`,
`onEachTick`, `succeed()`, `succeedWhen(Runnable)`, `succeedOnTickWhen`, `fail(String)`,
`absolutePos`, `relativePos`, `killAllEntities`.

Keep each test under the default 100-tick timeout or set `timeoutTicks` on the annotation.

---

## 8. Definition of done for a feature agent

* Every file you touched is in your ownership list from §2.
* `<Cap>Feature` implements the phases it needs; nothing client-only is referenced outside
  `initClient()`; nothing is declared outside `registerContent()`.
* At least one JUnit test and at least one real GameTest (beyond the `featureIsEnabled` stub).
* Your lang file has a key for every translatable string you emit.
* Your mixin config lists only your own classes, and has no `"refmap"` key.
* You did **not** run Gradle. You re-read your files instead.
* Hand the build agent: the list of files you added, and anything you are unsure compiles.

---

## 9. Gotchas inherited from the scaffold

1. `@GameTest` has no `templateNamespace` on vanilla/Fabric — see §7.2 for the exact spelling per
   loader. This cost a build cycle already; do not re-derive it.
2. Loom ≥ 1.17 dropped the mixin AP and refmaps. No `"refmap"` key anywhere.
3. GameTests are deliberately not wired into `check`, so `./gradlew build` never boots a server.
4. 1.21 renamed several data folders to the singular: `structure`, `loot_table`, `recipe`,
   `advancement`, `predicate`. Getting this wrong fails silently.
5. GeckoLib 4.9.2 is available in all three modules (`software.bernie.geckolib`). Its
   `geckolib.refmap.json could not be read` warning in dev is normal and harmless.
6. `common` compiles against the merged (client + server) Mojang-mapped jar, so client classes
   *compile* there. Only `initClient()` and code it reaches may *load* them at runtime.
7. Any new key added to `gradle.properties` that a resource file references must also be added to the
   `expandProps` map in `buildSrc/src/main/groovy/multiloader-common.gradle` — build agent's job.
