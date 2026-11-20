package com.papeleriarosita.controller;

import com.papeleriarosita.dao.CategoriaDAO;
import com.papeleriarosita.dao.ProductoDAO;
import com.papeleriarosita.dao.VentaDAO;
import com.papeleriarosita.model.Categoria;
import com.papeleriarosita.model.DetalleVenta;
import com.papeleriarosita.model.Producto;
import com.papeleriarosita.model.UserSession;
import com.papeleriarosita.model.Venta;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
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
 * Controlador de la Vista de Ventas y Facturación de Mostrador.
 * Gestiona el catálogo de productos disponibles, el carrito de compras en mostrador,
 * cobro en efectivo con cálculo de cambio en tiempo real y el historial/cuadre diario.
 */
public class VentasController {

    @FXML private StackPane rootStackPane;

    // Encabezado y KPIs superiores
    @FXML private Label lblKpiVentasHoy;
    @FXML private Label lblKpiRecaudadoHoy;
    @FXML private Label lblUsuarioCajero;

    // Barra de Búsqueda y Filtros de Productos
    @FXML private TextField txtBuscarProducto;
    @FXML private ComboBox<Categoria> cmbFiltroCategoria;
    @FXML private TextField txtCantidadVenta;
    @FXML private Button btnAgregarAlCarrito;

    // Catálogo de Productos
    @FXML private TableView<Producto> tblCatalogo;
    @FXML private TableColumn<Producto, String> colCatCodigo;
    @FXML private TableColumn<Producto, String> colCatNombre;
    @FXML private TableColumn<Producto, String> colCatCategoria;
    @FXML private TableColumn<Producto, String> colCatPrecio;
    @FXML private TableColumn<Producto, Integer> colCatStock;

    // Carrito de Compras
    @FXML private Label lblTotalArticulosCarrito;
    @FXML private TableView<DetalleVenta> tblCarrito;
    @FXML private TableColumn<DetalleVenta, String> colCarProducto;
    @FXML private TableColumn<DetalleVenta, Integer> colCarCantidad;
    @FXML private TableColumn<DetalleVenta, String> colCarPrecio;
    @FXML private TableColumn<DetalleVenta, String> colCarSubtotal;

    @FXML private ComboBox<String> cmbMetodoPago;
    @FXML private HBox boxCalculoEfectivo;
    @FXML private TextField txtEfectivoRecibido;
    @FXML private Label lblCambioCalculado;
    @FXML private Label lblTotalVenta;
    @FXML private Label lblMensajeVenta;
    @FXML private Button btnLimpiarCarrito;
    @FXML private Button btnConfirmarVenta;

    // =========================================================================
    // ELEMENTOS DEL HISTORIAL Y CUADRE DIARIO
    // =========================================================================
    @FXML private Label lblHistorialTotalVentas;
    @FXML private Label lblHistorialTotalRecaudado;
    @FXML private Label lblHistorialEfectivo;
    @FXML private Label lblHistorialElectronico;

    @FXML private DatePicker dpHistorialDesde;
    @FXML private DatePicker dpHistorialHasta;
    @FXML private ComboBox<String> cmbHistorialMetodo;

    @FXML private TableView<Venta> tblHistorialVentas;
    @FXML private TableColumn<Venta, Integer> colHisIdVenta;
    @FXML private TableColumn<Venta, String> colHisFecha;
    @FXML private TableColumn<Venta, String> colHisResponsable;
    @FXML private TableColumn<Venta, String> colHisCliente;
    @FXML private TableColumn<Venta, String> colHisMetodo;
    @FXML private TableColumn<Venta, Integer> colHisArticulos;
    @FXML private TableColumn<Venta, String> colHisTotal;
    @FXML private TableColumn<Venta, String> colHisEstado;

    @FXML private Label lblTituloDetalleVenta;
    @FXML private TableView<DetalleVenta> tblDetallesVenta;
    @FXML private TableColumn<DetalleVenta, String> colDetCodigo;
    @FXML private TableColumn<DetalleVenta, String> colDetProducto;
    @FXML private TableColumn<DetalleVenta, Integer> colDetCantidad;
    @FXML private TableColumn<DetalleVenta, String> colDetPrecioUnitario;
    @FXML private TableColumn<DetalleVenta, String> colDetSubtotal;

