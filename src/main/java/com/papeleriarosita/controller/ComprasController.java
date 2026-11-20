package com.papeleriarosita.controller;

import com.papeleriarosita.dao.CompraDAO;
import com.papeleriarosita.dao.ProductoDAO;
import com.papeleriarosita.dao.ProveedorDAO;
import com.papeleriarosita.model.Compra;
import com.papeleriarosita.model.DetalleCompra;
import com.papeleriarosita.model.DetalleRecepcionCompra;
import com.papeleriarosita.model.Producto;
import com.papeleriarosita.model.Proveedor;
import com.papeleriarosita.model.UserSession;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Controlador de la interfaz gráfica del Módulo de Compras y Abastecimiento.
 * Administra el registro de órdenes de compra a proveedores, seguimiento de pedidos pendientes,
 * y la recepción física de mercancía al inventario (entregas parciales).
 */
public class ComprasController {

    @FXML private StackPane rootStackPane;
    @FXML private VBox mainContainer;

    // Métricas en cabecera
    @FXML private Label lblTotalCompras;
    @FXML private Label lblTotalPendientes;
    @FXML private Label lblTotalParciales;
    @FXML private Label lblTotalRecibidas;

    // Filtros de búsqueda
    @FXML private ComboBox<Proveedor> cmbFiltroProveedor;
    @FXML private ComboBox<String> cmbFiltroEstado;
    @FXML private DatePicker dpFiltroFechaDesde;
    @FXML private DatePicker dpFiltroFechaHasta;

    // Botones de acción principales
    @FXML private Button btnNuevaCompra;
    @FXML private Button btnRegistrarRecepcion;
    @FXML private Button btnAnularCompra;

    // Tabla Maestra de Compras
    @FXML private TableView<Compra> tblCompras;
    @FXML private TableColumn<Compra, Integer> colIdCompra;
    @FXML private TableColumn<Compra, String> colFechaCompra;
    @FXML private TableColumn<Compra, String> colProveedor;
    @FXML private TableColumn<Compra, String> colUsuario;
    @FXML private TableColumn<Compra, String> colTotal;
    @FXML private TableColumn<Compra, String> colEstado;

    // Tabla de Detalle de la Compra Seleccionada
    @FXML private Label lblTituloDetalle;
    @FXML private HBox boxMetricasDetalle;
    @FXML private Label lblDetSolicitadas;
    @FXML private Label lblDetRecibidas;
    @FXML private Label lblDetPendientes;
    @FXML private TableView<DetalleCompra> tblDetalles;
    @FXML private TableColumn<DetalleCompra, String> colDetCodigo;
    @FXML private TableColumn<DetalleCompra, String> colDetProducto;
    @FXML private TableColumn<DetalleCompra, Integer> colDetCantidadSolicitada;
    @FXML private TableColumn<DetalleCompra, Integer> colDetCantidadRecibida;
    @FXML private TableColumn<DetalleCompra, Integer> colDetCantidadPendiente;
    @FXML private TableColumn<DetalleCompra, String> colDetPrecioUnitario;
    @FXML private TableColumn<DetalleCompra, String> colDetSubtotal;

    // Modal Overlay 1: Nueva Orden de Compra
    @FXML private VBox modalNuevaCompra;
    @FXML private Label lblResponsableNuevaCompra;
    @FXML private ComboBox<Proveedor> cmbNuevoProveedor;
    @FXML private ComboBox<Producto> cmbNuevoProducto;
    @FXML private TextField txtNuevaCantidad;
    @FXML private TextField txtNuevoCostoUnitario;
    @FXML private Button btnAgregarItem;
    @FXML private TableView<DetalleCompra> tblNuevoPedidoItems;
    @FXML private TableColumn<DetalleCompra, String> colNuevoCodigo;
    @FXML private TableColumn<DetalleCompra, String> colNuevoProducto;
    @FXML private TableColumn<DetalleCompra, Integer> colNuevoCantidad;
    @FXML private TableColumn<DetalleCompra, String> colNuevoCosto;
    @FXML private TableColumn<DetalleCompra, String> colNuevoSubtotal;
    @FXML private Label lblMensajeNuevaCompra;
    @FXML private Label lblTotalNuevoPedido;
    @FXML private Button btnGuardarNuevaCompra;

