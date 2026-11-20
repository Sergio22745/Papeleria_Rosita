package com.papeleriarosita.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Representa un ítem individual de una orden de compra.
 * Vincula el producto solicitado, cantidad solicitada, costo unitario,
 * y lleva el registro acumulador de unidades físicas recibidas (entregas parciales).
 */
public class DetalleCompra {

    private int idDetalleCompra;
    private int idCompra;
    private int idProducto;
    private String codigoProducto;
    private String nombreProducto;
    private int cantidad; // Unidades solicitadas al proveedor
    private BigDecimal precioUnitario; // Costo unitario de adquisición
    private BigDecimal subtotal; // cantidad * precioUnitario
    private int cantidadRecibida; // Unidades físicas ya recibidas en bodega

    private static final NumberFormat FORMATO_MONEDA = 
        NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));

    public DetalleCompra() {
        this.cantidad = 0;
        this.precioUnitario = BigDecimal.ZERO;
        this.subtotal = BigDecimal.ZERO;
        this.cantidadRecibida = 0;
    }

    public DetalleCompra(int idProducto, String codigoProducto, String nombreProducto,
                         int cantidad, BigDecimal precioUnitario) {
        this.idProducto = idProducto;
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.cantidad = Math.max(0, cantidad);
        this.precioUnitario = precioUnitario != null ? precioUnitario : BigDecimal.ZERO;
        this.cantidadRecibida = 0;
        recalcularSubtotal();
    }

    public DetalleCompra(int idDetalleCompra, int idCompra, int idProducto, String codigoProducto,
                         String nombreProducto, int cantidad, BigDecimal precioUnitario,
                         BigDecimal subtotal, int cantidadRecibida) {
        this.idDetalleCompra = idDetalleCompra;
        this.idCompra = idCompra;
        this.idProducto = idProducto;
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario != null ? precioUnitario : BigDecimal.ZERO;
        this.subtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
        this.cantidadRecibida = Math.max(0, cantidadRecibida);
    }

    // Getters y Setters
    public int getIdDetalleCompra() {
        return idDetalleCompra;
    }

    public void setIdDetalleCompra(int idDetalleCompra) {
        this.idDetalleCompra = idDetalleCompra;
    }

    public int getIdCompra() {
        return idCompra;
    }

    public void setIdCompra(int idCompra) {
        this.idCompra = idCompra;
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

    public int getCantidadRecibida() {
        return cantidadRecibida;
    }

    public void setCantidadRecibida(int cantidadRecibida) {
        this.cantidadRecibida = Math.max(0, cantidadRecibida);
    }

    public int getCantidadPendiente() {
        return Math.max(0, cantidad - cantidadRecibida);
    }

    public boolean isCompletado() {
        return cantidadRecibida >= cantidad && cantidad > 0;
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
        DetalleCompra that = (DetalleCompra) o;
        return idDetalleCompra == that.idDetalleCompra && idProducto == that.idProducto;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idDetalleCompra, idProducto);
    }

    @Override
    public String toString() {
        return getNombreProducto() + " (" + cantidad + " unid. @ " + getPrecioUnitarioFormateado() + ")";
    }
}
