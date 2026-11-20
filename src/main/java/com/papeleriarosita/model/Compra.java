package com.papeleriarosita.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Representa una orden de compra realizada a un distribuidor o proveedor.
 * Modela el ciclo de vida de compras: REGISTRADA (pedido), PARCIAL (entregas en curso),
 * RECIBIDA (completada) y ANULADA (cancelada sin recepciones).
 */
public class Compra {

    private int idCompra;
    private int idProveedor;
    private String nombreProveedor;
    private int idUsuario;
    private String nombreUsuario;
    private LocalDateTime fechaCompra;
    private BigDecimal total;
    private String estado; // REGISTRADA, PARCIAL, RECIBIDA, ANULADA
    private List<DetalleCompra> detalles;

    private static final DateTimeFormatter FORMATO_FECHA = 
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.of("es", "CO"));
    private static final NumberFormat FORMATO_MONEDA = 
        NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));

    public Compra() {
        this.total = BigDecimal.ZERO;
        this.estado = "REGISTRADA";
        this.detalles = new ArrayList<>();
    }

    public Compra(int idProveedor, int idUsuario, BigDecimal total, String estado) {
        this.idProveedor = idProveedor;
        this.idUsuario = idUsuario;
        this.total = total != null ? total : BigDecimal.ZERO;
        this.estado = estado != null ? estado : "REGISTRADA";
        this.detalles = new ArrayList<>();
    }

    public Compra(int idCompra, int idProveedor, String nombreProveedor, int idUsuario,
                  String nombreUsuario, LocalDateTime fechaCompra, BigDecimal total, String estado) {
        this.idCompra = idCompra;
        this.idProveedor = idProveedor;
        this.nombreProveedor = nombreProveedor;
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.fechaCompra = fechaCompra;
        this.total = total != null ? total : BigDecimal.ZERO;
        this.estado = estado != null ? estado : "REGISTRADA";
        this.detalles = new ArrayList<>();
    }

    // Getters y Setters
    public int getIdCompra() {
        return idCompra;
    }

    public void setIdCompra(int idCompra) {
        this.idCompra = idCompra;
    }

    public int getIdProveedor() {
        return idProveedor;
    }

    public void setIdProveedor(int idProveedor) {
        this.idProveedor = idProveedor;
    }

    public String getNombreProveedor() {
        return nombreProveedor != null ? nombreProveedor : "Proveedor #" + idProveedor;
    }

    public void setNombreProveedor(String nombreProveedor) {
        this.nombreProveedor = nombreProveedor;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario != null ? nombreUsuario : "Usuario #" + idUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public LocalDateTime getFechaCompra() {
        return fechaCompra;
    }

    public void setFechaCompra(LocalDateTime fechaCompra) {
        this.fechaCompra = fechaCompra;
    }

    public String getFechaCompraFormateada() {
        if (fechaCompra == null) {
            return "Pendiente";
        }
        return fechaCompra.format(FORMATO_FECHA);
    }

    public BigDecimal getTotal() {
        return total != null ? total : BigDecimal.ZERO;
    }

    public void setTotal(BigDecimal total) {
        this.total = total != null ? total : BigDecimal.ZERO;
    }

    public String getTotalFormateado() {
        return FORMATO_MONEDA.format(getTotal());
    }

    public String getEstado() {
        return estado != null ? estado : "REGISTRADA";
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public List<DetalleCompra> getDetalles() {
        return detalles;
    }

    public void setDetalles(List<DetalleCompra> detalles) {
        this.detalles = detalles != null ? detalles : new ArrayList<>();
    }

    // Métodos de cálculo y reglas de negocio
    public void recalcularTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        if (detalles != null) {
            for (DetalleCompra det : detalles) {
                if (det.getSubtotal() != null) {
                    sum = sum.add(det.getSubtotal());
                }
            }
        }
        this.total = sum;
    }

    public int getTotalCantidadSolicitada() {
        if (detalles == null) return 0;
        return detalles.stream().mapToInt(DetalleCompra::getCantidad).sum();
    }

    public int getTotalCantidadRecibida() {
        if (detalles == null) return 0;
        return detalles.stream().mapToInt(DetalleCompra::getCantidadRecibida).sum();
    }

    public int getTotalCantidadPendiente() {
        if (detalles == null) return 0;
        return detalles.stream().mapToInt(DetalleCompra::getCantidadPendiente).sum();
    }

    public boolean isRegistrada() {
        return "REGISTRADA".equalsIgnoreCase(estado);
    }

    public boolean isParcial() {
        return "PARCIAL".equalsIgnoreCase(estado);
    }

    public boolean isRecibida() {
        return "RECIBIDA".equalsIgnoreCase(estado);
    }

    public boolean isAnulada() {
        return "ANULADA".equalsIgnoreCase(estado);
    }

    public boolean permiteRecepcion() {
        return isRegistrada() || isParcial();
    }

    public boolean permiteAnulacion() {
        // Solo se puede anular si está registrada y no tiene recepciones físicas
        return isRegistrada() && getTotalCantidadRecibida() == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Compra compra = (Compra) o;
        return idCompra == compra.idCompra;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idCompra);
    }

    @Override
    public String toString() {
        return "Compra #" + idCompra + " (" + getNombreProveedor() + " - " + getEstado() + ")";
    }
}
