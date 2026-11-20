package com.papeleriarosita.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Modelo para consolidación de caja, desglose por método de pago y arqueo de mostrador.
 * Agrupa las ventas reales registradas en MySQL para un período o turno determinado.
 */
public class ResumenCaja {

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private int totalVentas;
    private BigDecimal totalRecaudado;
    private BigDecimal totalEfectivo;
    private BigDecimal totalTransferencia;
    private BigDecimal totalTarjeta;
    private int totalArticulos;

    // Valores para arqueo y cuadre físico de turno
    private BigDecimal baseApertura;
    private BigDecimal dineroContado;

    private static final NumberFormat FORMATO_MONEDA =
        NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));

    public ResumenCaja() {
        this.totalVentas = 0;
        this.totalRecaudado = BigDecimal.ZERO;
        this.totalEfectivo = BigDecimal.ZERO;
        this.totalTransferencia = BigDecimal.ZERO;
        this.totalTarjeta = BigDecimal.ZERO;
        this.totalArticulos = 0;
        this.baseApertura = BigDecimal.ZERO;
        this.dineroContado = BigDecimal.ZERO;
    }

    public ResumenCaja(LocalDate fechaDesde, LocalDate fechaHasta, int totalVentas,
                       BigDecimal totalRecaudado, BigDecimal totalEfectivo,
                       BigDecimal totalTransferencia, BigDecimal totalTarjeta, int totalArticulos) {
        this.fechaDesde = fechaDesde;
        this.fechaHasta = fechaHasta;
        this.totalVentas = totalVentas;
        this.totalRecaudado = totalRecaudado != null ? totalRecaudado : BigDecimal.ZERO;
        this.totalEfectivo = totalEfectivo != null ? totalEfectivo : BigDecimal.ZERO;
        this.totalTransferencia = totalTransferencia != null ? totalTransferencia : BigDecimal.ZERO;
        this.totalTarjeta = totalTarjeta != null ? totalTarjeta : BigDecimal.ZERO;
        this.totalArticulos = totalArticulos;
        this.baseApertura = BigDecimal.ZERO;
        this.dineroContado = BigDecimal.ZERO;
    }

    public LocalDate getFechaDesde() {
        return fechaDesde;
    }

    public void setFechaDesde(LocalDate fechaDesde) {
        this.fechaDesde = fechaDesde;
    }

    public LocalDate getFechaHasta() {
        return fechaHasta;
    }

    public void setFechaHasta(LocalDate fechaHasta) {
        this.fechaHasta = fechaHasta;
    }

    public int getTotalVentas() {
        return totalVentas;
    }

    public void setTotalVentas(int totalVentas) {
        this.totalVentas = totalVentas;
    }

    public BigDecimal getTotalRecaudado() {
        return totalRecaudado != null ? totalRecaudado : BigDecimal.ZERO;
    }

    public void setTotalRecaudado(BigDecimal totalRecaudado) {
        this.totalRecaudado = totalRecaudado != null ? totalRecaudado : BigDecimal.ZERO;
    }

    public String getTotalRecaudadoFormateado() {
        return FORMATO_MONEDA.format(getTotalRecaudado());
    }

    public BigDecimal getTotalEfectivo() {
        return totalEfectivo != null ? totalEfectivo : BigDecimal.ZERO;
    }

    public void setTotalEfectivo(BigDecimal totalEfectivo) {
        this.totalEfectivo = totalEfectivo != null ? totalEfectivo : BigDecimal.ZERO;
    }

    public String getTotalEfectivoFormateado() {
        return FORMATO_MONEDA.format(getTotalEfectivo());
    }

    public BigDecimal getTotalTransferencia() {
        return totalTransferencia != null ? totalTransferencia : BigDecimal.ZERO;
    }

    public void setTotalTransferencia(BigDecimal totalTransferencia) {
        this.totalTransferencia = totalTransferencia != null ? totalTransferencia : BigDecimal.ZERO;
    }

    public String getTotalTransferenciaFormateado() {
        return FORMATO_MONEDA.format(getTotalTransferencia());
    }

    public BigDecimal getTotalTarjeta() {
        return totalTarjeta != null ? totalTarjeta : BigDecimal.ZERO;
    }

    public void setTotalTarjeta(BigDecimal totalTarjeta) {
        this.totalTarjeta = totalTarjeta != null ? totalTarjeta : BigDecimal.ZERO;
    }

    public String getTotalTarjetaFormateado() {
        return FORMATO_MONEDA.format(getTotalTarjeta());
    }

    public BigDecimal getTotalElectronico() {
        return getTotalTransferencia().add(getTotalTarjeta());
    }

    public String getTotalElectronicoFormateado() {
        return FORMATO_MONEDA.format(getTotalElectronico());
    }

    public int getTotalArticulos() {
        return totalArticulos;
    }

    public void setTotalArticulos(int totalArticulos) {
        this.totalArticulos = totalArticulos;
    }

    public BigDecimal getBaseApertura() {
        return baseApertura != null ? baseApertura : BigDecimal.ZERO;
    }

    public void setBaseApertura(BigDecimal baseApertura) {
        this.baseApertura = baseApertura != null ? baseApertura : BigDecimal.ZERO;
    }

    public String getBaseAperturaFormateada() {
        return FORMATO_MONEDA.format(getBaseApertura());
    }

    public BigDecimal getDineroContado() {
        return dineroContado != null ? dineroContado : BigDecimal.ZERO;
    }

    public void setDineroContado(BigDecimal dineroContado) {
        this.dineroContado = dineroContado != null ? dineroContado : BigDecimal.ZERO;
    }

    public String getDineroContadoFormateado() {
        return FORMATO_MONEDA.format(getDineroContado());
    }

    /**
     * Calcula el efectivo total que debe haber físicamente en el cajón de mostrador:
     * Efectivo Esperado = Base de Apertura (Fondo) + Ventas cobradas en Efectivo.
     */
    public BigDecimal getEfectivoEsperado() {
        return getBaseApertura().add(getTotalEfectivo());
    }

    public String getEfectivoEsperadoFormateado() {
        return FORMATO_MONEDA.format(getEfectivoEsperado());
    }

    /**
     * Calcula la diferencia entre el dinero físico contado y el esperado:
     * Diferencia = Dinero Contado - Efectivo Esperado.
     * Si es 0: Cuadre perfecto.
     * Si es negativo: Faltante de dinero.
     * Si es positivo: Sobrante de dinero.
     */
    public BigDecimal getDiferenciaArqueo() {
        return getDineroContado().subtract(getEfectivoEsperado());
    }

    public String getDiferenciaArqueoFormateada() {
        return FORMATO_MONEDA.format(getDiferenciaArqueo());
    }

    public boolean isCuadrado() {
        return getDiferenciaArqueo().compareTo(BigDecimal.ZERO) == 0;
    }
}
