package adris.altoclef.tradecycle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Holds the wanted-list config and the index of the current server + dimension, and persists both:
 * <ul>
 *   <li>{@code config/tenorclef/tradecycle.json}</li>
 *   <li>{@code config/tenorclef/trades/<server>/<dimension>.json}</li>
 * </ul>
 * {@link #version()} increments on every change so renderers can cache derived text.
 */
public final class TradeStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static TradeStore instance;

    private final Path baseDir;
    private TradeCycleConfig config;
    private TradeIndex index = new TradeIndex();
    private String server;
    private String dimension;
    private long version;

    public TradeStore(Path baseDir) {
        this.baseDir = baseDir;
    }

    public static synchronized TradeStore get() {
        if (instance == null) {
            Path cfg;
            try {
                cfg = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            } catch (Throwable t) {
                cfg = Path.of("config");
            }
            instance = new TradeStore(cfg.resolve("tenorclef"));
        }
        return instance;
    }

    public Path baseDir() {
        return baseDir;
    }

    public Path configPath() {
        return baseDir.resolve("tradecycle.json");
    }

    public Path indexPath() {
        return server == null ? null : indexPath(baseDir, server, dimension);
    }

    public static Path indexPath(Path baseDir, String server, String dimension) {
        return baseDir.resolve("trades").resolve(sanitize(server)).resolve(sanitize(dimension) + ".json");
    }

    /** File-name safe: {@code mc.example.com:25565} → {@code mc.example.com_25565}, {@code minecraft:the_nether} → {@code minecraft_the_nether}. */
    public static String sanitize(String s) {
        if (s == null || s.isBlank()) return "unknown";
        String out = s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        while (out.startsWith(".")) out = out.substring(1);
        return out.isEmpty() ? "unknown" : out;
    }

    public synchronized TradeCycleConfig config() {
        if (config == null) {
            config = readJson(configPath(), TradeCycleConfig.class);
            if (config == null) {
                config = new TradeCycleConfig();
                saveConfig();
            }
            config.sanitize();
        }
        return config;
    }

    public synchronized void saveConfig() {
        writeJson(configPath(), config);
        version++;
    }

    public synchronized void reloadConfig() {
        config = null;
        config();
        version++;
    }

    public synchronized TradeIndex index() {
        return index;
    }

    public synchronized boolean hasWorld() {
        return server != null;
    }

    public synchronized String worldKey() {
        return server == null ? "(no world)" : server + " / " + dimension;
    }

    /** Loads the index for this server + dimension; saves the previous one first. No-op if unchanged. */
    public synchronized void switchTo(String server, String dimension) {
        if (server == null) {
            if (this.server != null) saveIndex();
            this.server = null;
            this.dimension = null;
            index = new TradeIndex();
            version++;
            return;
        }
        if (server.equals(this.server) && dimension.equals(this.dimension)) return;
        if (this.server != null) saveIndex();
        this.server = server;
        this.dimension = dimension;
        TradeIndex loaded = readJson(indexPath(), TradeIndex.class);
        index = loaded == null ? new TradeIndex() : loaded;
        if (index.villagers == null) index.villagers = new java.util.LinkedHashMap<>();
        index.villagers.values().removeIf(r -> r == null || r.uuid == null);
        index.villagers.values().forEach(r -> {
            if (r.offers == null) r.offers = new java.util.ArrayList<>();
            if (r.status == null) r.status = TradeStatus.UNKNOWN;
        });
        version++;
    }

    /** Call after every index change. */
    public synchronized void saveIndex() {
        version++;
        Path p = indexPath();
        if (p != null) writeJson(p, index);
    }

    public synchronized long version() {
        return version;
    }

    public synchronized void touch() {
        version++;
    }

    static <T> T readJson(Path p, Class<T> type) {
        if (p == null || !Files.isRegularFile(p)) return null;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, type);
        } catch (Exception e) {
            System.err.println("[tradecycle] cannot read " + p + ": " + e);
            return null;
        }
    }

    static void writeJson(Path p, Object value) {
        if (p == null || value == null) return;
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(value, w);
            }
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[tradecycle] cannot write " + p + ": " + e);
        }
    }

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    public static <T> T fromJson(String s, Class<T> type) {
        return GSON.fromJson(s, type);
    }
}
