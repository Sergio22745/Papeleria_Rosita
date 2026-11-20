package com.papeleriarosita.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Modelo que representa el Inventario de un producto en Papelería Rosita.
 * Mapea la tabla 'inventario' de MySQL junto con datos descriptivos del producto.
 */
public class Inventario {

    private int idInventario;
    private int idProducto;
    private String codigoProducto;
    private String nombreProducto;
    private String nombreCategoria;
    private BigDecimal precioVenta;
    private boolean activoProducto;
    private int stockActual;
    private int stockMinimo;
    private LocalDateTime fechaActualizacion;

    public Inventario() {}

    public Inventario(int idInventario, int idProducto, String codigoProducto, String nombreProducto,
                      String nombreCategoria, BigDecimal precioVenta, boolean activoProducto,
                      int stockActual, int stockMinimo, LocalDateTime fechaActualizacion) {
        this.idInventario = idInventario;
        this.idProducto = idProducto;
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.nombreCategoria = nombreCategoria;
        this.precioVenta = precioVenta != null ? precioVenta : BigDecimal.ZERO;
        this.activoProducto = activoProducto;
        this.stockActual = stockActual;
        this.stockMinimo = stockMinimo;
        this.fechaActualizacion = fechaActualizacion;
    }

    public int getIdInventario() {
        return idInventario;
    }

    public void setIdInventario(int idInventario) {
        this.idInventario = idInventario;
    }

    public int getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(int idProducto) {
        this.idProducto = idProducto;
    }

    public String getCodigoProducto() {
        return codigoProducto;
    }

    public void setCodigoProducto(String codigoProducto) {
        this.codigoProducto = codigoProducto;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public void setNombreProducto(String nombreProducto) {
        this.nombreProducto = nombreProducto;
    }

    public String getNombreCategoria() {
        return nombreCategoria != null ? nombreCategoria : "Sin Categoría";
    }

    public void setNombreCategoria(String nombreCategoria) {
        this.nombreCategoria = nombreCategoria;
    }

    public BigDecimal getPrecioVenta() {
        return precioVenta != null ? precioVenta : BigDecimal.ZERO;
    }

    public void setPrecioVenta(BigDecimal precioVenta) {
        this.precioVenta = precioVenta != null ? precioVenta : BigDecimal.ZERO;
    }

    public boolean isActivoProducto() {
        return activoProducto;
    }

    public void setActivoProducto(boolean activoProducto) {
        this.activoProducto = activoProducto;
    }

    public int getStockActual() {
        return stockActual;
    }

    public void setStockActual(int stockActual) {
        this.stockActual = stockActual;
    }

    public int getStockMinimo() {
        return stockMinimo;
    }

    public void setStockMinimo(int stockMinimo) {
        this.stockMinimo = stockMinimo;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public boolean estaAgotado() {
        return stockActual == 0;
    }

    public boolean tieneStockBajo() {
        return stockActual > 0 && stockActual <= stockMinimo;
    }

    public boolean tieneStockNormal() {
        return stockActual > stockMinimo;
    }

    public String getEstadoAlerta() {
        if (estaAgotado()) return "AGOTADO";
        if (tieneStockBajo()) return "STOCK BAJO";
        return "NORMAL";
    }

    @Override
    public String toString() {
        if (codigoProducto != null && !codigoProducto.isBlank()) {
            return codigoProducto + " - " + (nombreProducto != null ? nombreProducto : "");
        }
        return nombreProducto != null ? nombreProducto : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Inventario that = (Inventario) o;
        return idInventario == that.idInventario;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idInventario);
    }
}
