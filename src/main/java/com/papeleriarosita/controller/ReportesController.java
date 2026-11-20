package com.papeleriarosita.controller;

import com.papeleriarosita.dao.CajaDAO;
import com.papeleriarosita.dao.CategoriaDAO;
import com.papeleriarosita.dao.CompraDAO;
import com.papeleriarosita.dao.InventarioDAO;
import com.papeleriarosita.dao.ProveedorDAO;
import com.papeleriarosita.dao.ReporteDAO;
import com.papeleriarosita.dao.UsuarioDAO;
import com.papeleriarosita.dao.VentaDAO;
import com.papeleriarosita.model.Categoria;
import com.papeleriarosita.model.Compra;
import com.papeleriarosita.model.Inventario;
import com.papeleriarosita.model.MovimientoInventario;
import com.papeleriarosita.model.ProductoMasVendido;
import com.papeleriarosita.model.Proveedor;
import com.papeleriarosita.model.ResumenCaja;
import com.papeleriarosita.model.ResumenComprasReporte;
import com.papeleriarosita.model.ResumenInventarioReporte;
import com.papeleriarosita.model.UserSession;
import com.papeleriarosita.model.Usuario;
import com.papeleriarosita.model.Venta;
import com.papeleriarosita.util.ExportadorCSV;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controlador principal para el Módulo de Reportes de Papelería Rosita.
 * Proporciona análisis visual e interactivo de ventas, inventario, compras y caja,
 * con gráficos dinámicos y exportación en formato CSV estándar.
 */
public class ReportesController implements Initializable {

    @FXML private StackPane rootStackPane;
    @FXML private TabPane tabPaneReportes;

    // --- Filtros Generales y Ventas ---
    @FXML private DatePicker dpVentasDesde;
    @FXML private DatePicker dpVentasHasta;
    @FXML private ComboBox<Usuario> cmbVentasUsuario;
    @FXML private ComboBox<String> cmbVentasMetodo;
    @FXML private ComboBox<Categoria> cmbVentasCategoria;

    // --- KPIs de Ventas ---
    @FXML private Label lblKpiVentasTotalMonto;
    @FXML private Label lblKpiVentasCantidad;
    @FXML private Label lblKpiVentasUnidades;
    @FXML private Label lblKpiVentasTicketPromedio;

    // --- Gráficos de Ventas ---
    @FXML private BarChart<String, Number> chartVentasMensuales;
    @FXML private CategoryAxis axisMeses;
    @FXML private NumberAxis axisVentas;
    @FXML private PieChart chartMetodosPago;

    // --- Tablas de Ventas ---
    @FXML private TableView<ProductoMasVendido> tblTopProductos;
    @FXML private TableColumn<ProductoMasVendido, String> colTopCodigo;
    @FXML private TableColumn<ProductoMasVendido, String> colTopNombre;
    @FXML private TableColumn<ProductoMasVendido, String> colTopCategoria;
    @FXML private TableColumn<ProductoMasVendido, Integer> colTopCantidad;
    @FXML private TableColumn<ProductoMasVendido, String> colTopTotal;
    @FXML private TableColumn<ProductoMasVendido, String> colTopPrecioProm;

    @FXML private TableView<Venta> tblVentasReporte;
    @FXML private TableColumn<Venta, Integer> colVentaId;
    @FXML private TableColumn<Venta, String> colVentaFecha;
    @FXML private TableColumn<Venta, String> colVentaUsuario;
    @FXML private TableColumn<Venta, String> colVentaMetodo;
    @FXML private TableColumn<Venta, Integer> colVentaArticulos;
    @FXML private TableColumn<Venta, String> colVentaTotal;

    // --- Filtros e Inventario ---
    @FXML private ComboBox<Categoria> cmbInvCategoria;
    @FXML private ComboBox<String> cmbInvAlerta;

    // --- KPIs de Inventario ---
    @FXML private Label lblKpiInvValorTotal;
    @FXML private Label lblKpiInvUnidades;
    @FXML private Label lblKpiInvTotalProductos;
    @FXML private Label lblKpiInvDisponibles;
    @FXML private Label lblKpiInvBajoStock;
    @FXML private Label lblKpiInvAgotados;

    // --- Gráfico y Tablas de Inventario ---
    @FXML private PieChart chartStockCategorias;
    @FXML private TableView<Inventario> tblInventarioReporte;
    @FXML private TableColumn<Inventario, String> colInvCodigo;
    @FXML private TableColumn<Inventario, String> colInvNombre;
    @FXML private TableColumn<Inventario, String> colInvCategoria;
    @FXML private TableColumn<Inventario, Integer> colInvStockActual;
    @FXML private TableColumn<Inventario, Integer> colInvStockMin;
    @FXML private TableColumn<Inventario, String> colInvAlerta;
    @FXML private TableColumn<Inventario, String> colInvPrecioVenta;
    @FXML private TableColumn<Inventario, String> colInvValorTotal;

