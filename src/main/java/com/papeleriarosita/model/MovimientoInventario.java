package com.papeleriarosita.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Modelo que representa un Movimiento de Inventario en Papelería Rosita.
 * Mapea directamente con la tabla 'movimientos_inventario' de MySQL.
 */
public class MovimientoInventario {

    private int idMovimiento;
    private int idProducto;
    private String codigoProducto;
    private String nombreProducto;
    private Integer idUsuario;
    private String nombreUsuario;
    private LocalDateTime fechaMovimiento;
    private String tipoMovimiento; // 'ENTRADA', 'SALIDA', 'AJUSTE'
    private int cantidad;
    private String descripcion;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public MovimientoInventario() {}

    public MovimientoInventario(int idMovimiento, int idProducto, String codigoProducto, String nombreProducto,
                                Integer idUsuario, String nombreUsuario, LocalDateTime fechaMovimiento,
                                String tipoMovimiento, int cantidad, String descripcion) {
        this.idMovimiento = idMovimiento;
        this.idProducto = idProducto;
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario != null ? nombreUsuario : "Sistema / Desconocido";
        this.fechaMovimiento = fechaMovimiento;
        this.tipoMovimiento = tipoMovimiento;
        this.cantidad = cantidad;
        this.descripcion = descripcion;
    }

    public int getIdMovimiento() {
        return idMovimiento;
    }

    public void setIdMovimiento(int idMovimiento) {
        this.idMovimiento = idMovimiento;
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

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario != null ? nombreUsuario : "Sistema";
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public LocalDateTime getFechaMovimiento() {
        return fechaMovimiento;
    }

    public void setFechaMovimiento(LocalDateTime fechaMovimiento) {
        this.fechaMovimiento = fechaMovimiento;
    }

    public String getFechaFormateada() {
        return fechaMovimiento != null ? fechaMovimiento.format(FORMATTER) : "";
    }

    public String getTipoMovimiento() {
        return tipoMovimiento;
    }

    public void setTipoMovimiento(String tipoMovimiento) {
        this.tipoMovimiento = tipoMovimiento;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public String getDescripcion() {
        return descripcion != null ? descripcion : "";
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MovimientoInventario that = (MovimientoInventario) o;
        return idMovimiento == that.idMovimiento;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idMovimiento);
    }

    @Override
    public String toString() {
        return "MovimientoInventario{" +
                "idMovimiento=" + idMovimiento +
                ", idProducto=" + idProducto +
                ", tipoMovimiento='" + tipoMovimiento + '\'' +
                ", cantidad=" + cantidad +
                ", fechaMovimiento=" + fechaMovimiento +
                '}';
    }
}
