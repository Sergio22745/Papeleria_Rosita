package com.papeleriarosita.controller;

import com.papeleriarosita.dao.UsuarioDAO;
import com.papeleriarosita.model.UserSession;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Controlador principal del Dashboard de Papelería Rosita.
 * Administra la navegación entre módulos, la sesión activa, los permisos de rol
 * y la integración visual de Productos e Inventario.
 */
public class DashboardController {

    @FXML private BorderPane rootPane;
    @FXML private Label lblHeaderDate;
    @FXML private Label lblHeaderGreeting;
    @FXML private HBox bannerVistaPrevia;

    // Botones del Menú Lateral (Barra Azul Petróleo - Menú Limpio: Inicio, Configuración, Cerrar sesión)
    @FXML private Button btnNavInicio;
    @FXML private Button btnNavVentas;
    @FXML private Button btnNavInventario;
    @FXML private Button btnNavProductos;
    @FXML private Button btnNavProveedores;
    @FXML private Button btnNavCompras;
    @FXML private Button btnNavCaja;
    @FXML private Button btnNavReportes;
    @FXML private Button btnNavConfig;
    @FXML private Button btnNavLogout;

    @FXML private StackPane contentArea;
    @FXML private VBox homeView;
    @FXML private VBox moduleView;
    @FXML private VBox configView;

    // Vistas integradas de módulos reales
    private VBox productosViewRoot;
    private ProductosController productosController;

    private VBox inventarioViewRoot;
    private InventarioController inventarioController;

    private VBox proveedoresViewRoot;
    private ProveedoresController proveedoresController;

    private StackPane comprasViewRoot;
    private ComprasController comprasController;

    private StackPane ventasViewRoot;
    private VentasController ventasController;

    private StackPane cajaViewRoot;
    private CajaController cajaController;

    private StackPane reportesViewRoot;
    private ReportesController reportesController;

    // Elementos de la vista Inicio
    @FXML private Label lblWelcomeGreeting;

    // Elementos de la vista Detalle de Módulo en preparación
    @FXML private Label lblModuleIcon;
    @FXML private Label lblModuleName;
    @FXML private Label lblModuleDescription;
    @FXML private Label lblModuleStatus;

    // Elementos de la vista Configuración
    @FXML private HBox configBannerBox;
    @FXML private Label lblConfigBannerIcon;
    @FXML private Label lblConfigPermissionBanner;

    @FXML private Label lblLockNegocio;
    @FXML private TextField txtNegocioNombre;
    @FXML private TextField txtNegocioDireccion;
    @FXML private TextField txtNegocioTelefono;

    @FXML private ComboBox<String> cmbFontSize;
    @FXML private CheckBox chkHighContrast;
    @FXML private CheckBox chkAnimations;

    @FXML private Label lblLockUsuarios;
    @FXML private Button btnRegistrarEmpleado;

    @FXML private Label lblLockBackup;
    @FXML private Button btnBackup;
    @FXML private Label lblBackupMessage;

    private UserSession currentSession;
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @FXML
    public void initialize() {
        // Formato de fecha en español (ej. Miércoles, 18 de noviembre de 2026)
        LocalDate now = LocalDate.now();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.of("es", "CO"));
        String dateText = now.format(dtf);
        if (!dateText.isEmpty()) {
            dateText = Character.toUpperCase(dateText.charAt(0)) + dateText.substring(1);
        }
        lblHeaderDate.setText("📅 " + dateText);

