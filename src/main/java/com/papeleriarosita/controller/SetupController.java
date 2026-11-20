package com.papeleriarosita.controller;

import com.papeleriarosita.dao.UsuarioDAO;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Controlador para la Configuración Inicial de Cuentas en Papelería Rosita.
 * Permite registrar la PRIMERA y ÚNICA cuenta de Administradora (Rosita).
 * Si ya existe una Administradora, este formulario se bloquea automáticamente.
 */
public class SetupController {

    @FXML private TextField txtNombre;
    @FXML private TextField txtCorreo;

    @FXML private PasswordField txtContrasena;
    @FXML private TextField txtContrasenaVisible;
    @FXML private Button btnToggleContrasena;

    @FXML private PasswordField txtConfirmar;
    @FXML private TextField txtConfirmarVisible;
    @FXML private Button btnToggleConfirmar;

    @FXML private Button btnRegistrar;
    @FXML private Button btnVolver;
    @FXML private Label messageLabel;

    private boolean isPassVisible = false;
    private boolean isConfirmVisible = false;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML
    public void initialize() {
        // Sincronizar campos de contraseña con sus homólogos visibles
        if (txtContrasena != null && txtContrasenaVisible != null) {
            txtContrasenaVisible.textProperty().bindBidirectional(txtContrasena.textProperty());
        }
        if (txtConfirmar != null && txtConfirmarVisible != null) {
            txtConfirmarVisible.textProperty().bindBidirectional(txtConfirmar.textProperty());
        }

        // Bloquear si ya existe CUALQUIER usuario (no solo administradora)
        // La configuración inicial solo está disponible cuando la tabla está completamente vacía
        try {
            if (!usuarioDAO.estaVacia()) {
                bloquearPorExistenciaPrevia();
            }
        } catch (SQLException e) {
            // Si la base de datos aún no está conectada, se informa sin bloquear la vista
            System.err.println("[SETUP] Base de datos aún no disponible: " + e.getMessage());
        }

        clearMessage();
    }

    private void bloquearPorExistenciaPrevia() {
        showError("El sistema ya tiene usuarios registrados. La configuración inicial está bloqueada. Por favor, inicia sesión.");
        if (btnRegistrar != null) {
            btnRegistrar.setDisable(true);
        }
        if (txtNombre != null) txtNombre.setDisable(true);
        if (txtCorreo != null) txtCorreo.setDisable(true);
        if (txtContrasena != null) txtContrasena.setDisable(true);
        if (txtConfirmar != null) txtConfirmar.setDisable(true);
    }

    @FXML
    private void onToggleContrasena() {
        isPassVisible = !isPassVisible;
        if (isPassVisible) {
            txtContrasenaVisible.setVisible(true);
            txtContrasena.setVisible(false);
            btnToggleContrasena.setText("🔒");
            txtContrasenaVisible.requestFocus();
            txtContrasenaVisible.positionCaret(txtContrasenaVisible.getText().length());
        } else {
            txtContrasena.setVisible(true);
            txtContrasenaVisible.setVisible(false);
            btnToggleContrasena.setText("👁");
            txtContrasena.requestFocus();
            txtContrasena.positionCaret(txtContrasena.getText().length());
        }
    }

    @FXML
    private void onToggleConfirmar() {
        isConfirmVisible = !isConfirmVisible;
        if (isConfirmVisible) {
            txtConfirmarVisible.setVisible(true);
            txtConfirmar.setVisible(false);
            btnToggleConfirmar.setText("🔒");
            txtConfirmarVisible.requestFocus();
            txtConfirmarVisible.positionCaret(txtConfirmarVisible.getText().length());
        } else {
            txtConfirmar.setVisible(true);
            txtConfirmarVisible.setVisible(false);
            btnToggleConfirmar.setText("👁");
            txtConfirmar.requestFocus();
            txtConfirmar.positionCaret(txtConfirmar.getText().length());
        }
    }

    @FXML
    private void onRegistrarAdministradora() {
        clearMessage();

        String nombre = (txtNombre != null) ? txtNombre.getText() : "";
        String correo = (txtCorreo != null) ? txtCorreo.getText() : "";
        String contrasena = (txtContrasena != null) ? txtContrasena.getText() : "";
        String confirmar = (txtConfirmar != null) ? txtConfirmar.getText() : "";

        // 1. Validaciones de formulario
        if (nombre == null || nombre.trim().isEmpty()) {
            showError("Por favor, ingresa el nombre de la Administradora.");
            if (txtNombre != null) txtNombre.requestFocus();
            return;
        }

        if (correo == null || correo.trim().isEmpty() || !correo.contains("@")) {
            showError("Por favor, ingresa un correo electrónico válido.");
            if (txtCorreo != null) txtCorreo.requestFocus();
            return;
        }

        if (contrasena == null || contrasena.length() < 6) {
            showError("La contraseña debe contener al menos 6 caracteres.");
            if (txtContrasena != null) txtContrasena.requestFocus();
            return;
        }

        if (!contrasena.equals(confirmar)) {
            showError("Las contraseñas no coinciden. Por favor, verifica.");
            if (txtConfirmar != null) txtConfirmar.requestFocus();
            return;
        }

        // 2. Ejecución segura con UsuarioDAO
        try {
            // Doble comprobación atómica: impedir si ya existe CUALQUIER usuario
            if (!usuarioDAO.estaVacia()) {
                bloquearPorExistenciaPrevia();
                return;
            }

            usuarioDAO.registrarPrimeraAdministradora(nombre.trim(), correo.trim(), contrasena);

            showSuccess("¡Cuenta de Administradora creada exitosamente! Redirigiendo al inicio de sesión...");
            if (btnRegistrar != null) {
                btnRegistrar.setDisable(true);
            }

            // Pausa de 1.5 segundos antes de redirigir al login
            PauseTransition delay = new PauseTransition(Duration.seconds(1.5));
            delay.setOnFinished(event -> onVolverAlLogin());
            delay.play();

        } catch (IllegalStateException e) {
            bloquearPorExistenciaPrevia();
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (SQLException e) {
            showError("Error de base de datos al registrar: " + e.getMessage());
        }
    }

    @FXML
    private void onVolverAlLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/main-view.fxml"));
            Parent loginRoot = loader.load();

            Stage stage = (Stage) btnVolver.getScene().getWindow();
            stage.getScene().setRoot(loginRoot);
            stage.setTitle("Papelería Rosita - Iniciar Sesión");
        } catch (IOException e) {
            showError("No fue posible volver a la pantalla de inicio: " + e.getMessage());
        }
    }

    private void showError(String text) {
        if (messageLabel != null) {
            messageLabel.getStyleClass().removeAll("message-error", "message-success");
            messageLabel.getStyleClass().add("message-error");
            messageLabel.setText(text);
            messageLabel.setVisible(true);
            messageLabel.setManaged(true);
        }
    }

    private void showSuccess(String text) {
        if (messageLabel != null) {
            messageLabel.getStyleClass().removeAll("message-error", "message-success");
            messageLabel.getStyleClass().add("message-success");
            messageLabel.setText(text);
            messageLabel.setVisible(true);
            messageLabel.setManaged(true);
        }
    }

    private void clearMessage() {
        if (messageLabel != null) {
            messageLabel.getStyleClass().removeAll("message-error", "message-success");
            messageLabel.setText("");
            messageLabel.setVisible(false);
            messageLabel.setManaged(false);
        }
    }
}
