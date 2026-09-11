# CONTRACT.md — the rules for the 8 parallel feature agents

Single source of truth for everyone working on `creator-mods`. If this file and a plan in
`../plans/` disagree about *where a file goes* or *which API to call*, **this file wins**; the plans
win on *what the feature should do*.

Repo: `/Users/pega/developer/Minecraft/creator-mods`
Mod id: `creatormods` · Group: `dev.riftal` · MC 1.21.1 · Java 21 · Fabric + NeoForge.

---

## 0. The five rules that matter most

1. **Never call a vanilla API you have not read in the decompiled source.** Not "I'm fairly sure
   `LivingEntity` has that method." Grep it, quote it, then use it. The source tree is on this disk
   at `/Users/pega/developer/Minecraft/mc-sources/1.21.1` — §3 has the exact commands. Guessed
   vanilla signatures are the single largest source of wasted build cycles on this project.
2. **Do not run Gradle.** Not `./gradlew build`, not `runClient`, not `compileJava`. One build agent
   owns the daemon and the build directories; eight agents running Loom/ModDevGradle concurrently
   corrupt each other's caches. Write code, re-read it, run the §13 self-check, hand it over.
3. **Only touch the files your row in §2 lists.** Everything else is shared. A one-line "helpful"
   edit to `fabric.mod.json` is a merge conflict for seven other people.
4. **Everything you need is already registered.** Your mixin config, your lang file, your resource
   namespace, your GameTest stubs and your `<Cap>Feature` entry in `CreatorMods.FEATURES` all exist
   and are wired in. You never add a file to a shared list.
5. **Build the MVP, nothing else.** §12. A compiling MVP beats a half-written stretch goal every
   time, and the stretch goals are what get cut when the build agent runs out of budget.

---

## 1. Build and test commands (measured, not estimated)

The build agent runs these. They are listed so you know what your code has to survive.

```bash
cd /Users/pega/developer/Minecraft/creator-mods
export JAVA_HOME=$(/usr/libexec/java_home -v 21)      # Temurin 21.0.8

./gradlew build                        # 3 modules + JUnit + jars
./gradlew :common:test                 # JUnit 5 unit tests
./gradlew :fabric:runGameTest          # Fabric GameTest server
./gradlew :neoforge:runGameTestServer  # NeoForge GameTest server
./gradlew clean build                  # also green
```

**Measured on this machine (2026-09-11, M-series mac, Gradle 9.5, daemon stopped first):**

| Command | Time |
|---|---|
| `./gradlew clean build` (36 tasks, build cache on) | **8 s** |
| `./gradlew clean build --no-build-cache` | **11 s** |

The old "~2.5 min cold" line in this file was never measured and is now deleted. The only slow
build anyone will ever see here is the *first* one on a machine with empty Gradle caches, where
NeoForm downloads and decompiles Minecraft and Loom remaps it — a one-time cache population that
is already paid on this machine. A normal full build is seconds, so there is no excuse for batching
up twenty files before handing over.

Dev runs (configured, for the human): `:fabric:runClient`, `:fabric:runServer`,
`:neoforge:runClient`, `:neoforge:runServer`.