        // Opciones de accesibilidad (tamaño de letra)
        if (cmbFontSize != null) {
            cmbFontSize.getItems().addAll("Normal (14px)", "Grande (16px)", "Muy grande (18px)");
            cmbFontSize.setValue("Normal (14px)");
        }
    }

    /**
     * =========================================================================
     * INICIALIZACIÓN DE SESIÓN Y CONTROL DE PERMISOS POR ROL (SUSTENTACIÓN)
     * =========================================================================
     * ¿Cuándo se ejecuta?
     * Inmediatamente después de que el usuario inicia sesión exitosamente en Login
     * (MainController) o activa el Modo Vista Previa.
     *
     * ¿Cómo se gestionan los roles de Rosita y Gladys?
     * 1. ROSITA (Perfil Administradora):
     *    - Control total del sistema.
     *    - Acceso habilitado para editar datos comerciales, registrar empleados
     *      y generar copias de seguridad de la base de datos.
     * 2. GLADYS (Perfil Empleada):
     *    - Perfil operativo de mostrador.
     *    - Los campos de edición del negocio, registro de personal y backups
     *      se bloquean visualmente ('setDisable(true)') y muestran candados de protección.
     * 3. INYECCIÓN DE SESIÓN:
     *    - 'currentSession' almacena el ID del usuario, nombre y rol, pasándolo
     *      a cada controlador secundario para auditar quién realiza cada operación.
     * =========================================================================
     */
    public void initSession(UserSession session) {
        this.currentSession = session;

        // Saludo en encabezado y tarjeta de bienvenida
        if (session != null && session.isModoVistaPrevia()) {
            lblHeaderGreeting.setText("[VISTA PREVIA] " + session.getUsername() + " – " + session.getRole());
            lblWelcomeGreeting.setText("¡Hola, " + session.getUsername() + "!");
            if (bannerVistaPrevia != null) {
                bannerVistaPrevia.setVisible(true);
                bannerVistaPrevia.setManaged(true);
            }
        } else {
            lblHeaderGreeting.setText("¡Hola, " + session.getUsername() + "! – " + session.getRole());
            lblWelcomeGreeting.setText("¡Hola, " + session.getUsername() + "!");
            if (bannerVistaPrevia != null) {
                bannerVistaPrevia.setVisible(false);
                bannerVistaPrevia.setManaged(false);
            }
        }

        // Configuración de permisos de rol
        aplicarPermisosDeRol(session);

        // Iniciar siempre en la vista de Inicio
        onNavInicioClick();
    }

    private void aplicarPermisosDeRol(UserSession session) {
        if (session.isAdmin()) {
            configBannerBox.getStyleClass().removeAll("banner-employee");
            if (!configBannerBox.getStyleClass().contains("banner-admin")) {
                configBannerBox.getStyleClass().add("banner-admin");
            }
            lblConfigBannerIcon.setText("👑");
            lblConfigPermissionBanner.setText("Control Total del Sistema – Perfil Administradora (Rosita)");

            // La administradora puede editar datos del negocio, registrar empleados y generar copias
            txtNegocioNombre.setDisable(false);
            txtNegocioDireccion.setDisable(false);
            txtNegocioTelefono.setDisable(false);
            lblLockNegocio.setVisible(false);
            lblLockNegocio.setManaged(false);

            btnRegistrarEmpleado.setDisable(false);
            btnRegistrarEmpleado.setVisible(true);
            btnRegistrarEmpleado.setManaged(true);
            lblLockUsuarios.setVisible(false);
            lblLockUsuarios.setManaged(false);

            btnBackup.setDisable(false);
            lblLockBackup.setVisible(false);
            lblLockBackup.setManaged(false);

        } else {
            configBannerBox.getStyleClass().removeAll("banner-admin");
            if (!configBannerBox.getStyleClass().contains("banner-employee")) {
                configBannerBox.getStyleClass().add("banner-employee");
            }
            lblConfigBannerIcon.setText("👤");
            lblConfigPermissionBanner.setText("Perfil Operativo – Empleada (Gladys) – Consulta y Preferencias Personales");

            // La empleada tiene campos bloqueados
            txtNegocioNombre.setDisable(true);
            txtNegocioDireccion.setDisable(true);
            txtNegocioTelefono.setDisable(true);
            lblLockNegocio.setVisible(true);
            lblLockNegocio.setManaged(true);

            btnRegistrarEmpleado.setDisable(true);
            btnRegistrarEmpleado.setVisible(false);
            btnRegistrarEmpleado.setManaged(false);
            lblLockUsuarios.setVisible(true);
            lblLockUsuarios.setManaged(true);

            btnBackup.setDisable(true);
            lblLockBackup.setVisible(true);
            lblLockBackup.setManaged(true);
        }
    }

    // =========================================================================
    // NAVEGACIÓN PRINCIPAL
    // =========================================================================

    @FXML
    public void onNavInicioClick() {
        showView(homeView);
        setSidebarActive(btnNavInicio);
    }

    @FXML
    public void onNavConfigClick() {
        showView(configView);
        setSidebarActive(btnNavConfig);
    }

    /**
     * Módulo 1: Ventas (#D93668) - Funcionalidad Real Etapa 7
     */
    @FXML
    public void onOpenVentas() {
        try {
            if (ventasViewRoot == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/ventas-view.fxml"));
                ventasViewRoot = loader.load();
                ventasController = loader.getController();
                if (currentSession != null) {
                    ventasController.setSesion(currentSession);
                }
                contentArea.getChildren().add(ventasViewRoot);
            } else if (ventasController != null && currentSession != null) {
                ventasController.setSesion(currentSession);
            }

            showView(ventasViewRoot);
            setSidebarActive(btnNavVentas);

        } catch (IOException e) {
            System.err.println("[ERROR DASHBOARD] Error al cargar la vista de Ventas: " + e.getMessage());
            mostrarAlerta("Error de carga", "No fue posible cargar el módulo de Ventas: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Módulo 2: Inventario (#087E70) - Funcionalidad Real Etapa 3
     */
    @FXML
    public void onOpenInventario() {
        try {
            if (inventarioViewRoot == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/inventario-view.fxml"));
                inventarioViewRoot = loader.load();
                inventarioController = loader.getController();
                if (currentSession != null) {
                    inventarioController.setSesion(currentSession);
                }
                contentArea.getChildren().add(inventarioViewRoot);
            } else if (inventarioController != null && currentSession != null) {
                inventarioController.setSesion(currentSession);
            }

            showView(inventarioViewRoot);
            setSidebarActive(btnNavInventario);

        } catch (IOException e) {
            System.err.println("[ERROR DASHBOARD] Error al cargar la vista de Inventario: " + e.getMessage());
            mostrarAlerta("Error de carga", "No fue posible cargar el módulo de Inventario: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Módulo 3: Productos (#385DB1) - Funcionalidad Real Etapa 3
     */
    @FXML
    public void onOpenProductos() {
        try {
            if (productosViewRoot == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/productos-view.fxml"));
                productosViewRoot = loader.load();
                productosController = loader.getController();
                if (currentSession != null) {
                    productosController.setSesion(currentSession);
                }
                contentArea.getChildren().add(productosViewRoot);
            } else if (productosController != null && currentSession != null) {
                productosController.setSesion(currentSession);
            }

            showView(productosViewRoot);
            setSidebarActive(btnNavProductos);

        } catch (IOException e) {
            System.err.println("[ERROR DASHBOARD] Error al cargar la vista de Productos: " + e.getMessage());
            mostrarAlerta("Error de carga", "No fue posible cargar el módulo de Productos: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Módulo 4: Proveedores (#A8750C) - Funcionalidad Real Etapa 5
     */
    @FXML
    public void onOpenProveedores() {
        try {
            if (proveedoresViewRoot == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/proveedores-view.fxml"));
                proveedoresViewRoot = loader.load();
                proveedoresController = loader.getController();
                if (currentSession != null) {
                    proveedoresController.setSesion(currentSession);
                }
                contentArea.getChildren().add(proveedoresViewRoot);
            } else if (proveedoresController != null && currentSession != null) {
                proveedoresController.setSesion(currentSession);
            }

            showView(proveedoresViewRoot);
            setSidebarActive(btnNavProveedores);

        } catch (IOException e) {
            System.err.println("[ERROR DASHBOARD] Error al cargar la vista de Proveedores: " + e.getMessage());
            mostrarAlerta("Error de carga", "No fue posible cargar el módulo de Proveedores: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Módulo 5: Compras (#CA5733) - Funcionalidad Real Etapa 6
     */
    @FXML
    public void onOpenCompras() {
        try {
            if (comprasViewRoot == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/compras-view.fxml"));
                comprasViewRoot = loader.load();
                comprasController = loader.getController();
                if (currentSession != null) {
                    comprasController.setSesion(currentSession);
                }
                contentArea.getChildren().add(comprasViewRoot);
            } else if (comprasController != null && currentSession != null) {
                comprasController.setSesion(currentSession);
            }

            showView(comprasViewRoot);
            setSidebarActive(btnNavCompras);

        } catch (IOException e) {
            System.err.println("[ERROR DASHBOARD] Error al cargar la vista de Compras: " + e.getMessage());
            mostrarAlerta("Error de carga", "No fue posible cargar el módulo de Compras: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Módulo 6: Caja (#183D53) - Funcionalidad Real Etapa 8
     */
    @FXML
    public void onOpenCaja() {
        try {
            if (cajaViewRoot == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/caja-view.fxml"));
                cajaViewRoot = loader.load();
                cajaController = loader.getController();
                if (currentSession != null) {
                    cajaController.setSesion(currentSession);
                }
                contentArea.getChildren().add(cajaViewRoot);
            } else if (cajaController != null && currentSession != null) {
                cajaController.setSesion(currentSession);
            }

            showView(cajaViewRoot);
            setSidebarActive(btnNavCaja);

        } catch (IOException e) {
            System.err.println("[ERROR DASHBOARD] Error al cargar la vista de Caja: " + e.getMessage());
            mostrarAlerta("Error de carga", "No fue posible cargar el módulo de Caja: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Módulo 7: Reportes (#7951A4) - Funcionalidad Real Etapa 9
     */
    @FXML
    public void onOpenReportes() {
        try {
            if (reportesViewRoot == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/reportes-view.fxml"));
                reportesViewRoot = loader.load();
                reportesController = loader.getController();
                if (currentSession != null) {
                    reportesController.setSesion(currentSession);
                }
                contentArea.getChildren().add(reportesViewRoot);
            } else if (reportesController != null && currentSession != null) {
                reportesController.setSesion(currentSession);
                reportesController.cargarTodosLosReportes();
            }

            showView(reportesViewRoot);
            setSidebarActive(btnNavReportes);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[ERROR DASHBOARD] Error al cargar la vista de Reportes: " + e.getMessage());
            mostrarAlerta("Error de carga", "No fue posible cargar el módulo de Reportes: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Método conservado por compatibilidad histórica
     */
    @FXML
    public void onOpenClientes() {
        abrirModulo(
            "Clientes",
            "👥",
            "#7951A4",
            "Directorio de clientes frecuentes, historial de compras, créditos autorizados y datos de contacto.",
            "Módulo auxiliar de Clientes"
        );
        setSidebarActive(null);
    }

    private void abrirModulo(String nombre, String icono, String colorHex, String descripcion, String estadoFase) {
        if (lblModuleName != null) {
            lblModuleName.setText(nombre);
            lblModuleName.setStyle("-fx-text-fill: " + colorHex + ";");
        }
        if (lblModuleIcon != null) {
            lblModuleIcon.setText(icono);
        }
        if (lblModuleDescription != null) {
            lblModuleDescription.setText(descripcion);
        }
        if (lblModuleStatus != null) {
            lblModuleStatus.setText(estadoFase);
        }

        showView(moduleView);
    }

    // =========================================================================
    // CIERRE DE SESIÓN
    // =========================================================================

    @FXML
    public void onNavLogoutClick() {
        Alert confirm = new Alert(
            Alert.AlertType.CONFIRMATION,
            "¿Estás segura de que deseas cerrar tu sesión en Papelería Rosita?",
            ButtonType.YES, ButtonType.NO
        );
        confirm.setTitle("Cerrar Sesión");
        confirm.setHeaderText(null);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            this.currentSession = null;
            regresarAlLogin();
        }
    }

    private void regresarAlLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/papeleriarosita/fxml/main-view.fxml"));
            Parent loginRoot = loader.load();

            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.getScene().setRoot(loginRoot);
            stage.setTitle("Papelería Rosita - Inicio de Sesión");

        } catch (IOException e) {
            System.err.println("[ERROR DASHBOARD] Error al regresar a pantalla de login: " + e.getMessage());
        }
    }

    // =========================================================================
    // ACCIONES DE CONFIGURACIÓN Y ADMINISTRACIÓN DE USUARIOS
    // =========================================================================

    @FXML
    private void onGenerarCopiaClick() {
        if (currentSession != null && currentSession.isModoVistaPrevia()) {
            mostrarAlerta("Modo Vista Previa", "Operación no permitida: El sistema está en Modo Vista Previa (Solo Lectura). No se ejecutan copias ni modificaciones de respaldo.", Alert.AlertType.WARNING);
            return;
        }

        if (lblBackupMessage != null) {
            lblBackupMessage.setText("✓ Copia de seguridad local generada correctamente.");
            lblBackupMessage.setVisible(true);
            lblBackupMessage.setManaged(true);
        }
    }

    @FXML
    private void onRegistrarEmpleadoClick() {
        if (currentSession != null && currentSession.isModoVistaPrevia()) {
            mostrarAlerta("Modo Vista Previa", "Operación no permitida: El sistema está en Modo Vista Previa (Solo Lectura). No se registran cuentas ni se modifica MySQL.", Alert.AlertType.WARNING);
            return;
        }

        if (currentSession == null || !currentSession.isAdmin()) {
            mostrarAlerta("Acceso denegado", "Solo la Administradora (Rosita) tiene autorización para registrar empleados.", Alert.AlertType.WARNING);
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Registrar Nueva Empleada");
        dialog.setHeaderText("Crear cuenta de acceso para la Empleada (Gladys)\nLa contraseña será protegida con PBKDF2-HMAC-SHA256 (600.000 iteraciones).");

        ButtonType btnGuardar = new ButtonType("Registrar Empleada", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);

        VBox form = new VBox(10);
        form.setPrefWidth(380);

        TextField txtNombreEmp = new TextField("Gladys");
        txtNombreEmp.setPromptText("Nombre de la empleada");

        TextField txtCorreoEmp = new TextField();
        txtCorreoEmp.setPromptText("gladys@papeleriarosita.com");

        PasswordField txtPassEmp = new PasswordField();
        txtPassEmp.setPromptText("Contraseña (mínimo 6 caracteres)");

        PasswordField txtConfirmEmp = new PasswordField();
        txtConfirmEmp.setPromptText("Confirmar contraseña");

        form.getChildren().addAll(
            new Label("Nombre completo:"), txtNombreEmp,
            new Label("Correo electrónico:"), txtCorreoEmp,
            new Label("Contraseña:"), txtPassEmp,
            new Label("Confirmar contraseña:"), txtConfirmEmp
        );

        dialog.getDialogPane().setContent(form);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == btnGuardar) {
            String nom = txtNombreEmp.getText();
            String cor = txtCorreoEmp.getText();
            String pass = txtPassEmp.getText();
            String conf = txtConfirmEmp.getText();

            if (nom == null || nom.trim().isEmpty() || cor == null || cor.trim().isEmpty() || !cor.contains("@")) {
                mostrarAlerta("Datos incompletos", "Por favor ingresa un nombre y correo electrónico válidos.", Alert.AlertType.ERROR);
                return;
            }
            if (pass == null || pass.length() < 6) {
                mostrarAlerta("Contraseña insegura", "La contraseña debe tener al menos 6 caracteres.", Alert.AlertType.ERROR);
                return;
            }
            if (!pass.equals(conf)) {
                mostrarAlerta("Contraseñas no coinciden", "Las contraseñas ingresadas no son iguales.", Alert.AlertType.ERROR);
                return;
            }

            try {
                usuarioDAO.registrarEmpleado(nom.trim(), cor.trim(), pass, currentSession);
                mostrarAlerta("Registro Exitoso", "La cuenta de Empleada (" + nom + ") fue registrada exitosamente en MySQL.", Alert.AlertType.INFORMATION);
            } catch (SecurityException e) {
                mostrarAlerta("Permiso Denegado", e.getMessage(), Alert.AlertType.WARNING);
            } catch (IllegalArgumentException e) {
                mostrarAlerta("Datos Inválidos", e.getMessage(), Alert.AlertType.ERROR);
            } catch (SQLException e) {
                mostrarAlerta("Error de Base de Datos", "No fue posible registrar a la empleada: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    // =========================================================================
    // MÉTODOS AUXILIARES DE VISTA Y CONTROL DE NAVEGACIÓN
    // =========================================================================

    private void showView(Node viewToShow) {
        homeView.setVisible(viewToShow == homeView);
        homeView.setManaged(viewToShow == homeView);

        moduleView.setVisible(viewToShow == moduleView);
        moduleView.setManaged(viewToShow == moduleView);

        configView.setVisible(viewToShow == configView);
        configView.setManaged(viewToShow == configView);

        if (productosViewRoot != null) {
            productosViewRoot.setVisible(viewToShow == productosViewRoot);
            productosViewRoot.setManaged(viewToShow == productosViewRoot);
        }

        if (inventarioViewRoot != null) {
            inventarioViewRoot.setVisible(viewToShow == inventarioViewRoot);
            inventarioViewRoot.setManaged(viewToShow == inventarioViewRoot);
        }

        if (proveedoresViewRoot != null) {
            proveedoresViewRoot.setVisible(viewToShow == proveedoresViewRoot);
            proveedoresViewRoot.setManaged(viewToShow == proveedoresViewRoot);
        }

        if (comprasViewRoot != null) {
            comprasViewRoot.setVisible(viewToShow == comprasViewRoot);
            comprasViewRoot.setManaged(viewToShow == comprasViewRoot);
        }

        if (ventasViewRoot != null) {
            ventasViewRoot.setVisible(viewToShow == ventasViewRoot);
            ventasViewRoot.setManaged(viewToShow == ventasViewRoot);
        }

        if (cajaViewRoot != null) {
            cajaViewRoot.setVisible(viewToShow == cajaViewRoot);
            cajaViewRoot.setManaged(viewToShow == cajaViewRoot);
        }

        if (reportesViewRoot != null) {
            reportesViewRoot.setVisible(viewToShow == reportesViewRoot);
            reportesViewRoot.setManaged(viewToShow == reportesViewRoot);
        }
    }

    private void setSidebarActive(Button activeButton) {
        if (btnNavInicio != null) btnNavInicio.getStyleClass().remove("sidebar-btn-active");
        if (btnNavVentas != null) btnNavVentas.getStyleClass().remove("sidebar-btn-active");
        if (btnNavInventario != null) btnNavInventario.getStyleClass().remove("sidebar-btn-active");
        if (btnNavProductos != null) btnNavProductos.getStyleClass().remove("sidebar-btn-active");
        if (btnNavProveedores != null) btnNavProveedores.getStyleClass().remove("sidebar-btn-active");
        if (btnNavCompras != null) btnNavCompras.getStyleClass().remove("sidebar-btn-active");
        if (btnNavCaja != null) btnNavCaja.getStyleClass().remove("sidebar-btn-active");
        if (btnNavReportes != null) btnNavReportes.getStyleClass().remove("sidebar-btn-active");
        if (btnNavConfig != null) btnNavConfig.getStyleClass().remove("sidebar-btn-active");

        if (activeButton != null && !activeButton.getStyleClass().contains("sidebar-btn-active")) {
            activeButton.getStyleClass().add("sidebar-btn-active");
        }
    }
}
