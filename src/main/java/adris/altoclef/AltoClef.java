package adris.altoclef;


import adris.altoclef.butler.Butler;
import adris.altoclef.chains.*;
import adris.altoclef.trackers.BlockScanner;
import adris.altoclef.commandsystem.CommandExecutor;
import adris.altoclef.commandsystem.TabCompleter;
import adris.altoclef.control.AdapterMovementController;
import adris.altoclef.control.InputControls;
import adris.altoclef.control.MovementController;
import adris.altoclef.control.PlayerExtraController;
import adris.altoclef.control.SlotHandler;
import adris.altoclef.core.CoreServices;
import adris.altoclef.knowledge.AltoClefWorldKnowledge;
import adris.altoclef.knowledge.WorldKnowledge;
import adris.altoclef.threat.ThreatMonitor;
import adris.altoclef.threat.ThreatSignalCollector;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.ClientRenderEvent;
import adris.altoclef.eventbus.events.ClientTickEvent;
import adris.altoclef.eventbus.events.SendChatEvent;
import adris.altoclef.eventbus.events.TitleScreenEntryEvent;
import adris.altoclef.multiversion.DrawContextWrapper;
import adris.altoclef.multiversion.RenderLayerVer;
import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.trackers.*;
import adris.altoclef.trackers.storage.ContainerSubTracker;
import adris.altoclef.trackers.storage.ItemStorageTracker;
import adris.altoclef.ui.AltoClefTickChart;
import adris.altoclef.ui.CommandStatusOverlay;
import adris.altoclef.ui.MessagePriority;
import adris.altoclef.ui.MessageSender;
import adris.altoclef.util.helpers.InputHelper;
import adris.altoclef.util.helpers.StorageHelper;
import baritone.api.IBaritone; // Ostinato: driver API only
import baritone.api.policy.IAgentPolicy; // Ostinato: driver API
import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import adris.altoclef.multiversion.input.KeyCodes;

import java.util.*;
import java.util.function.Consumer;

/**
 * Central access point for AltoClef (compatibility shim).
 * Phase 3: prefer {@link #getWorldKnowledge()} / {@link #getMovement()} / {@link #getCoreServices()}
 * in new code; existing getters remain for ~317 call sites.
 */
public class AltoClef implements ModInitializer {

    // Static access to altoclef
    private static final Queue<Consumer<AltoClef>> _postInitQueue = new ArrayDeque<>();

    // Central Managers
    private static CommandExecutor commandExecutor;
    private TaskRunner taskRunner;
    private TrackerManager trackerManager;
    private BotBehaviour botBehaviour;
    private PlayerExtraController extraController;
    // Task chains
    private UserTaskChain userTaskChain;
    private FoodChain foodChain;
    private MobDefenseChain mobDefenseChain;
    private MLGBucketFallChain mlgBucketChain;
    // Trackers
    private ItemStorageTracker storageTracker;
    private ContainerSubTracker containerSubTracker;
    private EntityTracker entityTracker;
    private BlockScanner blockScanner;
    private SimpleChunkTracker chunkTracker;
    private MiscBlockTracker miscBlockTracker;
    private CraftingRecipeTracker craftingRecipeTracker;
    // Renderers
    private CommandStatusOverlay commandStatusOverlay;
    private AltoClefTickChart altoClefTickChart;
    // Settings
    private adris.altoclef.Settings settings;
    // Misc managers/input
    private MessageSender messageSender;
    private InputControls inputControls;
    private SlotHandler slotHandler;
    // Butler
    private Butler butler;
    // Phase 3 extracted facades (AltoClef remains compatibility shim)
    private WorldKnowledge worldKnowledge;
    private MovementController movementController;
    private CoreServices coreServices;
    // Phase 8 threat layer (MobDefense/WorldSurvival remain fallback)
    private ThreatMonitor threatMonitor;
    // Pausing
    private boolean paused = false;
    private Task storedTask;

    private static AltoClef instance;

    // Are we in game (playing in a server/world)
    public static boolean inGame() {
        return MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().getNetworkHandler() != null;
    }

