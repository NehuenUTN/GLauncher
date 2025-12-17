package com.milauncher;

import javafx.application.Platform;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileManager {

    private static final String PACK_VERSION = "1.1.1";

    public static Path getMinecraftDir() {
        return Paths.get(System.getenv("APPDATA"), ".GermFlogLauncher");
    }

    // Acepta un 'Consumer' para actualizar la barra de progreso
    public static void ensureMinecraftFiles(Consumer<Double> progressUpdater, Runnable onFinished) {
        new Thread(() -> {
            try {
                Path dir = getMinecraftDir();
                if (!Files.exists(dir)) Files.createDirectories(dir);

                Path versionFile = dir.resolve("pack_version.txt");
                boolean needUpdate = true;

                if (Files.exists(versionFile)) {
                    String installedVersion = Files.readString(versionFile).trim();
                    if (installedVersion.equals(PACK_VERSION)) {
                        needUpdate = false;
                    }
                }

                // Lógica de instalación
                if (needUpdate) {
                    System.out.println("Detectada nueva versión del paquete (" + PACK_VERSION + "). Actualizando archivos...");

                    // No borramos 'config' para intentar preservar configuraciones,
                    // pero borramos 'mods' para evitar crasheos.
                    deleteFolder(dir.resolve("mods"));

                    // Buscar el ZIP
                    Path localZip = Paths.get(System.getProperty("user.dir"), "minecraft_package.zip");
                    if (!Files.exists(localZip)) {
                        localZip = Paths.get("minecraft_package.zip");
                    }

                    // Descomprimir
                    if (Files.exists(localZip)) {
                        long totalSize = Files.size(localZip);
                        unzip(localZip, dir, totalSize, progressUpdater);

                        // Marcar como actualizado, escribiendo la nueva version en el archivo
                        Files.writeString(versionFile, PACK_VERSION);
                        System.out.println("Actualización a versión " + PACK_VERSION + " completada.");
                    } else {
                        System.out.println("ERROR CRÍTICO: No se encontró minecraft_package.zip");
                    }
                } else {
                    System.out.println("El paquete está actualizado (" + PACK_VERSION + "). Omitiendo descompresión.");
                    updateProgressSafe(progressUpdater, 1.0);
                }

                Platform.runLater(onFinished);

            } catch (Exception e) {
                e.printStackTrace();
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
}