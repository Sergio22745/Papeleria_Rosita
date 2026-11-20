package com.papeleriarosita.controller;

import com.papeleriarosita.dao.CajaDAO;
import com.papeleriarosita.dao.UsuarioDAO;
import com.papeleriarosita.model.DetalleVenta;
import com.papeleriarosita.model.ResumenCaja;
import com.papeleriarosita.model.UserSession;
import com.papeleriarosita.model.Usuario;
import com.papeleriarosita.model.Venta;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Controlador de la interfaz gráfica del Módulo de Control de Caja.
 * Realiza el arqueo y conciliación de dinero, consulta de ingresos por método de pago
 * (Efectivo, Transferencia, Tarjeta) y auditoría de transacciones para Rosita y Gladys.
 */
public class CajaController {

    @FXML private StackPane rootStackPane;

    // Badges de cabecera
    @FXML private Label lblHeaderTotalBadge;
    @FXML private Label lblHeaderEfectivoBadge;
    @FXML private Label lblHeaderUsuarioBadge;

    // Filtros de búsqueda
    @FXML private DatePicker dpFechaDesde;
    @FXML private DatePicker dpFechaHasta;
    @FXML private ComboBox<Usuario> cmbFiltroUsuario;
    @FXML private ComboBox<String> cmbFiltroMetodo;

    // Tarjetas KPI de ingresos
    @FXML private Label lblKpiTotalRecaudado;
    @FXML private Label lblKpiTransacciones;
    @FXML private Label lblKpiEfectivo;
    @FXML private Label lblKpiTransferencias;
    @FXML private Label lblKpiTarjetas;

    // Arqueo y conciliación de efectivo
    @FXML private Label lblBadgeEstadoArqueo;
    @FXML private TextField txtBaseApertura;
    @FXML private Label lblArqueoVentasEfectivo;
    @FXML private Label lblEfectivoEsperado;
    @FXML private TextField txtDineroContado;
    @FXML private Label lblDiferenciaCalculada;
    @FXML private Button btnGenerarActaArqueo;

    // Tabla de Ventas
    @FXML private TableView<Venta> tblVentasCaja;
    @FXML private TableColumn<Venta, Integer> colIdVenta;
    @FXML private TableColumn<Venta, String> colFecha;
    @FXML private TableColumn<Venta, String> colUsuario;
    @FXML private TableColumn<Venta, String> colCliente;
    @FXML private TableColumn<Venta, String> colMetodoPago;
    @FXML private TableColumn<Venta, Integer> colArticulos;
    @FXML private TableColumn<Venta, String> colTotal;
    @FXML private TableColumn<Venta, String> colEstado;

    // Tabla de Detalle de Venta Seleccionada
    @FXML private Label lblTituloDetalleVenta;
    @FXML private TableView<DetalleVenta> tblDetallesVentaCaja;
    @FXML private TableColumn<DetalleVenta, String> colDetCodigo;
    @FXML private TableColumn<DetalleVenta, String> colDetProducto;
    @FXML private TableColumn<DetalleVenta, Integer> colDetCantidad;
    @FXML private TableColumn<DetalleVenta, String> colDetPrecioUnitario;
    @FXML private TableColumn<DetalleVenta, String> colDetSubtotal;

    // DAOs y colecciones de datos
    private final CajaDAO cajaDAO = new CajaDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    private final ObservableList<Venta> listaVentasCaja = FXCollections.observableArrayList();
    private final ObservableList<DetalleVenta> listaDetallesCaja = FXCollections.observableArrayList();

    private UserSession sesionActual;
    private ResumenCaja resumenActual = new ResumenCaja();

