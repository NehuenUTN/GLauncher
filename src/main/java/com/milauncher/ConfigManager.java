package com.milauncher;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class ConfigManager {

    private static final Path CONFIG_PATH = FileManager.getMinecraftDir().resolve("launcher.properties");
    private static Properties props = new Properties();

    static {
        load();
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream is = Files.newInputStream(CONFIG_PATH)) {
                props.load(is);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void save() {
        try {
            if (!Files.exists(FileManager.getMinecraftDir())) {
                Files.createDirectories(FileManager.getMinecraftDir());
            }
            try (OutputStream os = Files.newOutputStream(CONFIG_PATH)) {
                props.store(os, "Configuracion del Launcher");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getUsername() {
        return props.getProperty("username", "");
    }

    public static void setUsername(String username) {
        props.setProperty("username", username);
        save();
    }

    public static String getRam() {
        // Por defecto 4096MB (4GB)
        return props.getProperty("ram", "4096");
    }

    public static void setRam(String ram) {
        props.setProperty("ram", ram);
        save();
    }
}