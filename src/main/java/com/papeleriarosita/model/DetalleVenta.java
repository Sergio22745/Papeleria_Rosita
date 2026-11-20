package com.papeleriarosita.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Representa un artículo vendido dentro de una venta.
 * Almacena el producto, cantidad vendida, precio unitario de venta y subtotal.
 */
public class DetalleVenta {

    private int idDetalleVenta;
    private int idVenta;
    private int idProducto;
    private String codigoProducto;
    private String nombreProducto;
    private int cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;

    // Campo transitorio para visualización y validación en interfaz
    private int stockDisponible;

    private static final NumberFormat FORMATO_MONEDA =
        NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));

    public DetalleVenta() {
        this.cantidad = 1;
        this.precioUnitario = BigDecimal.ZERO;
        this.subtotal = BigDecimal.ZERO;
    }

    public DetalleVenta(int idProducto, String codigoProducto, String nombreProducto,
                        int cantidad, BigDecimal precioUnitario, int stockDisponible) {
        this.idProducto = idProducto;
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario != null ? precioUnitario : BigDecimal.ZERO;
        this.stockDisponible = stockDisponible;
        recalcularSubtotal();
    }

    public DetalleVenta(int idDetalleVenta, int idVenta, int idProducto, String codigoProducto,
                        String nombreProducto, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
        this.idDetalleVenta = idDetalleVenta;
        this.idVenta = idVenta;
        this.idProducto = idProducto;
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario != null ? precioUnitario : BigDecimal.ZERO;
        this.subtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
    }

    // Getters y Setters
    public int getIdDetalleVenta() {
        return idDetalleVenta;
    }

    public void setIdDetalleVenta(int idDetalleVenta) {
        this.idDetalleVenta = idDetalleVenta;
    }

    public int getIdVenta() {
        return idVenta;
    }

    public void setIdVenta(int idVenta) {
        this.idVenta = idVenta;
    }

    public int getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(int idProducto) {
        this.idProducto = idProducto;
    }

    public String getCodigoProducto() {
        return codigoProducto != null ? codigoProducto : "";
    }

    public void setCodigoProducto(String codigoProducto) {
        this.codigoProducto = codigoProducto;
    }

    public String getNombreProducto() {
        return nombreProducto != null ? nombreProducto : "Producto #" + idProducto;
    }

    public void setNombreProducto(String nombreProducto) {
        this.nombreProducto = nombreProducto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = Math.max(0, cantidad);
        recalcularSubtotal();
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario != null ? precioUnitario : BigDecimal.ZERO;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario != null ? precioUnitario : BigDecimal.ZERO;
        recalcularSubtotal();
    }

    public String getPrecioUnitarioFormateado() {
        return FORMATO_MONEDA.format(getPrecioUnitario());
    }

    public BigDecimal getSubtotal() {
        return subtotal != null ? subtotal : BigDecimal.ZERO;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public String getSubtotalFormateado() {
        return FORMATO_MONEDA.format(getSubtotal());
    }

    public int getStockDisponible() {
        return stockDisponible;
    }

    public void setStockDisponible(int stockDisponible) {
        this.stockDisponible = stockDisponible;
    }

    public void recalcularSubtotal() {
        if (precioUnitario != null && cantidad > 0) {
            this.subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        } else {
            this.subtotal = BigDecimal.ZERO;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DetalleVenta that = (DetalleVenta) o;
        return idDetalleVenta == that.idDetalleVenta && idProducto == that.idProducto;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idDetalleVenta, idProducto);
    }

    @Override
    public String toString() {
        return getNombreProducto() + " (" + cantidad + " unid. @ " + getPrecioUnitarioFormateado() + ")";
    }
}