    // Modal Overlay 2: Recepción Física de Mercancía
    @FXML private VBox modalRecepcion;
    @FXML private Label lblInfoCompraRecep;
    @FXML private TextField txtRecepRemision;
    @FXML private TextField txtRecepObservaciones;
    @FXML private TableView<ItemRecepcionVM> tblRecepItems;
    @FXML private TableColumn<ItemRecepcionVM, String> colRecepCodigo;
    @FXML private TableColumn<ItemRecepcionVM, String> colRecepProducto;
    @FXML private TableColumn<ItemRecepcionVM, Integer> colRecepSolicitado;
    @FXML private TableColumn<ItemRecepcionVM, Integer> colRecepYaRecibido;
    @FXML private TableColumn<ItemRecepcionVM, Integer> colRecepPendiente;
    @FXML private TableColumn<ItemRecepcionVM, String> colRecepAEntregar;
    @FXML private Label lblMensajeRecepcion;
    @FXML private Label lblTotalIngresanHoy;
    @FXML private Button btnConfirmarRecepcion;

    // DAOs y listas observables
    private final CompraDAO compraDAO = new CompraDAO();
    private final ProveedorDAO proveedorDAO = new ProveedorDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();

    private final ObservableList<Compra> listaCompras = FXCollections.observableArrayList();
    private final ObservableList<DetalleCompra> listaDetalles = FXCollections.observableArrayList();
    private final ObservableList<DetalleCompra> listaNuevoPedido = FXCollections.observableArrayList();
    private final ObservableList<ItemRecepcionVM> listaItemsRecepcion = FXCollections.observableArrayList();

    private UserSession sesionActual;
    private Compra compraSeleccionada;

