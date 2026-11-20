package com.papeleriarosita.controller;

import com.papeleriarosita.dao.CategoriaDAO;
import com.papeleriarosita.dao.ProductoDAO;
import com.papeleriarosita.model.Categoria;
import com.papeleriarosita.model.Producto;
import com.papeleriarosita.model.UserSession;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;

/**
 * Controlador para el Módulo de Productos en Papelería Rosita.
 * Permite registrar, consultar, buscar, editar y activar/inactivar productos.
 *
 * REGLA DE NEGOCIO:
 * El precio de venta lo establece y modifica manualmente Rosita (Administradora).
 * Si la sesión activa es de Empleada (Gladys), el campo de precio permanece bloqueado.
 */
public class ProductosController {

    // Controles de la tabla y búsqueda
    @FXML private TextField txtBuscar;
    @FXML private TableView<Producto> tblProductos;
    @FXML private TableColumn<Producto, String> colCodigo;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, String> colCategoria;
    @FXML private TableColumn<Producto, String> colPrecio;
    @FXML private TableColumn<Producto, Integer> colStock;
    @FXML private TableColumn<Producto, String> colEstado;

    @FXML private Label lblTotalProductos;
    @FXML private Label lblMensajeTabla;

    // Panel del formulario
    @FXML private VBox formPanel;
    @FXML private Label lblTituloFormulario;
    @FXML private TextField txtCodigo;
    @FXML private TextField txtNombre;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private TextArea txtDescripcion;
    @FXML private TextField txtPrecioVenta;
    @FXML private Label lblAvisoPrecio;
    @FXML private TextField txtStockMinimoInicial;
    @FXML private VBox boxStockMinimo;
    @FXML private CheckBox chkActivo;
    @FXML private Button btnGuardar;
    @FXML private Label lblMensajeForm;

    // Botones de acción principales
    @FXML private Button btnNuevo;
    @FXML private Button btnEditar;
    @FXML private Button btnCambiarEstado;

    private final ProductoDAO productoDAO = new ProductoDAO();
    private final CategoriaDAO categoriaDAO = new CategoriaDAO();

    private final ObservableList<Producto> listaProductos = FXCollections.observableArrayList();
    private final ObservableList<Categoria> listaCategorias = FXCollections.observableArrayList();

    private Producto productoSeleccionadoParaEdicion = null;
    private UserSession sesionActual;

    private static final DecimalFormat MONEDA_FORMAT = new DecimalFormat("$ #,##0.00");