    private static final NumberFormat FORMATO_MONEDA = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));

    @FXML
    public void initialize() {
        configurarColumnasTablaVentas();
        configurarColumnasTablaDetalles();
        configurarFiltros();

        // Listeners para recálculo interactivo de arqueo
        txtBaseApertura.textProperty().addListener((obs, oldV, newV) -> recalcularArqueo());
        txtDineroContado.textProperty().addListener((obs, oldV, newV) -> recalcularArqueo());

        // Listener de selección de venta para ver productos
        tblVentasCaja.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            cargarDetallesVenta(newSel);
        });

        // Iniciar en el día actual
        dpFechaDesde.setValue(LocalDate.now());
        dpFechaHasta.setValue(LocalDate.now());
    }

    public void setSesion(UserSession sesion) {
        this.sesionActual = sesion;
        if (sesion != null && lblHeaderUsuarioBadge != null) {
            lblHeaderUsuarioBadge.setText("Sesión: " + sesion.getUsername() + " (" + sesion.getRole() + ")");
        }
        cargarUsuariosFiltro();
        onConsultarCaja();
    }

    private void configurarColumnasTablaVentas() {
        tblVentasCaja.setItems(listaVentasCaja);

        colIdVenta.setCellValueFactory(new PropertyValueFactory<>("idVenta"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fechaVentaFormateada"));
        colUsuario.setCellValueFactory(new PropertyValueFactory<>("nombreUsuario"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("nombreCliente"));
        colArticulos.setCellValueFactory(new PropertyValueFactory<>("totalArticulos"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalFormateado"));

        colMetodoPago.setCellValueFactory(new PropertyValueFactory<>("metodoPago"));
        colMetodoPago.setCellFactory(col -> new TableCell<>() {
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

        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colEstado.setCellFactory(col -> new TableCell<>() {
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

    private void configurarColumnasTablaDetalles() {
        tblDetallesVentaCaja.setItems(listaDetallesCaja);

        colDetCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoProducto"));
        colDetProducto.setCellValueFactory(new PropertyValueFactory<>("nombreProducto"));
        colDetCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colDetPrecioUnitario.setCellValueFactory(new PropertyValueFactory<>("precioUnitarioFormateado"));
        colDetSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotalFormateado"));
    }

    private void configurarFiltros() {
        cmbFiltroMetodo.getItems().setAll("TODOS", "EFECTIVO", "TRANSFERENCIA", "TARJETA");
        cmbFiltroMetodo.setValue("TODOS");
    }

    private void cargarUsuariosFiltro() {
        try {
            List<Usuario> usuarios = usuarioDAO.listarTodos();
            cmbFiltroUsuario.getItems().clear();
            Usuario todos = new Usuario(0, "Todos los usuarios", "", "", "TODOS", null);
            cmbFiltroUsuario.getItems().add(todos);
            cmbFiltroUsuario.getItems().addAll(usuarios);
            cmbFiltroUsuario.setValue(todos);

            cmbFiltroUsuario.setConverter(new StringConverter<>() {
                @Override
                public String toString(Usuario u) {
                    return u != null ? u.getNombre() : "";
                }

                @Override
                public Usuario fromString(String string) {
                    return null;
                }
            });

        } catch (SQLException e) {
            System.err.println("[CAJA] Error al cargar lista de usuarios para filtro: " + e.getMessage());
        }
    }

    // =========================================================================
    // CONSULTA Y ARQUEO DE CAJA
    // =========================================================================

    @FXML
    public void onConsultarCaja() {
        if (sesionActual == null) return;

        LocalDate fDesde = dpFechaDesde.getValue();
        LocalDate fHasta = dpFechaHasta.getValue();

        Usuario usuarioSel = cmbFiltroUsuario.getValue();
        Integer idUsuario = (usuarioSel != null && usuarioSel.getIdUsuario() > 0) ? usuarioSel.getIdUsuario() : null;
        String metodo = cmbFiltroMetodo.getValue();

        try {
            // 1. Obtener consolidado matemático de ingresos de ventas
            resumenActual = cajaDAO.obtenerResumenCaja(fDesde, fHasta, idUsuario, sesionActual);

            // Actualizar tarjetas métricas
            lblKpiTotalRecaudado.setText(resumenActual.getTotalRecaudadoFormateado());
            lblKpiTransacciones.setText(resumenActual.getTotalVentas() + " venta" + (resumenActual.getTotalVentas() == 1 ? "" : "s") + " registradas (" + resumenActual.getTotalArticulos() + " artículos)");
            lblKpiEfectivo.setText(resumenActual.getTotalEfectivoFormateado());
            lblKpiTransferencias.setText(resumenActual.getTotalTransferenciaFormateado());
            lblKpiTarjetas.setText(resumenActual.getTotalTarjetaFormateado());

            lblHeaderTotalBadge.setText("Total: " + resumenActual.getTotalRecaudadoFormateado());
            lblHeaderEfectivoBadge.setText("Efectivo: " + resumenActual.getTotalEfectivoFormateado());
            lblArqueoVentasEfectivo.setText(resumenActual.getTotalEfectivoFormateado());

            // 2. Obtener lista detallada de transacciones
            List<Venta> ventas = cajaDAO.listarVentasCaja(fDesde, fHasta, idUsuario, metodo, sesionActual);
            listaVentasCaja.setAll(ventas);

            if (tblVentasCaja.getItems().isEmpty()) {
                listaDetallesCaja.clear();
                lblTituloDetalleVenta.setText("📦 Artículos de la Venta Seleccionada");
            }

            // 3. Recalcular arqueo
            recalcularArqueo();

        } catch (SQLException e) {
            System.err.println("[ERROR CAJA] Error al consultar datos de caja: " + e.getMessage());
            if (sesionActual == null || !sesionActual.isModoVistaPrevia()) {
                mostrarAlerta("Error de Conexión", "No fue posible conectar con la base de datos para consultar caja: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    public void onConsultarHoy() {
        dpFechaDesde.setValue(LocalDate.now());
        dpFechaHasta.setValue(LocalDate.now());
        onConsultarCaja();
    }

    @FXML
    public void onLimpiarFiltros() {
        dpFechaDesde.setValue(LocalDate.now());
        dpFechaHasta.setValue(LocalDate.now());
        if (!cmbFiltroUsuario.getItems().isEmpty()) {
            cmbFiltroUsuario.getSelectionModel().selectFirst();
        }
        cmbFiltroMetodo.setValue("TODOS");
        onConsultarCaja();
    }

    private void cargarDetallesVenta(Venta venta) {
        if (venta == null) {
            listaDetallesCaja.clear();
            lblTituloDetalleVenta.setText("📦 Artículos de la Venta Seleccionada");
            return;
        }

        try {
            List<DetalleVenta> detalles = cajaDAO.listarDetallesVenta(venta.getIdVenta());
            listaDetallesCaja.setAll(detalles);
            lblTituloDetalleVenta.setText("📦 Artículos de la Venta #" + venta.getIdVenta() + " (" + venta.getNombreUsuario() + " – " + venta.getMetodoPago() + " – " + venta.getTotalFormateado() + ")");
        } catch (SQLException e) {
            System.err.println("[ERROR CAJA] Error al cargar detalles de venta: " + e.getMessage());
        }
    }

    private void recalcularArqueo() {
        BigDecimal base = parsearMonto(txtBaseApertura.getText());
        BigDecimal contado = parsearMonto(txtDineroContado.getText());

        resumenActual.setBaseApertura(base);
        resumenActual.setDineroContado(contado);

        lblEfectivoEsperado.setText(resumenActual.getEfectivoEsperadoFormateado());

        BigDecimal dif = resumenActual.getDiferenciaArqueo();
        lblDiferenciaCalculada.setText(resumenActual.getDiferenciaArqueoFormateada());

        lblBadgeEstadoArqueo.getStyleClass().clear();
        if (dif.compareTo(BigDecimal.ZERO) == 0) {
            lblBadgeEstadoArqueo.setText("✓ Cuadre Exacto");
            lblBadgeEstadoArqueo.getStyleClass().add("badge-arqueo-ok");
            lblDiferenciaCalculada.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #087E70; -fx-padding: 6px 0;");
        } else if (dif.compareTo(BigDecimal.ZERO) < 0) {
            lblBadgeEstadoArqueo.setText("⚠️ Faltante: " + FORMATO_MONEDA.format(dif.abs()));
            lblBadgeEstadoArqueo.getStyleClass().add("badge-arqueo-descuadre");
            lblDiferenciaCalculada.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #D93668; -fx-padding: 6px 0;");
        } else {
            lblBadgeEstadoArqueo.setText("ℹ️ Sobrante: " + FORMATO_MONEDA.format(dif));
            lblBadgeEstadoArqueo.getStyleClass().add("badge-arqueo-descuadre");
            lblDiferenciaCalculada.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #385DB1; -fx-padding: 6px 0;");
        }
    }

    private BigDecimal parsearMonto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(texto.trim().replace("$", "").replace(".", "").replace(",", "."));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    @FXML
    public void onRegistrarArqueoTurno() {
        if (sesionActual != null && sesionActual.isModoVistaPrevia()) {
            mostrarAlerta("Modo Vista Previa", "Operación no permitida: El sistema está en Modo Vista Previa (Solo Lectura). No se registran actas de arqueo ni se modifica información.", Alert.AlertType.WARNING);
            return;
        }

        if (sesionActual == null) return;

        recalcularArqueo();

        String estadoArqueo = resumenActual.isCuadrado() ? "CUADRADO (Exacto)" :
            (resumenActual.getDiferenciaArqueo().compareTo(BigDecimal.ZERO) < 0 ?
                "DESCUADRE (Faltante de " + FORMATO_MONEDA.format(resumenActual.getDiferenciaArqueo().abs()) + ")" :
                "DESCUADRE (Sobrante de " + FORMATO_MONEDA.format(resumenActual.getDiferenciaArqueo()) + ")");

        StringBuilder sb = new StringBuilder();
        sb.append("📋 RESUMEN DEL ARQUEO DE CAJA – PAPELERÍA ROSITA\n");
        sb.append("─────────────────────────────────────────────\n");
        sb.append("• Responsable de Caja: ").append(sesionActual.getUsername()).append(" (").append(sesionActual.getRole()).append(")\n");
        sb.append("• Período Consultado: ").append(dpFechaDesde.getValue()).append(" al ").append(dpFechaHasta.getValue()).append("\n\n");

        sb.append("DESGLOSE DE INGRESOS EN VENTAS (MySQL):\n");
        sb.append("  - Ventas en Efectivo:       ").append(resumenActual.getTotalEfectivoFormateado()).append("\n");
        sb.append("  - Transferencias Bancarias: ").append(resumenActual.getTotalTransferenciaFormateado()).append("\n");
        sb.append("  - Tarjetas / Datáfono:      ").append(resumenActual.getTotalTarjetaFormateado()).append("\n");
        sb.append("  TOTAL RECAUDADO EN VENTAS:  ").append(resumenActual.getTotalRecaudadoFormateado()).append("\n");
        sb.append("  Transacciones Activas:      ").append(resumenActual.getTotalVentas()).append(" ventas\n\n");

        sb.append("CONCILIACIÓN FÍSICA DE EFECTIVO (CAJÓN):\n");
        sb.append("  - Base de Apertura (Fondo): ").append(resumenActual.getBaseAperturaFormateada()).append("\n");
        sb.append("  + Ventas en Efectivo:       ").append(resumenActual.getTotalEfectivoFormateado()).append("\n");
        sb.append("  = Efectivo Esperado:        ").append(resumenActual.getEfectivoEsperadoFormateado()).append("\n");
        sb.append("  - Dinero Físico Contado:    ").append(resumenActual.getDineroContadoFormateado()).append("\n");
        sb.append("  = Diferencia de Arqueo:     ").append(resumenActual.getDiferenciaArqueoFormateada()).append("\n");
        sb.append("  ESTADO DE ARQUEO:           ").append(estadoArqueo).append("\n");
        sb.append("─────────────────────────────────────────────\n");
        sb.append("ℹ️ NOTA TÉCNICA DE PERSISTENCIA:\n");
        sb.append("El cálculo y conciliación de caja se realiza contra los registros reales de 'ventas' y 'detalle_venta'.\n");
        sb.append("La base de datos MySQL actual no cuenta con tablas para persistir sesiones de apertura/cierre ('caja').\n");
        sb.append("Para guardar formalmente los históricos de arqueo, se requerirá una futura migración de esquema.");

        mostrarAlerta("Acta de Arqueo de Caja", sb.toString(), Alert.AlertType.INFORMATION);
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