    /**
     * Executes commands (ex. `@get`/`@gamer`)
     */
    public static CommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // As such, nothing will be loaded here but basic initialization.
        EventBus.subscribe(TitleScreenEntryEvent.class, evt -> ensureLoaded());

        if (instance != null) {
            throw new IllegalStateException("AltoClef already loaded!");
        }
        instance = this;
        // Librarian trade system: keybinds must exist before the options file is read (no-op below 26.3).
        try {
            adris.altoclef.tradecycle.mc.TradeMc.init();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static boolean loadStarted;

    /**
     * Runs {@link #onInitializeLoad} once. Normally the title screen triggers it; a launch that skips the title
     * screen (quick play) would otherwise leave every manager null, so the client tick calls this too.
     */
    public static void ensureLoaded() {
        if (loadStarted || instance == null) return;
        loadStarted = true;
        instance.onInitializeLoad();
    }

    public void onInitializeLoad() {
        // This code should be run after Minecraft loads everything else in.
        // This is the actual start point, controlled by a mixin.

        initializeBaritoneSettings();
        hookFreecamStatus();

        // Central Managers
        commandExecutor = new CommandExecutor(this);
        taskRunner = new TaskRunner(this);
        trackerManager = new TrackerManager(this);
        botBehaviour = new BotBehaviour(this);
        extraController = new PlayerExtraController(this);

        // Task chains
        userTaskChain = new UserTaskChain(taskRunner);
        mobDefenseChain = new MobDefenseChain(taskRunner);
        new DeathMenuChain(taskRunner);
        new PlayerInteractionFixChain(taskRunner);
        mlgBucketChain = new MLGBucketFallChain(taskRunner);
        new UnstuckChain(taskRunner);
        new PreEquipItemChain(taskRunner);
        new WorldSurvivalChain(taskRunner);
        foodChain = new FoodChain(taskRunner);

        // Trackers
        storageTracker = new ItemStorageTracker(this, trackerManager, container -> containerSubTracker = container);
        entityTracker = new EntityTracker(trackerManager);
        blockScanner = new BlockScanner(this);
        chunkTracker = new SimpleChunkTracker(this);
        miscBlockTracker = new MiscBlockTracker(this);
        craftingRecipeTracker = new CraftingRecipeTracker(trackerManager);

        // Renderers
        commandStatusOverlay = new CommandStatusOverlay();
        altoClefTickChart = new AltoClefTickChart(MinecraftClient.getInstance().textRenderer);

        // Misc managers
        messageSender = new MessageSender();
        inputControls = new InputControls();
        slotHandler = new SlotHandler(this);

        butler = new Butler(this);
        adris.altoclef.sigil.SigilService.get().warmUp();

        // Phase 3: facades over existing trackers + MovementEngineAdapter
        worldKnowledge = new AltoClefWorldKnowledge(this);
        movementController = AdapterMovementController.INSTANCE;
        coreServices = new CoreServices(worldKnowledge, movementController);
        threatMonitor = new ThreatMonitor();

        initializeCommands();
        adris.altoclef.maze.TunnelProvisionBridge.install(); // Ostinato: #maze.tunnels survival kit
        adris.altoclef.maze.MazeFurnishBridge.install(); // no-op unless the Ostinato jar has the maze API (26.3)

        // Load settings
        adris.altoclef.Settings.load(newSettings -> {
            settings = newSettings;
            // Baritone's `acceptableThrowawayItems` should match our own.
            List<Item> baritoneCanPlace = Arrays.stream(settings.getThrowawayItems(true))
                    .filter(item -> item != Items.SOUL_SAND && item != Items.MAGMA_BLOCK && item != Items.SAND && item
                            != Items.GRAVEL).toList();
            getClientBaritoneSettings().acceptableThrowawayItems.value.addAll(baritoneCanPlace);
            // S196: never let AIR in (earlier loads may already have added it); Baritone's
            // throwaway search matches an empty hotbar slot against AIR and "places" nothing.
            getClientBaritoneSettings().acceptableThrowawayItems.value
                    .removeIf(item -> item == null || item == net.minecraft.item.Items.AIR);
            // If we should run an idle command...
            if ((!getUserTaskChain().isActive() || getUserTaskChain().isRunningIdleTask()) && getModSettings().shouldRunIdleCommandWhenNotActive()) {
                getUserTaskChain().signalNextTaskToBeIdleTask();
                getCommandExecutor().executeWithPrefix(getModSettings().getIdleCommand());
            }
            // Don't break blocks or place blocks where we are explicitly protected.
            getExtraBaritoneSettings().avoidBlockBreak(blockPos -> settings.isPositionExplicitlyProtected(blockPos));
            getExtraBaritoneSettings().avoidBlockPlace(blockPos -> settings.isPositionExplicitlyProtected(blockPos));
            getExtraBaritoneSettings().getForceSaveToolPredicates().add((state, item) -> StorageHelper.shouldSaveStack(this, state.getBlock(), item));
        });

        // Headless SIM: if autoLoadWorld is on, the mixin path (AutoWorldLoadMixin) drives
        // the title screen into the newest save. Nothing else is needed here.

        // Receive + cancel chat
        EventBus.subscribe(SendChatEvent.class, evt -> {
            String line = evt.message;
            if (getCommandExecutor().isClientCommand(line)) {
                evt.cancel();
                getCommandExecutor().execute(line);
            }
        });

        // Tick with the client
        EventBus.subscribe(ClientTickEvent.class, evt -> {
            long nanos = System.nanoTime();
            onClientTick();
            altoClefTickChart.pushTickNanos(System.nanoTime()-nanos);
        });

        // Render
        EventBus.subscribe(ClientRenderEvent.class, evt -> onClientRenderOverlay(evt.context));

        // Playground
        Playground.IDLE_TEST_INIT_FUNCTION(this);

        // Tasks
        TaskCatalogue.init();

        getClientBaritone().getGameEventHandler().registerEventListener(new TabCompleter());

        // External mod initialization
        runEnqueuedPostInits();
    }

    // Client tick
    private boolean tickErrorLogged = false;

    private void onClientTick() {
        // Fire the headless auto-run first so an exception later in the tick can't starve it.
        try {
            maybeFireAutoRunCommand();
        } catch (Throwable t) {
            if (!tickErrorLogged) {
                tickErrorLogged = true;
                Debug.logHarness("AUTORUN: tick error " + t);
                t.printStackTrace();
            }
        }
        runEnqueuedPostInits();

        inputControls.onTickPre();

        // Cancel shortcut
        if (InputHelper.isKeyPressed(KeyCodes.LEFT_CONTROL) && InputHelper.isKeyPressed(KeyCodes.K)) {
            stopTasks();
        }
        try {
            adris.altoclef.tasks.speedrun.testrun2.gui.T2MenuKeys.tick();
        } catch (Throwable ignored) {}
        try {
            adris.altoclef.tasks.speedrun.testrun2.dj.DjPlayer.tick();
        } catch (Throwable ignored) {}
        try {
            adris.altoclef.tasks.speedrun.testrun2.util.QueueWatch.tick(this);
        } catch (Throwable ignored) {}

        // TODO: should this go here?
        storageTracker.setDirty();
        containerSubTracker.onServerTick();
        miscBlockTracker.tick();
        trackerManager.tick();
        blockScanner.tick();
        // Phase 8: assess threat from existing signals; may pause/fail @goal PlanExecutor
        if (threatMonitor != null && inGame()) {
            threatMonitor.tick(ThreatSignalCollector.collect(this));
            adris.altoclef.planner.GoalManager gm =
                    adris.altoclef.commands.GoalCommand.getActiveManager();
            if (gm != null) {
                threatMonitor.applyToGoalManager(gm);
            }
        }
        adris.altoclef.swarm.SwarmRuntime.tick();
        taskRunner.tick();

        messageSender.tick();

        inputControls.onTickPost();
    }

    /**
     * Headless SIM: fire {@code autoRunCommand} once after the bot is actually in a world.
     * Kept deliberately small and guarded so it is inert for normal play.
     */
    private int autoRunDelay = -1;
    private boolean autoRunFired = false;

    /** Reroll count and world the auto-run last fired in (S192). */
    private int autoRunReroll = -1;
    private Object autoRunWorld = null;

    private int autoRunProbe = 0;
    // Separate from autoRunProbe, which the debug probe also bumps every call (the shared count never hit % 40 == 0).
    private int autoRunRespawnTick = 0;

    private void maybeFireAutoRunCommand() {
        if (!autoRunFired && ++autoRunProbe % 200 == 1 && Boolean.getBoolean("tenorclef.autorun.debug")) {
            adris.altoclef.Settings ds = getModSettings();
            Debug.logHarness("AUTORUN: probe inGame=" + inGame() + " settings=" + (ds != null)
                    + " cmd=" + (ds == null ? null : ds.getAutoRunCommand()));
        }
        // S192: re-arm once per rerolled world. A reroll (ResetSignal) ends the old task with
        // phase DONE and creates a fresh world, but the command used to fire only once per
        // client launch: live run fix3 sat idle in the new world until DEADMAN exited with 87.
        // Requiring a different world object keeps it from re-firing in the old world during
        // the tick before the queued disconnect runs; respawns/portals keep the reroll count.
        if (autoRunFired && inGame()
                && adris.altoclef.util.AutoWorldState.rerollCount() != autoRunReroll
                && getWorld() != autoRunWorld) {
            autoRunFired = false;
            autoRunDelay = -1;
            Debug.logHarness("AUTORUN: re-armed for rerolled world (reroll #"
                    + adris.altoclef.util.AutoWorldState.rerollCount() + ")");
        }
        if (autoRunFired || !inGame()) {
            return;
        }
        // A world saved with a dead player boots to the death screen; respawn so the run isn't stuck on it.
        if (getPlayer().getHealth() <= 0) {
            if (autoRunRespawnTick++ % 40 == 0) {
                getPlayer().requestRespawn();
                MinecraftClient.getInstance().setScreen(null);
            }
            return;
        }
        // Fully qualified: this file imports baritone.api.Settings, which shadows
        // adris.altoclef.Settings (see getModSettings() declaring the full name too).
        adris.altoclef.Settings s = getModSettings();
        if (s == null) {
            return;
        }
        String cmd = s.getAutoRunCommand();
        if (cmd == null || cmd.isEmpty()) {
            return;
        }
        if (autoRunDelay < 0) {
            // Give trackers/scanner a moment to populate before the task starts.
            autoRunDelay = 20 * Integer.getInteger("tenorclef.autorun.delay", 5);
            // logInternal (not logMessage): logMessage routes to the in-game chat HUD
            // once a player exists, so harness greps of latest.log would miss it.
            Debug.logHarness("AUTORUN: armed '" + cmd + "' (firing in 5s)");
        }
        if (--autoRunDelay > 0) {
            return;
        }
        autoRunFired = true;
        autoRunReroll = adris.altoclef.util.AutoWorldState.rerollCount();
        autoRunWorld = getWorld();
        Debug.logHarness("AUTORUN: executing '" + cmd + "'");
        Debug.logMessage("AUTORUN: executing '" + cmd + "'");
        // -Dtenorclef.autorun.freecam=true: start Ostinato's freecam first, for reproducing freecam issues headless.
        if (Boolean.getBoolean("tenorclef.autorun.freecam")) {
            try {
                getClientBaritone().getCommandManager().execute("freecam");
            } catch (Throwable t) {
                Debug.logWarning("AUTORUN freecam failed: " + t);
            }
        }
        try {
            getCommandExecutor().executeWithPrefix(cmd);
        } catch (Throwable t) {
            Debug.logWarning("AUTORUN failed: " + t.getMessage());
        }
    }

    public void stopTasks() {
        if (userTaskChain != null) {
            userTaskChain.cancel(this);
        }
        if (taskRunner.getCurrentTaskChain() != null) {
            taskRunner.getCurrentTaskChain().stop();
        }
        commandStatusOverlay.resetTimer();
    }

    /// GETTERS AND SETTERS

    private void onClientRenderOverlay(DrawContextWrapper context) {
        context.setRenderLayer(RenderLayerVer.getGuiOverlay());
        if (settings.shouldShowTaskChain()) {
            commandStatusOverlay.render(this, context);
        }
        try {
            adris.altoclef.tasks.speedrun.testrun2.gui.SegnoOverlay.render(context);
        } catch (Throwable ignored) {}

        if (settings.shouldShowDebugTickMs()) {
            altoClefTickChart.render(this, context, 1, context.getScaledWindowWidth() / 2 - 124);
        }
    }

    private void initializeBaritoneSettings() {
        getExtraBaritoneSettings().canWalkOnEndPortal(false);
        getClientBaritoneSettings().freeLook.value = false;
        getClientBaritoneSettings().overshootTraverse.value = false;
        getClientBaritoneSettings().allowOvershootDiagonalDescend.value = true;
        getClientBaritoneSettings().allowInventory.value = true;
        getClientBaritoneSettings().allowParkour.value = false;
        // S295: Ostinato sprintJump (on by default since 86b1c6ae) took "Wrong Y coordinate" from ~5 to 650-1800
        // per run and s294t lost 15 hp to falls on a hilltop. Keep it off until it handles slopes.
        getClientBaritoneSettings().sprintJump.value = false;
        // S322: Ostinato kinematic travel (physics look-ahead) drives plain walking legs of Baritone paths.
        // On by default for speed; -Dtenorclef.kinematic=false disables it.
        try { getClientBaritoneSettings().kinematicTravel.value = !"false".equals(System.getProperty("tenorclef.kinematic")); } catch (Throwable ignored) {}
        // s269t/s270t: path computed but never executed; -Dtenorclef.baritoneDebug=true surfaces PathExecutor cancel/pause reasons.
        if (Boolean.getBoolean("tenorclef.baritoneDebug")) getClientBaritoneSettings().chatDebug.value = true;
        getClientBaritoneSettings().allowParkourAscend.value = false;
        getClientBaritoneSettings().allowParkourPlace.value = false;
        getClientBaritoneSettings().allowDiagonalDescend.value = false;
        // @pathbench (seed 12345, 16-goal ring, 2 rounds): 4.5 vs default 3.563 cut A* time
        // 3-5x (52.8->17.6ms, 6506->2725 nodes) with 100% goal rate and +0.3-1% path cost.
        getClientBaritoneSettings().costHeuristic.value = 4.5;
        getClientBaritoneSettings().allowDiagonalAscend.value = false;
        getClientBaritoneSettings().blocksToAvoid.value = new LinkedList<>(List.of(Blocks.FLOWERING_AZALEA, Blocks.AZALEA,
                Blocks.POWDER_SNOW, Blocks.BIG_DRIPLEAF, Blocks.BIG_DRIPLEAF_STEM, Blocks.CAVE_VINES,
                Blocks.CAVE_VINES_PLANT, Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.SWEET_BERRY_BUSH,
                Blocks.WARPED_ROOTS, Blocks.VINE, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.TALL_GRASS, Blocks.LARGE_FERN,
                Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD,
                Blocks.AMETHYST_CLUSTER, Blocks.SCULK, Blocks.SCULK_VEIN));

        // dont try to break nether portal block
        getClientBaritoneSettings().blocksToAvoidBreaking.value.add(Blocks.NETHER_PORTAL);
        getClientBaritoneSettings().blocksToDisallowBreaking.value.add(Blocks.NETHER_PORTAL);

        // Let baritone move items to hotbar to use them
        // Reduces a bit of far rendering to save FPS
        getClientBaritoneSettings().fadePath.value = true;
        // Don't let baritone scan dropped items, we handle that ourselves.
        getClientBaritoneSettings().mineScanDroppedItems.value = false;
        // Don't let baritone wait for drops, we handle that ourselves.
        getClientBaritoneSettings().mineDropLoiterDurationMSThanksLouca.value = 0L;

        // Water bucket placement will be handled by us exclusively
        getExtraBaritoneSettings().configurePlaceBucketButDontFall(true);

        // For render smoothing
        getClientBaritoneSettings().randomLooking.value = 0.0;
        getClientBaritoneSettings().randomLooking113.value = 0.0;

        // Give baritone more time to calculate paths. Sometimes they can be really far away.
        // Was: 2000L
        getClientBaritoneSettings().failureTimeoutMS.reset();
        // Was: 5000L
        getClientBaritoneSettings().planAheadFailureTimeoutMS.reset();
        // Was 100
        getClientBaritoneSettings().movementTimeoutTicks.reset();
    }

    // List all command sources here.
    private void initializeCommands() {
        try {
            // This creates the commands. If you want any more commands feel free to initialize new command lists.
            AltoClefCommands.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // TODO refactor codebase to use this instead of passing an argument around
    /**
     * @return the instance of this class or null if it has not been initialized yet
     */
    public static AltoClef getInstance() {
        return instance;
    }

    /**
     * Phase 3 read-only world/tracker facade. Prefer over digging through AltoClef getters in new code.
     */
    public WorldKnowledge getWorldKnowledge() {
        return worldKnowledge;
    }

    /**
     * Phase 3 travel controller (wraps MovementEngineAdapter). Prefer for go-to / follow.
     */
    public MovementController getMovement() {
        return movementController;
    }

    /**
     * Bundled Phase 3 services for injection-style call sites.
     */
    public CoreServices getCoreServices() {
        return coreServices;
    }

    /**
     * Phase 8 threat monitor (latest assessment; interrupts GoalManager on HIGH/CRITICAL).
     */
    public ThreatMonitor getThreatMonitor() {
        return threatMonitor;
    }

    /**
     * Runs the highest priority task chain
     * (task chains run the task tree)
     */
    public TaskRunner getTaskRunner() {
        return taskRunner;
    }

    /**
     * The user task chain (runs your command. Ex. Get Diamonds, Beat the Game)
     */
    /**
     * Ostinato's freecam draws the bot as a translucent ghost with a status tag; feed it the current task.
     * Typed Ostinato API on 26.3; reflective on older lines, whose pinned jars predate it.
     */
    private void hookFreecamStatus() {
        try {
            java.util.function.Supplier<String> status = () -> {
                adris.altoclef.tasksystem.TaskChain chain = taskRunner == null ? null : taskRunner.getCurrentTaskChain();
                if (chain == null || chain.getTasks().isEmpty()) {
                    return null;
                }
                java.util.List<adris.altoclef.tasksystem.Task> tasks = chain.getTasks();
                return tasks.get(tasks.size() - 1).toString();
            };
            getClientBaritone().setFreecamStatus(status); // Ostinato: typed API
        } catch (Exception | LinkageError ignored) {
        }
    }

    public UserTaskChain getUserTaskChain() {
        return userTaskChain;
    }

    /**
     * Controls bot behaviours, like whether to temporarily "protect" certain blocks or items
     */
    public BotBehaviour getBehaviour() {
        return botBehaviour;
    }

    /**
     * Controls tasks, for pausing and unpausing the bot
     */
    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean pausing) {
        this.paused = pausing;
    }

    /**
     * storages the task you where doing before pausing.
     */
    public void setStoredTask(Task currentTask) {
        this.storedTask = currentTask;
    }

    /**
     * Gets the task you where doing before pausing.
     */
    public Task getStoredTask() {
        return storedTask;
    }

    /**
     * Tracks items in your inventory and in storage containers.
     */
    public ItemStorageTracker getItemStorage() {
        return storageTracker;
    }

    /**
     * Tracks loaded entities
     */
    public EntityTracker getEntityTracker() {
        return entityTracker;
    }

    /**
     * Manages a list of all available recipes
     */
    public CraftingRecipeTracker getCraftingRecipeTracker() {
        return craftingRecipeTracker;
    }

    /**
     * Tracks blocks and their positions - better version of BlockTracker
     */
    public BlockScanner getBlockScanner() {
        return blockScanner;
    }

    /**
     * Tracks of whether a chunk is loaded/visible or not
     */
    public SimpleChunkTracker getChunkTracker() {
        return chunkTracker;
    }

    /**
     * Tracks random block things, like the last nether portal we used
     */
    public MiscBlockTracker getMiscBlockTracker() {
        return miscBlockTracker;
    }

    /**
     * Baritone access (could just be static honestly)
     */
    public IBaritone getClientBaritone() {
        if (getPlayer() == null) {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        }
        return BaritoneAPI.getProvider().getBaritoneForPlayer(getPlayer());
    }

    /**
     * Baritone settings access (could just be static honestly)
     */
    public Settings getClientBaritoneSettings() {
        return BaritoneAPI.getSettings();
    }

    /**
     * Baritone settings special to AltoClef (could just be static honestly)
     */
    public IAgentPolicy getExtraBaritoneSettings() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getAgentPolicy(); // Ostinato: driver API
    }

    /**
     * AltoClef Settings
     */
    public adris.altoclef.Settings getModSettings() {
        return settings;
    }

    /**
     * Butler controller. Keeps track of users and lets you receive user messages
     */
    public Butler getButler() {
        return butler;
    }

    /**
     * Sends chat messages (avoids auto-kicking)
     */
    public MessageSender getMessageSender() {
        return messageSender;
    }

    /**
     * Does Inventory/container slot actions
     */
    public SlotHandler getSlotHandler() {
        return slotHandler;
    }

    /**
     * Minecraft player client access (could just be static honestly)
     */
    public ClientPlayerEntity getPlayer() {
        return MinecraftClient.getInstance().player;
    }

    /**
     * Minecraft world access (could just be static honestly)
     */
    public ClientWorld getWorld() {
        return MinecraftClient.getInstance().world;
    }

    /**
     * Minecraft client interaction controller access (could just be static honestly)
     */
    public ClientPlayerInteractionManager getController() {
        return MinecraftClient.getInstance().interactionManager;
    }

    /**
     * Extra controls not present in ClientPlayerInteractionManager. This REALLY should be made static or combined with something else.
     */
    public PlayerExtraController getControllerExtras() {
        return extraController;
    }

    /**
     * Manual control over input actions (ex. jumping, attacking)
     */
    public InputControls getInputControls() {
        return inputControls;
    }

    /**
     * Run a user task
     */
    public void runUserTask(Task task) {
        runUserTask(task, () -> {
        });
    }

    /**
     * Run a user task
     */
    public void runUserTask(Task task, Runnable onFinish) {
        userTaskChain.runTask(this, task, onFinish);
    }

    /**
     * Cancel currently running user task
     */
    public void cancelUserTask() {
        userTaskChain.cancel(this);
    }

    /**
     * Takes control away to eat food
     */
    public FoodChain getFoodChain() {
        return foodChain;
    }

    /**
     * Takes control away to defend against mobs
     */
    public MobDefenseChain getMobDefenseChain() {
        return mobDefenseChain;
    }

    /**
     * Takes control away to perform bucket saves
     */
    public MLGBucketFallChain getMLGBucketChain() {
        return mlgBucketChain;
    }

    public void log(String message) {
        log(message, MessagePriority.TIMELY);
    }

    /**
     * Logs to the console and also messages any player using the bot as a butler.
     */
    public void log(String message, MessagePriority priority) {
        Debug.logMessage(message);
    }

    public void logWarning(String message) {
        logWarning(message, MessagePriority.TIMELY);
    }

    /**
     * Logs a warning to the console and also alerts any player using the bot as a butler.
     */
    public void logWarning(String message, MessagePriority priority) {
        Debug.logWarning(message);
    }

    private void runEnqueuedPostInits() {
        synchronized (_postInitQueue) {
            while (!_postInitQueue.isEmpty()) {
                _postInitQueue.poll().accept(this);
            }
        }
    }

}
