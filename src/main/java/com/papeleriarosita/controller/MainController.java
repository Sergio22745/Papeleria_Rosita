package com.papeleriarosita.controller;

import com.papeleriarosita.dao.UsuarioDAO;
import com.papeleriarosita.model.UserSession;
import com.papeleriarosita.model.Usuario;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Controlador de la pantalla de inicio de sesión de Papelería Rosita.
 * Autentica mediante credenciales explícitas (correo electrónico y contraseña)
 * contra la tabla 'usuarios' de MySQL utilizando UsuarioDAO.
 * También detecta si el sistema no tiene cuentas y permite acceder a la Configuración Inicial.
 */
public class MainController {

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField passwordTextField;

    @FXML
    private Button btnTogglePassword;

    @FXML
    private Button btnLogin;

    @FXML
    private Hyperlink linkSetupInicial;

    @FXML
    private Label messageLabel;

    private boolean isPasswordVisible = false;

    // DAO para acceso a la tabla 'usuarios'
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML
    public void initialize() {
        // Limpiar campos al inicializar
        if (emailField != null) {
            emailField.setText("");
        }
        if (passwordField != null) {
            passwordField.setText("");
        }
        if (passwordTextField != null) {
            passwordTextField.setText("");
            passwordTextField.setVisible(false);
        }

        // Sincronizar el texto bidireccionalmente entre PasswordField y TextField
        if (passwordField != null && passwordTextField != null) {
            passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());
        }

        // Limpiar mensaje de error cuando el usuario empiece a escribir
        if (emailField != null) {
            emailField.textProperty().addListener((obs, oldVal, newVal) -> limpiarMensajeSiHayError());
        }
        if (passwordField != null) {
            passwordField.textProperty().addListener((obs, oldVal, newVal) -> limpiarMensajeSiHayError());
        }

        // Comprobar si ya existe Administradora para mostrar u ocultar la configuración inicial
        verificarDisponibilidadSetupInicial();

        clearMessage();
    }

    private void verificarDisponibilidadSetupInicial() {
        try {
            if (usuarioDAO.existeAdministradora()) {
                if (linkSetupInicial != null) {
                    linkSetupInicial.setVisible(false);
                    linkSetupInicial.setManaged(false);
                }
            } else {
                if (linkSetupInicial != null) {
                    linkSetupInicial.setVisible(true);
                    linkSetupInicial.setManaged(true);
                }
            }
        } catch (SQLException e) {
            // Si la conexión con MySQL aún no está lista, se mantiene el enlace disponible
            if (linkSetupInicial != null) {
                linkSetupInicial.setVisible(true);
                linkSetupInicial.setManaged(true);
            }
        }
    }

    private void limpiarMensajeSiHayError() {
        if (messageLabel != null && messageLabel.isVisible() && messageLabel.getStyleClass().contains("message-error")) {
            clearMessage();
        }
    }

    @FXML
    private void onTogglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            passwordTextField.setVisible(true);
            passwordField.setVisible(false);
            btnTogglePassword.setText("🔒 Ocultar");
            passwordTextField.requestFocus();
            passwordTextField.positionCaret(passwordTextField.getText().length());
        } else {
            passwordField.setVisible(true);
            passwordTextField.setVisible(false);
            btnTogglePassword.setText("👁 Mostrar");
            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
        }
    }

    @FXML
    private void onLoginButtonClick() {
        // 1. Validar correo electrónico
        String enteredEmail = (emailField != null) ? emailField.getText() : "";
        if (enteredEmail == null || enteredEmail.trim().isEmpty()) {
            showError("Por favor, ingresa tu correo electrónico.");
            if (emailField != null) {
                emailField.requestFocus();
            }
            return;
        }

        // 2. Validar contraseña
        String enteredPassword = passwordField.getText();
        if (enteredPassword == null || enteredPassword.isEmpty()) {
            showError("Por favor, ingresa tu contraseña de acceso.");
            focusPasswordField();
            return;
        }

        // 3. Autenticación mediante credenciales (correo y contraseña)
        try {
            Optional<Usuario> usuarioOpt = usuarioDAO.autenticarPorCredenciales(enteredEmail.trim(), enteredPassword);

            if (usuarioOpt.isEmpty()) {
                showError("Credenciales incorrectas. Verifica tu correo y contraseña.");
                focusPasswordField();
                return;
            }

            Usuario usuario = usuarioOpt.get();

            // Normalización a los roles oficiales del proyecto (ADMINISTRADORA / EMPLEADA)
            String rolOficial = UserSession.normalizarRol(usuario.getRol());
            UserSession session = new UserSession(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getCorreo(),
                rolOficial
            );

            // 4. Navegar al Panel Principal
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/dashboard-view.fxml"));
            Parent dashboardRoot = loader.load();

            DashboardController dashboardController = loader.getController();
            dashboardController.initSession(session);

            Stage stage = (Stage) btnLogin.getScene().getWindow();
            stage.getScene().setRoot(dashboardRoot);
            stage.setTitle("Papelería Rosita - Panel Principal");

        } catch (SQLException e) {
            System.err.println("[ERROR LOGIN] Error de base de datos durante autenticación: " + e.getMessage());
            showError("Error de conexión a la base de datos: " + e.getMessage());
            focusPasswordField();
        } catch (IOException e) {
            System.err.println("[ERROR LOGIN] Error al cargar la vista del panel principal: " + e.getMessage());
            showError("No fue posible cargar el panel principal: " + e.getMessage());
        }
    }

    @FXML
    private void onIrASetupInicial() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/setup-view.fxml"));
            Parent setupRoot = loader.load();

            Stage stage = (Stage) btnLogin.getScene().getWindow();
            stage.getScene().setRoot(setupRoot);
            stage.setTitle("Papelería Rosita - Configuración Inicial");
        } catch (IOException e) {
            showError("No fue posible cargar el asistente de configuración inicial: " + e.getMessage());
        }
    }

    @FXML
    private void onVistaPreviaAdmin() {
        iniciarModoVistaPrevia(UserSession.ROL_ADMINISTRADORA);
    }

    @FXML
    private void onVistaPreviaEmpleada() {
        iniciarModoVistaPrevia(UserSession.ROL_EMPLEADA);
    }

    private void iniciarModoVistaPrevia(String rol) {
        try {
            UserSession previewSession = UserSession.crearModoVistaPrevia(rol);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/dashboard-view.fxml"));
            Parent dashboardRoot = loader.load();

            DashboardController dashboardController = loader.getController();
            dashboardController.initSession(previewSession);

            Stage stage = (Stage) btnLogin.getScene().getWindow();
            stage.getScene().setRoot(dashboardRoot);
            stage.setTitle("Papelería Rosita - [Modo Vista Previa: " + previewSession.getRole() + "]");

        } catch (IOException e) {
            System.err.println("[ERROR LOGIN] Error al cargar la vista previa: " + e.getMessage());
            showError("No fue posible cargar el modo de vista previa: " + e.getMessage());
        }
    }

    private void focusPasswordField() {
        if (isPasswordVisible) {
            passwordTextField.requestFocus();
        } else {
            passwordField.requestFocus();
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

    private void clearMessage() {
        if (messageLabel != null) {
            messageLabel.getStyleClass().removeAll("message-error", "message-success");
            messageLabel.setText("");
            messageLabel.setVisible(false);
            messageLabel.setManaged(false);
        }
    }
}
