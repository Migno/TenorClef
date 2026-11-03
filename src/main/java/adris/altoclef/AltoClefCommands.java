package adris.altoclef;

import adris.altoclef.commands.*;
import adris.altoclef.commands.random.ScanCommand;
import adris.altoclef.commands.random.DummyTaskCommand;
import adris.altoclef.commands.random.CycleTestCommand;
import adris.altoclef.commandsystem.exception.CommandException;

/**
 * Initializes altoclef's built in commands.
 */
public class AltoClefCommands {

    public static void init() throws CommandException {
        // List commands here
        AltoClef.getCommandExecutor().registerNewCommand(
                new HelpCommand(),
                new GetCommand(),
                new GoalCommand(),
                new BenchCommand(),
                new ThreatCommand(),
                new ListCommand(),
                new EquipCommand(),
                new DepositCommand(),
                new StashCommand(),
                new GotoCommand(),
                new IdleCommand(),
                new HeroCommand(),
                new CoordsCommand(),
                new StatusCommand(),
                new TaskIntrospectCommand(TaskIntrospectCommand.Mode.TASK),
                new TaskIntrospectCommand(TaskIntrospectCommand.Mode.TREE),
                new TaskIntrospectCommand(TaskIntrospectCommand.Mode.WHY),
                new InventoryCommand(),
                new LocateStructureCommand(),
                new StopCommand(),
                new PauseCommand(),
                new UnPauseCommand(),
                new SetGammaCommand(),
                new TestCommand(),
                new FoodCommand(),
                new MeatCommand(),
                new ReloadSettingsCommand(),
                new GamerCommand(),
                new MarvionCommand(),
                new TestRunCommand(),
                new ManhuntCommand(),
                new RunnerCommand(),
                new CycleTestCommand(),
                new DummyTaskCommand(),
                new FollowCommand(),
                new PvpCommand(),
                new ScanCommand(),
                new GiveCommand(),
                new BuildSchematicCommand(),
                new T2CoreCommand(),
                new Testrun2Command(),
                new ExtraGetCommand(),
                new AgentCommand(),
                new AgentDoCommand(),
                new EscapeCommand(),
                new HomeCommand(),
                new SetHomeCommand(),
                new BackCommand(),
                new LogDumpCommand(),
                new VillageCommand(),
                new T2DoctorCommand(),
                new T2PanicCommand(),
                new T2MenuCommand(),
                new SwarmCommand(),
                new CompositionCommand(),
                new MapArtCommand(),
                new DjCommand(),
                new ButlerCommand(),
                new FleetCommand(),
                new HeadlessCommand(),
                new ZeroCycleCommand(),
                new GroundZeroCommand(),
                new WarpCommand(),
                new PathBenchCommand(),
                new ShowcaseCommand(),
                new AutoBuildCommand()
        );
    }
}
