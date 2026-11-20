package com.papeleriarosita.controller;

import com.papeleriarosita.dao.InventarioDAO;
import com.papeleriarosita.model.Inventario;
import com.papeleriarosita.model.MovimientoInventario;
import com.papeleriarosita.model.UserSession;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Controlador para el Módulo de Inventario en Papelería Rosita.
 * Gestiona existencias físicas, alertas de stock mínimo y agotados,
 * registro de entradas de mercancía y consulta del historial de movimientos.
 */
public class InventarioController {

    // Tarjetas de Métricas / Alertas
    @FXML private Label lblTotalItems;
    @FXML private Label lblTotalStockBajo;
    @FXML private Label lblTotalAgotados;

    // Filtros y Búsqueda
    @FXML private TextField txtBuscar;
    @FXML private Button btnFiltroTodos;
    @FXML private Button btnFiltroBajoStock;
    @FXML private Button btnFiltroAgotados;

    // Tabla de Existencias de Inventario
    @FXML private TableView<Inventario> tblInventario;
    @FXML private TableColumn<Inventario, String> colCodigo;
    @FXML private TableColumn<Inventario, String> colProducto;
    @FXML private TableColumn<Inventario, String> colCategoria;
    @FXML private TableColumn<Inventario, Integer> colStockActual;
    @FXML private TableColumn<Inventario, Integer> colStockMinimo;
    @FXML private TableColumn<Inventario, String> colEstadoAlerta;
    @FXML private TableColumn<Inventario, String> colUltimaActualizacion;

    // Botones de Acción de Inventario
    @FXML private Button btnEntradaFisica;
    @FXML private Button btnModificarStockMin;
    @FXML private Button btnVerMovimientos;

    // Panel de Historial de Movimientos
    @FXML private VBox panelMovimientos;
    @FXML private TableView<MovimientoInventario> tblMovimientos;
    @FXML private TableColumn<MovimientoInventario, String> colMovFecha;
    @FXML private TableColumn<MovimientoInventario, String> colMovCodigo;
    @FXML private TableColumn<MovimientoInventario, String> colMovProducto;
    @FXML private TableColumn<MovimientoInventario, String> colMovTipo;
    @FXML private TableColumn<MovimientoInventario, Integer> colMovCantidad;
    @FXML private TableColumn<MovimientoInventario, String> colMovMotivo;
    @FXML private TableColumn<MovimientoInventario, String> colMovUsuario;

    @FXML private Label lblMensajeEstado;

    private final InventarioDAO inventarioDAO = new InventarioDAO();

    private final ObservableList<Inventario> listaInventario = FXCollections.observableArrayList();
    private final ObservableList<MovimientoInventario> listaMovimientos = FXCollections.observableArrayList();

    private UserSession sesionActual;
    private String filtroActual = "TODOS";

    private static final DateTimeFormatter FECHA_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        configurarTablaInventario();
        configurarTablaMovimientos();
        cargarDatosInventario();

        if (panelMovimientos != null) {
            panelMovimientos.setVisible(false);
            panelMovimientos.setManaged(false);
        }