    // DAOs y colecciones de datos
    private final VentaDAO ventaDAO = new VentaDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final CategoriaDAO categoriaDAO = new CategoriaDAO();

    private final ObservableList<Producto> listaCatalogo = FXCollections.observableArrayList();
    private final ObservableList<DetalleVenta> listaCarrito = FXCollections.observableArrayList();
    private final ObservableList<Venta> listaHistorial = FXCollections.observableArrayList();
    private final ObservableList<DetalleVenta> listaDetallesVenta = FXCollections.observableArrayList();

    private UserSession sesionActual;
    private static final NumberFormat FORMATO_MONEDA = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));

    @FXML
    public void initialize() {
        configurarColumnasCatalogo();
        configurarColumnasCarrito();
        configurarColumnasHistorial();
        configurarColumnasDetalleVenta();

        configurarMetodosPago();
        configurarFiltrosCategorias();

        // Listener de efectivo para calcular cambio en tiempo real
        if (txtEfectivoRecibido != null) {
            txtEfectivoRecibido.textProperty().addListener((obs, oldVal, newVal) -> calcularCambio());
        }

        // Listener de selección en el catálogo (doble clic para agregar rápido)
        tblCatalogo.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && tblCatalogo.getSelectionModel().getSelectedItem() != null) {
                onAgregarAlCarrito();
            }
        });

        // Listener de selección en historial para mostrar productos vendidos
        tblHistorialVentas.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            cargarDetallesVentaSeleccionada(newSel);
        });

        cargarCatalogoProductos();
        cargarHistorialVentas();
        actualizarKpisDiarios();
    }

    public void setSesion(UserSession sesion) {
        this.sesionActual = sesion;
        if (sesion != null && lblUsuarioCajero != null) {
            lblUsuarioCajero.setText("Cajera: " + sesion.getUsername() + " (" + sesion.getRole() + ")");
        }
    }

    private void configurarColumnasCatalogo() {
        tblCatalogo.setItems(listaCatalogo);

        colCatCodigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        colCatNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colCatCategoria.setCellValueFactory(new PropertyValueFactory<>("nombreCategoria"));
        colCatPrecio.setCellValueFactory(new PropertyValueFactory<>("precioVentaFormateado"));
        colCatStock.setCellValueFactory(new PropertyValueFactory<>("stockActual"));

        // Alerta visual de stock en la tabla de catálogo
        colCatStock.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Integer stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || stock == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(stock));
                    if (stock <= 0) {
                        setStyle("-fx-text-fill: #D93668; -fx-font-weight: bold;");
                    } else if (stock <= 5) {
                        setStyle("-fx-text-fill: #B78103; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #087E70; -fx-font-weight: bold;");
                    }
                }
            }
        });
    }

    private void configurarColumnasCarrito() {
        tblCarrito.setItems(listaCarrito);

        colCarProducto.setCellValueFactory(new PropertyValueFactory<>("nombreProducto"));
        colCarCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colCarPrecio.setCellValueFactory(new PropertyValueFactory<>("precioUnitarioFormateado"));
        colCarSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotalFormateado"));
    }

    private void configurarColumnasHistorial() {
        tblHistorialVentas.setItems(listaHistorial);

        colHisIdVenta.setCellValueFactory(new PropertyValueFactory<>("idVenta"));
        colHisFecha.setCellValueFactory(new PropertyValueFactory<>("fechaVentaFormateada"));
        colHisResponsable.setCellValueFactory(new PropertyValueFactory<>("nombreUsuario"));
        colHisCliente.setCellValueFactory(new PropertyValueFactory<>("nombreCliente"));
        colHisArticulos.setCellValueFactory(new PropertyValueFactory<>("totalArticulos"));
        colHisTotal.setCellValueFactory(new PropertyValueFactory<>("totalFormateado"));

        colHisMetodo.setCellValueFactory(new PropertyValueFactory<>("metodoPago"));
        colHisMetodo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String metodo, boolean empty) {
                super.updateItem(metodo, empty);
                if (empty || metodo == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(metodo);
                    badge.getStyleClass().clear();
                    switch (metodo.toUpperCase()) {
                        case "EFECTIVO" -> badge.getStyleClass().add("badge-metodo-efectivo");
                        case "TRANSFERENCIA" -> badge.getStyleClass().add("badge-metodo-transferencia");
                        case "TARJETA" -> badge.getStyleClass().add("badge-metodo-tarjeta");
                        default -> badge.setStyle("-fx-padding: 3px 8px; -fx-background-color: #E2E8F0; -fx-background-radius: 10px;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        colHisEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colHisEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean empty) {
                super.updateItem(estado, empty);
                if (empty || estado == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(estado);
                    badge.getStyleClass().clear();
                    if ("REGISTRADA".equalsIgnoreCase(estado)) {
                        badge.getStyleClass().add("badge-estado-recibida");
                    } else {
                        badge.getStyleClass().add("badge-estado-anulada");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
    }

    private void configurarColumnasDetalleVenta() {
        tblDetallesVenta.setItems(listaDetallesVenta);

        colDetCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoProducto"));
        colDetProducto.setCellValueFactory(new PropertyValueFactory<>("nombreProducto"));
        colDetCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colDetPrecioUnitario.setCellValueFactory(new PropertyValueFactory<>("precioUnitarioFormateado"));
        colDetSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotalFormateado"));
    }

    private void configurarMetodosPago() {
        // En Papelería Rosita se trabaja exclusivamente con efectivo
        if (cmbMetodoPago != null) {
            cmbMetodoPago.getItems().setAll("EFECTIVO");
            cmbMetodoPago.setValue("EFECTIVO");
        }

        if (boxCalculoEfectivo != null) {
            boxCalculoEfectivo.setVisible(true);
            boxCalculoEfectivo.setManaged(true);
        }

        if (cmbHistorialMetodo != null) {
            cmbHistorialMetodo.getItems().setAll("TODOS", "EFECTIVO");
            cmbHistorialMetodo.setValue("TODOS");
        }
    }

    private void configurarFiltrosCategorias() {
        try {
            List<Categoria> categorias = categoriaDAO.listarTodas();
            cmbFiltroCategoria.getItems().clear();
            Categoria todas = new Categoria(0, "Todas las categorías", "Filtro general");
            cmbFiltroCategoria.getItems().add(todas);
            cmbFiltroCategoria.getItems().addAll(categorias);
            cmbFiltroCategoria.setValue(todas);

            cmbFiltroCategoria.setConverter(new StringConverter<>() {
                @Override
                public String toString(Categoria cat) {
                    return cat != null ? cat.getNombre() : "";
                }

                @Override
                public Categoria fromString(String string) {
                    return null;
                }
            });

            cmbFiltroCategoria.valueProperty().addListener((obs, oldVal, newVal) -> onBuscarProducto());

        } catch (SQLException e) {
            System.err.println("[ERROR VENTAS] Error al cargar categorías: " + e.getMessage());
        }
    }

    // =========================================================================
    // ACCIONES DE CATÁLOGO Y BÚSQUEDA
    // =========================================================================

    @FXML
    public void cargarCatalogoProductos() {
        try {
            List<Producto> productos = productoDAO.listarTodos();
            // Filtrar únicamente productos activos para la venta de mostrador
            List<Producto> activos = productos.stream().filter(Producto::isActivo).toList();
            listaCatalogo.setAll(activos);
        } catch (SQLException e) {
            System.err.println("[ERROR VENTAS] Error al cargar catálogo de productos: " + e.getMessage());
            mostrarErrorVenta("No fue posible cargar el catálogo de productos: " + e.getMessage());
        }
    }

    @FXML
    public void onBuscarProducto() {
        String criterio = txtBuscarProducto.getText();
        Categoria catSel = cmbFiltroCategoria.getValue();
        Integer idCat = (catSel != null && catSel.getIdCategoria() > 0) ? catSel.getIdCategoria() : null;

        try {
            List<Producto> filtrados = productoDAO.buscarPorNombreOCodigo(criterio);
            // Filtrar productos activos y por categoría si está seleccionada
            List<Producto> resultado = filtrados.stream()
                .filter(Producto::isActivo)
                .filter(p -> idCat == null || p.getIdCategoria() == idCat)
                .toList();
            listaCatalogo.setAll(resultado);
        } catch (SQLException e) {
            System.err.println("[ERROR VENTAS] Error en búsqueda de productos: " + e.getMessage());
        }
    }

    @FXML
    public void onLimpiarBusquedaProducto() {
        txtBuscarProducto.clear();
        if (!cmbFiltroCategoria.getItems().isEmpty()) {
            cmbFiltroCategoria.setValue(cmbFiltroCategoria.getItems().get(0));
        }
        cargarCatalogoProductos();
    }

    // =========================================================================
    // ACCIONES DEL CARRITO DE COMPRAS Y COBRO EN MOSTRADOR
    // =========================================================================

    @FXML
    public void onAgregarAlCarrito() {
        lblMensajeVenta.setText("");

        Producto prodSel = tblCatalogo.getSelectionModel().getSelectedItem();
        if (prodSel == null) {
            mostrarErrorVenta("Seleccione un producto del catálogo para agregar al carrito.");
            return;
        }

        int cantidad = 1;
        try {
            cantidad = Integer.parseInt(txtCantidadVenta.getText().trim());
            if (cantidad <= 0) {
                mostrarErrorVenta("La cantidad a vender debe ser un número entero mayor a cero.");
                return;
            }
        } catch (NumberFormatException e) {
            mostrarErrorVenta("La cantidad ingresada no es válida.");
            return;
        }

        // Validar stock disponible
        int stockDisponible = prodSel.getStockActual();
        int cantidadYaEnCarrito = 0;
        for (DetalleVenta item : listaCarrito) {
            if (item.getIdProducto() == prodSel.getIdProducto()) {
                cantidadYaEnCarrito += item.getCantidad();
            }
        }

        if (cantidadYaEnCarrito + cantidad > stockDisponible) {
            mostrarErrorVenta("Stock insuficiente para '" + prodSel.getNombre() + "'. Disponible en tienda: " + stockDisponible + " unidades.");
            return;
        }

        // Si ya está en el carrito, aumentar la cantidad del renglón existente
        boolean yaExiste = false;
        for (DetalleVenta item : listaCarrito) {
            if (item.getIdProducto() == prodSel.getIdProducto()) {
                item.setCantidad(item.getCantidad() + cantidad);
                item.recalcularSubtotal();
                yaExiste = true;
                break;
            }
        }

        if (!yaExiste) {
            DetalleVenta nuevoItem = new DetalleVenta();
            nuevoItem.setIdProducto(prodSel.getIdProducto());
            nuevoItem.setCodigoProducto(prodSel.getCodigo());
            nuevoItem.setNombreProducto(prodSel.getNombre());
            nuevoItem.setCantidad(cantidad);
            nuevoItem.setPrecioUnitario(prodSel.getPrecioVenta());
            nuevoItem.recalcularSubtotal();
            listaCarrito.add(nuevoItem);
        }

        tblCarrito.refresh();
        recalcularTotalCarrito();
        txtCantidadVenta.setText("1");
    }

    @FXML
    public void onAumentarCantidadCarrito() {
        DetalleVenta sel = tblCarrito.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarErrorVenta("Seleccione un artículo del carrito para aumentar.");
            return;
        }

        // Validar stock con respecto al catálogo
        Producto prod = buscarProductoEnCatalogo(sel.getIdProducto());
        int stockMax = (prod != null) ? prod.getStockActual() : Integer.MAX_VALUE;

        if (sel.getCantidad() + 1 > stockMax) {
            mostrarErrorVenta("No hay más existencias disponibles en stock para '" + sel.getNombreProducto() + "'.");
            return;
        }

        sel.setCantidad(sel.getCantidad() + 1);
        sel.recalcularSubtotal();
        tblCarrito.refresh();
        recalcularTotalCarrito();
    }

    @FXML
    public void onDisminuirCantidadCarrito() {
        DetalleVenta sel = tblCarrito.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarErrorVenta("Seleccione un artículo del carrito para disminuir.");
            return;
        }

        if (sel.getCantidad() - 1 <= 0) {
            listaCarrito.remove(sel);
        } else {
            sel.setCantidad(sel.getCantidad() - 1);
            sel.recalcularSubtotal();
        }

        tblCarrito.refresh();
        recalcularTotalCarrito();
    }

    @FXML
    public void onQuitarItemCarrito() {
        DetalleVenta sel = tblCarrito.getSelectionModel().getSelectedItem();
        if (sel != null) {
            listaCarrito.remove(sel);
            recalcularTotalCarrito();
        } else {
            mostrarErrorVenta("Seleccione un ítem del carrito para quitarlo.");
        }
    }

    @FXML
    public void onLimpiarCarrito() {
        listaCarrito.clear();
        recalcularTotalCarrito();
        if (txtEfectivoRecibido != null) {
            txtEfectivoRecibido.clear();
        }
        if (lblCambioCalculado != null) {
            lblCambioCalculado.setText("");
        }
        if (lblMensajeVenta != null) {
            lblMensajeVenta.setText("");
        }
    }

    private void recalcularTotalCarrito() {
        BigDecimal total = BigDecimal.ZERO;
        int articulos = 0;
        for (DetalleVenta item : listaCarrito) {
            if (item.getSubtotal() != null) {
                total = total.add(item.getSubtotal());
            }
            articulos += item.getCantidad();
        }
        lblTotalVenta.setText(FORMATO_MONEDA.format(total));
        lblTotalArticulosCarrito.setText(articulos + " artículo" + (articulos == 1 ? "" : "s"));
        calcularCambio();
    }

    private void calcularCambio() {
        if (txtEfectivoRecibido == null || lblCambioCalculado == null) {
            return;
        }

        String texto = txtEfectivoRecibido.getText();
        if (texto == null || texto.trim().isEmpty()) {
            lblCambioCalculado.setText("Cambio: $ 0,00");
            lblCambioCalculado.setStyle("-fx-font-weight: bold; -fx-text-fill: #087E70; -fx-font-size: 14px;");
            return;
        }

        try {
            BigDecimal recibido = new BigDecimal(texto.trim().replace("$", "").replace(",", "."));
            BigDecimal total = BigDecimal.ZERO;
            for (DetalleVenta item : listaCarrito) {
                total = total.add(item.getSubtotal());
            }

            BigDecimal cambio = recibido.subtract(total);
            if (cambio.compareTo(BigDecimal.ZERO) >= 0) {
                lblCambioCalculado.setText("Cambio: " + FORMATO_MONEDA.format(cambio));
                lblCambioCalculado.setStyle("-fx-font-weight: bold; -fx-text-fill: #087E70; -fx-font-size: 14px;");
            } else {
                lblCambioCalculado.setText("Faltan: " + FORMATO_MONEDA.format(cambio.abs()));
                lblCambioCalculado.setStyle("-fx-font-weight: bold; -fx-text-fill: #D93668; -fx-font-size: 14px;");
            }
        } catch (Exception e) {
            lblCambioCalculado.setText("Monto inválido");
            lblCambioCalculado.setStyle("-fx-text-fill: #D93668; -fx-font-weight: bold; -fx-font-size: 14px;");
        }
    }

    /**
     * =========================================================================
     * COBRO EN MOSTRADOR Y TRANSICIÓN DE VENTA (SUSTENTACIÓN)
     * =========================================================================
     * ¿Cuándo se ejecuta?
     * Cuando el usuario hace clic en el botón '💵 Cobrar en Efectivo' en la interfaz.
     *
     * ¿Qué valida y qué hace?
     * 1. Comprueba que haya una cajera activa (UserSession).
     * 2. Verifica que el carrito no esté vacío.
     * 3. Valida que el efectivo recibido sea mayor o igual al total a cobrar
     *    y calcula el cambio exacto para el cliente.
     * 4. Muestra un diálogo de confirmación visual para evitar errores de digitación.
     * 5. Al confirmar, delega a 'VentaDAO.registrarVenta(...)', el cual abre la
     *    transacción en MySQL y dispara los triggers de inventario.
     * 6. Al tener éxito: limpia el carrito, recarga el catálogo con los nuevos stocks
     *    descontados y actualiza los indicadores del arqueo diario en tiempo real.
     * =========================================================================
     */
    @FXML
    public void onConfirmarVenta() {
        if (sesionActual != null && sesionActual.isModoVistaPrevia()) {
            mostrarErrorVenta("Operación no permitida: El sistema está en Modo Vista Previa (Solo Lectura). No se registran cobros ni se descuenta inventario.");
            return;
        }

        lblMensajeVenta.setText("");

        if (sesionActual == null) {
            mostrarErrorVenta("No hay una sesión de usuario activa para autorizar la venta.");
            return;
        }

        if (listaCarrito.isEmpty()) {
            mostrarErrorVenta("El carrito está vacío. Agregue productos antes de cobrar.");
            return;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (DetalleVenta item : listaCarrito) {
            total = total.add(item.getSubtotal());
        }

        // Medio de pago único: EFECTIVO
        String metodo = (cmbMetodoPago != null && cmbMetodoPago.getValue() != null) ? cmbMetodoPago.getValue() : "EFECTIVO";

        // Validar pago en efectivo y calcular cambio
        BigDecimal cambio = BigDecimal.ZERO;
        String txtRec = (txtEfectivoRecibido != null) ? txtEfectivoRecibido.getText() : "";
        if (txtRec == null || txtRec.trim().isEmpty()) {
            mostrarErrorVenta("Ingrese el dinero en efectivo recibido del cliente.");
            return;
        }
        try {
            BigDecimal recibido = new BigDecimal(txtRec.trim().replace("$", "").replace(",", "."));
            if (recibido.compareTo(total) < 0) {
                mostrarErrorVenta("El monto en efectivo recibido es inferior al total a pagar.");
                return;
            }
            cambio = recibido.subtract(total);
        } catch (Exception e) {
            mostrarErrorVenta("Ingrese un valor numérico válido para el efectivo recibido.");
            return;
        }

        // Diálogo de confirmación previa
        Alert confirm = new Alert(
            Alert.AlertType.CONFIRMATION,
            "¿Confirmar venta por valor de " + FORMATO_MONEDA.format(total) + " en efectivo?",
            ButtonType.YES, ButtonType.NO
        );
        confirm.setTitle("Confirmar Venta");
        confirm.setHeaderText("Cobro en Mostrador (Efectivo)");
        Optional<ButtonType> resp = confirm.showAndWait();

        if (resp.isPresent() && resp.get() == ButtonType.YES) {
            Venta venta = new Venta();
            venta.setIdUsuario(sesionActual.getIdUsuario());
            venta.setMetodoPago(metodo);
            venta.setDetalles(new ArrayList<>(listaCarrito));
            venta.setTotal(total);

            try {
                int idVentaGenerada = ventaDAO.registrarVenta(venta, sesionActual);

                // Éxito: Limpiar carrito, actualizar catálogo y KPIs
                onLimpiarCarrito();
                cargarCatalogoProductos();
                cargarHistorialVentas();
                actualizarKpisDiarios();

                String msgCambio = "\n• Efectivo Recibido: " + txtRec +
                                   "\n• Cambio a entregar: " + FORMATO_MONEDA.format(cambio);

                mostrarAlerta(
                    "Venta Registrada con Éxito",
                    "Venta #" + idVentaGenerada + " completada satisfactoriamente.\n\n" +
                    "• Total cobrado: " + FORMATO_MONEDA.format(total) + "\n" +
                    "• Método de pago: " + metodo + "\n" +
                    "• Responsable: " + sesionActual.getUsername() +
                    msgCambio + "\n\n" +
                    "El inventario fue actualizado en tiempo real.",
                    Alert.AlertType.INFORMATION
                );

            } catch (IllegalStateException e) {
                mostrarAlerta("Validación de Inventario", e.getMessage(), Alert.AlertType.WARNING);
            } catch (SQLException e) {
                mostrarAlerta("Error al Registrar Venta", "No fue posible guardar la venta en la base de datos: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void mostrarErrorVenta(String mensaje) {
        lblMensajeVenta.setText("⚠️ " + mensaje);
        lblMensajeVenta.setStyle("-fx-text-fill: #D93668; -fx-font-weight: bold;");
    }

    private Producto buscarProductoEnCatalogo(int idProducto) {
        for (Producto p : listaCatalogo) {
            if (p.getIdProducto() == idProducto) return p;
        }
        return null;
    }

    // =========================================================================
    // LÓGICA DEL HISTORIAL Y CONSULTAS
    // =========================================================================

    @FXML
    public void cargarHistorialVentas() {
        try {
            LocalDate fDesde = (dpHistorialDesde != null) ? dpHistorialDesde.getValue() : null;
            LocalDate fHasta = (dpHistorialHasta != null) ? dpHistorialHasta.getValue() : null;
            String metodo = (cmbHistorialMetodo != null) ? cmbHistorialMetodo.getValue() : "TODOS";

            List<Venta> ventas = ventaDAO.listarVentas(fDesde, fHasta, null, metodo);
            listaHistorial.setAll(ventas);

            if (tblHistorialVentas.getItems().isEmpty()) {
                listaDetallesVenta.clear();
                lblTituloDetalleVenta.setText("📦 Artículos de la Venta Seleccionada");
            }

        } catch (SQLException e) {
            System.err.println("[ERROR HISTORIAL VENTAS] Error: " + e.getMessage());
        }
    }

    @FXML
    public void onFiltrarHistorial() {
        cargarHistorialVentas();
    }

    @FXML
    public void onFiltrarHoy() {
        if (dpHistorialDesde != null) dpHistorialDesde.setValue(LocalDate.now());
        if (dpHistorialHasta != null) dpHistorialHasta.setValue(LocalDate.now());
        cargarHistorialVentas();
    }

    @FXML
    public void onLimpiarFiltrosHistorial() {
        if (dpHistorialDesde != null) dpHistorialDesde.setValue(null);
        if (dpHistorialHasta != null) dpHistorialHasta.setValue(null);
        if (cmbHistorialMetodo != null) cmbHistorialMetodo.setValue("TODOS");
        cargarHistorialVentas();
    }

    private void cargarDetallesVentaSeleccionada(Venta venta) {
        if (venta == null) {
            listaDetallesVenta.clear();
            lblTituloDetalleVenta.setText("📦 Artículos de la Venta Seleccionada");
            return;
        }

        try {
            List<DetalleVenta> detalles = ventaDAO.listarDetalles(venta.getIdVenta());
            listaDetallesVenta.setAll(detalles);
            lblTituloDetalleVenta.setText("📦 Artículos de la Venta #" + venta.getIdVenta() + " (" + venta.getNombreUsuario() + " – " + venta.getFechaVentaFormateada() + ")");
        } catch (SQLException e) {
            System.err.println("[ERROR VENTAS] Error al cargar detalles de la venta: " + e.getMessage());
        }
    }

    private void actualizarKpisDiarios() {
        try {
            VentaDAO.ResumenVentasDiarias resumen = ventaDAO.obtenerResumenDiario(LocalDate.now());

            if (lblHistorialTotalVentas != null) lblHistorialTotalVentas.setText(String.valueOf(resumen.totalVentas()));
            if (lblHistorialTotalRecaudado != null) lblHistorialTotalRecaudado.setText(FORMATO_MONEDA.format(resumen.totalRecaudado()));
            if (lblHistorialEfectivo != null) lblHistorialEfectivo.setText(FORMATO_MONEDA.format(resumen.totalEfectivo()));
            if (lblHistorialElectronico != null) lblHistorialElectronico.setText(FORMATO_MONEDA.format(resumen.totalElectronico()));

            if (lblKpiVentasHoy != null) lblKpiVentasHoy.setText("Ventas hoy: " + resumen.totalVentas());
            if (lblKpiRecaudadoHoy != null) lblKpiRecaudadoHoy.setText("Recaudado hoy: " + FORMATO_MONEDA.format(resumen.totalRecaudado()));

        } catch (SQLException e) {
            System.err.println("[ERROR VENTAS] No fue posible calcular el arqueo diario: " + e.getMessage());
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
