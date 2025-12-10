package com.milauncher;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ForgeLauncher {

    public static void launchGame() {
        try {
            Path root = FileManager.getMinecraftDir();

            // --- DETECCIÓN DE JAVA ---
            // Buscamos si hay una carpeta "runtime" dentro de nuestro directorio
            Path bundledJava = root.resolve("runtime").resolve("jdk-21.0.8").resolve("bin").resolve("java.exe");
            String javaCommand = "java"; // Por defecto usa el del sistema

            if (Files.exists(bundledJava)) {
                System.out.println("Usando Java integrado: " + bundledJava);
                javaCommand = bundledJava.toAbsolutePath().toString();
            } else {
                System.out.println("Usando Java del sistema (puede fallar si no es v17+)");
            }

            String vanillaVersion = "1.20.1";
            String forgeVersion = "1.20.1-forge-47.4.10";

            System.out.println("[Launcher] Root Minecraft dir = " + root);

            // 1. Cargar JSON vanilla
            Path vanillaJsonPath = root.resolve("versions").resolve(vanillaVersion)
                    .resolve(vanillaVersion + ".json");
            if (!Files.exists(vanillaJsonPath)) {
                System.out.println("ERROR CRITICO: No se encuentra el JSON Vanilla: " + vanillaJsonPath);
                return;
            }
            JsonObject vanillaJson = JsonParser.parseString(Files.readString(vanillaJsonPath)).getAsJsonObject();
            System.out.println("[Launcher] Carga de JSON vanilla OK");

            // 2. Cargar JSON Forge
            Path forgeJsonPath = root.resolve("versions").resolve(forgeVersion)
                    .resolve(forgeVersion + ".json");
            if (!Files.exists(forgeJsonPath)) {
                System.out.println("ERROR CRITICO: No se encuentra el JSON NeoForge: " + forgeJsonPath);
                return;
            }
            JsonObject forgeJson = JsonParser.parseString(Files.readString(forgeJsonPath)).getAsJsonObject();
            System.out.println("[Launcher] Carga de JSON NeoForge OK");

            // 3. MainClass
            String mainClass = forgeJson.get("mainClass").getAsString();

            // 4. Classpath
            String classPath = buildClasspath(root, vanillaVersion, forgeJson, vanillaJson);

            // 5. JVM args + RAM Configurada
            String ram = ConfigManager.getRam(); // <-- LECTURA DE CONFIG
            List<String> jvmArgs = buildJvmArgs(forgeJson, classPath, root, forgeVersion);

            // 6. Game args
            List<String> gameArgs = buildGameArgs(vanillaJson, forgeJson, root, forgeVersion);

            // 7. Ejecutar Java
            List<String> finalCommand = new ArrayList<>();
            finalCommand.add(javaCommand); // <-- USAMOS EL JAVA DETECTADO
            finalCommand.add("-Xmx" + ram + "M"); // <-- RAM CONFIGURADA
            finalCommand.addAll(jvmArgs);
            finalCommand.add(mainClass);
            finalCommand.addAll(gameArgs);

            System.out.println("\n========== JAVA LAUNCH COMMAND ==========");
            System.out.println("Comando listo. Iniciando proceso...");

            ProcessBuilder pb = new ProcessBuilder(finalCommand);
            pb.directory(root.toFile());
            pb.inheritIO();
            pb.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String buildClasspath(Path root, String vanillaVersion, JsonObject forgeJson, JsonObject vanillaJson) {
        Set<String> libs = new LinkedHashSet<>();

        // Añade librerías de FORGE
        addLibrariesFromJson(libs, forgeJson, root);

        // Añade librerías VANILLA (Minecraft base)
        // Al ser un Set, si una librería de Vanilla ya fue añadida por Forge, se ignora y no da error.
        addLibrariesFromJson(libs, vanillaJson, root);

        return String.join(File.pathSeparator, libs);
    }

    // Método auxiliar modificado para aceptar Collection<String> (funciona con Sets y Lists)
    private static void addLibrariesFromJson(Collection<String> libs, JsonObject json, Path root) {
        if (!json.has("libraries")) return;

        for (JsonElement e : json.getAsJsonArray("libraries")) {
            JsonObject lib = e.getAsJsonObject();

            if (!checkRules(lib)) continue;

            if (lib.has("downloads")) {
                JsonObject downloads = lib.getAsJsonObject("downloads");
                if (downloads.has("artifact")) {
                    JsonObject artifact = downloads.getAsJsonObject("artifact");
                    String path = artifact.get("path").getAsString();
                    libs.add(root.resolve("libraries").resolve(path).toString());
                }
            } else {
                // Soporte para librerías sin bloque 'downloads' explícito (común en versiones viejas o mods directos)
                if (lib.has("name")) {
                    String name = lib.get("name").getAsString();
                    String[] parts = name.split(":");
                    if(parts.length >= 3) {
                        String domain = parts[0].replace('.', '/');
                        String artifact = parts[1];
                        String version = parts[2];
                        String jarName = artifact + "-" + version + ".jar";
                        Path p = root.resolve("libraries").resolve(domain).resolve(artifact).resolve(version).resolve(jarName);
                        libs.add(p.toString());
                    }
                }
            }
        }
    }

    private static boolean checkRules(JsonObject lib) {
        if (!lib.has("rules")) return true;

        JsonArray rules = lib.getAsJsonArray("rules");
        boolean allow = false;

        for (JsonElement r : rules) {
            JsonObject rule = r.getAsJsonObject();
            String action = rule.get("action").getAsString();

            if (rule.has("os")) {
                String osName = rule.getAsJsonObject("os").get("name").getAsString();
                if (osName.equalsIgnoreCase("windows")) {
                    allow = action.equals("allow");
                }
            } else {
                if (action.equals("allow")) allow = true;
            }
        }
        return allow;
    }

    private static List<String> buildJvmArgs(JsonObject forgeJson, String cp, Path root, String forgeVersionName) {
        List<String> args = new ArrayList<>();
        // Argumento crítico para que encuentre los natives (DLLs)
        args.add("-Djava.library.path=" + root.resolve("libraries").toString());

        JsonArray jvm = forgeJson.getAsJsonObject("arguments").getAsJsonArray("jvm");

        for (JsonElement e : jvm) {
            if (e.isJsonPrimitive()) {
                args.add(e.getAsString()
                        .replace("${library_directory}", root.resolve("libraries").toString())
                        .replace("${classpath_separator}", File.pathSeparator)
                        .replace("${version_name}", forgeVersionName)
                );
            }
        }

        args.add("-cp");
        args.add(cp);

        return args;
    }

    private static List<String> buildGameArgs(JsonObject vanillaJson, JsonObject forgeJson, Path root, String forgeVersionName) {
        List<String> out = new ArrayList<>();

        if (vanillaJson.has("arguments")) {
            JsonArray gameVanilla = vanillaJson.getAsJsonObject("arguments").getAsJsonArray("game");
            addArgsFromJson(out, gameVanilla, root, forgeVersionName);
        }

        if (forgeJson.has("arguments")) {
            JsonArray gameForge = forgeJson.getAsJsonObject("arguments").getAsJsonArray("game");
            addArgsFromJson(out, gameForge, root, forgeVersionName);
        }

        return out;
    }

    private static void addArgsFromJson(List<String> out, JsonArray arr, Path root, String forgeVersionName) {
        for (JsonElement e : arr) {
            if (e.isJsonPrimitive()) {
                out.add(e.getAsString()
                        .replace("${auth_player_name}", "Player")
                        .replace("${version_name}", forgeVersionName)
                        .replace("${game_directory}", root.toString())
                        .replace("${assets_root}", root.resolve("assets").toString())
                        .replace("${assets_index_name}", "1.20")
                        .replace("${auth_uuid}", "00000000-0000-0000-0000-000000000000")
                        .replace("${auth_access_token}", "0")
                        .replace("${clientid}", "0")
                        .replace("${auth_xuid}", "0")
                        .replace("${user_type}", "mojang")
                        .replace("${version_type}", "release")
                );
            }
        }
    }
}