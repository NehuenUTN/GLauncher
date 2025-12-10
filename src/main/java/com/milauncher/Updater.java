package com.milauncher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert.AlertType;

public class Updater {

    // --- CONFIGURACIÓN ---
    private static final String CURRENT_VERSION = "1.0";
    private static final String VERSION_URL = "";

    // Ruta temporal para descargar el nuevo instalador
    private static final Path DOWNLOAD_PATH = Paths.get(System.getProperty("java.io.tmpdir"), "RustCraft_Setup_New.exe");

    public static void checkUpdate() {
        new Thread(() -> {
            try {
                if (VERSION_URL.isEmpty()) {
                    System.out.println("AVISO: URL de actualización vacía.");
                    return;
                }

                // 1. Descargar el archivo version.json
                JsonObject versionInfo;
                try (InputStreamReader reader = new InputStreamReader(new URL(VERSION_URL).openStream())) {
                    versionInfo = JsonParser.parseReader(reader).getAsJsonObject();
                }

                String latestVersion = versionInfo.get("latest_version").getAsString();
                String downloadUrl = versionInfo.get("download_url").getAsString();

                System.out.println("Verificando actualizaciones... Actual: " + CURRENT_VERSION + " | Nueva: " + latestVersion);

                // 2. Comparar versiones
                if (isNewer(latestVersion, CURRENT_VERSION)) {
                    // Usamos Platform.runLater para mostrar la ventana en el hilo de la interfaz
                    Platform.runLater(() -> showUpdateDialog(latestVersion, downloadUrl));
                } else {
                    System.out.println("El launcher está actualizado.");
                }

            } catch (Exception e) {
                System.err.println("Error al chequear actualizaciones: " + e.getMessage());
            }
        }).start();
    }

    // Lógica de comparación flexible (funciona con "1.0", "1.1", "2.0", etc.)
    private static boolean isNewer(String latest, String current) {
        try {
            String[] l = latest.split("\\.");
            String[] c = current.split("\\.");
            int length = Math.max(l.length, c.length);

            for (int i = 0; i < length; i++) {
                int v1 = i < l.length ? Integer.parseInt(l[i]) : 0;
                int v2 = i < c.length ? Integer.parseInt(c[i]) : 0;

                if (v1 > v2) return true;
                if (v1 < v2) return false;
            }
        } catch (NumberFormatException e) {
            System.err.println("Error al comparar versiones (formato incorrecto): " + e.getMessage());
        }
        return false;
    }

    private static void showUpdateDialog(String latestVersion, String downloadUrl) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Actualización Disponible");
        alert.setHeaderText("¡Nueva Versión " + latestVersion + " disponible!");
        alert.setContentText("Es necesario actualizar el launcher para continuar. ¿Deseas descargarla ahora?");

        ButtonType updateButton = new ButtonType("Actualizar");
        ButtonType laterButton = new ButtonType("Salir"); // Forzamos un poco al usuario a actualizar

        alert.getButtonTypes().setAll(updateButton, laterButton);

        alert.showAndWait().ifPresent(result -> {
            if (result == updateButton) {
                downloadAndRunInstaller(downloadUrl);
            } else {
                Platform.exit();
            }
        });
    }

    private static void downloadAndRunInstaller(String downloadUrl) {
        new Thread(() -> {
            try {
                System.out.println("Descargando instalador desde: " + downloadUrl);

                // 1. Descargar
                try (java.io.InputStream in = new URL(downloadUrl).openStream()) {
                    Files.copy(in, DOWNLOAD_PATH, StandardCopyOption.REPLACE_EXISTING);
                }

                System.out.println("Descarga completa. Ejecutando instalador...");

                // 2. Ejecutar
                new ProcessBuilder(DOWNLOAD_PATH.toAbsolutePath().toString()).start();

                // 3. Cerrar este launcher
                Platform.runLater(() -> {
                    Platform.exit();
                    System.exit(0);
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    Alert error = new Alert(AlertType.ERROR);
                    error.setTitle("Error");
                    error.setHeaderText("Fallo en la descarga");
                    error.setContentText("No se pudo descargar la actualización automáticamente.\n" + e.getMessage());
                    error.showAndWait();
                });
            }
        }).start();
    }
}