    @FXML private TableView<MovimientoInventario> tblKardexReporte;
    @FXML private TableColumn<MovimientoInventario, String> colKardexFecha;
    @FXML private TableColumn<MovimientoInventario, String> colKardexProducto;
    @FXML private TableColumn<MovimientoInventario, String> colKardexTipo;
    @FXML private TableColumn<MovimientoInventario, Integer> colKardexCantidad;
    @FXML private TableColumn<MovimientoInventario, String> colKardexMotivo;

    // --- Filtros y Compras ---
    @FXML private DatePicker dpComprasDesde;
    @FXML private DatePicker dpComprasHasta;
    @FXML private ComboBox<Proveedor> cmbComprasProveedor;
    @FXML private ComboBox<String> cmbComprasEstado;

    // --- KPIs de Compras ---
    @FXML private Label lblKpiComprasTotalMonto;
    @FXML private Label lblKpiComprasTotalOrdenes;
    @FXML private Label lblKpiComprasRecibidas;
    @FXML private Label lblKpiComprasParciales;
    @FXML private Label lblKpiComprasUnidadesSolicitadas;
    @FXML private Label lblKpiComprasUnidadesPendientes;

    // --- Gráfico y Tabla de Compras ---
    @FXML private PieChart chartComprasProveedores;
    @FXML private TableView<Compra> tblComprasReporte;
    @FXML private TableColumn<Compra, Integer> colCompraId;
    @FXML private TableColumn<Compra, String> colCompraFecha;
    @FXML private TableColumn<Compra, String> colCompraProveedor;
    @FXML private TableColumn<Compra, String> colCompraUsuario;
    @FXML private TableColumn<Compra, String> colCompraEstado;
    @FXML private TableColumn<Compra, String> colCompraTotal;

    // --- Filtros y Caja ---
    @FXML private DatePicker dpCajaDesde;
    @FXML private DatePicker dpCajaHasta;
    @FXML private ComboBox<Usuario> cmbCajaUsuario;

    // --- KPIs de Caja ---
    @FXML private Label lblKpiCajaTotalRecaudado;
    @FXML private Label lblKpiCajaTransacciones;
    @FXML private Label lblKpiCajaEfectivo;
    @FXML private Label lblKpiCajaTransferencia;
    @FXML private Label lblKpiCajaTarjeta;

    // --- Tabla de Caja ---
    @FXML private TableView<Venta> tblCajaReporte;
    @FXML private TableColumn<Venta, Integer> colCajaId;
    @FXML private TableColumn<Venta, String> colCajaFecha;
    @FXML private TableColumn<Venta, String> colCajaUsuario;
    @FXML private TableColumn<Venta, String> colCajaCliente;
    @FXML private TableColumn<Venta, String> colCajaMetodo;
    @FXML private TableColumn<Venta, String> colCajaTotal;

    // --- Capa de Acceso a Datos ---
    private final ReporteDAO reporteDAO = new ReporteDAO();
    private final VentaDAO ventaDAO = new VentaDAO();
    private final InventarioDAO inventarioDAO = new InventarioDAO();
    private final CompraDAO compraDAO = new CompraDAO();
    private final CajaDAO cajaDAO = new CajaDAO();
    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final ProveedorDAO proveedorDAO = new ProveedorDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    private UserSession sesionActual;

    private static final NumberFormat FORMATO_MONEDA =
        NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
    private static final DateTimeFormatter FORMATO_FECHA_HORA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String[] NOMBRES_MESES = {
        "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    };

    // Colecciones observables
    private final ObservableList<ProductoMasVendido> listaTopProductos = FXCollections.observableArrayList();
    private final ObservableList<Venta> listaVentas = FXCollections.observableArrayList();
    private final ObservableList<Inventario> listaInventario = FXCollections.observableArrayList();
    private final ObservableList<MovimientoInventario> listaKardex = FXCollections.observableArrayList();
    private final ObservableList<Compra> listaCompras = FXCollections.observableArrayList();
    private final ObservableList<Venta> listaCajaVentas = FXCollections.observableArrayList();

    private ResumenInventarioReporte ultimoResumenInventario;
    private ResumenCaja ultimoResumenCaja;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configurarColumnasTablas();
        inicializarFiltrosFechas();
        cargarCombosFiltros();
    }

