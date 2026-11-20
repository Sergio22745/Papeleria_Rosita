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
 * Representa una venta realizada en mostrador o punto de venta de Papelería Rosita.
 * Registra fecha y hora, responsable (usuario autenticado), cliente (opcional),
 * método de pago y estado.
 */
public class Venta {

    private int idVenta;
    private Integer idCliente;
    private String nombreCliente;
    private int idUsuario;
    private String nombreUsuario;
    private LocalDateTime fechaVenta;
    private String metodoPago; // EFECTIVO, TRANSFERENCIA, TARJETA
    private String estado;     // REGISTRADA, ANULADA
    private BigDecimal total;
    private int totalArticulos;
    private List<DetalleVenta> detalles;

    private static final DateTimeFormatter FORMATO_FECHA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.of("es", "CO"));
    private static final NumberFormat FORMATO_MONEDA =
        NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));

    public Venta() {
        this.metodoPago = "EFECTIVO";
        this.estado = "REGISTRADA";
        this.total = BigDecimal.ZERO;
        this.totalArticulos = 0;
        this.detalles = new ArrayList<>();
    }

    public Venta(int idUsuario, String metodoPago) {
        this();
        this.idUsuario = idUsuario;
        this.metodoPago = metodoPago != null ? metodoPago : "EFECTIVO";
    }

    public Venta(int idVenta, Integer idCliente, String nombreCliente, int idUsuario,
                 String nombreUsuario, LocalDateTime fechaVenta, String metodoPago,
                 String estado, BigDecimal total, int totalArticulos) {
        this.idVenta = idVenta;
        this.idCliente = idCliente;
        this.nombreCliente = nombreCliente;
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.fechaVenta = fechaVenta;
        this.metodoPago = metodoPago != null ? metodoPago : "EFECTIVO";
        this.estado = estado != null ? estado : "REGISTRADA";
        this.total = total != null ? total : BigDecimal.ZERO;
        this.totalArticulos = totalArticulos;
        this.detalles = new ArrayList<>();
    }

    // Getters y Setters
    public int getIdVenta() {
        return idVenta;
    }

    public void setIdVenta(int idVenta) {
        this.idVenta = idVenta;
    }

    public Integer getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(Integer idCliente) {
        this.idCliente = idCliente;
    }

    public String getNombreCliente() {
        return (nombreCliente != null && !nombreCliente.trim().isEmpty()) ? nombreCliente : "Cliente General";
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
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

    public LocalDateTime getFechaVenta() {
        return fechaVenta;
    }

    public void setFechaVenta(LocalDateTime fechaVenta) {
        this.fechaVenta = fechaVenta;
    }

    public String getFechaVentaFormateada() {
        if (fechaVenta == null) return "-";
        return fechaVenta.format(FORMATO_FECHA);
    }

    public String getMetodoPago() {
        return metodoPago != null ? metodoPago : "EFECTIVO";
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getEstado() {
        return estado != null ? estado : "REGISTRADA";
    }

    public void setEstado(String estado) {
        this.estado = estado;
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

    public int getTotalArticulos() {
        return totalArticulos;
    }

    public void setTotalArticulos(int totalArticulos) {
        this.totalArticulos = totalArticulos;
    }

    public List<DetalleVenta> getDetalles() {
        return detalles;
    }

    public void setDetalles(List<DetalleVenta> detalles) {
        this.detalles = detalles != null ? detalles : new ArrayList<>();
        recalcularTotales();
    }

    public void recalcularTotales() {
        BigDecimal sum = BigDecimal.ZERO;
        int items = 0;
        if (detalles != null) {
            for (DetalleVenta d : detalles) {
                if (d.getSubtotal() != null) {
                    sum = sum.add(d.getSubtotal());
                }
                items += d.getCantidad();
            }
        }
        this.total = sum;
        this.totalArticulos = items;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Venta venta = (Venta) o;
        return idVenta == venta.idVenta;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idVenta);
    }

    @Override
    public String toString() {
        return "Venta #" + idVenta + " (" + getTotalFormateado() + " - " + getMetodoPago() + ")";
    }
}