    private static final NumberFormat FORMATO_MONEDA = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));

    @FXML
    public void initialize() {
        configurarColumnasMaestro();
        configurarColumnasDetalle();
        configurarColumnasNuevoPedido();
        configurarColumnasRecepcion();

        configurarFiltros();

        // Listener de selección de compra
        tblCompras.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            compraSeleccionada = newSel;
            actualizarEstadoAcciones(newSel);
            cargarDetallesCompra(newSel);
        });

        // Evento cambio de producto en nuevo pedido: prellenar costo con precio venta aproximado
        if (cmbNuevoProducto != null) {
            cmbNuevoProducto.getSelectionModel().selectedItemProperty().addListener((obs, oldP, newP) -> {
                if (newP != null && (txtNuevoCostoUnitario.getText() == null || txtNuevoCostoUnitario.getText().trim().isEmpty())) {
                    txtNuevoCostoUnitario.setText(newP.getPrecioVenta() != null ? newP.getPrecioVenta().toString() : "0");
                }
            });
        }

        cargarCompras();
    }

    public void setSesion(UserSession sesion) {
        this.sesionActual = sesion;
        aplicarPermisosDeRol();
    }

    private void aplicarPermisosDeRol() {
        boolean puedeCrear = sesionActual != null && (sesionActual.isAdmin() || sesionActual.isEmpleado());
        btnNuevaCompra.setDisable(!puedeCrear);

        if (lblResponsableNuevaCompra != null && sesionActual != null) {
            lblResponsableNuevaCompra.setText("Responsable: " + sesionActual.getUsername() + " (" + sesionActual.getRole() + ")");
        }

        // Anular compras solo permitido a la Administradora
        if (sesionActual != null && !sesionActual.isAdmin()) {
            btnAnularCompra.setVisible(false);
            btnAnularCompra.setManaged(false);
        }
    }

    private void configurarColumnasMaestro() {
        tblCompras.setItems(listaCompras);

        colIdCompra.setCellValueFactory(new PropertyValueFactory<>("idCompra"));
        colFechaCompra.setCellValueFactory(new PropertyValueFactory<>("fechaCompraFormateada"));
        colProveedor.setCellValueFactory(new PropertyValueFactory<>("nombreProveedor"));
        colUsuario.setCellValueFactory(new PropertyValueFactory<>("nombreUsuario"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalFormateado"));

        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colEstado.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().clear();
                    switch (item.toUpperCase()) {
                        case "REGISTRADA" -> badge.getStyleClass().add("badge-estado-registrada");
                        case "PARCIAL" -> badge.getStyleClass().add("badge-estado-parcial");
                        case "RECIBIDA" -> badge.getStyleClass().add("badge-estado-recibida");
                        case "ANULADA" -> badge.getStyleClass().add("badge-estado-anulada");
                        default -> badge.setStyle("-fx-padding: 3px 8px; -fx-background-color: #E2E8F0; -fx-background-radius: 10px;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
    }

    private void configurarColumnasDetalle() {
        tblDetalles.setItems(listaDetalles);

        colDetCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoProducto"));
        colDetProducto.setCellValueFactory(new PropertyValueFactory<>("nombreProducto"));
        colDetCantidadSolicitada.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colDetCantidadRecibida.setCellValueFactory(new PropertyValueFactory<>("cantidadRecibida"));
        colDetCantidadPendiente.setCellValueFactory(new PropertyValueFactory<>("cantidadPendiente"));
        colDetPrecioUnitario.setCellValueFactory(new PropertyValueFactory<>("precioUnitarioFormateado"));
        colDetSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotalFormateado"));
    }

    private void configurarColumnasNuevoPedido() {
        tblNuevoPedidoItems.setItems(listaNuevoPedido);

        colNuevoCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoProducto"));
        colNuevoProducto.setCellValueFactory(new PropertyValueFactory<>("nombreProducto"));
        colNuevoCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colNuevoCosto.setCellValueFactory(new PropertyValueFactory<>("precioUnitarioFormateado"));
        colNuevoSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotalFormateado"));
    }

    private void configurarColumnasRecepcion() {
        tblRecepItems.setItems(listaItemsRecepcion);

        colRecepCodigo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCodigo()));
        colRecepProducto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProducto()));
        colRecepSolicitado.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getCantidadSolicitada()).asObject());
        colRecepYaRecibido.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getCantidadYaRecibida()).asObject());
        colRecepPendiente.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getCantidadPendiente()).asObject());

        // Columna editable para indicar unidades que ingresan hoy
        colRecepAEntregar.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getCantidadIngresaHoy())));
        colRecepAEntregar.setCellFactory(col -> new TableCell<>() {
            private final TextField textField = new TextField();

            {
                textField.setPrefWidth(90.0);
                textField.setStyle("-fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;");
                textField.textProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal == null || newVal.trim().isEmpty()) {
                        return;
                    }
                    if (!newVal.matches("\\d*")) {
                        textField.setText(newVal.replaceAll("[^\\d]", ""));
                        return;
                    }
                    int index = getIndex();
                    if (index >= 0 && index < listaItemsRecepcion.size()) {
                        ItemRecepcionVM item = listaItemsRecepcion.get(index);
                        try {
                            int valor = Integer.parseInt(textField.getText().trim());
                            if (valor > item.getCantidadPendiente()) {
                                valor = item.getCantidadPendiente();
                                textField.setText(String.valueOf(valor));
                                lblMensajeRecepcion.setText("⚠️ Máximo permitido para " + item.getProducto() + ": " + valor);
                                lblMensajeRecepcion.setStyle("-fx-text-fill: #CA5733;");
                            } else {
                                lblMensajeRecepcion.setText("");
                            }
                            item.setCantidadIngresaHoy(valor);
                            actualizarTotalUnidadesRecepcion();
                        } catch (NumberFormatException ignored) {}
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= listaItemsRecepcion.size()) {
                    setGraphic(null);
                    setText(null);
                } else {
                    ItemRecepcionVM vm = listaItemsRecepcion.get(getIndex());
                    textField.setText(String.valueOf(vm.getCantidadIngresaHoy()));
                    // Si ya está completado, deshabilitar edición
                    textField.setDisable(vm.getCantidadPendiente() == 0);
                    setGraphic(textField);
                    setText(null);
                }
            }
        });
    }

    private void configurarFiltros() {
        cmbFiltroEstado.getItems().setAll("TODOS", "REGISTRADA", "PARCIAL", "RECIBIDA", "ANULADA");
        cmbFiltroEstado.setValue("TODOS");

        // Cargar proveedores en el combo de filtro
        try {
            List<Proveedor> provs = proveedorDAO.listarTodos();
            cmbFiltroProveedor.getItems().clear();
            // Agregar opción "Todos" con ID 0
            Proveedor todos = new Proveedor(0, "Todos los proveedores", "", "", "");
            cmbFiltroProveedor.getItems().add(todos);
            cmbFiltroProveedor.getItems().addAll(provs);
            cmbFiltroProveedor.setValue(todos);

            // Converter para mostrar nombres limpios
            StringConverter<Proveedor> converter = new StringConverter<>() {
                @Override
                public String toString(Proveedor p) {
                    return p != null ? p.getNombre() : "";
                }

                @Override
                public Proveedor fromString(String string) {
                    return null;
                }
            };
            cmbFiltroProveedor.setConverter(converter);
            if (cmbNuevoProveedor != null) {
                cmbNuevoProveedor.setConverter(converter);
            }

        } catch (SQLException e) {
            System.err.println("[COMPRAS] No se pudo cargar catálogo de proveedores para filtros: " + e.getMessage());
        }

        // Converter para ComboBox de Producto
        if (cmbNuevoProducto != null) {
            cmbNuevoProducto.setConverter(new StringConverter<>() {
                @Override
                public String toString(Producto p) {
                    return p != null ? (p.getCodigo() + " - " + p.getNombre()) : "";
                }

                @Override
                public Producto fromString(String string) {
                    return null;
                }
            });
        }
    }

    // =========================================================================
    // CONSULTA Y CARGA DE DATOS
    // =========================================================================

    @FXML
    public void cargarCompras() {
        try {
            Proveedor selProv = cmbFiltroProveedor.getValue();
            Integer idProv = (selProv != null && selProv.getIdProveedor() > 0) ? selProv.getIdProveedor() : null;

            String estado = cmbFiltroEstado.getValue();
            LocalDate fDesde = dpFiltroFechaDesde.getValue();
            LocalDate fHasta = dpFiltroFechaHasta.getValue();

            List<Compra> compras = compraDAO.listarCompras(idProv, estado, fDesde, fHasta);
            listaCompras.setAll(compras);

            actualizarMetricas(compras);

            if (tblCompras.getItems().isEmpty()) {
                limpiarDetalles();
            }

        } catch (SQLException e) {
            System.err.println("[ERROR COMPRAS] Error al listar compras: " + e.getMessage());
            if (sesionActual == null || !sesionActual.isModoVistaPrevia()) {
                mostrarAlerta("Error de Conexión", "No fue posible conectar con la base de datos para consultar compras: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    public void onFiltrar() {
        cargarCompras();
    }

    @FXML
    public void onLimpiarFiltros() {
        if (!cmbFiltroProveedor.getItems().isEmpty()) {
            cmbFiltroProveedor.getSelectionModel().selectFirst();
        }
        cmbFiltroEstado.setValue("TODOS");
        dpFiltroFechaDesde.setValue(null);
        dpFiltroFechaHasta.setValue(null);
        cargarCompras();
    }

    private void cargarDetallesCompra(Compra compra) {
        if (compra == null) {
            limpiarDetalles();
            return;
        }

        try {
            List<DetalleCompra> detalles = compraDAO.listarDetalles(compra.getIdCompra());
            listaDetalles.setAll(detalles);
            compra.setDetalles(detalles);

            lblTituloDetalle.setText("📦 Detalle de la Orden #" + compra.getIdCompra() + " – " + compra.getNombreProveedor());
            boxMetricasDetalle.setVisible(true);
            lblDetSolicitadas.setText("Solicitado: " + compra.getTotalCantidadSolicitada() + " unid.");
            lblDetRecibidas.setText("Recibido: " + compra.getTotalCantidadRecibida() + " unid.");
            lblDetPendientes.setText("Pendiente: " + compra.getTotalCantidadPendiente() + " unid.");

        } catch (SQLException e) {
            System.err.println("[ERROR COMPRAS] Error al cargar detalles de la compra #" + compra.getIdCompra() + ": " + e.getMessage());
            listaDetalles.clear();
        }
    }

    private void limpiarDetalles() {
        listaDetalles.clear();
        lblTituloDetalle.setText("📦 Detalle de la Orden Seleccionada");
        boxMetricasDetalle.setVisible(false);
    }

    private void actualizarEstadoAcciones(Compra compra) {
        if (compra == null) {
            btnRegistrarRecepcion.setDisable(true);
            btnAnularCompra.setDisable(true);
            return;
        }

        // Permite registrar recepción si no está completada ni anulada
        btnRegistrarRecepcion.setDisable(!compra.permiteRecepcion());

        // Anulación permitida solo a administradora si está en estado REGISTRADA sin recepciones
        boolean esAdmin = sesionActual != null && sesionActual.isAdmin();
        btnAnularCompra.setDisable(!(esAdmin && compra.permiteAnulacion()));
    }

    private void actualizarMetricas(List<Compra> compras) {
        int total = compras.size();
        int pendientes = 0;
        int parciales = 0;
        int recibidas = 0;

        for (Compra c : compras) {
            if (c.isRegistrada()) pendientes++;
            else if (c.isParcial()) parciales++;
            else if (c.isRecibida()) recibidas++;
        }

        lblTotalCompras.setText("Órdenes: " + total);
        lblTotalPendientes.setText("Pendientes: " + pendientes);
        lblTotalParciales.setText("Parciales: " + parciales);
        lblTotalRecibidas.setText("Recibidas: " + recibidas);
    }

    // =========================================================================
    // MODAL: NUEVA ORDEN DE COMPRA
    // =========================================================================

    @FXML
    public void onAbrirNuevaCompra() {
        lblMensajeNuevaCompra.setText("");
        listaNuevoPedido.clear();
        recalcularTotalNuevoPedido();

        // Cargar proveedores disponibles
        try {
            List<Proveedor> provs = proveedorDAO.listarTodos();
            cmbNuevoProveedor.getItems().setAll(provs);
            if (!provs.isEmpty()) {
                cmbNuevoProveedor.getSelectionModel().selectFirst();
            }
        } catch (SQLException e) {
            mostrarAlerta("Catálogo de Proveedores", "No se pudo cargar la lista de proveedores: " + e.getMessage(), Alert.AlertType.ERROR);
            return;
        }

        // Cargar productos disponibles
        try {
            List<Producto> productos = productoDAO.listarTodos();
            cmbNuevoProducto.getItems().setAll(productos);
            if (!productos.isEmpty()) {
                cmbNuevoProducto.getSelectionModel().selectFirst();
            }
        } catch (SQLException e) {
            mostrarAlerta("Catálogo de Productos", "No se pudo cargar la lista de productos: " + e.getMessage(), Alert.AlertType.ERROR);
            return;
        }

        txtNuevaCantidad.setText("1");
        modalNuevaCompra.setVisible(true);
    }

    @FXML
    public void onCerrarModalNuevaCompra() {
        modalNuevaCompra.setVisible(false);
    }

    @FXML
    public void onAgregarItemNuevoPedido() {
        lblMensajeNuevaCompra.setText("");
        Producto prod = cmbNuevoProducto.getValue();
        if (prod == null) {
            mostrarErrorNuevoPedido("Debe seleccionar un producto para agregar.");
            return;
        }

        int cantidad;
        try {
            cantidad = Integer.parseInt(txtNuevaCantidad.getText().trim());
            if (cantidad <= 0) {
                mostrarErrorNuevoPedido("La cantidad debe ser un número entero mayor a cero.");
                return;
            }
        } catch (NumberFormatException e) {
            mostrarErrorNuevoPedido("Ingrese un valor numérico válido para la cantidad.");
            return;
        }

        BigDecimal costoUnitario;
        try {
            String textoCosto = txtNuevoCostoUnitario.getText().trim().replace("$", "").replace(",", ".");
            costoUnitario = new BigDecimal(textoCosto);
            if (costoUnitario.compareTo(BigDecimal.ZERO) < 0) {
                mostrarErrorNuevoPedido("El costo unitario no puede ser negativo.");
                return;
            }
        } catch (Exception e) {
            mostrarErrorNuevoPedido("Ingrese un costo unitario válido (ejemplo: 4500.00).");
            return;
        }

        // Verificar si el producto ya está en la lista: si existe, acumular cantidad
        boolean yaExiste = false;
        for (DetalleCompra item : listaNuevoPedido) {
            if (item.getIdProducto() == prod.getIdProducto()) {
                item.setCantidad(item.getCantidad() + cantidad);
                item.setPrecioUnitario(costoUnitario);
                item.recalcularSubtotal();
                yaExiste = true;
                break;
            }
        }

        if (!yaExiste) {
            DetalleCompra nuevoItem = new DetalleCompra(
                prod.getIdProducto(),
                prod.getCodigo(),
                prod.getNombre(),
                cantidad,
                costoUnitario
            );
            listaNuevoPedido.add(nuevoItem);
        }

        tblNuevoPedidoItems.refresh();
        recalcularTotalNuevoPedido();

        txtNuevaCantidad.setText("1");
    }

    @FXML
    public void onQuitarItemNuevoPedido() {
        DetalleCompra seleccionado = tblNuevoPedidoItems.getSelectionModel().getSelectedItem();
        if (seleccionado != null) {
            listaNuevoPedido.remove(seleccionado);
            recalcularTotalNuevoPedido();
        } else {
            mostrarErrorNuevoPedido("Seleccione un ítem de la tabla para quitarlo.");
        }
    }

    private void recalcularTotalNuevoPedido() {
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleCompra item : listaNuevoPedido) {
            if (item.getSubtotal() != null) {
                total = total.add(item.getSubtotal());
            }
        }
        lblTotalNuevoPedido.setText(FORMATO_MONEDA.format(total));
    }

    private void mostrarErrorNuevoPedido(String mensaje) {
        lblMensajeNuevaCompra.setText("⚠️ " + mensaje);
        lblMensajeNuevaCompra.setStyle("-fx-text-fill: #D93668; -fx-font-weight: bold;");
    }

    @FXML
    public void onGuardarNuevaCompra() {
        if (sesionActual != null && sesionActual.isModoVistaPrevia()) {
            mostrarErrorNuevoPedido("Operación no permitida en Modo Vista Previa (Solo Lectura). No se guardan órdenes reales.");
            return;
        }

        lblMensajeNuevaCompra.setText("");
        Proveedor prov = cmbNuevoProveedor.getValue();
        if (prov == null || prov.getIdProveedor() <= 0) {
            mostrarErrorNuevoPedido("Debe seleccionar un proveedor existente.");
            return;
        }

        if (listaNuevoPedido.isEmpty()) {
            mostrarErrorNuevoPedido("Debe agregar al menos un producto a la orden de compra.");
            return;
        }

        if (sesionActual == null) {
            mostrarErrorNuevoPedido("Se requiere una sesión de usuario autenticada.");
            return;
        }

        Compra nuevaCompra = new Compra();
        nuevaCompra.setIdProveedor(prov.getIdProveedor());
        nuevaCompra.setIdUsuario(sesionActual.getIdUsuario());
        nuevaCompra.setDetalles(new ArrayList<>(listaNuevoPedido));
        nuevaCompra.recalcularTotal();
        nuevaCompra.setEstado("REGISTRADA");

        try {
            int idGenerado = compraDAO.registrarOrdenCompra(nuevaCompra, sesionActual);
            onCerrarModalNuevaCompra();
            cargarCompras();

            // Seleccionar la nueva compra
            for (Compra c : tblCompras.getItems()) {
                if (c.getIdCompra() == idGenerado) {
                    tblCompras.getSelectionModel().select(c);
                    break;
                }
            }

            mostrarAlerta(
                "Orden Registrada",
                "La orden de compra #" + idGenerado + " para '" + prov.getNombre() + "' fue registrada con éxito en estado REGISTRADA.\n\n" +
                "Recuerde: Las existencias de inventario no han sido alteradas hasta que registre la recepción física de la mercancía.",
                Alert.AlertType.INFORMATION
            );

        } catch (IllegalStateException e) {
            // Manejo especial cuando MySQL requiere la migración de la Etapa 4
            mostrarAlerta(
                "Aviso de Base de Datos (Etapa 4)",
                e.getMessage(),
                Alert.AlertType.WARNING
            );
        } catch (SQLException e) {
            mostrarAlerta("Error al Registrar", "No fue posible guardar la orden de compra: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // =========================================================================
    // MODAL: RECEPCIÓN FÍSICA DE MERCANCÍA (ENTREGAS PARCIALES)
    // =========================================================================

    @FXML
    public void onAbrirRecepcion() {
        if (compraSeleccionada == null) {
            mostrarAlerta("Selección requerida", "Seleccione una orden de compra para registrar su recepción.", Alert.AlertType.WARNING);
            return;
        }

        if (!compraSeleccionada.permiteRecepcion()) {
            mostrarAlerta("Operación no permitida", "La orden #" + compraSeleccionada.getIdCompra() + " ya se encuentra " + compraSeleccionada.getEstado() + ".", Alert.AlertType.WARNING);
            return;
        }

        lblInfoCompraRecep.setText("Orden #" + compraSeleccionada.getIdCompra() + " – " + compraSeleccionada.getNombreProveedor() + " (" + compraSeleccionada.getEstado() + ")");
        txtRecepRemision.clear();
        txtRecepObservaciones.clear();
        lblMensajeRecepcion.setText("");

        // Cargar ítems a la tabla de recepción
        listaItemsRecepcion.clear();
        for (DetalleCompra det : listaDetalles) {
            listaItemsRecepcion.add(new ItemRecepcionVM(
                det.getIdDetalleCompra(),
                det.getIdProducto(),
                det.getCodigoProducto(),
                det.getNombreProducto(),
                det.getCantidad(),
                det.getCantidadRecibida(),
                det.getCantidadPendiente()
            ));
        }

        actualizarTotalUnidadesRecepcion();
        modalRecepcion.setVisible(true);
    }

    @FXML
    public void onCerrarModalRecepcion() {
        modalRecepcion.setVisible(false);
    }

    private void actualizarTotalUnidadesRecepcion() {
        int totalIngresa = 0;
        for (ItemRecepcionVM item : listaItemsRecepcion) {
            totalIngresa += item.getCantidadIngresaHoy();
        }
        lblTotalIngresanHoy.setText("Total unidades a ingresar hoy: " + totalIngresa);
    }

    @FXML
    public void onConfirmarRecepcionFisica() {
        if (sesionActual != null && sesionActual.isModoVistaPrevia()) {
            lblMensajeRecepcion.setText("⚠️ Modo Vista Previa: No se registran recepciones físicas ni se altera el inventario real.");
            return;
        }

        lblMensajeRecepcion.setText("");

        if (compraSeleccionada == null || sesionActual == null) {
            return;
        }

        List<DetalleRecepcionCompra> entregas = new ArrayList<>();
        int totalUnidades = 0;

        for (ItemRecepcionVM item : listaItemsRecepcion) {
            int ingresa = item.getCantidadIngresaHoy();
            if (ingresa < 0) {
                lblMensajeRecepcion.setText("⚠️ Las unidades a recibir no pueden ser negativas.");
                return;
            }
            if (ingresa > item.getCantidadPendiente()) {
                lblMensajeRecepcion.setText("⚠️ Para " + item.getProducto() + " intenta recibir más del saldo pendiente.");
                return;
            }
            if (ingresa > 0) {
                entregas.add(new DetalleRecepcionCompra(
                    item.getIdDetalleCompra(),
                    item.getIdProducto(),
                    ingresa,
                    txtRecepObservaciones.getText()
                ));
                totalUnidades += ingresa;
            }
        }

        if (totalUnidades == 0) {
            lblMensajeRecepcion.setText("⚠️ Ingrese al menos una unidad para registrar la recepción física.");
            lblMensajeRecepcion.setStyle("-fx-text-fill: #D93668; -fx-font-weight: bold;");
            return;
        }

        String remision = txtRecepRemision.getText();
        String obs = txtRecepObservaciones.getText();

        try {
            int idRecepcion = compraDAO.registrarRecepcionFisica(
                compraSeleccionada.getIdCompra(),
                remision,
                obs,
                entregas,
                sesionActual
            );

            onCerrarModalRecepcion();
            cargarCompras();

            // Re-seleccionar la compra para refrescar detalles y saldos
            for (Compra c : tblCompras.getItems()) {
                if (c.getIdCompra() == compraSeleccionada.getIdCompra()) {
                    tblCompras.getSelectionModel().select(c);
                    break;
                }
            }

            mostrarAlerta(
                "Recepción Registrada con Éxito",
                "Se registró la recepción física #" + idRecepcion + " de la compra #" + compraSeleccionada.getIdCompra() + ".\n\n" +
                "• Unidades ingresadas al almacén: " + totalUnidades + "\n" +
                "• El inventario fue incrementado por las unidades recibidas.\n" +
                "• Se registró trazabilidad en movimientos de inventario.",
                Alert.AlertType.INFORMATION
            );

        } catch (IllegalStateException e) {
            mostrarAlerta("Migración Pendiente", e.getMessage(), Alert.AlertType.WARNING);
        } catch (SQLException e) {
            mostrarAlerta("Error al Recibir", "No fue posible registrar la recepción física: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // =========================================================================
    // ANULACIÓN DE COMPRA
    // =========================================================================

    @FXML
    public void onAnularCompra() {
        if (sesionActual != null && sesionActual.isModoVistaPrevia()) {
            mostrarAlerta("Modo Vista Previa", "Operación no permitida: El sistema está en Modo Vista Previa (Solo Lectura). No se anulan órdenes reales en MySQL.", Alert.AlertType.WARNING);
            return;
        }

        if (compraSeleccionada == null) {
            mostrarAlerta("Selección Requerida", "Seleccione la orden de compra que desea anular.", Alert.AlertType.WARNING);
            return;
        }

        if (!compraSeleccionada.permiteAnulacion()) {
            mostrarAlerta("Operación Rechazada", "Solo se pueden anular órdenes en estado REGISTRADA que no tengan mercancía recibida en inventario.", Alert.AlertType.WARNING);
            return;
        }

        Alert confirmacion = new Alert(
            Alert.AlertType.CONFIRMATION,
            "¿Está seguro de que desea anular la orden de compra #" + compraSeleccionada.getIdCompra() + " de '" + compraSeleccionada.getNombreProveedor() + "'?\n\nEsta operación es definitiva.",
            ButtonType.YES, ButtonType.NO
        );
        confirmacion.setTitle("Confirmar Anulación");
        confirmacion.setHeaderText("Anular Orden de Compra");
        Optional<ButtonType> resp = confirmacion.showAndWait();

        if (resp.isPresent() && resp.get() == ButtonType.YES) {
            try {
                compraDAO.anularCompra(compraSeleccionada.getIdCompra(), "Anulación solicitada por usuario", sesionActual);
                cargarCompras();
                mostrarAlerta("Orden Anulada", "La compra #" + compraSeleccionada.getIdCompra() + " ha sido marcada como ANULADA.", Alert.AlertType.INFORMATION);
            } catch (Exception e) {
                mostrarAlerta("Error al Anular", "No fue posible anular la orden: " + e.getMessage(), Alert.AlertType.ERROR);
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
    // VIEW MODEL INTERNO PARA RECEPCIONES FÍSICAS
    // =========================================================================

    public static class ItemRecepcionVM {
        private final int idDetalleCompra;
        private final int idProducto;
        private final String codigo;
        private final String producto;
        private final int cantidadSolicitada;
        private final int cantidadYaRecibida;
        private final int cantidadPendiente;
        private final IntegerProperty cantidadIngresaHoy;

        public ItemRecepcionVM(int idDetalleCompra, int idProducto, String codigo,
                               String producto, int cantidadSolicitada, int cantidadYaRecibida,
                               int cantidadPendiente) {
            this.idDetalleCompra = idDetalleCompra;
            this.idProducto = idProducto;
            this.codigo = codigo;
            this.producto = producto;
            this.cantidadSolicitada = cantidadSolicitada;
            this.cantidadYaRecibida = cantidadYaRecibida;
            this.cantidadPendiente = cantidadPendiente;
            // Por defecto prellenamos con el saldo pendiente si es > 0
            this.cantidadIngresaHoy = new SimpleIntegerProperty(cantidadPendiente);
        }

        public int getIdDetalleCompra() { return idDetalleCompra; }
        public int getIdProducto() { return idProducto; }
        public String getCodigo() { return codigo; }
        public String getProducto() { return producto; }
        public int getCantidadSolicitada() { return cantidadSolicitada; }
        public int getCantidadYaRecibida() { return cantidadYaRecibida; }
        public int getCantidadPendiente() { return cantidadPendiente; }

        public int getCantidadIngresaHoy() { return cantidadIngresaHoy.get(); }
        public void setCantidadIngresaHoy(int val) { cantidadIngresaHoy.set(val); }
        public IntegerProperty cantidadIngresaHoyProperty() { return cantidadIngresaHoy; }
    }
}
