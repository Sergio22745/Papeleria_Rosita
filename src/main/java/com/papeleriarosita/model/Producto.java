package com.papeleriarosita.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Modelo que representa un Producto en Papelería Rosita.
 * Mapea directamente con la tabla 'productos' de MySQL.
 * Incluye campos calculados de visualización (nombreCategoria, stockActual, stockMinimo).
 */
public class Producto {

    private int idProducto;
    private String codigo;
    private String nombre;
    private String descripcion;
    private int idCategoria;
    private String nombreCategoria;
    private BigDecimal precioVenta;
    private boolean activo;

    // Campos complementarios de inventario (asociados vía JOIN con la tabla 'inventario')
    private int stockActual;
    private int stockMinimo;

    public Producto() {
        this.activo = true;
        this.precioVenta = BigDecimal.ZERO;
    }

    public Producto(int idProducto, String codigo, String nombre, String descripcion,
                    int idCategoria, String nombreCategoria, BigDecimal precioVenta, boolean activo) {
        this.idProducto = idProducto;
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.idCategoria = idCategoria;
        this.nombreCategoria = nombreCategoria;
        this.precioVenta = precioVenta != null ? precioVenta : BigDecimal.ZERO;
        this.activo = activo;
    }

    public Producto(int idProducto, String codigo, String nombre, String descripcion,
                    int idCategoria, String nombreCategoria, BigDecimal precioVenta,
                    boolean activo, int stockActual, int stockMinimo) {
        this(idProducto, codigo, nombre, descripcion, idCategoria, nombreCategoria, precioVenta, activo);
        this.stockActual = stockActual;
        this.stockMinimo = stockMinimo;
    }

    // Getters y Setters
    public int getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(int idProducto) {
        this.idProducto = idProducto;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public int getIdCategoria() {
        return idCategoria;
    }

    public void setIdCategoria(int idCategoria) {
        this.idCategoria = idCategoria;
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

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public String getEstadoTexto() {
        return activo ? "ACTIVO" : "INACTIVO";
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

    public boolean tieneStockBajo() {
        return stockActual > 0 && stockActual <= stockMinimo;
    }

    public boolean estaAgotado() {
        return stockActual == 0;
    }

    public String getEstadoStock() {
        if (estaAgotado()) return "AGOTADO";
        if (tieneStockBajo()) return "STOCK BAJO";
        return "NORMAL";
    }

    @Override
    public String toString() {
        return (codigo != null ? codigo + " - " : "") + (nombre != null ? nombre : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Producto producto = (Producto) o;
        return idProducto == producto.idProducto;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idProducto);
    }
}
