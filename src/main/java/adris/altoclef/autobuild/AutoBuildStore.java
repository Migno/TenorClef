package adris.altoclef.autobuild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * {@code config/tenorclef/autobuild.json} (settings) and {@code config/tenorclef/autobuild_state.json} (the build
 * in progress). Writes go through a temp file and a move, so a crash mid-write leaves the old file.
 */
public final class AutoBuildStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static AutoBuildStore instance;

    private final Path baseDir;
    private AutoBuildConfig config;

    public AutoBuildStore(Path baseDir) {
        this.baseDir = baseDir;
    }

    public static synchronized AutoBuildStore get() {
        if (instance == null) {
            Path cfg;
            try {
                cfg = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            } catch (Throwable t) {
                cfg = Path.of("config");
            }
            instance = new AutoBuildStore(cfg.resolve("tenorclef"));
        }
        return instance;
    }

    public Path configPath() {
        return baseDir.resolve("autobuild.json");
    }

    public Path statePath() {
        return baseDir.resolve("autobuild_state.json");
    }

    public synchronized AutoBuildConfig config() {
        if (config == null) {
            config = read(configPath(), AutoBuildConfig.class);
            if (config == null) {
                config = new AutoBuildConfig();
                saveConfig();
            }
            config.sanitize();
        }
        return config;
    }

    public synchronized void reloadConfig() {
        config = null;
        config();
    }

    public synchronized void saveConfig() {
        write(configPath(), config());
    }

    /** The saved build, or null. */
    public synchronized AutoBuildState loadState() {
        AutoBuildState s = read(statePath(), AutoBuildState.class);
        return s == null ? null : s.sanitize();
    }

    public synchronized void saveState(AutoBuildState s) {
        s.updatedAt = System.currentTimeMillis();
        write(statePath(), s);
    }

    public synchronized void clearState() {
        try {
            Files.deleteIfExists(statePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static <T> T read(Path p, Class<T> type) {
        if (!Files.isRegularFile(p)) return null;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, type);
        } catch (Exception e) {
            System.err.println("autobuild: could not read " + p + ": " + e);
            return null;
        }
    }

    private static void write(Path p, Object value) {
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(value, w);
            }
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("autobuild: could not write " + p + ": " + e);
        }
    }
}
