package com.papeleriarosita.controller;

import com.papeleriarosita.dao.ProveedorDAO;
import com.papeleriarosita.model.Proveedor;
import com.papeleriarosita.model.UserSession;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador de la interfaz gráfica del Módulo de Proveedores.
 * Permite registrar, editar, buscar y consultar proveedores de Papelería Rosita.
 */
public class ProveedoresController {

    @FXML private Label lblTotalProveedores;
    @FXML private TextField txtBuscar;
    @FXML private Button btnNuevo;
    @FXML private Button btnEditar;

    @FXML private TableView<Proveedor> tblProveedores;
    @FXML private TableColumn<Proveedor, Integer> colId;
    @FXML private TableColumn<Proveedor, String> colNombre;
    @FXML private TableColumn<Proveedor, String> colTelefono;
    @FXML private TableColumn<Proveedor, String> colCorreo;
    @FXML private TableColumn<Proveedor, String> colDireccion;

    @FXML private VBox formCard;
    @FXML private Label lblTituloFormulario;
    @FXML private TextField txtNombre;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtCorreo;
    @FXML private TextArea txtDireccion;
    @FXML private Label lblMensajeFormulario;
    @FXML private Button btnCancelar;
    @FXML private Button btnGuardar;

    private final ObservableList<Proveedor> listaProveedores = FXCollections.observableArrayList();
    private final ProveedorDAO proveedorDAO = new ProveedorDAO();
    private UserSession sesionActual;

    private Proveedor proveedorSeleccionado = null;
    private boolean modoEdicion = false;

