package dev.focuslens.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.focuslens.FocusLens;
import dev.focuslens.platform.Services;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and saves {@link FocusLensConfig}. Writes go to a temporary file that is then moved over the
 * real one, so a crash during saving never leaves a half-written config behind.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "focuslens.json";

    private static FocusLensConfig config = new FocusLensConfig();

    private ConfigManager() {
    }

    public static FocusLensConfig get() {
        return config;
    }

    public static Path path() {
        return Services.PLATFORM.configDir().resolve(FILE_NAME);
    }

    public static void load() {
        Path file = path();
        if (!Files.exists(file)) {
            config = new FocusLensConfig();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            FocusLensConfig loaded = GSON.fromJson(reader, FocusLensConfig.class);
            config = loaded != null ? loaded : new FocusLensConfig();
            config.validate();
        } catch (IOException | JsonParseException e) {
            FocusLens.LOG.error("Could not read {}, using defaults. The broken file is kept as {}.broken", file, FILE_NAME, e);
            try {
                Files.move(file, file.resolveSibling(FILE_NAME + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                FocusLens.LOG.warn("Could not back up broken config", moveError);
            }
            config = new FocusLensConfig();
        }
        save();
    }

    public static void save() {
        config.validate();
        Path file = path();
        Path temp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            FocusLens.LOG.error("Could not save {}", file, e);
        }
    }
}