    public void setSesion(UserSession sesion) {
    this.sesionActual = sesion;

    if (this.sesionActual != null) {
        cargarTodosLosReportes();
    }
}

    private void inicializarFiltrosFechas() {
        LocalDate hoy = LocalDate.now();
        LocalDate inicioMes = YearMonth.now().atDay(1);

        dpVentasDesde.setValue(inicioMes);
        dpVentasHasta.setValue(hoy);

        dpComprasDesde.setValue(inicioMes);
        dpComprasHasta.setValue(hoy);

        dpCajaDesde.setValue(hoy);
        dpCajaHasta.setValue(hoy);
    }

    private void configurarColumnasTablas() {
        // --- 1. Top Productos ---
        colTopCodigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        colTopNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colTopCategoria.setCellValueFactory(new PropertyValueFactory<>("categoria"));
        colTopCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidadVendida"));
        colTopTotal.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTotalRecaudadoFormateado()));
        colTopPrecioProm.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPrecioPromedioFormateado()));
        tblTopProductos.setItems(listaTopProductos);

        // --- 2. Ventas Reporte ---
        colVentaId.setCellValueFactory(new PropertyValueFactory<>("idVenta"));
        colVentaFecha.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getFechaVenta() != null ? cell.getValue().getFechaVenta().format(FORMATO_FECHA_HORA) : ""
        ));
        colVentaUsuario.setCellValueFactory(new PropertyValueFactory<>("nombreUsuario"));
        colVentaMetodo.setCellValueFactory(new PropertyValueFactory<>("metodoPago"));
        colVentaArticulos.setCellValueFactory(new PropertyValueFactory<>("totalArticulos"));
        colVentaTotal.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTotalFormateado()));
        tblVentasReporte.setItems(listaVentas);

        // --- 3. Inventario Reporte ---
        colInvCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoProducto"));
        colInvNombre.setCellValueFactory(new PropertyValueFactory<>("nombreProducto"));
        colInvCategoria.setCellValueFactory(new PropertyValueFactory<>("nombreCategoria"));
        colInvStockActual.setCellValueFactory(new PropertyValueFactory<>("stockActual"));
        colInvStockMin.setCellValueFactory(new PropertyValueFactory<>("stockMinimo"));
        colInvAlerta.setCellValueFactory(new PropertyValueFactory<>("estadoAlerta"));
        colInvPrecioVenta.setCellValueFactory(cell -> new SimpleStringProperty(FORMATO_MONEDA.format(cell.getValue().getPrecioVenta())));
        colInvValorTotal.setCellValueFactory(cell -> {
            BigDecimal total = cell.getValue().getPrecioVenta().multiply(BigDecimal.valueOf(cell.getValue().getStockActual()));
            return new SimpleStringProperty(FORMATO_MONEDA.format(total));
        });
        tblInventarioReporte.setItems(listaInventario);

        // Estilo de alerta en la columna de inventario
        colInvAlerta.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String alerta, boolean empty) {
                super.updateItem(alerta, empty);
                if (empty || alerta == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(alerta);
                    if ("AGOTADO".equalsIgnoreCase(alerta)) {
                        setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                    } else if ("STOCK BAJO".equalsIgnoreCase(alerta)) {
                        setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // --- 4. Kárdex Reporte ---
        colKardexFecha.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getFechaMovimiento() != null ? cell.getValue().getFechaMovimiento().format(FORMATO_FECHA_HORA) : ""
        ));
        colKardexProducto.setCellValueFactory(new PropertyValueFactory<>("nombreProducto"));
        colKardexTipo.setCellValueFactory(new PropertyValueFactory<>("tipoMovimiento"));
        colKardexCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colKardexMotivo.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        tblKardexReporte.setItems(listaKardex);

        // --- 5. Compras Reporte ---
        colCompraId.setCellValueFactory(new PropertyValueFactory<>("idCompra"));
        colCompraFecha.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getFechaCompra() != null ? cell.getValue().getFechaCompra().format(FORMATO_FECHA_HORA) : ""
        ));
        colCompraProveedor.setCellValueFactory(new PropertyValueFactory<>("nombreProveedor"));
        colCompraUsuario.setCellValueFactory(new PropertyValueFactory<>("nombreUsuario"));
        colCompraEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colCompraTotal.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTotalFormateado()));
        tblComprasReporte.setItems(listaCompras);

        // --- 6. Caja Reporte ---
        colCajaId.setCellValueFactory(new PropertyValueFactory<>("idVenta"));
        colCajaFecha.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getFechaVenta() != null ? cell.getValue().getFechaVenta().format(FORMATO_FECHA_HORA) : ""
        ));
        colCajaUsuario.setCellValueFactory(new PropertyValueFactory<>("nombreUsuario"));
        colCajaCliente.setCellValueFactory(new PropertyValueFactory<>("nombreCliente"));
        colCajaMetodo.setCellValueFactory(new PropertyValueFactory<>("metodoPago"));
        colCajaTotal.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTotalFormateado()));
        tblCajaReporte.setItems(listaCajaVentas);
    }

    private void cargarCombosFiltros() {
        try {
            // Usuarios
            List<Usuario> usuarios = usuarioDAO.listarTodos();
            Usuario opcionTodosUsuarios = new Usuario(0, "Todos los usuarios", "", "", "TODOS", null);
            ObservableList<Usuario> listaUsuarios = FXCollections.observableArrayList();
            listaUsuarios.add(opcionTodosUsuarios);
            listaUsuarios.addAll(usuarios);

            StringConverter<Usuario> conversorUsuario = new StringConverter<>() {
                @Override
                public String toString(Usuario u) {
                    return u != null ? u.getNombre() : "";
                }
                @Override
                public Usuario fromString(String string) { return null; }
            };

            cmbVentasUsuario.setConverter(conversorUsuario);
            cmbVentasUsuario.setItems(listaUsuarios);
            cmbVentasUsuario.getSelectionModel().selectFirst();

            cmbCajaUsuario.setConverter(conversorUsuario);
            cmbCajaUsuario.setItems(listaUsuarios);
            cmbCajaUsuario.getSelectionModel().selectFirst();

            // Métodos de pago
            ObservableList<String> metodos = FXCollections.observableArrayList(
                "Todos los métodos", "EFECTIVO", "TRANSFERENCIA", "TARJETA"
            );
            cmbVentasMetodo.setItems(metodos);
            cmbVentasMetodo.getSelectionModel().selectFirst();

            // Categorías
            List<Categoria> categorias = categoriaDAO.listarTodas();
            Categoria opcionTodasCat = new Categoria(0, "Todas las categorías", "Filtro general");
            ObservableList<Categoria> listaCat = FXCollections.observableArrayList();
            listaCat.add(opcionTodasCat);
            listaCat.addAll(categorias);

            StringConverter<Categoria> conversorCat = new StringConverter<>() {
                @Override
                public String toString(Categoria c) {
                    return c != null ? c.getNombre() : "";
                }
                @Override
                public Categoria fromString(String string) { return null; }
            };

            cmbVentasCategoria.setConverter(conversorCat);
            cmbVentasCategoria.setItems(listaCat);
            cmbVentasCategoria.getSelectionModel().selectFirst();

            cmbInvCategoria.setConverter(conversorCat);
            cmbInvCategoria.setItems(listaCat);
            cmbInvCategoria.getSelectionModel().selectFirst();

            // Alertas de inventario
            ObservableList<String> alertas = FXCollections.observableArrayList(
                "Todos los estados", "DISPONIBLES", "STOCK BAJO", "AGOTADOS"
            );
            cmbInvAlerta.setItems(alertas);
            cmbInvAlerta.getSelectionModel().selectFirst();

            // Proveedores
            List<Proveedor> proveedores = proveedorDAO.listarTodos();
            Proveedor opcionTodosProv = new Proveedor(0, "Todos los proveedores", "", "", "");
            ObservableList<Proveedor> listaProv = FXCollections.observableArrayList();
            listaProv.add(opcionTodosProv);
            listaProv.addAll(proveedores);

            cmbComprasProveedor.setConverter(new StringConverter<>() {
                @Override
                public String toString(Proveedor p) {
                    return p != null ? p.getNombre() : "";
                }
                @Override
                public Proveedor fromString(String string) { return null; }
            });
            cmbComprasProveedor.setItems(listaProv);
            cmbComprasProveedor.getSelectionModel().selectFirst();

            // Estados de Compra
            ObservableList<String> estadosCompra = FXCollections.observableArrayList(
                "Todos los estados", "REGISTRADA", "PARCIAL", "RECIBIDA", "ANULADA"
            );
            cmbComprasEstado.setItems(estadosCompra);
            cmbComprasEstado.getSelectionModel().selectFirst();

        } catch (SQLException e) {
            System.err.println("[REPORTES] Error de catálogos: " + e.getMessage());
            if (sesionActual == null || !sesionActual.isModoVistaPrevia()) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error de Catálogos",
                    "No se pudieron cargar los catálogos para filtros: " + e.getMessage());
            }
        }
    }

    public void cargarTodosLosReportes() {
        consultarReportesVentas();
        consultarReportesInventario();
        consultarReportesCompras();
        consultarReportesCaja();
    }

    // =========================================================================
    // ACCIONES DE REPORTES DE VENTAS
    // =========================================================================

    @FXML
    public void onConsultarVentas() {
        consultarReportesVentas();
    }

    private void consultarReportesVentas() {
        try {
            LocalDate fDesde = dpVentasDesde.getValue();
            LocalDate fHasta = dpVentasHasta.getValue();

            Usuario uSel = cmbVentasUsuario.getValue();
            Integer idUsuario = (uSel != null && uSel.getIdUsuario() > 0) ? uSel.getIdUsuario() : null;

            String metodoSel = cmbVentasMetodo.getValue();
            if ("Todos los métodos".equalsIgnoreCase(metodoSel)) {
                metodoSel = null;
            }

            Categoria catSel = cmbVentasCategoria.getValue();
            Integer idCategoria = (catSel != null && catSel.getIdCategoria() > 0) ? catSel.getIdCategoria() : null;

            // 1. Obtener lista de ventas (excluye ANULADAS)
            List<Venta> ventas = ventaDAO.listarVentas(fDesde, fHasta, idUsuario, metodoSel);
            listaVentas.setAll(ventas);

            // 2. Calcular KPIs de Ventas
            BigDecimal totalVendido = BigDecimal.ZERO;
            int totalUnidades = 0;
            int cantVentas = ventas.size();

            for (Venta v : ventas) {
                totalVendido = totalVendido.add(v.getTotal());
                totalUnidades += v.getTotalArticulos();
            }

            BigDecimal ticketPromedio = BigDecimal.ZERO;
            if (cantVentas > 0) {
                ticketPromedio = totalVendido.divide(BigDecimal.valueOf(cantVentas), 2, RoundingMode.HALF_UP);
            }

            lblKpiVentasTotalMonto.setText(FORMATO_MONEDA.format(totalVendido));
            lblKpiVentasCantidad.setText(cantVentas + " ventas registradas");
            lblKpiVentasUnidades.setText(totalUnidades + " unidades");
            lblKpiVentasTicketPromedio.setText(FORMATO_MONEDA.format(ticketPromedio));

            // 3. Top Productos Más Vendidos
            List<ProductoMasVendido> topProd = reporteDAO.obtenerProductosMasVendidos(fDesde, fHasta, idCategoria, 20);
            listaTopProductos.setAll(topProd);

            // 4. Gráfico de Ventas Mensuales (Año actual)
            actualizarGraficoVentasMensuales();

            // 5. Gráfico de Métodos de Pago
            actualizarGraficoMetodosPago(fDesde, fHasta);

        } catch (SQLException e) {
            System.err.println("[REPORTES] Error en reporte de ventas: " + e.getMessage());
            if (sesionActual == null || !sesionActual.isModoVistaPrevia()) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error en Reporte de Ventas",
                    "Ocurrió un error al consultar las ventas: " + e.getMessage());
            }
        }
    }

    private void actualizarGraficoVentasMensuales() throws SQLException {
        int anio = LocalDate.now().getYear();
        Map<Integer, BigDecimal> ventasMensuales = reporteDAO.obtenerVentasMensuales(anio);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Ventas " + anio);

        for (int mes = 1; mes <= 12; mes++) {
            String nombreMes = NOMBRES_MESES[mes - 1];
            BigDecimal total = ventasMensuales.getOrDefault(mes, BigDecimal.ZERO);
            series.getData().add(new XYChart.Data<>(nombreMes, total));
        }

        chartVentasMensuales.getData().clear();
        chartVentasMensuales.getData().add(series);
    }

    private void actualizarGraficoMetodosPago(LocalDate fDesde, LocalDate fHasta) throws SQLException {
        Map<String, BigDecimal> distribucion = reporteDAO.obtenerDistribucionMetodosPago(fDesde, fHasta);
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();

        distribucion.forEach((metodo, total) -> {
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                pieData.add(new PieChart.Data(metodo + " (" + FORMATO_MONEDA.format(total) + ")", total.doubleValue()));
            }
        });

        chartMetodosPago.setData(pieData);
    }

    // =========================================================================
    // ACCIONES DE REPORTES DE INVENTARIO
    // =========================================================================

    @FXML
    public void onConsultarInventario() {
        consultarReportesInventario();
    }

    private void consultarReportesInventario() {
        try {
            Categoria catSel = cmbInvCategoria.getValue();
            String alertaSel = cmbInvAlerta.getValue();

            // 1. Obtener KPIs de Inventario
            ultimoResumenInventario = reporteDAO.obtenerResumenInventario();
            lblKpiInvValorTotal.setText(ultimoResumenInventario.getValorTotalFormateado());
            lblKpiInvUnidades.setText(ultimoResumenInventario.getTotalStockUnidadesFormateado() + " unidades");
            lblKpiInvTotalProductos.setText(ultimoResumenInventario.getTotalProductosFormateado() + " productos en catálogo");
            lblKpiInvDisponibles.setText(String.valueOf(ultimoResumenInventario.getProductosDisponibles()));
            lblKpiInvBajoStock.setText(String.valueOf(ultimoResumenInventario.getProductosBajoStock()));
            lblKpiInvAgotados.setText(String.valueOf(ultimoResumenInventario.getProductosAgotados()));

            // 2. Obtener lista de inventario filtrada
            List<Inventario> inventarioCompleto = inventarioDAO.listarTodo();

            if (catSel != null && catSel.getIdCategoria() > 0) {
                final String nombreCat = catSel.getNombre();
                inventarioCompleto = inventarioCompleto.stream()
                    .filter(inv -> nombreCat.equalsIgnoreCase(inv.getNombreCategoria()))
                    .toList();
            }

            // Aplicar filtro de alerta en memoria si corresponde
            if (alertaSel != null && !"Todos los estados".equalsIgnoreCase(alertaSel)) {
                inventarioCompleto = inventarioCompleto.stream().filter(inv -> {
                    if ("DISPONIBLES".equalsIgnoreCase(alertaSel)) return inv.tieneStockNormal();
                    if ("STOCK BAJO".equalsIgnoreCase(alertaSel)) return inv.tieneStockBajo();
                    if ("AGOTADOS".equalsIgnoreCase(alertaSel)) return inv.estaAgotado();
                    return true;
                }).toList();
            }

            listaInventario.setAll(inventarioCompleto);

            // 3. Gráfico de distribución de stock por categorías
            Map<String, Integer> stockPorCat = reporteDAO.obtenerDistribucionStockCategorias();
            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            stockPorCat.forEach((cat, unidades) -> {
                pieData.add(new PieChart.Data(cat + " (" + unidades + " u.)", unidades));
            });
            chartStockCategorias.setData(pieData);

            // 4. Últimos movimientos Kárdex
            List<MovimientoInventario> ultimosMovimientos = inventarioDAO.listarMovimientos(null, 50);
            listaKardex.setAll(ultimosMovimientos);

        } catch (SQLException e) {
            System.err.println("[REPORTES] Error en inventario: " + e.getMessage());
            if (sesionActual == null || !sesionActual.isModoVistaPrevia()) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error en Reporte de Inventario",
                    "Ocurrió un error al consultar el inventario: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // ACCIONES DE REPORTES DE COMPRAS
    // =========================================================================

    @FXML
    public void onConsultarCompras() {
        consultarReportesCompras();
    }

    private void consultarReportesCompras() {
        try {
            LocalDate fDesde = dpComprasDesde.getValue();
            LocalDate fHasta = dpComprasHasta.getValue();

            Proveedor provSel = cmbComprasProveedor.getValue();
            Integer idProveedor = (provSel != null && provSel.getIdProveedor() > 0) ? provSel.getIdProveedor() : null;

            String estadoSel = cmbComprasEstado.getValue();
            if ("Todos los estados".equalsIgnoreCase(estadoSel)) {
                estadoSel = null;
            }

            // 1. Obtener KPIs de compras
            ResumenComprasReporte resumen = reporteDAO.obtenerResumenCompras(fDesde, fHasta);
            lblKpiComprasTotalMonto.setText(resumen.getMontoTotalFormateado());
            lblKpiComprasTotalOrdenes.setText(resumen.getTotalComprasFormateado() + " órdenes de compra");
            lblKpiComprasRecibidas.setText(resumen.getComprasRecibidas() + " recibidas");
            lblKpiComprasParciales.setText(resumen.getComprasParciales() + " entregas parciales");
            lblKpiComprasUnidadesSolicitadas.setText(resumen.getTotalUnidadesSolicitadasFormateado() + " unidades");
            lblKpiComprasUnidadesPendientes.setText(resumen.getTotalUnidadesPendientesFormateado() + " pendientes");

            // 2. Obtener lista de compras
            List<Compra> compras = compraDAO.listarCompras(idProveedor, estadoSel, fDesde, fHasta);
            listaCompras.setAll(compras);

            // 3. Gráfico de compras por proveedor
            Map<String, BigDecimal> comprasProv = reporteDAO.obtenerDistribucionComprasProveedor(fDesde, fHasta);
            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            comprasProv.forEach((prov, total) -> {
                if (total.compareTo(BigDecimal.ZERO) > 0) {
                    pieData.add(new PieChart.Data(prov + " (" + FORMATO_MONEDA.format(total) + ")", total.doubleValue()));
                }
            });
            chartComprasProveedores.setData(pieData);

        } catch (Exception e) {
            System.err.println("[REPORTES] Error en compras: " + e.getMessage());
            if (lblKpiComprasTotalMonto != null) lblKpiComprasTotalMonto.setText("$ 0");
            if (lblKpiComprasTotalOrdenes != null) lblKpiComprasTotalOrdenes.setText("0 órdenes de compra");
            if (lblKpiComprasRecibidas != null) lblKpiComprasRecibidas.setText("0 recibidas");
            if (lblKpiComprasParciales != null) lblKpiComprasParciales.setText("0 entregas parciales");
            if (lblKpiComprasUnidadesSolicitadas != null) lblKpiComprasUnidadesSolicitadas.setText("0 unidades");
            if (lblKpiComprasUnidadesPendientes != null) lblKpiComprasUnidadesPendientes.setText("0 pendientes");
            listaCompras.clear();
            if (chartComprasProveedores != null) chartComprasProveedores.setData(FXCollections.observableArrayList());
        }
    }

    // =========================================================================
    // ACCIONES DE REPORTES DE CAJA
    // =========================================================================

    @FXML
    public void onConsultarCaja() {
        consultarReportesCaja();
    }

    private void consultarReportesCaja() {
        try {
            LocalDate fDesde = dpCajaDesde.getValue();
            LocalDate fHasta = dpCajaHasta.getValue();

            Usuario uSel = cmbCajaUsuario.getValue();
            Integer idUsuario = (uSel != null && uSel.getIdUsuario() > 0) ? uSel.getIdUsuario() : null;

            // 1. Obtener KPIs de Caja
            ultimoResumenCaja = cajaDAO.obtenerResumenCaja(fDesde, fHasta, idUsuario, sesionActual);
            lblKpiCajaTotalRecaudado.setText(ultimoResumenCaja.getTotalRecaudadoFormateado());
            lblKpiCajaTransacciones.setText(ultimoResumenCaja.getTotalVentas() + " transacciones válidas");
            lblKpiCajaEfectivo.setText(ultimoResumenCaja.getTotalEfectivoFormateado());
            lblKpiCajaTransferencia.setText(ultimoResumenCaja.getTotalTransferenciaFormateado());
            lblKpiCajaTarjeta.setText(ultimoResumenCaja.getTotalTarjetaFormateado());

            // 2. Obtener ventas individuales de caja
            List<Venta> ventasCaja = cajaDAO.listarVentasCaja(fDesde, fHasta, idUsuario, null, sesionActual);
            listaCajaVentas.setAll(ventasCaja);

        } catch (SQLException e) {
            System.err.println("[REPORTES] Error en caja: " + e.getMessage());
            if (sesionActual == null || !sesionActual.isModoVistaPrevia()) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error en Reporte de Caja",
                    "Ocurrió un error al consultar el consolidado de caja: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // EXPORTACIÓN A CSV (UTF-8 con BOM y separador ;)
    // =========================================================================

    @FXML
    public void onExportarVentasCSV() {
        if (listaVentas.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin Datos", "No hay registros de ventas para exportar.");
            return;
        }

        File destino = seleccionarArchivoGuardar("reporte_ventas_" + LocalDate.now() + ".csv");
        if (destino != null) {
            try {
                ExportadorCSV.exportarVentas(destino, listaVentas);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Exportación Exitosa",
                    "El reporte de ventas ha sido exportado exitosamente:\n" + destino.getAbsolutePath());
            } catch (IOException e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error al Exportar",
                    "No se pudo escribir el archivo CSV: " + e.getMessage());
            }
        }
    }

    @FXML
    public void onExportarTopProductosCSV() {
        if (listaTopProductos.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin Datos", "No hay ranking de productos para exportar.");
            return;
        }

        File destino = seleccionarArchivoGuardar("ranking_top_productos_" + LocalDate.now() + ".csv");
        if (destino != null) {
            try {
                ExportadorCSV.exportarProductosMasVendidos(destino, listaTopProductos);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Exportación Exitosa",
                    "El ranking de productos más vendidos fue exportado exitosamente:\n" + destino.getAbsolutePath());
            } catch (IOException e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error al Exportar",
                    "No se pudo escribir el archivo CSV: " + e.getMessage());
            }
        }
    }

    @FXML
    public void onExportarInventarioCSV() {
        if (listaInventario.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin Datos", "No hay datos de inventario para exportar.");
            return;
        }

        File destino = seleccionarArchivoGuardar("reporte_inventario_" + LocalDate.now() + ".csv");
        if (destino != null) {
            try {
                ExportadorCSV.exportarInventario(destino, listaInventario, ultimoResumenInventario);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Exportación Exitosa",
                    "El reporte de inventario ha sido exportado exitosamente:\n" + destino.getAbsolutePath());
            } catch (IOException e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error al Exportar",
                    "No se pudo escribir el archivo CSV: " + e.getMessage());
            }
        }
    }

    @FXML
    public void onExportarKardexCSV() {
        if (listaKardex.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin Datos", "No hay movimientos de kárdex para exportar.");
            return;
        }

        File destino = seleccionarArchivoGuardar("reporte_kardex_" + LocalDate.now() + ".csv");
        if (destino != null) {
            try {
                ExportadorCSV.exportarMovimientosKardex(destino, listaKardex);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Exportación Exitosa",
                    "El historial del kárdex ha sido exportado exitosamente:\n" + destino.getAbsolutePath());
            } catch (IOException e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error al Exportar",
                    "No se pudo escribir el archivo CSV: " + e.getMessage());
            }
        }
    }

    @FXML
    public void onExportarComprasCSV() {
        if (listaCompras.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin Datos", "No hay registros de compras para exportar.");
            return;
        }

        File destino = seleccionarArchivoGuardar("reporte_compras_" + LocalDate.now() + ".csv");
        if (destino != null) {
            try {
                ExportadorCSV.exportarCompras(destino, listaCompras);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Exportación Exitosa",
                    "El reporte de órdenes de compra ha sido exportado exitosamente:\n" + destino.getAbsolutePath());
            } catch (IOException e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error al Exportar",
                    "No se pudo escribir el archivo CSV: " + e.getMessage());
            }
        }
    }

    @FXML
    public void onExportarCajaCSV() {
        if (listaCajaVentas.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin Datos", "No hay movimientos de caja para exportar.");
            return;
        }

        File destino = seleccionarArchivoGuardar("arqueo_caja_" + LocalDate.now() + ".csv");
        if (destino != null) {
            try {
                ExportadorCSV.exportarCaja(destino, ultimoResumenCaja, listaCajaVentas);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Exportación Exitosa",
                    "El arqueo consolidado de caja ha sido exportado exitosamente:\n" + destino.getAbsolutePath());
            } catch (IOException e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error al Exportar",
                    "No se pudo escribir el archivo CSV: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // ATAJOS RÁPIDOS DE FECHAS
    // =========================================================================

    @FXML
    public void onFiltroHoy() {
        LocalDate hoy = LocalDate.now();
        dpVentasDesde.setValue(hoy);
        dpVentasHasta.setValue(hoy);
        dpComprasDesde.setValue(hoy);
        dpComprasHasta.setValue(hoy);
        dpCajaDesde.setValue(hoy);
        dpCajaHasta.setValue(hoy);
        cargarTodosLosReportes();
    }

    @FXML
    public void onFiltroEsteMes() {
        LocalDate hoy = LocalDate.now();
        LocalDate inicioMes = YearMonth.now().atDay(1);
        dpVentasDesde.setValue(inicioMes);
        dpVentasHasta.setValue(hoy);
        dpComprasDesde.setValue(inicioMes);
        dpComprasHasta.setValue(hoy);
        dpCajaDesde.setValue(inicioMes);
        dpCajaHasta.setValue(hoy);
        cargarTodosLosReportes();
    }

    @FXML
    public void onFiltroAnioActual() {
        LocalDate hoy = LocalDate.now();
        LocalDate inicioAnio = LocalDate.of(hoy.getYear(), 1, 1);
        dpVentasDesde.setValue(inicioAnio);
        dpVentasHasta.setValue(hoy);
        dpComprasDesde.setValue(inicioAnio);
        dpComprasHasta.setValue(hoy);
        dpCajaDesde.setValue(inicioAnio);
        dpCajaHasta.setValue(hoy);
        cargarTodosLosReportes();
    }

    @FXML
    public void onRefrescarTodo() {
        cargarTodosLosReportes();
    }

    private File seleccionarArchivoGuardar(String nombreSugerido) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar Reporte CSV");
        fileChooser.setInitialFileName(nombreSugerido);
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Archivo CSV (*.csv)", "*.csv")
        );
        return fileChooser.showSaveDialog(rootStackPane.getScene().getWindow());
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo, mensaje, ButtonType.OK);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
