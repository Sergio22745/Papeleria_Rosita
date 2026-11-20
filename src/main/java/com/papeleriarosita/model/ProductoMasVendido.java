package com.papeleriarosita.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Modelo para representar el ranking de productos más vendidos en el módulo de reportes.
 */
public class ProductoMasVendido {

    private int idProducto;
    private String codigo;
    private String nombre;
    private String categoria;
    private int cantidadVendida;
    private BigDecimal totalRecaudado;

    private static final NumberFormat FORMATO_MONEDA =
        NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));

    public ProductoMasVendido() {
        this.totalRecaudado = BigDecimal.ZERO;
    }

    public ProductoMasVendido(int idProducto, String codigo, String nombre,
                              String categoria, int cantidadVendida, BigDecimal totalRecaudado) {
        this.idProducto = idProducto;
        this.codigo = codigo != null ? codigo : "";
        this.nombre = nombre != null ? nombre : "";
        this.categoria = categoria != null ? categoria : "";
        this.cantidadVendida = cantidadVendida;
        this.totalRecaudado = totalRecaudado != null ? totalRecaudado : BigDecimal.ZERO;
    }

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

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public int getCantidadVendida() {
        return cantidadVendida;
    }

    public void setCantidadVendida(int cantidadVendida) {
        this.cantidadVendida = cantidadVendida;
    }

    public BigDecimal getTotalRecaudado() {
        return totalRecaudado != null ? totalRecaudado : BigDecimal.ZERO;
    }

    public void setTotalRecaudado(BigDecimal totalRecaudado) {
        this.totalRecaudado = totalRecaudado;
    }

    public String getTotalRecaudadoFormateado() {
        return FORMATO_MONEDA.format(getTotalRecaudado());
    }

    public BigDecimal getPrecioPromedio() {
        if (cantidadVendida > 0 && totalRecaudado != null) {
            return totalRecaudado.divide(BigDecimal.valueOf(cantidadVendida), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    public String getPrecioPromedioFormateado() {
        return FORMATO_MONEDA.format(getPrecioPromedio());
    }
}