    @FXML
    public void initialize() {
        configurarColumnasTabla();
        cargarCategorias();
        cargarProductos();

        // Ocultar panel de formulario al inicio
        cerrarFormulario();

        // Listener de selección en la tabla para habilitar/deshabilitar botones
        tblProductos.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean haySeleccion = newVal != null;
            if (btnEditar != null) btnEditar.setDisable(!haySeleccion);
            if (btnCambiarEstado != null) {
                btnCambiarEstado.setDisable(!haySeleccion);
                if (haySeleccion) {
                    btnCambiarEstado.setText(newVal.isActivo() ? "⛔ Inactivar" : "✓ Activar");
                }
            }
        });
    }

    /**
     * Recibe e inicializa la sesión activa del usuario para control de permisos.
     */
    public void setSesion(UserSession sesion) {
        this.sesionActual = sesion;
        aplicarPermisosDeRol();
    }

    private void aplicarPermisosDeRol() {
        boolean esAdmin = sesionActual != null && sesionActual.isAdmin();

        // Si es empleada, no puede modificar el precio de venta (establecido por Rosita)
        if (txtPrecioVenta != null) {
            txtPrecioVenta.setEditable(esAdmin);
        }
        if (lblAvisoPrecio != null) {
            lblAvisoPrecio.setVisible(!esAdmin);
            lblAvisoPrecio.setManaged(!esAdmin);
            if (!esAdmin) {
                lblAvisoPrecio.setText("ℹ️ El precio de venta es establecido exclusivamente por Rosita.");
            }
        }
    }

    private void configurarColumnasTabla() {
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colCategoria.setCellValueFactory(new PropertyValueFactory<>("nombreCategoria"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stockActual"));

        // Formato para precio de venta
        colPrecio.setCellValueFactory(cellData -> {
            BigDecimal precio = cellData.getValue().getPrecioVenta();
            return new SimpleStringProperty(precio != null ? MONEDA_FORMAT.format(precio) : "$ 0.00");
        });

        // Formato con badge para estado Activo / Inactivo
        colEstado.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getEstadoTexto()));
        colEstado.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("ACTIVO".equalsIgnoreCase(item)) {
                        setStyle("-fx-text-fill: #087E70; -fx-font-weight: bold; -fx-alignment: CENTER;");
                    } else {
                        setStyle("-fx-text-fill: #8E9BAE; -fx-font-weight: bold; -fx-alignment: CENTER;");
                    }
                }
            }
        });

        tblProductos.setItems(listaProductos);
    }

    public void cargarCategorias() {
        try {
            // Asegurar categorías base si está vacía
            categoriaDAO.asegurarCategoriasIniciales();
            List<Categoria> cats = categoriaDAO.listarTodas();
            listaCategorias.setAll(cats);
            if (cmbCategoria != null) {
                cmbCategoria.setItems(listaCategorias);
            }
        } catch (SQLException e) {
            System.err.println("[PRODUCTOS] Error al cargar categorías: " + e.getMessage());
        }
    }

    @FXML
    public void cargarProductos() {
        try {
            List<Producto> productos = productoDAO.listarTodos();
            listaProductos.setAll(productos);
            actualizarContadorTotal();
            mostrarMensajeTabla("Catálogo actualizado (" + productos.size() + " productos)");
        } catch (SQLException e) {
            mostrarMensajeTabla("Error al conectar con la base de datos: " + e.getMessage());
        }
    }

    @FXML
    private void onBuscar() {
        String criterio = txtBuscar != null ? txtBuscar.getText() : "";
        try {
            List<Producto> resultados = productoDAO.buscarPorNombreOCodigo(criterio);
            listaProductos.setAll(resultados);
            actualizarContadorTotal();
            if (resultados.isEmpty()) {
                mostrarMensajeTabla("No se encontraron productos con el criterio: '" + criterio + "'");
            } else {
                mostrarMensajeTabla("Resultados de búsqueda: " + resultados.size() + " producto(s)");
            }
        } catch (SQLException e) {
            mostrarMensajeTabla("Error al buscar productos: " + e.getMessage());
        }
    }

    @FXML
    private void onLimpiarBusqueda() {
        if (txtBuscar != null) txtBuscar.clear();
        cargarProductos();
    }

    // =========================================================================
    // ACCIONES DE FORMULARIO: NUEVO / EDITAR / GUARDAR / CANCELAR
    // =========================================================================

    @FXML
    private void onNuevoProducto() {
        productoSeleccionadoParaEdicion = null;
        limpiarFormulario();
        lblTituloFormulario.setText("Registrar Nuevo Producto");
        btnGuardar.setText("Guardar Producto");

        // En creación mostramos el campo de stock mínimo inicial
        if (boxStockMinimo != null) {
            boxStockMinimo.setVisible(true);
            boxStockMinimo.setManaged(true);
        }

        formPanel.setVisible(true);
        formPanel.setManaged(true);
        txtCodigo.requestFocus();
    }

    @FXML
    private void onEditarProducto() {
        Producto seleccionado = tblProductos.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarAlerta("Selecciona un producto", "Por favor selecciona un producto de la tabla para editar.", Alert.AlertType.WARNING);
            return;
        }

        productoSeleccionadoParaEdicion = seleccionado;
        lblTituloFormulario.setText("Editar Producto: " + seleccionado.getNombre());
        btnGuardar.setText("Guardar Cambios");

        txtCodigo.setText(seleccionado.getCodigo());
        txtNombre.setText(seleccionado.getNombre());
        txtDescripcion.setText(seleccionado.getDescripcion() != null ? seleccionado.getDescripcion() : "");
        txtPrecioVenta.setText(seleccionado.getPrecioVenta().toString());
        chkActivo.setSelected(seleccionado.isActivo());

        // Seleccionar categoría correspondiente en el ComboBox
        for (Categoria cat : listaCategorias) {
            if (cat.getIdCategoria() == seleccionado.getIdCategoria()) {
                cmbCategoria.setValue(cat);
                break;
            }
        }

        // En edición ocultamos stock mínimo inicial (se administra en el módulo de Inventario)
        if (boxStockMinimo != null) {
            boxStockMinimo.setVisible(false);
            boxStockMinimo.setManaged(false);
        }

        formPanel.setVisible(true);
        formPanel.setManaged(true);
        txtNombre.requestFocus();
    }

    @FXML
    private void onGuardar() {
        if (sesionActual != null && sesionActual.isModoVistaPrevia()) {
            mostrarAlerta("Modo Vista Previa", "Operación no permitida: El sistema está en Modo Vista Previa (Solo Lectura). No se guardan ni modifican productos reales.", Alert.AlertType.WARNING);
            return;
        }

        lblMensajeForm.setText("");

        String codigo = txtCodigo.getText();
        String nombre = txtNombre.getText();
        Categoria categoria = cmbCategoria.getValue();
        String descripcion = txtDescripcion.getText();
        String precioStr = txtPrecioVenta.getText();

        // 1. Validaciones de formulario
        if (codigo == null || codigo.trim().isEmpty()) {
            mostrarErrorForm("El código del producto es obligatorio.");
            txtCodigo.requestFocus();
            return;
        }
        if (nombre == null || nombre.trim().length() < 2) {
            mostrarErrorForm("Ingresa un nombre descriptivo de al menos 2 caracteres.");
            txtNombre.requestFocus();
            return;
        }
        if (categoria == null) {
            mostrarErrorForm("Por favor selecciona una categoría para el producto.");
            cmbCategoria.requestFocus();
            return;
        }

        BigDecimal precio;
        try {
            if (precioStr == null || precioStr.trim().isEmpty()) {
                precio = BigDecimal.ZERO;
            } else {
                precio = new BigDecimal(precioStr.trim().replace(",", "."));
            }
            if (precio.compareTo(BigDecimal.ZERO) < 0) {
                mostrarErrorForm("El precio de venta no puede ser negativo.");
                txtPrecioVenta.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            mostrarErrorForm("Ingresa un precio de venta numérico válido (ej. 4500.00).");
            txtPrecioVenta.requestFocus();
            return;
        }

        int stockMinimoInicial = 0;
        if (productoSeleccionadoParaEdicion == null && txtStockMinimoInicial != null) {
            String stockMinStr = txtStockMinimoInicial.getText();
            if (stockMinStr != null && !stockMinStr.trim().isEmpty()) {
                try {
                    stockMinimoInicial = Integer.parseInt(stockMinStr.trim());
                    if (stockMinimoInicial < 0) {
                        mostrarErrorForm("El stock mínimo no puede ser negativo.");
                        txtStockMinimoInicial.requestFocus();
                        return;
                    }
                } catch (NumberFormatException e) {
                    mostrarErrorForm("El stock mínimo debe ser un número entero (ej. 5).");
                    txtStockMinimoInicial.requestFocus();
                    return;
                }
            }
        }

        try {
            if (productoSeleccionadoParaEdicion == null) {
                // REGISTRAR NUEVO
                Producto nuevo = new Producto();
                nuevo.setCodigo(codigo.trim());
                nuevo.setNombre(nombre.trim());
                nuevo.setDescripcion(descripcion != null ? descripcion.trim() : null);
                nuevo.setIdCategoria(categoria.getIdCategoria());
                nuevo.setNombreCategoria(categoria.getNombre());
                nuevo.setPrecioVenta(precio);
                nuevo.setActivo(chkActivo.isSelected());

                productoDAO.registrar(nuevo, stockMinimoInicial, sesionActual);
                mostrarAlerta("Producto Registrado", "El producto '" + nuevo.getNombre() + "' fue registrado exitosamente.\n(El registro de inventario inicial fue creado automáticamente).", Alert.AlertType.INFORMATION);
            } else {
                // ACTUALIZAR EXISTENTE
                productoSeleccionadoParaEdicion.setCodigo(codigo.trim());
                productoSeleccionadoParaEdicion.setNombre(nombre.trim());
                productoSeleccionadoParaEdicion.setDescripcion(descripcion != null ? descripcion.trim() : null);
                productoSeleccionadoParaEdicion.setIdCategoria(categoria.getIdCategoria());
                productoSeleccionadoParaEdicion.setNombreCategoria(categoria.getNombre());
                productoSeleccionadoParaEdicion.setPrecioVenta(precio);
                productoSeleccionadoParaEdicion.setActivo(chkActivo.isSelected());

                productoDAO.actualizar(productoSeleccionadoParaEdicion, sesionActual);
                mostrarAlerta("Producto Actualizado", "Los cambios del producto fueron guardados con éxito.", Alert.AlertType.INFORMATION);
            }

            cerrarFormulario();
            cargarProductos();

        } catch (SecurityException e) {
            mostrarErrorForm(e.getMessage());
        } catch (IllegalArgumentException e) {
            mostrarErrorForm(e.getMessage());
        } catch (SQLException e) {
            mostrarErrorForm("Error de base de datos: " + e.getMessage());
        }
    }

    @FXML
    private void onCancelarFormulario() {
        cerrarFormulario();
    }

    @FXML
    private void onCambiarEstadoActivo() {
        if (sesionActual != null && sesionActual.isModoVistaPrevia()) {
            mostrarAlerta("Modo Vista Previa", "Operación no permitida: El sistema está en Modo Vista Previa (Solo Lectura). No se modifican estados en MySQL.", Alert.AlertType.WARNING);
            return;
        }

        Producto seleccionado = tblProductos.getSelectionModel().getSelectedItem();
        if (seleccionado == null) return;

        boolean nuevoEstado = !seleccionado.isActivo();
        String accion = nuevoEstado ? "activar" : "inactivar";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar cambio de estado");
        confirm.setHeaderText("¿Deseas " + accion + " el producto '" + seleccionado.getNombre() + "'?");
        confirm.setContentText(nuevoEstado
            ? "El producto volverá a estar disponible para ventas y movimientos."
            : "El producto quedará inactivo en el catálogo.");

        Optional<ButtonType> resp = confirm.showAndWait();
        if (resp.isPresent() && resp.get() == ButtonType.OK) {
            try {
                productoDAO.cambiarEstadoActivo(seleccionado.getIdProducto(), nuevoEstado);
                seleccionado.setActivo(nuevoEstado);
                tblProductos.refresh();
                mostrarMensajeTabla("Producto " + (nuevoEstado ? "activado" : "inactivado") + " exitosamente.");
            } catch (SQLException e) {
                mostrarAlerta("Error", "No se pudo cambiar el estado: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void cerrarFormulario() {
        if (formPanel != null) {
            formPanel.setVisible(false);
            formPanel.setManaged(false);
        }
        limpiarFormulario();
        productoSeleccionadoParaEdicion = null;
    }

    private void limpiarFormulario() {
        if (txtCodigo != null) txtCodigo.clear();
        if (txtNombre != null) txtNombre.clear();
        if (txtDescripcion != null) txtDescripcion.clear();
        if (txtPrecioVenta != null) txtPrecioVenta.clear();
        if (txtStockMinimoInicial != null) txtStockMinimoInicial.setText("5");
        if (cmbCategoria != null && !listaCategorias.isEmpty()) cmbCategoria.getSelectionModel().selectFirst();
        if (chkActivo != null) chkActivo.setSelected(true);
        if (lblMensajeForm != null) lblMensajeForm.setText("");
    }

    private void mostrarErrorForm(String error) {
        if (lblMensajeForm != null) {
            lblMensajeForm.setText("⚠️ " + error);
            lblMensajeForm.setStyle("-fx-text-fill: #D93668; -fx-font-weight: bold;");
        }
    }

    private void mostrarMensajeTabla(String msg) {
        if (lblMensajeTabla != null) {
            lblMensajeTabla.setText(msg);
        }
    }

    private void actualizarContadorTotal() {
        if (lblTotalProductos != null) {
            lblTotalProductos.setText("Total catálogo: " + listaProductos.size() + " productos");
        }
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