    @FXML
    public void initialize() {
        configurarColumnasTabla();

        // Listener de selección en tabla
        tblProveedores.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            proveedorSeleccionado = newSel;
            if (newSel != null) {
                cargarDatosEnFormulario(newSel);
                btnEditar.setDisable(false);
            } else {
                if (!modoEdicion) {
                    btnEditar.setDisable(true);
                }
            }
        });

        // Limpiar mensaje al escribir
        txtNombre.textProperty().addListener((obs, o, n) -> ocultarMensajeSiHayError());
        txtTelefono.textProperty().addListener((obs, o, n) -> ocultarMensajeSiHayError());
        txtCorreo.textProperty().addListener((obs, o, n) -> ocultarMensajeSiHayError());

        cargarProveedores();
    }

    /**
     * Recibe la sesión activa del usuario para control de accesos y autorizaciones.
     */
    public void setSesion(UserSession sesion) {
        this.sesionActual = sesion;
        aplicarPermisosDeRol();
    }

    private void aplicarPermisosDeRol() {
        boolean puedeGestionar = sesionActual != null && (sesionActual.isAdmin() || sesionActual.isEmpleado());
        btnNuevo.setDisable(!puedeGestionar);
        btnGuardar.setDisable(!puedeGestionar);
    }

    private void configurarColumnasTabla() {
        colId.setCellValueFactory(new PropertyValueFactory<>("idProveedor"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colTelefono.setCellValueFactory(new PropertyValueFactory<>("telefono"));
        colCorreo.setCellValueFactory(new PropertyValueFactory<>("correo"));
        colDireccion.setCellValueFactory(new PropertyValueFactory<>("direccion"));

        tblProveedores.setItems(listaProveedores);
    }

    /**
     * Carga y refresca el listado completo de proveedores desde MySQL.
     */
    @FXML
    public void cargarProveedores() {
        try {
            List<Proveedor> proveedores = proveedorDAO.listarTodos();
            listaProveedores.setAll(proveedores);
            actualizarInsigniaTotal(proveedores.size());
            ocultarMensaje();

        } catch (SQLException e) {
            System.err.println("[ERROR PROVEEDORES] Error al consultar proveedores: " + e.getMessage());
            if (sesionActual == null || !sesionActual.isModoVistaPrevia()) {
                mostrarAlerta(
                    "Error de Base de Datos",
                    "No fue posible consultar el listado de proveedores: " + e.getMessage(),
                    Alert.AlertType.ERROR
                );
            }
        }
    }

    /**
     * Realiza la búsqueda de proveedores por coincidencia en el nombre o teléfono.
     */
    @FXML
    public void onBuscar() {
        String criterio = txtBuscar.getText();
        if (criterio == null || criterio.trim().isEmpty()) {
            cargarProveedores();
            return;
        }

        try {
            List<Proveedor> resultados = proveedorDAO.buscarPorNombre(criterio.trim());
            listaProveedores.setAll(resultados);
            actualizarInsigniaTotal(resultados.size());

        } catch (SQLException e) {
            System.err.println("[ERROR PROVEEDORES] Error al buscar proveedores: " + e.getMessage());
            mostrarAlerta(
                "Error de Búsqueda",
                "No fue posible realizar la búsqueda: " + e.getMessage(),
                Alert.AlertType.ERROR
            );
        }
    }

    /**
     * Restablece el campo de búsqueda y carga todos los registros.
     */
    @FXML
    public void onLimpiarBusqueda() {
        txtBuscar.setText("");
        cargarProveedores();
    }

    /**
     * Prepara el formulario lateral para registrar un nuevo proveedor.
     */
    @FXML
    public void onNuevoProveedor() {
        modoEdicion = false;
        proveedorSeleccionado = null;
        tblProveedores.getSelectionModel().clearSelection();

        lblTituloFormulario.setText("➕ Registrar Proveedor");
        limpiarFormulario();
        btnGuardar.setText("💾 Guardar Proveedor");
        btnEditar.setDisable(true);
        txtNombre.requestFocus();
    }

    /**
     * Prepara el formulario para editar el proveedor seleccionado.
     */
    @FXML
    public void onEditarProveedor() {
        if (proveedorSeleccionado == null) {
            mostrarAlerta(
                "Selección requerida",
                "Por favor selecciona un proveedor de la tabla para editar sus datos.",
                Alert.AlertType.WARNING
            );
            return;
        }

        modoEdicion = true;
        lblTituloFormulario.setText("✏️ Editar Proveedor (#" + proveedorSeleccionado.getIdProveedor() + ")");
        cargarDatosEnFormulario(proveedorSeleccionado);
        btnGuardar.setText("💾 Guardar Cambios");
        txtNombre.requestFocus();
    }

    /**
     * Guarda el registro nuevo o actualiza el existente en MySQL.
     */
    @FXML
    public void onGuardarProveedor() {
        if (sesionActual != null && sesionActual.isModoVistaPrevia()) {
            mostrarMensaje("Operación no permitida: El sistema está en Modo Vista Previa (Solo Lectura). No se modifican ni guardan proveedores reales.", true);
            return;
        }

        String nombre = txtNombre.getText();
        String telefono = txtTelefono.getText();
        String correo = txtCorreo.getText();
        String direccion = txtDireccion.getText();

        // 1. Validaciones de interfaz
        if (nombre == null || nombre.trim().isEmpty()) {
            mostrarMensaje("El nombre o razón social del proveedor es obligatorio.", true);
            txtNombre.requestFocus();
            return;
        }

        try {
            if (!modoEdicion) {
                // REGISTRAR NUEVO PROVEEDOR
                Proveedor nuevo = new Proveedor(
                    nombre.trim(),
                    normalizar(telefono),
                    normalizar(correo),
                    normalizar(direccion)
                );

                int idGenerado = proveedorDAO.registrarProveedor(nuevo, sesionActual);
                cargarProveedores();
                seleccionarProveedorPorId(idGenerado);
                mostrarMensaje("✓ Proveedor '" + nuevo.getNombre() + "' registrado exitosamente.", false);

            } else {
                // EDITAR PROVEEDOR EXISTENTE
                if (proveedorSeleccionado == null) {
                    mostrarMensaje("No hay ningún proveedor seleccionado para actualizar.", true);
                    return;
                }

                proveedorSeleccionado.setNombre(nombre.trim());
                proveedorSeleccionado.setTelefono(normalizar(telefono));
                proveedorSeleccionado.setCorreo(normalizar(correo));
                proveedorSeleccionado.setDireccion(normalizar(direccion));

                boolean actualizado = proveedorDAO.editarProveedor(proveedorSeleccionado, sesionActual);
                if (actualizado) {
                    int idEditado = proveedorSeleccionado.getIdProveedor();
                    cargarProveedores();
                    seleccionarProveedorPorId(idEditado);
                    mostrarMensaje("✓ Proveedor #" + idEditado + " actualizado correctamente.", false);
                } else {
                    mostrarMensaje("No se pudo actualizar el proveedor. Verifique que el registro aún exista.", true);
                }
            }

        } catch (IllegalArgumentException e) {
            mostrarMensaje(e.getMessage(), true);
        } catch (SecurityException e) {
            mostrarMensaje("Permiso denegado: " + e.getMessage(), true);
        } catch (SQLException e) {
            System.err.println("[ERROR PROVEEDORES] Error de persistencia: " + e.getMessage());
            mostrarMensaje("Error de base de datos: " + e.getMessage(), true);
        }
    }

    /**
     * Cancela la edición o registro actual y limpia el formulario.
     */
    @FXML
    public void onCancelarFormulario() {
        limpiarFormulario();
        modoEdicion = false;
        proveedorSeleccionado = null;
        tblProveedores.getSelectionModel().clearSelection();
        lblTituloFormulario.setText("➕ Registrar Proveedor");
        btnGuardar.setText("💾 Guardar Proveedor");
        btnEditar.setDisable(true);
        ocultarMensaje();
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================

    private void cargarDatosEnFormulario(Proveedor p) {
        if (p != null) {
            txtNombre.setText(p.getNombre() != null ? p.getNombre() : "");
            txtTelefono.setText(p.getTelefono() != null ? p.getTelefono() : "");
            txtCorreo.setText(p.getCorreo() != null ? p.getCorreo() : "");
            txtDireccion.setText(p.getDireccion() != null ? p.getDireccion() : "");
            ocultarMensaje();
        }
    }

    private void limpiarFormulario() {
        txtNombre.setText("");
        txtTelefono.setText("");
        txtCorreo.setText("");
        txtDireccion.setText("");
    }

    private void seleccionarProveedorPorId(int id) {
        for (Proveedor p : listaProveedores) {
            if (p.getIdProveedor() == id) {
                tblProveedores.getSelectionModel().select(p);
                tblProveedores.scrollTo(p);
                break;
            }
        }
    }

    private void actualizarInsigniaTotal(int total) {
        if (lblTotalProveedores != null) {
            lblTotalProveedores.setText("Total: " + total + (total == 1 ? " proveedor" : " proveedores"));
        }
    }

    private void mostrarMensaje(String texto, boolean esError) {
        if (lblMensajeFormulario != null) {
            lblMensajeFormulario.getStyleClass().removeAll("message-error", "message-success");
            lblMensajeFormulario.getStyleClass().add(esError ? "message-error" : "message-success");
            lblMensajeFormulario.setText(texto);
            lblMensajeFormulario.setVisible(true);
            lblMensajeFormulario.setManaged(true);
        }
    }

    private void ocultarMensaje() {
        if (lblMensajeFormulario != null) {
            lblMensajeFormulario.setText("");
            lblMensajeFormulario.setVisible(false);
            lblMensajeFormulario.setManaged(false);
        }
    }

    private void ocultarMensajeSiHayError() {
        if (lblMensajeFormulario != null && lblMensajeFormulario.isVisible()
            && lblMensajeFormulario.getStyleClass().contains("message-error")) {
            ocultarMensaje();
        }
    }

    private String normalizar(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        return texto.trim();
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
