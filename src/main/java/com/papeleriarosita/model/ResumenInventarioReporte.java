package com.papeleriarosita.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Modelo que encapsula los indicadores clave (KPIs) y métricas cuantitativas
 * del inventario para el módulo de reportes de Papelería Rosita.
 */
public class ResumenInventarioReporte {

    private int totalProductos;
    private int totalStockUnidades;
    private int productosDisponibles;
    private int productosBajoStock;
    private int productosAgotados;
    private BigDecimal valorTotalInventario;

    private static final NumberFormat FORMATO_MONEDA =
        NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
    private static final NumberFormat FORMATO_ENTERO =
        NumberFormat.getIntegerInstance(Locale.of("es", "CO"));

    public ResumenInventarioReporte() {
        this.valorTotalInventario = BigDecimal.ZERO;
    }

    public ResumenInventarioReporte(int totalProductos, int totalStockUnidades,
                                   int productosDisponibles, int productosBajoStock,
                                   int productosAgotados, BigDecimal valorTotalInventario) {
        this.totalProductos = totalProductos;
        this.totalStockUnidades = totalStockUnidades;
        this.productosDisponibles = productosDisponibles;
        this.productosBajoStock = productosBajoStock;
        this.productosAgotados = productosAgotados;
        this.valorTotalInventario = valorTotalInventario != null ? valorTotalInventario : BigDecimal.ZERO;
    }

    public int getTotalProductos() {
        return totalProductos;
    }

    public void setTotalProductos(int totalProductos) {
        this.totalProductos = totalProductos;
    }

    public int getTotalStockUnidades() {
        return totalStockUnidades;
    }

    public void setTotalStockUnidades(int totalStockUnidades) {
        this.totalStockUnidades = totalStockUnidades;
    }

    public int getProductosDisponibles() {
        return productosDisponibles;
    }

    public void setProductosDisponibles(int productosDisponibles) {
        this.productosDisponibles = productosDisponibles;
    }

    public int getProductosBajoStock() {
        return productosBajoStock;
    }

    public void setProductosBajoStock(int productosBajoStock) {
        this.productosBajoStock = productosBajoStock;
    }

    public int getProductosAgotados() {
        return productosAgotados;
    }

    public void setProductosAgotados(int productosAgotados) {
        this.productosAgotados = productosAgotados;
    }

    public BigDecimal getValorTotalInventario() {
        return valorTotalInventario != null ? valorTotalInventario : BigDecimal.ZERO;
    }

    public void setValorTotalInventario(BigDecimal valorTotalInventario) {
        this.valorTotalInventario = valorTotalInventario;
    }

    public String getValorTotalFormateado() {
        return FORMATO_MONEDA.format(getValorTotalInventario());
    }

    public String getTotalStockUnidadesFormateado() {
        return FORMATO_ENTERO.format(totalStockUnidades);
    }

    public String getTotalProductosFormateado() {
        return FORMATO_ENTERO.format(totalProductos);
    }
}