        // Listener de selección para habilitar botones dependientes de fila
        tblInventario.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean haySeleccion = newVal != null;
            if (btnModificarStockMin != null) btnModificarStockMin.setDisable(!haySeleccion);
        });
    }

    public void setSesion(UserSession sesion) {
        this.sesionActual = sesion;
    }

    private void configurarTablaInventario() {
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoProducto"));
        colProducto.setCellValueFactory(new PropertyValueFactory<>("nombreProducto"));
        colCategoria.setCellValueFactory(new PropertyValueFactory<>("nombreCategoria"));
        colStockActual.setCellValueFactory(new PropertyValueFactory<>("stockActual"));
        colStockMinimo.setCellValueFactory(new PropertyValueFactory<>("stockMinimo"));

        // Formato para última actualización
        colUltimaActualizacion.setCellValueFactory(cellData -> {
            var fecha = cellData.getValue().getFechaActualizacion();
            return new SimpleStringProperty(fecha != null ? fecha.format(FECHA_FORMATTER) : "—");
        });

        // Formato visual para Stock Actual (color diferenciado)
        colStockActual.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Integer stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || stock == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(stock.toString());
                    Inventario inv = getTableView().getItems().get(getIndex());
                    if (inv.estaAgotado()) {
                        setStyle("-fx-text-fill: #D93668; -fx-font-weight: bold; -fx-alignment: CENTER;");
                    } else if (inv.tieneStockBajo()) {
                        setStyle("-fx-text-fill: #EF5578; -fx-font-weight: bold; -fx-alignment: CENTER;");
                    } else {
                        setStyle("-fx-text-fill: #087E70; -fx-font-weight: bold; -fx-alignment: CENTER;");
                    }
                }
            }
        });

        // Formato con Badge para Alerta de Stock
        colEstadoAlerta.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getEstadoAlerta()));
        colEstadoAlerta.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean empty) {
                super.updateItem(estado, empty);
                if (empty || estado == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(estado);
                    if ("AGOTADO".equalsIgnoreCase(estado)) {
                        setStyle("-fx-background-color: #FDEDEC; -fx-text-fill: #D93668; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-background-radius: 4px;");
                    } else if ("STOCK BAJO".equalsIgnoreCase(estado)) {
                        setStyle("-fx-background-color: #FEF9E7; -fx-text-fill: #B78103; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-background-radius: 4px;");
                    } else {
                        setStyle("-fx-background-color: #E8F8F5; -fx-text-fill: #087E70; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-background-radius: 4px;");
                    }
                }
            }
        });

        tblInventario.setItems(listaInventario);
    }

    private void configurarTablaMovimientos() {
        if (tblMovimientos == null) return;

        colMovFecha.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFechaFormateada()));
        colMovCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoProducto"));
        colMovProducto.setCellValueFactory(new PropertyValueFactory<>("nombreProducto"));
        colMovCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colMovMotivo.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        colMovUsuario.setCellValueFactory(new PropertyValueFactory<>("nombreUsuario"));

        colMovTipo.setCellValueFactory(new PropertyValueFactory<>("tipoMovimiento"));
        colMovTipo.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String tipo, boolean empty) {
                super.updateItem(tipo, empty);
                if (empty || tipo == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(tipo);
                    if ("ENTRADA".equalsIgnoreCase(tipo)) {
                        setStyle("-fx-text-fill: #087E70; -fx-font-weight: bold; -fx-alignment: CENTER;");
                    } else if ("SALIDA".equalsIgnoreCase(tipo)) {
                        setStyle("-fx-text-fill: #D93668; -fx-font-weight: bold; -fx-alignment: CENTER;");
                    } else {
                        setStyle("-fx-text-fill: #385DB1; -fx-font-weight: bold; -fx-alignment: CENTER;");
                    }
                }
            }
        });

        tblMovimientos.setItems(listaMovimientos);
    }

    // =========================================================================
    // CARGA DE DATOS Y FILTROS
    // =========================================================================

    @FXML
    public void cargarDatosInventario() {
        try {
            List<Inventario> inventarioCompleto;
            switch (filtroActual) {
                case "STOCK_BAJO" -> inventarioCompleto = inventarioDAO.listarStockBajo();
                case "AGOTADOS"   -> inventarioCompleto = inventarioDAO.listarAgotados();
                default           -> inventarioCompleto = inventarioDAO.listarTodo();
            }

            listaInventario.setAll(inventarioCompleto);
            actualizarMetricasResumen();
            mostrarMensaje("Inventario actualizado (" + inventarioCompleto.size() + " productos)");

        } catch (SQLException e) {
            mostrarMensaje("Error al conectar con la base de datos: " + e.getMessage());
        }
    }

    private void actualizarMetricasResumen() {
        try {
            List<Inventario> todos = inventarioDAO.listarTodo();
            long total = todos.size();
            long agotados = todos.stream().filter(Inventario::estaAgotado).count();
            long stockBajo = todos.stream().filter(Inventario::tieneStockBajo).count();

            if (lblTotalItems != null) lblTotalItems.setText(String.valueOf(total));
            if (lblTotalStockBajo != null) lblTotalStockBajo.setText(String.valueOf(stockBajo));
            if (lblTotalAgotados != null) lblTotalAgotados.setText(String.valueOf(agotados));
        } catch (SQLException ignored) {}
    }

    @FXML
    private void onBuscar() {
        String criterio = txtBuscar != null ? txtBuscar.getText() : "";
        try {
            List<Inventario> resultados = inventarioDAO.buscarPorFiltro(criterio);
            listaInventario.setAll(resultados);
            mostrarMensaje("Búsqueda finalizada: " + resultados.size() + " producto(s)");
        } catch (SQLException e) {
            mostrarMensaje("Error al buscar: " + e.getMessage());
        }
    }

    @FXML
    private void onLimpiarBusqueda() {
        if (txtBuscar != null) txtBuscar.clear();
        onFiltroTodos();
    }

    @FXML
    private void onFiltroTodos() {
        filtroActual = "TODOS";
        resaltarBotonFiltro(btnFiltroTodos);
        cargarDatosInventario();
    }

    @FXML
    private void onFiltroStockBajo() {
        filtroActual = "STOCK_BAJO";
        resaltarBotonFiltro(btnFiltroBajoStock);
        cargarDatosInventario();
    }

    @FXML
    private void onFiltroAgotados() {
        filtroActual = "AGOTADOS";
        resaltarBotonFiltro(btnFiltroAgotados);
        cargarDatosInventario();
    }

    private void resaltarBotonFiltro(Button activo) {
        if (btnFiltroTodos != null) btnFiltroTodos.getStyleClass().remove("filter-btn-active");
        if (btnFiltroBajoStock != null) btnFiltroBajoStock.getStyleClass().remove("filter-btn-active");
        if (btnFiltroAgotados != null) btnFiltroAgotados.getStyleClass().remove("filter-btn-active");

        if (activo != null && !activo.getStyleClass().contains("filter-btn-active")) {
            activo.getStyleClass().add("filter-btn-active");
        }
    }

    // =========================================================================
    // OPERACIONES DE INVENTARIO: ENTRADA FÍSICA Y STOCK MÍNIMO
    // =========================================================================

    /**
     * Abre el diálogo para registrar una entrada física de mercancía.
     * Ejemplo obligatorio:
     * - Existencia inicial: 20 unidades.
     * - Venta: 3 unidades -> 17.
     * - Recepción física: 10 unidades -> 27 unidades.
     */
    @FXML
    private void onRegistrarEntradaFisica() {
        if (sesionActual != null && sesionActual.isModoVistaPrevia()) {
            mostrarAlerta("Modo Vista Previa", "Operación no permitida: El sistema está en Modo Vista Previa (Solo Lectura). No se modifican existencias ni se registran movimientos reales.", Alert.AlertType.WARNING);
            return;
        }

        Inventario seleccionado = tblInventario.getSelectionModel().getSelectedItem();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Registrar Entrada Física de Mercancía");
        dialog.setHeaderText("Recepción física de mercancía al almacén / estantería\nLa cantidad sumará directamente a las existencias y registrará un movimiento de ENTRADA.");

        ButtonType btnAceptar = new ButtonType("Registrar Entrada", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnAceptar, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16, 20, 16, 20));

        ComboBox<Inventario> cmbProductos = new ComboBox<>(listaInventario);
        cmbProductos.setPrefWidth(360);
        cmbProductos.setConverter(new StringConverter<>() {
            @Override
            public String toString(Inventario inv) {
                if (inv == null) return "";
                String cod = inv.getCodigoProducto();
                String nom = inv.getNombreProducto();
                if (cod != null && !cod.isBlank()) {
                    return cod + " - " + (nom != null ? nom : "");
                }
                return nom != null ? nom : "";
            }

            @Override
            public Inventario fromString(String string) {
                return null;
            }
        });
        if (seleccionado != null) {
            cmbProductos.setValue(seleccionado);
        } else if (!listaInventario.isEmpty()) {
            cmbProductos.getSelectionModel().selectFirst();
        }

        Label lblStockActualInfo = new Label();
        actualizarLabelStockActual(lblStockActualInfo, cmbProductos.getValue());
        cmbProductos.valueProperty().addListener((obs, oldV, newV) -> actualizarLabelStockActual(lblStockActualInfo, newV));

        TextField txtCantidad = new TextField();
        txtCantidad.setPromptText("Ej. 10");

        TextArea txtMotivo = new TextArea();
        txtMotivo.setPromptText("Motivo de la entrada física (ej. Recepción de pedido de papelería, Conteo físico, Reposición)");
        txtMotivo.setPrefRowCount(3);

        grid.add(new Label("Producto:"), 0, 0);
        grid.add(cmbProductos, 1, 0);
        grid.add(lblStockActualInfo, 1, 1);
        grid.add(new Label("Cantidad a ingresar:"), 0, 2);
        grid.add(txtCantidad, 1, 2);
        grid.add(new Label("Motivo / Observaciones:"), 0, 3);
        grid.add(txtMotivo, 1, 3);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> resultado = dialog.showAndWait();
        if (resultado.isPresent() && resultado.get() == btnAceptar) {
            Inventario item = cmbProductos.getValue();
            if (item == null) {
                mostrarAlerta("Producto requerido", "Debes seleccionar un producto para registrar la entrada.", Alert.AlertType.ERROR);
                return;
            }

            int cantidad;
            try {
                cantidad = Integer.parseInt(txtCantidad.getText().trim());
                if (cantidad <= 0) {
                    mostrarAlerta("Cantidad inválida", "La cantidad debe ser un número entero mayor a cero.", Alert.AlertType.ERROR);
                    return;
                }
            } catch (NumberFormatException e) {
                mostrarAlerta("Cantidad numérica requerida", "Ingresa un número entero válido para la cantidad.", Alert.AlertType.ERROR);
                return;
            }

            String motivo = txtMotivo.getText();
            if (motivo == null || motivo.trim().isEmpty()) {
                mostrarAlerta("Motivo obligatorio", "Debes indicar el motivo o justificación de la entrada física.", Alert.AlertType.ERROR);
                return;
            }

            try {
                int stockAnterior = item.getStockActual();
                inventarioDAO.registrarEntradaFisica(item.getIdProducto(), cantidad, motivo.trim(), sesionActual);
                int stockNuevo = stockAnterior + cantidad;

                mostrarAlerta("Entrada Registrada Exitosamente",
                    "Producto: " + item.getNombreProducto() +
                    "\nExistencia anterior: " + stockAnterior + " unidades" +
                    "\nCantidad ingresada: +" + cantidad + " unidades" +
                    "\nNueva existencia: " + stockNuevo + " unidades" +
                    "\n\nEl movimiento de ENTRADA quedó registrado en la trazabilidad.",
                    Alert.AlertType.INFORMATION);

                cargarDatosInventario();

            } catch (SecurityException e) {
                mostrarAlerta("Acceso Denegado", e.getMessage(), Alert.AlertType.WARNING);
            } catch (IllegalStateException e) {
                mostrarAlerta("Inconsistencia en Inventario", e.getMessage(), Alert.AlertType.ERROR);
            } catch (IllegalArgumentException e) {
                mostrarAlerta("Datos Inválidos", e.getMessage(), Alert.AlertType.ERROR);
            } catch (SQLException e) {
                mostrarAlerta("Error de Base de Datos", "No fue posible registrar la entrada: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void actualizarLabelStockActual(Label label, Inventario inv) {
        if (label == null) return;
        if (inv != null) {
            label.setText("Existencias actuales: " + inv.getStockActual() + " unidades (Stock mín: " + inv.getStockMinimo() + ")");
            label.setStyle("-fx-text-fill: #183D53; -fx-font-style: italic; -fx-font-size: 11px;");
        } else {
            label.setText("");
        }
    }

    /**
     * Permite establecer y modificar el stock mínimo de un producto.
     */
    @FXML
    private void onModificarStockMinimo() {
        if (sesionActual != null && sesionActual.isModoVistaPrevia()) {
            mostrarAlerta("Modo Vista Previa", "Operación no permitida: El sistema está en Modo Vista Previa (Solo Lectura). No se modifican parámetros en MySQL.", Alert.AlertType.WARNING);
            return;
        }

        Inventario seleccionado = tblInventario.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarAlerta("Selecciona un producto", "Selecciona un producto de la tabla para modificar su stock mínimo.", Alert.AlertType.WARNING);
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Modificar Stock Mínimo");
        dialog.setHeaderText("Configurar umbral de alerta de existencias bajas para:\n" + seleccionado.getNombreProducto());

        ButtonType btnAceptar = new ButtonType("Actualizar Stock Mínimo", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnAceptar, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16, 20, 16, 20));

        TextField txtStockMin = new TextField(String.valueOf(seleccionado.getStockMinimo()));
        txtStockMin.setPromptText("Ej. 5");

        grid.add(new Label("Stock actual:"), 0, 0);
        grid.add(new Label(seleccionado.getStockActual() + " unidades"), 1, 0);
        grid.add(new Label("Nuevo stock mínimo:"), 0, 1);
        grid.add(txtStockMin, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> resultado = dialog.showAndWait();
        if (resultado.isPresent() && resultado.get() == btnAceptar) {
            try {
                int nuevoMin = Integer.parseInt(txtStockMin.getText().trim());
                if (nuevoMin < 0) {
                    mostrarAlerta("Valor no válido", "El stock mínimo no puede ser menor a cero.", Alert.AlertType.ERROR);
                    return;
                }

                inventarioDAO.actualizarStockMinimo(seleccionado.getIdProducto(), nuevoMin);
                mostrarAlerta("Stock Mínimo Actualizado", "El stock mínimo de '" + seleccionado.getNombreProducto() + "' fue actualizado a " + nuevoMin + " unidades.", Alert.AlertType.INFORMATION);
                cargarDatosInventario();

            } catch (IllegalStateException e) {
                mostrarAlerta("Inconsistencia en Inventario", e.getMessage(), Alert.AlertType.ERROR);
            } catch (NumberFormatException e) {
                mostrarAlerta("Valor no válido", "Ingresa un número entero válido para el stock mínimo.", Alert.AlertType.ERROR);
            } catch (SQLException e) {
                mostrarAlerta("Error de base de datos", "No fue posible actualizar el stock mínimo: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    // =========================================================================
    // HISTORIAL DE MOVIMIENTOS
    // =========================================================================

    @FXML
    private void onToggleVerMovimientos() {
        if (panelMovimientos == null) return;

        boolean visible = !panelMovimientos.isVisible();
        panelMovimientos.setVisible(visible);
        panelMovimientos.setManaged(visible);

        if (visible) {
            btnVerMovimientos.setText("📦 Ocultar Movimientos");
            cargarHistorialMovimientos();
        } else {
            btnVerMovimientos.setText("📜 Ver Movimientos");
        }
    }

    private void cargarHistorialMovimientos() {
        try {
            Inventario seleccionado = tblInventario.getSelectionModel().getSelectedItem();
            Integer idProducto = seleccionado != null ? seleccionado.getIdProducto() : null;

            List<MovimientoInventario> movimientos = inventarioDAO.listarMovimientos(idProducto, 100);
            listaMovimientos.setAll(movimientos);

            if (idProducto != null) {
                mostrarMensaje("Mostrando últimos " + movimientos.size() + " movimientos de: " + seleccionado.getNombreProducto());
            } else {
                mostrarMensaje("Mostrando últimos " + movimientos.size() + " movimientos generales del inventario");
            }
        } catch (SQLException e) {
            mostrarMensaje("Error al consultar movimientos: " + e.getMessage());
        }
    }

    private void mostrarMensaje(String msg) {
        if (lblMensajeEstado != null) {
            lblMensajeEstado.setText(msg);
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