`:neoforge:runData` also exists in `neoforge/build.gradle`, and it is **dead weight — see §7.
Datagen is banned on this project.** No provider is registered anywhere, `common/src/generated/`
does not exist, and nobody is to create it.

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
common/src/main/resources/assets/creator_<id>/**                    # lang, textures, models, sounds
common/src/main/resources/data/creator_<id>/**                      # recipes, loot, tags, structures
common/src/main/java/dev/riftal/creator/features/<id>/ASSETS.md      # §9, mandatory if you ship art
fabric/src/main/java/dev/riftal/creator/features/<id>/**            # Fabric-only glue, if any
fabric/src/gametest/java/dev/riftal/creator/features/<id>/**        # Fabric GameTest stubs
neoforge/src/main/java/dev/riftal/creator/features/<id>/**          # NeoForge-only glue, if any
```

Two files in those trees already exist and are yours to fill in:

* `common/.../features/<id>/<Cap>Feature.java` — the four lifecycle methods.
* `common/.../features/<id>/gametest/<Cap>GameTests.java` — GameTest bodies.

### Loader resource directories are FORBIDDEN, entirely

```
fabric/src/main/resources/**       # off limits
neoforge/src/main/resources/**     # off limits
fabric/src/gametest/resources/**   # off limits
```

Only `common/src/main/resources/**` is merged into **both** loader jars (see
`buildSrc/src/main/groovy/multiloader-loader.gradle`). Those two loader resource trees contain
exactly four kinds of file — the mod metadata, the loader mixin config, the `META-INF/services`
files and nothing else — and every one of them is shared by all eight features. A texture, model,
lang file or loot table you put there ships on one loader only, which is the worst possible bug:
it works in your test and is missing in half the release. **Every resource you write goes under
`common/src/main/resources/{assets,data}/creator_<id>/`. No exceptions, not even "just this once
for a Fabric-only thing".**

### Files NOBODY may edit

Ask the build agent if you think one of these genuinely has to change. Do not edit it yourself.

```
build.gradle  settings.gradle  gradle.properties  gradlew  gradle/**  buildSrc/**
common/build.gradle   fabric/build.gradle   neoforge/build.gradle
common/src/main/java/dev/riftal/creator/Constants.java
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
fabric/src/main/resources/**                                      # ALL of it, see above
fabric/src/gametest/resources/fabric.mod.json                     # <- the 9 test classes; §11.2
fabric/src/main/java/dev/riftal/creator/CreatorModsFabric*.java
fabric/src/main/java/dev/riftal/creator/core/**
fabric/src/main/java/dev/riftal/creator/platform/**
neoforge/src/main/resources/**                                    # ALL of it, see above
neoforge/src/main/java/dev/riftal/creator/CreatorModsNeoForge.java
neoforge/src/main/java/dev/riftal/creator/core/**
neoforge/src/main/java/dev/riftal/creator/platform/**
CONTRACT.md  README.md  LICENSE  .gitignore  .gitattributes
common/src/generated/**            # does not exist; datagen is banned; do not create it
```

---

## 3. Before you use any vanilla API

**Hard rule: you may not call a `net.minecraft.*` method, constructor or field that you have not
confirmed, in this session, in the decompiled 1.21.1 source on this disk.** If you did not grep it,
you do not use it. Your plan/handover note must be able to quote the line.

This is not bureaucracy. Every previous failure on this project traces back to a method that existed
in 1.19, or in Yarn mappings, or in a blog post, and does not exist in Mojang-mapped 1.21.1.

### Where the source is

```bash
export MCSRC=/Users/pega/developer/Minecraft/mc-sources/1.21.1
```

5364 `.java` files: **vanilla Minecraft 1.21.1, Mojang mappings, Parchment parameter names**,
with `@OnlyIn(Dist.CLIENT)` markers injected by NeoForm. This is *exactly* the API surface
`:common` compiles against — it was extracted from this project's own NeoForm cache
(`~/.gradle/caches/neoformruntime/intermediate_results/transformSources_*_output.zip`).
Compiled classes for descriptor lookups: `/Users/pega/developer/Minecraft/mc-sources/minecraft-1.21.1-mojmap.jar`.

If that directory is ever missing, do **not** guess and do **not** run Gradle: tell the build agent
to re-run `./gradlew :common:createMinecraftArtifacts` and re-extract it.

### The five commands you actually need

```bash
# 1. Where does a class live?
find $MCSRC -name "LivingEntity.java"
#   /Users/pega/developer/Minecraft/mc-sources/1.21.1/net/minecraft/world/entity/LivingEntity.java

# 2. Exact signature of the method you want to call or inject into
grep -n "public boolean hurt(" $MCSRC/net/minecraft/world/entity/LivingEntity.java
#   1110:    public boolean hurt(DamageSource source, float amount) {

# 3. The whole public surface of a small class
grep -n "    public " $MCSRC/net/minecraft/server/level/ServerBossEvent.java
#   20:    public ServerBossEvent(Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
#   25:    public void setProgress(float progress) {
#   99:    public void addPlayer(ServerPlayer player) {   ... etc

# 4. Is this class client-only? (if yes it may only be touched from initClient())
grep -n "@OnlyIn(Dist.CLIENT)" $MCSRC/net/minecraft/client/gui/GuiGraphics.java | head -1

# 5. How does vanilla itself use it? (best answer to "what are the arguments")
grep -rn "\.setProgress(" $MCSRC/net/minecraft --include=*.java | head
```

### JVM descriptors for mixins

`@Inject(method = ...)` needs the descriptor spelled exactly right. Read it, never invent it:

```bash
javap -p -s -cp /Users/pega/developer/Minecraft/mc-sources/minecraft-1.21.1-mojmap.jar \
      net.minecraft.world.entity.LivingEntity | grep -A2 "boolean hurt"
#   public boolean hurt(net.minecraft.world.damagesource.DamageSource, float);
#     descriptor: (Lnet/minecraft/world/damagesource/DamageSource;F)Z
```

### Things this grep would have told you (all verified in that tree)

* `new ResourceLocation("ns","path")` — **gone**, the constructor is private.
  `ResourceLocation.fromNamespaceAndPath(ns, path)` (line 61 of `ResourceLocation.java`).
* `ItemStack#getOrCreateTag/getTag/hasTag/setTag` — **gone**, zero hits in `ItemStack.java`.
  Per-stack state is a `DataComponentType`: `public <T> T set(DataComponentType<? super T>, T)`.
* `Item.Properties#registryKey(...)` / `setId(...)` — **does not exist in 1.21.1** (that is 1.21.2+).
  Zero hits. If a snippet you are copying has it, the snippet is from the wrong version.
* `MobEffectInstance` takes a **`Holder<MobEffect>`**, not a `MobEffect`:
  `public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier)`. Get the holder
  with `BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect)` (`Registry#wrapAsHolder`, line 151).
* `EntityType.Builder#build` takes the registry path string: `public EntityType<T> build(String key)`.
* `Level#explode(@Nullable Entity, double, double, double, float, Level.ExplosionInteraction)` — one
  of six overloads; pick the one whose parameters you can see.

Same rule applies to GeckoLib (`software.bernie.geckolib`, compile-only in all three modules): if
you cannot point at the signature, do not call it.

---

## 4. Your feature's lifecycle

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
  content silently does not exist. Registries freeze early in 1.21; there is no second chance.
* **Never touch a client-only class outside `initClient()`.** `HudLayers`, `HudText`,
  `ClientRenderers`, anything under `net.minecraft.client.*`. Loading one of those on a dedicated
  server is a hard `NoClassDefFoundError`. It compiles fine in `common` (§15.6) — only a dedicated
  server catches it, and we do not boot one per feature. `initClient()` is idempotent — NeoForge
  calls it from several registration events whose order is not fixed.
* **Never read a `RegistryEntry` from a static initialiser.** `RegistryEntry.get()` throws until the
  loader has flushed. Store the entry, call `.get()` inside a method body.
* **No config value may change *what* you register.** Reading config during registration is a
  startup NPE on NeoForge and a client/server desync everywhere else. Register unconditionally;
  branch on config at *runtime*. (The whole feature being disabled is handled for you — a disabled
  feature gets no lifecycle calls at all.)
* A feature that throws in any phase is logged and skipped; the other seven keep running. Do not
  rely on that — it is a safety net, not a design.

### Logging

There is exactly **one** logger in this mod:

```java
import static dev.riftal.creator.Constants.LOG;

LOG.info("[vault] altar primed at {}", pos);
```

* **Never call `LoggerFactory.getLogger(...)`.** Not in your feature, not in a mixin, not "just for
  debugging". Eight private loggers make the log the human records unreadable.
* Prefix every message with `[<your feature id>]` so the human can filter on camera.
* SLF4J `{}` placeholders, never string concatenation. `LOG.debug` for anything chatty.
* **Never log inside a tick loop, an entity tick, or a render call.** Not even at debug.

---

## 5. The core API, with a compiling example of each

Everything below is in `dev.riftal.creator.core`. Every snippet is copied from a file that was
compiled against this repo.

### 5.1 Blocks, items, entity types, sounds, particles — `core.registry.Registrar`

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

### 5.2 Commands — `core.command.CommandHelper` + `SilentMode`

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

### 5.3 HUD — `core.hud.HudLayers` + `HudText` (client only)

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

### 5.4 Client renderers — `core.client.ClientRenderers` (client only)

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

### 5.5 Payloads — `core.net.Payloads`

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
**Never trust the payload**: a C2S packet carrying a `BlockPos`, a slot index, an entity id or an
amount is attacker-controlled. Re-check distance, cooldown, permission, ownership and numeric bounds
server-side, every time. Register C2S in `registerContent()` (both sides) and S2C receivers in
`initClient()` — the S2C *type* is registered on both sides automatically.

### 5.6 Per-player persistence — `core.data.PlayerData<T>`

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
payload. For per-*world* state use vanilla `SavedData` inside your own package. Stored values must
be **immutable** (records, or copy-on-write) — mutating a stored value in place is the classic
"my data doesn't save" bug.

### 5.7 Scheduling — `core.sched.TickScheduler`

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

Use it instead of per-tick scans. No feature may run an entity/chunk scan every tick: schedule it
every 5, 10 or 20 ticks, or drive it from an event. Anything that shows up in a spark profile is a
bug against this contract.

### 5.8 Utilities — `core.util`

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

Sounds and particles are spawned from the **logical server** (`Fx.*` already does this). A
`level.playSound`/`addParticle` call guarded by `level.isClientSide` only reaches the host — the
classic "works in single-player, silent for everyone else" bug.

### 5.9 Feature toggles — `core.config.CreatorConfig`

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

## 6. Naming conventions

| Thing | Rule | Example |
|---|---|---|
| Registry id | `creator_<id>:snake_case` | `creator_vault:cursed_altar` |
| Java package | `dev.riftal.creator.features.<id>[.sub]` | `dev.riftal.creator.features.vault.block` |
| Feature class | `<Cap>Feature` | `VaultFeature` |
| Block / item translation key | `block.creator_<id>.<path>` / `item.creator_<id>.<path>` | `block.creator_vault.cursed_altar` |
| Entity translation key | `entity.creator_<id>.<path>` | `entity.creator_colossus.ashen_colossus` |
| Creative tab translation key | `itemGroup.creator_<id>` | `itemGroup.creator_vault` |
| Command feedback key | `commands.creator_<id>.<verb>` | `commands.creator_toolkit.take` |
| HUD / misc key | `hud.creator_<id>.<thing>` / `<feature>.creator_<id>.<thing>` | `hud.creator_powers.cooldown` |
| Lang file | `assets/creator_<id>/lang/en_us.json` | one file per feature, never shared |
| Block texture | `assets/creator_<id>/textures/block/<path>.png` | |
| Item texture | `assets/creator_<id>/textures/item/<path>.png` | |
| Entity texture | `assets/creator_<id>/textures/entity/<path>.png` | |
| Block model | `assets/creator_<id>/models/block/<path>.json` | |
| Item model | `assets/creator_<id>/models/item/<path>.json` | |
| Blockstate | `assets/creator_<id>/blockstates/<path>.json` | |
| Sound definitions | `assets/creator_<id>/sounds.json` | one per namespace, yours alone |
| Sound file | `assets/creator_<id>/sounds/<category>/<name>.ogg` | Ogg **Vorbis** (never Opus) |
| Sound event id | `creator_<id>:<category>.<name>` | `creator_colossus:colossus.roar` |
| GeckoLib model | `assets/creator_<id>/geo/<path>.geo.json` | |
| GeckoLib animation | `assets/creator_<id>/animations/<path>.animation.json` | |
| Loot table | `data/creator_<id>/loot_table/<kind>/<path>.json` | 1.21 uses the **singular** folder |
| Recipe | `data/creator_<id>/recipe/<path>.json` | singular |
| Tag | `data/creator_<id>/tags/<registry>/<path>.json` | `tags` stays plural, the registry is singular |
| Structure / GameTest template | `data/creator_<id>/structure/<name>.nbt` | singular since 1.21 |
| Payload id | `creator_<id>:<snake_case>` | `creator_powers:use_power` |
| Attachment id | `creator_<id>:<snake_case>` | `creator_evolve:stage` |
| `TickScheduler` tag | `rl("<what>")` → `creator_<id>:<what>` | `creator_events:bloodmoon` |
| Mixin class | `dev.riftal.creator.features.<id>.mixin.<Cap><Target>Mixin` | `VaultChestBlockEntityMixin` |

Mod-level ids (`creatormods:*`) belong to core. Never register anything in the `creatormods`
namespace or in `minecraft`.

---

## 7. Resources: you hand-write every JSON. Datagen is BANNED.

**There is no datagen in this project and none is being added.** Do not write a `DataProvider`, a
`BlockStateProvider`, a `RecipeProvider`, an `ItemModelProvider`, a `LootTableProvider` or a
`GatherDataEvent` handler. Do not create `common/src/generated/`. Do not ask the build agent to run
`:neoforge:runData` — no provider is registered, so it generates nothing, and a half-wired datagen
run is a shared-state change that breaks the other seven agents.

Every file below is hand-written, by you, under your own `creator_<id>` trees. They are small. The
schemas below are copied from **real vanilla 1.21.1 files** unzipped from this project's own
Minecraft artifacts, so they are correct for this version.

### 7.1 A Block needs five files

For block `cursed_altar` in feature `vault` (registered as `creator_vault:cursed_altar`):

| # | File | Why |
|---|---|---|
| 1 | `assets/creator_vault/blockstates/cursed_altar.json` | which model to draw for which state. Missing = purple/black cube |
| 2 | `assets/creator_vault/models/block/cursed_altar.json` | the block model |
| 3 | `assets/creator_vault/models/item/cursed_altar.json` | the `BlockItem` in the inventory/hand |
| 4 | `assets/creator_vault/textures/block/cursed_altar.png` | the texture (§9 for placeholder art) |
| 5 | `data/creator_vault/loot_table/blocks/cursed_altar.json` | **drops nothing when mined without this** |

Plus one line in `assets/creator_vault/lang/en_us.json`:
`"block.creator_vault.cursed_altar": "Cursed Altar"`.

```json
// common/src/main/resources/assets/creator_vault/blockstates/cursed_altar.json
{
  "variants": {
    "": { "model": "creator_vault:block/cursed_altar" }
  }
}
```

A block with properties lists one variant per state combination, exactly like vanilla `lantern`:
`{ "variants": { "hanging=false": { "model": "..." }, "hanging=true": { "model": "..." } } }`.

```json
// common/src/main/resources/assets/creator_vault/models/block/cursed_altar.json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "creator_vault:block/cursed_altar"
  }
}
```

```json
// common/src/main/resources/assets/creator_vault/models/item/cursed_altar.json
{
  "parent": "creator_vault:block/cursed_altar"
}
```

```json
// common/src/main/resources/data/creator_vault/loot_table/blocks/cursed_altar.json
{
  "type": "minecraft:block",
  "random_sequence": "creator_vault:blocks/cursed_altar",
  "pools": [
    {
      "rolls": 1.0,
      "bonus_rolls": 0.0,
      "conditions": [ { "condition": "minecraft:survives_explosion" } ],
      "entries": [ { "type": "minecraft:item", "name": "creator_vault:cursed_altar" } ]
    }
  ]
}
```

(That is byte-for-byte the shape of vanilla `data/minecraft/loot_table/blocks/dirt.json`. Note the
**singular** `loot_table` directory and the **plural** `blocks` subfolder — both are load-bearing,
and getting either wrong fails silently.)

### 7.2 An Item needs two files

For item `vault_key` in feature `vault`:

| # | File |
|---|---|
| 1 | `assets/creator_vault/models/item/vault_key.json` |
| 2 | `assets/creator_vault/textures/item/vault_key.png` |

Plus `"item.creator_vault.vault_key": "Vault Key"` in your lang file.

```json
// common/src/main/resources/assets/creator_vault/models/item/vault_key.json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "creator_vault:item/vault_key"
  }
}
```

`item/generated` is the flat 2D sprite; `item/handheld` is the tool/sword pose. A tool or armour
item needs nothing else in 1.21.1 — attributes and durability come from code. (1.21.1 uses
`models/item/`. The `items/` "item model definition" folder is 1.21.4+ — do not use it here.)

### 7.3 An EntityType needs

For entity `ashen_colossus` in feature `colossus`:

| # | File / thing | Notes |
|---|---|---|
| 1 | `assets/creator_colossus/textures/entity/ashen_colossus.png` | referenced by your renderer's `getTextureLocation` |
| 2 | lang key `entity.creator_colossus.ashen_colossus` | otherwise the name shows as the raw key |
| 3 | `EntityAttributes.register(...)` in `registerContent()` | **code**, §5.1. Missing = crash on spawn |
| 4 | `ClientRenderers.entityRenderer(...)` in `initClient()` | **code**, §5.4. Missing = invisible entity |

There is **no JSON model** for a vanilla-style entity: the model is Java (`EntityModel` +
`ModelLayerLocation` + `LayerDefinition`). If you use GeckoLib instead, you additionally write
`assets/creator_colossus/geo/ashen_colossus.geo.json` and
`assets/creator_colossus/animations/ashen_colossus.animation.json` by hand.

Optional spawn egg — it is an ordinary item (`SpawnEggItem`) and therefore needs an item model,
which for once has no texture of its own:

```json
// common/src/main/resources/assets/creator_colossus/models/item/ashen_colossus_spawn_egg.json
{
  "parent": "minecraft:item/template_spawn_egg"
}
```

### 7.4 Sounds

`assets/creator_<id>/sounds.json` maps **sound event path → files**. Schema verified against
`SoundEventRegistrationSerializer` in the decompiled source (`replace`, `subtitle`, `sounds`):

```json
// common/src/main/resources/assets/creator_colossus/sounds.json
{
  "colossus.roar": {
    "subtitle": "subtitles.creator_colossus.colossus.roar",
    "sounds": [ "creator_colossus:colossus/roar" ]
  }
}
```

The file then lives at `assets/creator_colossus/sounds/colossus/roar.ogg`, and the `SoundEvent` you
register in code is `creator_colossus:colossus.roar` (§5.1). Add the `subtitles.*` key to your lang
file too.

### 7.5 Recipes and tags

```json
// common/src/main/resources/data/creator_vault/recipe/vault_key.json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "pattern": [ " I ", "IGI", " I " ],
  "key": {
    "I": "minecraft:iron_ingot",
    "G": "minecraft:gold_ingot"
  },
  "result": { "id": "creator_vault:vault_key", "count": 1 }
}
```

Tags go in `data/creator_<id>/tags/<registry>/<path>.json` — singular registry folder
(`tags/block`, `tags/item`, `tags/entity_type`) — and **never** carry `"replace": true`.
If your feature adds ores/ingots/tools, put them in the hierarchical conventional tags
(`c:ores/silver`, not `c:silver_ores`).

### 7.6 The lang file is not optional

Every translatable key you emit — blocks, items, entities, creative tab, command feedback, HUD
strings, subtitles — has a line in `assets/creator_<id>/lang/en_us.json`. An in-game
`item.creator_vault.vault_key` is the single most visible "this mod is slop" signal there is, and it
is a 30-second fix at write time.

---

## 8. Creative tabs

**At most one creative tab per feature, and only if your feature actually adds items.** Its id is
`creator_<id>` — the same word as your namespace, nothing else:

```java
private final Registrar<CreativeModeTab> tabs = registrar(Registries.CREATIVE_MODE_TAB);

public final RegistryEntry<CreativeModeTab> tab = tabs.register("creator_vault", () ->
        CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup.creator_vault"))
                .icon(() -> new ItemStack(vaultKey.get()))
                .displayItems((params, output) -> {
                    output.accept(vaultKey.get());
                    output.accept(altarItem.get());
                })
                .build());
```

(`builder`, `title`, `icon`, `displayItems`, `build` and `DisplayItemsGenerator.accept(params,
output)` are all verified in `net/minecraft/world/item/CreativeModeTab.java`.)

* **Never a shared tab.** Do not create a `creatormods` tab, do not add your items to another
  feature's tab, do not append to a vanilla tab from `common`. Eight agents editing one tab is a
  guaranteed conflict, and a disabled feature must take its whole tab with it.
* The tab's translation key is `itemGroup.creator_<id>`, in your own lang file.
* No items → no tab. An empty creative tab is worse than none.

---

## 9. Placeholder art and audio — you generate your own

Nobody is waiting on an artist. Every feature generates its own placeholder assets, checks them in,
and declares them. **Placeholders must be obviously placeholder** (flat colours, a border, a letter)
— never AI-generated imagery, never a texture copied out of vanilla or another mod.

### 9.1 PNG textures — python3 stdlib only (tested, works)

Power-of-two sizes only: **16×16** for blocks/items, 32/64 for entities, 64×64 for a GUI sprite
sheet. RGBA, 8-bit. Save the generator script next to your feature (`.../features/<id>/tools/`) so
the art can be regenerated.

```python
# python3 make_placeholder.py  ->  writes a 16x16 RGBA PNG, no dependencies
import struct, zlib

def write_png(path, width, height, pixels):
    """pixels: list of rows, each row a list of (r, g, b, a) tuples, 0-255."""
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBBB", *px) for px in row) for row in pixels
    )

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))  # 8-bit RGBA
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)

SIZE = 16
rows = []
for y in range(SIZE):
    row = []
    for x in range(SIZE):
        edge = x == 0 or y == 0 or x == SIZE - 1 or y == SIZE - 1
        shade = 40 if (x + y) % 2 == 0 else 0
        row.append((20, 10, 30, 255) if edge else (90 + shade, 40 + shade, 120 + shade, 255))
    rows.append(row)

write_png("cursed_altar.png", SIZE, SIZE, rows)
```

Verified output: `PNG image data, 16 x 16, 8-bit/color RGBA, non-interlaced`, 101 bytes.

### 9.2 Ogg audio — ffmpeg synthesis (tested, works on this machine)

Minecraft plays **Ogg Vorbis** (not Opus, not mp3, not wav). The ffmpeg on this machine is built
**without libvorbis**, and ffmpeg's native `vorbis` encoder is stereo-only, so this is the command
that actually works here:

```bash
ffmpeg -v error -y -f lavfi -i "sine=frequency=220:duration=1.2" \
       -af "afade=t=out:st=0.8:d=0.4,volume=0.5" \
       -ac 2 -ar 44100 -c:a vorbis -strict -2 -b:a 96k \
       common/src/main/resources/assets/creator_colossus/sounds/colossus/roar.ogg

# verify what you just made
ffprobe -v error -show_entries stream=codec_name,channels,sample_rate \
        -show_entries format=duration -of default=nw=1 <file>.ogg
#   codec_name=vorbis / sample_rate=44100 / channels=2 / duration=1.200181
```

Swap `sine=frequency=…` for `anoisesrc=color=brown`, `sine=frequency=90`, chained `afade`/`atempo`/
`aecho` filters, etc. — it is a placeholder, it only has to be the right length and not silent.

**Known limitation, record it in ASSETS.md:** these placeholders are 2-channel, so Minecraft plays
them non-positionally (no distance attenuation). The real replacement asset must be **mono,
44.1 kHz**.

### 9.3 `ASSETS.md` is mandatory

If your feature ships any texture, model or sound, it also ships
`common/src/main/java/dev/riftal/creator/features/<id>/ASSETS.md`:

```markdown
# creator_vault — asset inventory

| File | Kind | Status | What a real artist should do |
|---|---|---|---|
| assets/creator_vault/textures/block/cursed_altar.png | 16x16 PNG | PROCEDURAL PLACEHOLDER (tools/make_placeholder.py) | Carved obsidian altar, runes glowing on the top face |
| assets/creator_vault/textures/item/vault_key.png | 16x16 PNG | PROCEDURAL PLACEHOLDER | Ornate brass key, 3/4 view |
| assets/creator_vault/sounds/vault/unlock.ogg | 1.2 s Vorbis, **stereo** | PROCEDURAL PLACEHOLDER (ffmpeg sine) | Heavy tumbler + stone grind, **mono 44.1 kHz** |
| assets/creator_vault/models/block/cursed_altar.json | JSON | HAND-WRITTEN, FINAL | — |

Regenerate placeholders: `python3 .../features/vault/tools/make_placeholder.py`
```

Nothing in the repo may claim to be finished art when it is not.

---

## 10. Mixins

Your config already exists and is already listed in `fabric.mod.json` and `neoforge.mods.toml`.
Append class names (relative to the declared package) to it and nothing else:

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "dev.riftal.creator.features.vault.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": ["VaultChestBlockEntityMixin"],
  "client": ["VaultGuiMixin"],
  "server": [],
  "injectors": { "defaultRequire": 1 }
}
```

### 10.1 Class naming is mandatory: `<Cap><Target>Mixin`

Two features will target the same vanilla class, and Mixin resolves by **simple name** in places
where a collision is confusing at best. So the class name always carries your feature:

```
ToolkitServerPlayerMixin      ✅        ServerPlayerMixin      ❌
VaultChestBlockEntityMixin    ✅        ChestBlockEntityMixin  ❌
PowersLivingEntityMixin       ✅        LivingEntityMixin      ❌
```

Package stays `dev.riftal.creator.features.<id>.mixin`. `"mixins"` = both sides, `"client"` =
client only, `"server"` = dedicated server only.

### 10.2 What you may write

* **`@Overwrite` is forbidden.** No exceptions, no "but it's simpler". So is `@Redirect` — it is
  exclusive and silently breaks every other mod touching that method. Use
  **`@Inject`**, **`@ModifyVariable`**, **`@ModifyReturnValue`**, **`@ModifyExpressionValue`**,
  **`@WrapOperation`** (MixinExtras is available in `common`), **`@Accessor`**, **`@Invoker`**.
  Avoid `@ModifyConstant` — it does not stack.
* **Every injection names an explicit method signature**, read out of the decompiled source or
  `javap -s` (§3) — never guessed, never a bare method name when the class has overloads:

```java
@Mixin(LivingEntity.class)
public abstract class PowersLivingEntityMixin {

    @Inject(
        method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"),
        cancellable = true)
    private void creator_powers$absorbHit(DamageSource source, float amount,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!CreatorMods.isEnabled("powers")) return;          // MANDATORY, see 10.3
        if (!PowersFeature.shieldActive((LivingEntity) (Object) this)) return;
        cir.setReturnValue(false);
    }
}
```

* **Every cancellable injection is guarded by your feature's enabled-check as its first statement.**
  `if (!CreatorMods.isEnabled("<id>")) return;`. A mixin is applied by the loader whether or not the
  feature is enabled — without this guard, switching a feature off in `config/creatormods.json`
  still changes vanilla behaviour, and the human's clean single-feature recording is ruined. Same
  guard on any injection with a side effect, cancellable or not.
* **Never an unconditional `cir.setReturnValue(...)` / `ci.cancel()`** at HEAD. Return early on
  every path that is not your case.
* **Every injected member is prefixed** `creator_<id>$name` and every new field is `@Unique`.
  Unprefixed members collide across mods and across our own eight features.
* `@Shadow` only for members that really exist; `@Final` on shadowed finals.
* There is **no `"refmap"` key** and there must not be one: Loom ≥ 1.17 dropped the mixin annotation
  processor and remaps annotations at `remapJar` time instead.
* Target **vanilla classes only**. Loader-specific mixins (targeting Fabric API or NeoForge classes)
  would go in the shared `creatormods.fabric.mixins.json` / `creatormods.neoforge.mixins.json`,
  which you may not edit. Ask the build agent; usually there is a non-mixin hook.
* **Prefer an existing event/hook over a mixin.** Every mixin is a porting cost and a compat risk.
  If core already exposes what you need (§5), use that instead.

---

## 11. Tests

### 11.1 JUnit — `common/src/test/java/dev/riftal/creator/features/<id>/`

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

### 11.2 GameTest — a body in `common`, a stub per loader (all three files already exist)

> **FABRIC DISCOVERY — READ THIS TWICE.** Fabric finds GameTests **only** through the
> `fabric-gametest` entrypoint list in `fabric/src/gametest/resources/fabric.mod.json`. That list
> hard-names the 9 existing classes, **nobody may edit it**, and it is the entire discovery
> mechanism. Therefore: **a Fabric GameTest MUST be a new method on your existing
> `<Cap>FabricGameTests` class.** If you create a sibling class — `VaultAltarFabricGameTests`,
> `MoreVaultTests`, anything — it is **silently never run**. No error. No warning. The build stays
> green and your feature simply has no Fabric coverage. Same rule for the NeoForge stub: new
> methods on `<Cap>NeoForgeGameTests`, never a new class.

Both stub files are pre-registered: the Fabric one is listed in the `fabric-gametest` entrypoint,
the NeoForge one is auto-scanned and `creator_<id>` is already in
`neoforge.enabledGameTestNamespaces`. You add **methods**, in all three files, for each test.

**1. Body** — `common/src/main/java/dev/riftal/creator/features/vault/gametest/VaultGameTests.java`:

```java
public static void altarAcceptsTheKey(GameTestHelper helper) {
    BlockPos pos = new BlockPos(1, 1, 1);
    helper.setBlock(pos, VaultFeature.ALTAR.get());
    helper.succeedWhen(() -> helper.assertBlockPresent(VaultFeature.ALTAR.get(), pos));
}
```

**2. Fabric stub** — a new method in the existing `fabric/src/gametest/java/.../VaultFabricGameTests.java`.
Fabric uses `template()` **verbatim**, so it must be a full `namespace:path`:

```java
@GameTest(template = EMPTY)                 // EMPTY = VaultFeature.NAMESPACE + ":empty"
public void altarAcceptsTheKey(GameTestHelper helper) {
    VaultGameTests.altarAcceptsTheKey(helper);
}
```

**3. NeoForge stub** — a new method in the existing `neoforge/src/main/java/.../VaultNeoForgeGameTests.java`.
The class carries `@GameTestHolder("creator_vault") @PrefixGameTestTemplate(false)`, so the template
is a bare path:

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

**Every test must end in an explicit success call** (`succeed()`, `succeedWhen(...)`,
`succeedOnTickWhen(...)`). A test that just runs off the end times out after 100 ticks and reports a
confusing timeout instead of an assertion. Keep each test under that 100-tick default or set
`timeoutTicks` on the annotation.

---

## 12. Scope: MVP only

**Implement the MVP section of your feature's plan. Nothing else.**

* Stretch goals, "nice to have", "phase 2", "if time permits" sections are **out of scope**.
* You may start a stretch goal only when the MVP is complete *and* you believe it compiles *and*
  every §14 item is ticked — and even then, keep it in separate files so it can be dropped.
* If the plan's MVP is ambiguous, implement the smaller reading and say so in your handover.
* Do not invent scope: no extra blocks "for symmetry", no config surface nobody asked for, no
  refactor of someone else's feature, no edits to core to make your life easier.

A feature that lands a working MVP is a success. A feature that lands three half-finished systems
costs the build agent an hour and gets reverted.

---

## 13. How not to waste the build agent's time

You cannot compile. The build agent can, once, for everyone. Before you hand over, **re-read every
file you wrote against this list** — these are the ten mistakes that have actually cost cycles on
1.21.1 multiloader work:

1. **Imports.** Every type you name has an `import`. No wildcard imports. And **no Yarn names** —
   `Identifier`, `MinecraftClient`, `PacketByteBuf`, `World` as a type, `class_1234` are from the
   wrong mapping set and compile nowhere here. Mojang mappings only: `ResourceLocation`,
   `Minecraft`, `RegistryFriendlyByteBuf`, `Level`.
2. **Client-only classes referenced from common.** Anything under `net.minecraft.client.*` (and
   `HudLayers`/`HudText`/`ClientRenderers`) may appear only in code reached from `initClient()` —
   including as a *field type*, a *method parameter or return type*, or a lambda captured in a
   common class. It compiles in `common` (§15.6) and dies on a dedicated server. Check with
   `grep -n "@OnlyIn(Dist.CLIENT)" $MCSRC/<path>.java`.
3. **`RegistryEntry` vs the object.** `Registrar.register` returns `RegistryEntry<T>`, a supplier.
   Pass `altar.get()` where a `Block` is wanted, `altar` where a `Supplier` is wanted — and never
   call `.get()` in a static/field initialiser (§4).
4. **`Holder<T>` vs the direct object.** Lots of 1.21.1 signatures take a `Holder`:
   `new MobEffectInstance(Holder<MobEffect>, ...)`, enchantments, `Holder<SoundEvent>`. Grep the
   constructor before you call it; wrap with `BuiltInRegistries.X.wrapAsHolder(value)`.
5. **`new ResourceLocation(...)` does not exist.** `ResourceLocation.fromNamespaceAndPath(ns, path)`
   (or `rl("path")` from `Feature`, which is what you should be using).
6. **Data components, not NBT.** `stack.getOrCreateTag()/getTag()/setTag()/hasTag()` are all gone.
   Register a `DataComponentType` with a `Codec` (+ `StreamCodec` if the client needs it) and use
   `stack.set(TYPE, value)` / `stack.get(TYPE)`. Component values must be **immutable records** —
   the component map hashes on the value, so mutating one breaks saving, tooltips and stacking.
7. **Registry timing.** Everything is declared in `registerContent()`; nothing is registered from a
   static block, a constructor side effect, or `initCommon()`. No config branch decides *what* gets
   registered. `RegistryEntry.get()` only inside method bodies.
8. **EntityType generics.** `EntityType.Builder.<Keeper>of(Keeper::new, MobCategory.MONSTER)` — the
   explicit type witness is required or the lambda will not infer; `.build("keeper")` takes the path
   string; the field is `RegistryEntry<EntityType<Keeper>>`. Custom `LivingEntity` without
   `EntityAttributes.register` crashes on first spawn.
9. **Mixin target descriptors.** `method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"`
   must match `javap -s` exactly (§3). Non-void target → `CallbackInfoReturnable<T>` (and the `T`
   must be the boxed return type); void target → `CallbackInfo`. Injected members prefixed
   `creator_<id>$`, new fields `@Unique`, no `@Overwrite`/`@Redirect`, enabled-guard first (§10).
10. **Resources, quietly wrong.** Singular `loot_table` / `recipe` / `structure` / `advancement`
    directories; `tags/<singular registry>/`; every model/blockstate/texture path spelled with your
    own `creator_<id>` namespace; a lang key for every translatable string; sound files that exist
    where `sounds.json` says they do. None of these fail the build — they fail in the recording.

Two more that are not compile errors but will get your code sent back: a `level.playSound` /
`addParticle` on the client side only (§5.8), and an unvalidated C2S payload (§5.5).

When you hand over, list: the files you added, anything you are unsure compiles, and any vanilla
signature you used that you could *not* find in `$MCSRC` (there should be none).

---

## 14. Definition of done for a feature agent

* The plan's **MVP** is implemented; stretch goals are untouched (§12).
* Every file you touched is in your ownership list from §2 — nothing under
  `fabric/src/main/resources/**` or `neoforge/src/main/resources/**`, nothing in core.
* Every vanilla API you called was read in `$MCSRC` first (§3).
* `<Cap>Feature` implements the phases it needs; nothing client-only is referenced outside
  `initClient()`; nothing is declared outside `registerContent()`.
* Every block has a blockstate + block model + item model + texture + loot table; every item has a
  model + texture; every entity has a texture, attributes and a renderer (§7).
* You wrote your own placeholder art and an `ASSETS.md` that says what is placeholder (§9).
* At most one creative tab, id `creator_<id>`, only if you have items (§8).
* Your lang file has a key for every translatable string you emit.
* At least one JUnit test and at least one real GameTest (beyond the `featureIsEnabled` stub), added
  as **methods on the existing test classes** (§11.2).
* Your mixin config lists only your own `<Cap><Target>Mixin` classes, has no `"refmap"` key, and
  every cancellable injection is guarded by the feature's enabled-check (§10).
* You logged through `Constants.LOG` only, prefixed `[<id>]`, never per tick (§4).
* You ran the §13 self-check line by line.
* You did **not** run Gradle. You re-read your files instead.

---

## 15. Gotchas inherited from the scaffold

1. `@GameTest` has no `templateNamespace` on vanilla/Fabric — see §11.2 for the exact spelling per
   loader. This cost a build cycle already; do not re-derive it.
2. Loom ≥ 1.17 dropped the mixin AP and refmaps. No `"refmap"` key anywhere.
3. GameTests are deliberately not wired into `check`, so `./gradlew build` never boots a server.
   A green build is **not** proof your feature works; it is proof it compiles.
4. 1.21 renamed several data folders to the singular: `structure`, `loot_table`, `recipe`,
   `advancement`, `predicate`. Getting this wrong fails silently.
5. GeckoLib 4.9.2 is available in all three modules (`software.bernie.geckolib`). Its
   `geckolib.refmap.json could not be read` warning in dev is normal and harmless.
6. `common` compiles against the merged (client + server) Mojang-mapped jar, so client classes
   *compile* there. Only `initClient()` and code it reaches may *load* them at runtime.
7. Any new key added to `gradle.properties` that a resource file references must also be added to the
   `expandProps` map in `buildSrc/src/main/groovy/multiloader-common.gradle` — build agent's job.
8. `common/src/main/resources` is the only resource tree merged into both loader jars
   (`multiloader-loader.gradle`). `multiloader-loader.gradle` would also copy
   `common/src/generated/resources` if it existed — it does not, datagen is banned (§7), and
   creating it is a contract violation.
9. `pack.mcmeta` declares `pack_format` 34, correct for 1.21.1 resources. It is a shared file; if
   your feature thinks it needs a different pack format, it does not.
