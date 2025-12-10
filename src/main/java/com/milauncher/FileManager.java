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

    public static Path getMinecraftDir() {
        return Paths.get(System.getenv("APPDATA"), ".GermFlogLauncher");
    }

    // Acepta un 'Consumer' para actualizar la barra de progreso
    public static void ensureMinecraftFiles(Consumer<Double> progressUpdater, Runnable onFinished) {
        new Thread(() -> {
            try {
                Path dir = getMinecraftDir();
                if (!Files.exists(dir)) Files.createDirectories(dir);

                Path versionDir = dir.resolve("versions").resolve("1.20.1");

                // Verificar si falta la version. En un futuro chequeando version.txt
                if (!Files.exists(versionDir)) {
                    System.out.println("Instalando archivos...");

                    // Como vamos a descomprimir, borramos lo viejo para limpiar.
                    deleteFolder(dir.resolve("mods"));

                    Path localZip = Paths.get(System.getProperty("user.dir"), "minecraft_package.zip");
                    if (!Files.exists(localZip)) {
                        localZip = Paths.get("minecraft_package.zip");
                    }
                    if (Files.exists(localZip)) {
                        long totalSize = Files.size(localZip); // Tamaño aproximado para calcular progreso
                        unzip(localZip, dir, totalSize, progressUpdater);
                    } else {
                        System.out.println("No se encontró el ZIP.");
                    }
                }
                else {
                    System.out.println("Archivos verificados correctamente.");
                    progressUpdater.accept(1.0); // Barra llena instantánea si ya está instalado
                }

                // Al terminar, avisamos a la UI (siempre en el hilo de JavaFX)
                Platform.runLater(() -> {
                    progressUpdater.accept(1.0); // 100%
                    onFinished.run();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void unzip(Path zipPath, Path dest, long totalSize, Consumer<Double> progressUpdater) throws IOException {
        try (InputStream fis = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {

            ZipEntry entry;
            long bytesRead = 0;

            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = dest.resolve(entry.getName());

                // Actualizar progreso aproximado
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    try (OutputStream fos = Files.newOutputStream(newPath)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                            // Aquí podrías sumar bytes y calcular porcentaje real si pasas el tamaño total descomprimido.
                        }
                    }
                }

                // Cálculo de progreso "fake" pero visual para el usuario (avanza un poquito por archivo)
                // Lo ideal es contar bytes leídos del FileInputStream original, pero requiere estructura compleja.
                final double currentProgress = Math.random(); // Placeholder: En UI real implementaremos avance visual indeterminado o por bytes.
            }
        }
    }

    // Método para borrar carpetas recursivamente (limpieza de mods)
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