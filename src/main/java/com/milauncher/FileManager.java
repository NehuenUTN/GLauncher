package com.milauncher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.URL;

import javafx.application.Platform;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileManager {

    private static final String VERSION_URL = "https://raw.githubusercontent.com/NehuenUTN/GLauncher/refs/heads/main/version.json";

    public static Path getMinecraftDir() {
        return Paths.get(System.getenv("APPDATA"), ".GermFlogLauncher");
    }

    // Acepta un 'Consumer' para actualizar la barra de progreso
    // Le agregamos un Consumer<String> para poder enviarle textos a la pantalla principal
    public static void ensureMinecraftFiles(Consumer<Double> progressUpdater, Consumer<String> statusUpdater, Runnable onFinished) {
        new Thread(() -> {
            try {
                Path dir = getMinecraftDir();
                if (!Files.exists(dir)) Files.createDirectories(dir);

                Path versionFile = dir.resolve("pack_version.txt");

                // 1. PREGUNTARLE A GITHUB CUÁL ES LA ÚLTIMA VERSIÓN
                statusUpdater.accept("Verificando actualizaciones de mods...");
                String remoteVersion = "1.0.0"; // Versión por defecto si falla algo
                String packageUrl = "";

                try (InputStreamReader reader = new InputStreamReader(new URL(VERSION_URL).openStream())) {
                    JsonObject versionInfo = JsonParser.parseReader(reader).getAsJsonObject();
                    // Leemos los nuevos campos que vamos a agregar al JSON
                    if (versionInfo.has("modpack_version") && versionInfo.has("modpack_url")) {
                        remoteVersion = versionInfo.get("modpack_version").getAsString();
                        packageUrl = versionInfo.get("modpack_url").getAsString();
                    }
                } catch (Exception e) {
                    System.err.println("No se pudo leer version.json de GitHub: " + e.getMessage());
                }

                // 2. COMPARAR CON LO QUE TIENE EL JUGADOR INSTALADO
                boolean needUpdate = true;
                if (Files.exists(versionFile)) {
                    String installedVersion = Files.readString(versionFile).trim();
                    // Si la versión instalada es igual a la de GitHub, no hacemos nada
                    if (installedVersion.equals(remoteVersion) || packageUrl.isEmpty()) {
                        needUpdate = false;
                    }
                }

                // 3. DESCARGAR Y EXTRAER SI ES NECESARIO
                if (needUpdate && !packageUrl.isEmpty()) {
                    System.out.println("Actualizando mods a la versión remota: " + remoteVersion);

                    statusUpdater.accept("Limpiando mods antiguos...");
                    deleteFolder(dir.resolve("mods")); // Borramos la carpeta mods vieja

                    Path localZip = dir.resolve("minecraft_package.zip");

                    statusUpdater.accept("Descargando actualización (esto puede tardar)...");
                    updateProgressSafe(progressUpdater, 0.0);
                    downloadPackage(packageUrl, localZip, progressUpdater); // (El método de descarga que agregamos antes)

                    // Descomprimir
                    if (Files.exists(localZip)) {
                        statusUpdater.accept("Extrayendo archivos...");
                        long totalSize = Files.size(localZip);
                        unzip(localZip, dir, totalSize, progressUpdater);

                        // Limpieza
                        Files.deleteIfExists(localZip);
                        // Guardamos la nueva versión en el archivo de texto del jugador
                        Files.writeString(versionFile, remoteVersion);
                        System.out.println("Actualización de mods completada.");
                    }
                } else {
                    System.out.println("Los mods están actualizados a la versión: " + remoteVersion);
                    updateProgressSafe(progressUpdater, 1.0);
                }

                Platform.runLater(onFinished);

            } catch (Exception e) {
                e.printStackTrace();
                // Si hay un error crítico, intentamos lanzar el juego igual con lo que haya
                Platform.runLater(onFinished);
            }
        }).start();
    }

    private static void unzip(Path zipPath, Path dest, long totalSize, Consumer<Double> progressUpdater) throws IOException {
        // Usamos un InputStream "monitorizado" para contar los bytes reales que se leen del disco
        try (InputStream fis = Files.newInputStream(zipPath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             // Aquí envolvemos el stream para interceptar la lectura
             InputStream progressStream = new InputStream() {
                 long bytesRead = 0;
                 double lastProgress = 0;

                 @Override
                 public int read() throws IOException {
                     int b = bis.read();
                     if (b != -1) update(1);
                     return b;
                 }

                 @Override
                 public int read(byte[] b, int off, int len) throws IOException {
                     int n = bis.read(b, off, len);
                     if (n > 0) update(n);
                     return n;
                 }

                 private void update(int n) {
                     bytesRead += n;
                     double currentProgress = (double) bytesRead / totalSize;

                     // Optimizacion: Solo actualizamos la UI si avanzó al menos un 1% o terminamos
                     // Esto evita congelar la interfaz con millones de llamadas
                     if (currentProgress - lastProgress >= 0.01 || currentProgress >= 1.0) {
                         lastProgress = currentProgress;
                         updateProgressSafe(progressUpdater, currentProgress);
                     }
                 }
             };
             ZipInputStream zis = new ZipInputStream(progressStream)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = dest.resolve(entry.getName());

                if (Files.exists(newPath)) {
                    String fileName = newPath.getFileName().toString();
                    if (fileName.equals("options.txt") ||
                            fileName.equals("servers.dat") ||
                            fileName.equals("optionsof.txt")) { // optionsof.txt es de Optifine

                        System.out.println("Saltando archivo protegido: " + fileName);
                        continue; // Salta al siguiente archivo del ZIP sin extraer este
                    }
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    try (OutputStream fos = Files.newOutputStream(newPath)) {
                        byte[] buffer = new byte[8192]; // Buffer de 8KB para copia rápida
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                // Nota: El progreso se actualiza automáticamente gracias al 'progressStream'
                // mientras el ZipInputStream lee datos.
            }
        }
    }

    // Método seguro para actualizar la UI desde un hilo secundario
    private static void updateProgressSafe(Consumer<Double> updater, double value) {
        Platform.runLater(() -> updater.accept(value));
    }

    private static void deleteFolder(Path path) {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void downloadPackage(String urlString, Path dest, Consumer<Double> progressUpdater) throws IOException {
        java.net.URL url = new java.net.URL(urlString);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        long fileSize = conn.getContentLengthLong();

        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            double lastProgress = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (fileSize > 0) {
                    double progress = (double) totalRead / fileSize;
                    // Actualiza la barra cada 1% para no saturar la UI
                    if (progress - lastProgress >= 0.01 || progress >= 1.0) {
                        lastProgress = progress;
                        updateProgressSafe(progressUpdater, progress);
                    }
                }
            }
        }
    }
}