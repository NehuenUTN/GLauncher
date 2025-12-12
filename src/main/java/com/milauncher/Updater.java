package com.milauncher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Updater {

    // --- CONFIGURACIÓN ---
    private static final String CURRENT_VERSION = "1.1.2";
    private static final String VERSION_URL = "https://raw.githubusercontent.com/NehuenUTN/GLauncher/refs/heads/main/version.json";

    // Ruta temporal para descargar el nuevo instalador
    private static final Path DOWNLOAD_PATH = Paths.get(System.getProperty("java.io.tmpdir"), "GLauncher_Setup_New.exe");

    public static void checkUpdate() {
        new Thread(() -> {
            try {
                if (VERSION_URL.isEmpty()) return;

                // 1. Obtener info de versión
                JsonObject versionInfo;
                try (InputStreamReader reader = new InputStreamReader(new URL(VERSION_URL).openStream())) {
                    versionInfo = JsonParser.parseReader(reader).getAsJsonObject();
                }

                String latestVersion = versionInfo.get("latest_version").getAsString();
                String downloadUrl = versionInfo.get("download_url").getAsString();

                System.out.println("Verificando actualizaciones... Actual: " + CURRENT_VERSION + " | Nueva: " + latestVersion);

                // 2. Si hay nueva versión, preguntamos
                if (isNewer(latestVersion, CURRENT_VERSION)) {
                    Platform.runLater(() -> showConfirmDialog(latestVersion, downloadUrl));
                }

            } catch (Exception e) {
                System.err.println("Error update: " + e.getMessage());
            }
        }).start();
    }

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
        } catch (Exception e) { return false; }
        return false;
    }

    // Paso 1: Preguntar si quiere actualizar
    private static void showConfirmDialog(String latestVersion, String downloadUrl) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Actualización Disponible");
        alert.setHeaderText("¡Nueva versión " + latestVersion + " disponible!");
        alert.setContentText("Es necesario actualizar el launcher para continuar.\nLa descarga puede tomar unos minutos.");

        ButtonType btnUpdate = new ButtonType("Actualizar");
        ButtonType btnExit = new ButtonType("Salir");

        alert.getButtonTypes().setAll(btnUpdate, btnExit);

        alert.showAndWait().ifPresent(type -> {
            if (type == btnUpdate) {
                // Si acepta, mostramos la ventana de descarga
                showDownloadProgressWindow(downloadUrl);
            } else {
                Platform.exit();
                System.exit(0);
            }
        });
    }

    // Paso 2: Ventana de Descarga con Barra (Bloqueante)
    private static void showDownloadProgressWindow(String downloadUrl) {
        Stage progressStage = new Stage();
        progressStage.initModality(Modality.APPLICATION_MODAL); // BLOQUEA la ventana principal
        progressStage.setTitle("Descargando Actualización...");
        progressStage.setResizable(false);

        Label lblStatus = new Label("Iniciando descarga...");
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        VBox layout = new VBox(15, lblStatus, progressBar);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-padding: 20; -fx-background-color: #2b2b2b;");
        lblStatus.setStyle("-fx-text-fill: white;");

        progressStage.setScene(new Scene(layout));
        progressStage.show();

        // Iniciar descarga en hilo separado
        new Thread(() -> downloadFileWithProgress(downloadUrl, progressBar, lblStatus, progressStage)).start();
    }

    // Paso 3: Lógica de descarga byte a byte para mover la barra
    private static void downloadFileWithProgress(String urlString, ProgressBar bar, Label lbl, Stage stage) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            long fileSize = connection.getContentLengthLong(); // Tamaño total

            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(DOWNLOAD_PATH.toFile())) {

                byte[] buffer = new byte[8192]; // Buffer de 8KB
                int bytesRead;
                long totalRead = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    // Calcular porcentaje
                    double progress = (double) totalRead / fileSize;

                    // Actualizar UI (Importante: Platform.runLater)
                    final long finalTotalRead = totalRead;
                    Platform.runLater(() -> {
                        bar.setProgress(progress);
                        lbl.setText(String.format("Descargado: %.1f MB / %.1f MB",
                                finalTotalRead / 1024.0 / 1024.0, fileSize / 1024.0 / 1024.0));
                    });
                }
            }

            // Descarga finalizada
            Platform.runLater(() -> {
                stage.close();
                runInstaller();
            });

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                stage.close();
                Alert error = new Alert(AlertType.ERROR);
                error.setTitle("Error");
                error.setHeaderText("Fallo en la descarga");
                error.setContentText("Error: " + e.getMessage());
                error.showAndWait();
            });
        }
    }

    // Paso 4: Ejecutar el instalador
    private static void runInstaller() {
        try {
            System.out.println("Ejecutando instalador...");
            new ProcessBuilder(DOWNLOAD_PATH.toAbsolutePath().toString()).start();
            Platform.exit();
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}