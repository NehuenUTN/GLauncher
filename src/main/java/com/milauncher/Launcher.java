package com.milauncher;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.InputStream;

public class Launcher extends Application {

    private StackPane rootPane;
    private VBox loginScreen;
    private BorderPane mainDashboard;

    // Elementos del Dashboard
    private Label userLabel;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Button startButton;

    @Override
    public void start(Stage stage) {
        System.out.println("--- INICIANDO LAUNCHER (DEBUG) ---");

        try {
            // Cargar config
            ConfigManager.load();
            System.out.println("Configuración cargada.");

            Updater.checkUpdate();

            rootPane = new StackPane();

            // Fondo base (Color sólido en caso de que no cargue la imagen)
            rootPane.setStyle("-fx-background-color: #2b2b2b;");

            // Intentar cargar imagen de fondo
            try {
                InputStream bgStream = getClass().getResourceAsStream("/background.png");
                if (bgStream != null) {
                    Image img = new Image(bgStream);
                    BackgroundImage bg = new BackgroundImage(img, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, new BackgroundSize(100, 100, true, true, true, true));
                    rootPane.setStyle(null);
                    rootPane.setBackground(new Background(bg));
                    System.out.println("Fondo cargado OK.");
                } else {
                    System.out.println("AVISO: background.png no encontrado. Usando color sólido.");
                }
            } catch (Exception e) {
                System.err.println("Error cargando fondo: " + e.getMessage());
            }

            // Crear pantallas
            createLoginScreen();
            createMainDashboard(stage);

            // Seleccionar pantalla inicial
            if (ConfigManager.getUsername().isEmpty()) {
                rootPane.getChildren().add(loginScreen);
            } else {
                rootPane.getChildren().add(mainDashboard);
                updateUserInfo();
            }

            Scene scene = new Scene(rootPane, 1280, 720);

            // 3. Cargar CSS (Protegido)
            try {
                if (getClass().getResource("/style.css") != null) {
                    scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
                    System.out.println("CSS cargado OK.");
                } else {
                    System.out.println("AVISO: style.css no encontrado. Usando estilos por defecto.");
                }
            } catch (Exception e) {
                System.err.println("Error cargando CSS: " + e.getMessage());
            }

            stage.setScene(scene);
            stage.setTitle("GLauncher");

            // 4. Cargar Icono de Ventana
            try {
                InputStream logoStream = getClass().getResourceAsStream("/logo.png");
                if (logoStream != null) {
                    stage.getIcons().add(new Image(logoStream));
                    System.out.println("Icono de ventana cargado OK.");
                } else {
                    System.out.println("AVISO: logo.png no encontrado. Usando icono por defecto de Java.");
                }
            } catch (Exception e) {
                System.err.println("Error cargando logo.png: " + e.getMessage());
            }

            stage.show();
            System.out.println("--- LAUNCHER INICIADO EXITOSAMENTE ---");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createLoginScreen() {
        loginScreen = new VBox(20);
        loginScreen.setAlignment(Pos.CENTER);
        loginScreen.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-padding: 50; -fx-max-width: 400; -fx-background-radius: 20;");
        loginScreen.setMaxSize(400, 300);

        Label title = new Label("Ingresa tu nombre de usuario:");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        TextField userField = new TextField();
        userField.setPromptText("Ingresa tu nombre de usuario");
        userField.setStyle("-fx-font-size: 16px;");

        Button enterBtn = new Button("Entrar");
        enterBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 16px;");
        enterBtn.setDefaultButton(true);
        enterBtn.setOnAction(e -> {
            String name = userField.getText().trim();
            if (!name.isEmpty()) {
                ConfigManager.setUsername(name);
                switchScreen(mainDashboard);
                updateUserInfo();
            }
        });

        loginScreen.getChildren().addAll(title, userField, enterBtn);
    }

    private void createMainDashboard(Stage stage) {
        mainDashboard = new BorderPane();
        mainDashboard.setPadding(new javafx.geometry.Insets(20));

        // --- TOP ---
        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER);

        VBox userInfo = new VBox(5);
        userLabel = new Label("Usuario: " + ConfigManager.getUsername());
        userLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        Hyperlink changeUser = new Hyperlink("Modificar");
        changeUser.setStyle("-fx-text-fill: #aaa;");
        changeUser.setOnAction(e -> switchScreen(loginScreen));

        userInfo.getChildren().addAll(userLabel, changeUser);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox socialBox = new VBox(5);
        socialBox.setAlignment(Pos.TOP_RIGHT);
        Label socialTitle = new Label("Redes           ");
        socialTitle.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;");

        HBox icons = new HBox(10);
        icons.getChildren().addAll(
                createLinkButton("youtube.png", "https://www.youtube.com/@germflog", stage),
                createLinkButton("twitch.png", "https://www.twitch.tv/germflog", stage),
                createLinkButton("kick.png", "https://kick.com/germflog", stage),
                createLinkButton("twitter.png", "https://x.com/germflog_03", stage)
        );
        socialBox.getChildren().addAll(socialTitle, icons);

        topBar.getChildren().addAll(userInfo, spacer, socialBox);
        mainDashboard.setTop(topBar);

        // --- CENTER / BOTTOM ---
        VBox bottomContainer = new VBox(15);
        bottomContainer.setAlignment(Pos.BOTTOM_CENTER);
        bottomContainer.setPadding(new javafx.geometry.Insets(0, 0, 50, 0));

        HBox playBox = new HBox(15);
        playBox.setAlignment(Pos.CENTER);

        startButton = new Button("INICIAR");
        startButton.setPrefSize(200, 60);
        startButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold; -fx-background-radius: 10;");

        Button settingsBtn = new Button("⚙");
        settingsBtn.setStyle("-fx-font-size: 20px;");
        settingsBtn.setOnAction(e -> showRamConfig());

        playBox.getChildren().addAll(startButton, settingsBtn);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setVisible(false);

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: white;");

        bottomContainer.getChildren().addAll(playBox, statusLabel, progressBar);
        mainDashboard.setCenter(bottomContainer);

        // --- BOTTOM LEFT (Apoyo) ---
        HBox supportBox = new HBox(10);
        supportBox.setAlignment(Pos.BOTTOM_LEFT);
        Label supportText = new Label("Si queres apoyar al proyecto:");
        supportText.setStyle("-fx-text-fill: white; -fx-font-size: 18px");

        // Carga protegida del icono de cafecito
        ImageView supportIcon = new ImageView();
        try {
            InputStream is = getClass().getResourceAsStream("/cafecito.png");
            if (is != null) {
                Image image = new Image(is);
                supportIcon.setImage(image);
                supportIcon.setFitWidth(24);
                supportIcon.setFitHeight(24);
            } else {
                System.out.println("AVISO: cafecito.png no encontrado.");
            }
        } catch (Exception e) {
            System.err.println("Error cafecito icon: " + e.getMessage());
        }

        Button donateBtn = new Button();
        if (supportIcon.getImage() != null) {
            donateBtn.setGraphic(supportIcon);
            donateBtn.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        } else {
            donateBtn.setText("♥"); // Fallback a texto si no hay imagen
            donateBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        }
        donateBtn.setOnAction(e -> getHostServices().showDocument("https://cafecito.app/germflog"));

        supportBox.getChildren().addAll(supportText, donateBtn);
        mainDashboard.setBottom(supportBox);

        startButton.setOnAction(e -> {
            startButton.setDisable(true);
            progressBar.setVisible(true);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            statusLabel.setText("Verificando archivos y descomprimiendo...");

            FileManager.ensureMinecraftFiles(
                    progress -> progressBar.setProgress(progress),
                    () -> {
                        statusLabel.setText("Iniciando Minecraft...");
                        progressBar.setProgress(1.0);
                        new Thread(ForgeLauncher::launchGame).start(); // Lanzar el juego
                        // Cerrar el launcher
                        new Thread(() -> {
                            try { Thread.sleep(2000); } catch (InterruptedException ex) {}
                            javafx.application.Platform.runLater(() -> {
                                javafx.application.Platform.exit();
                                System.exit(0); // Fuerza bruta para matar todo proceso de JavaFX
                            });
                        }).start();
                    }
            );
        });
    }

    private Button createLinkButton(String imageFileName, String url, Stage stage) {
        Button btn = new Button();
        try {
            InputStream is = getClass().getResourceAsStream("/" + imageFileName);
            if (is != null) {
                ImageView icon = new ImageView(new Image(is));
                icon.setFitWidth(30);
                icon.setFitHeight(30);
                btn.setGraphic(icon);
                btn.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            } else {
                // Si no hay imagen, mostramos texto de respaldo (ej: "youtube.png" -> "YT")
                String textFallback = imageFileName.length() > 2 ? imageFileName.substring(0, 2).toUpperCase() : "??";
                btn.setText(textFallback);
                btn.setStyle("-fx-background-color: #555; -fx-text-fill: white;");
                System.out.println("AVISO: " + imageFileName + " no encontrado.");
            }
        } catch (Exception e) {
            btn.setText("??");
        }
        btn.setOnAction(e -> getHostServices().showDocument(url));
        return btn;
    }

    private void showRamConfig() {
        TextInputDialog dialog = new TextInputDialog(ConfigManager.getRam());
        dialog.setTitle("Configuración");
        dialog.setHeaderText("Asignación de Memoria RAM (MB)");
        dialog.setContentText("RAM:");
        dialog.showAndWait().ifPresent(ram -> ConfigManager.setRam(ram));
    }

    private void switchScreen(javafx.scene.Node screen) {
        rootPane.getChildren().clear();
        rootPane.getChildren().add(screen);
    }

    private void updateUserInfo() {
        if (userLabel != null) userLabel.setText("Usuario: " + ConfigManager.getUsername());
    }

    public static void main(String[] args) {
        // Iniciamos la aplicación
        launch(args);
    }
}