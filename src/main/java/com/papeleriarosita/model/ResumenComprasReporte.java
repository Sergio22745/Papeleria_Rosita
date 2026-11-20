package com.papeleriarosita.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Modelo que encapsula los indicadores clave (KPIs) y métricas cuantitativas
 * de abastecimiento y compras para el módulo de reportes.
 */
public class ResumenComprasReporte {

    private int totalCompras;
    private BigDecimal montoTotal;
    private int comprasRegistradas;
    private int comprasParciales;
    private int comprasRecibidas;
    private int comprasAnuladas;
    private int totalUnidadesSolicitadas;
    private int totalUnidadesRecibidas;

    private static final NumberFormat FORMATO_MONEDA =
        NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
    private static final NumberFormat FORMATO_ENTERO =
        NumberFormat.getIntegerInstance(Locale.of("es", "CO"));

    public ResumenComprasReporte() {
        this.montoTotal = BigDecimal.ZERO;
    }

    public ResumenComprasReporte(int totalCompras, BigDecimal montoTotal,
                                 int comprasRegistradas, int comprasParciales,
                                 int comprasRecibidas, int comprasAnuladas,
                                 int totalUnidadesSolicitadas, int totalUnidadesRecibidas) {
        this.totalCompras = totalCompras;
        this.montoTotal = montoTotal != null ? montoTotal : BigDecimal.ZERO;
        this.comprasRegistradas = comprasRegistradas;
        this.comprasParciales = comprasParciales;
        this.comprasRecibidas = comprasRecibidas;
        this.comprasAnuladas = comprasAnuladas;
        this.totalUnidadesSolicitadas = totalUnidadesSolicitadas;
        this.totalUnidadesRecibidas = totalUnidadesRecibidas;
    }

    public int getTotalCompras() {
        return totalCompras;
    }

    public void setTotalCompras(int totalCompras) {
        this.totalCompras = totalCompras;
    }

    public BigDecimal getMontoTotal() {
        return montoTotal != null ? montoTotal : BigDecimal.ZERO;
    }

    public void setMontoTotal(BigDecimal montoTotal) {
        this.montoTotal = montoTotal;
    }

    public int getComprasRegistradas() {
        return comprasRegistradas;
    }

    public void setComprasRegistradas(int comprasRegistradas) {
        this.comprasRegistradas = comprasRegistradas;
    }

    public int getComprasParciales() {
        return comprasParciales;
    }

    public void setComprasParciales(int comprasParciales) {
        this.comprasParciales = comprasParciales;
    }

    public int getComprasRecibidas() {
        return comprasRecibidas;
    }

    public void setComprasRecibidas(int comprasRecibidas) {
        this.comprasRecibidas = comprasRecibidas;
    }

    public int getComprasAnuladas() {
        return comprasAnuladas;
    }

    public void setComprasAnuladas(int comprasAnuladas) {
        this.comprasAnuladas = comprasAnuladas;
    }

    public int getTotalUnidadesSolicitadas() {
        return totalUnidadesSolicitadas;
    }

    public void setTotalUnidadesSolicitadas(int totalUnidadesSolicitadas) {
        this.totalUnidadesSolicitadas = totalUnidadesSolicitadas;
    }

    public int getTotalUnidadesRecibidas() {
        return totalUnidadesRecibidas;
    }

    public void setTotalUnidadesRecibidas(int totalUnidadesRecibidas) {
        this.totalUnidadesRecibidas = totalUnidadesRecibidas;
    }

    public int getTotalUnidadesPendientes() {
        int pendientes = totalUnidadesSolicitadas - totalUnidadesRecibidas;
        return Math.max(0, pendientes);
    }

    public String getMontoTotalFormateado() {
        return FORMATO_MONEDA.format(getMontoTotal());
    }

    public String getTotalComprasFormateado() {
        return FORMATO_ENTERO.format(totalCompras);
    }

    public String getTotalUnidadesSolicitadasFormateado() {
        return FORMATO_ENTERO.format(totalUnidadesSolicitadas);
    }

    public String getTotalUnidadesRecibidasFormateado() {
        return FORMATO_ENTERO.format(totalUnidadesRecibidas);
    }

    public String getTotalUnidadesPendientesFormateado() {
        return FORMATO_ENTERO.format(getTotalUnidadesPendientes());
    }
}
