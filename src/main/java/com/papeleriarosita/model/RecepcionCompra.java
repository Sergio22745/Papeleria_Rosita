package com.papeleriarosita.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Representa una recepción física de mercancía asociada a una orden de compra.
 * Registra fecha, remisión del proveedor, responsable y lista de ítems ingresados físicamente al almacén.
 */
public class RecepcionCompra {

    private int idRecepcion;
    private int idCompra;
    private int idUsuario;
    private String nombreUsuario;
    private LocalDateTime fechaRecepcion;
    private String numeroRemision;
    private String observaciones;
    private List<DetalleRecepcionCompra> detalles;

    private static final DateTimeFormatter FORMATO_FECHA = 
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.of("es", "CO"));

    public RecepcionCompra() {
        this.detalles = new ArrayList<>();
    }

    public RecepcionCompra(int idCompra, int idUsuario, String numeroRemision, String observaciones) {
        this.idCompra = idCompra;
        this.idUsuario = idUsuario;
        this.numeroRemision = numeroRemision;
        this.observaciones = observaciones;
        this.detalles = new ArrayList<>();
    }

    public RecepcionCompra(int idRecepcion, int idCompra, int idUsuario, String nombreUsuario,
                           LocalDateTime fechaRecepcion, String numeroRemision, String observaciones) {
        this.idRecepcion = idRecepcion;
        this.idCompra = idCompra;
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.fechaRecepcion = fechaRecepcion;
        this.numeroRemision = numeroRemision;
        this.observaciones = observaciones;
        this.detalles = new ArrayList<>();
    }

    public int getIdRecepcion() {
        return idRecepcion;
    }

    public void setIdRecepcion(int idRecepcion) {
        this.idRecepcion = idRecepcion;
    }

    public int getIdCompra() {
        return idCompra;
    }

    public void setIdCompra(int idCompra) {
        this.idCompra = idCompra;
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

    public LocalDateTime getFechaRecepcion() {
        return fechaRecepcion;
    }

    public void setFechaRecepcion(LocalDateTime fechaRecepcion) {
        this.fechaRecepcion = fechaRecepcion;
    }

    public String getFechaRecepcionFormateada() {
        if (fechaRecepcion == null) return "-";
        return fechaRecepcion.format(FORMATO_FECHA);
    }

    public String getNumeroRemision() {
        return numeroRemision != null ? numeroRemision : "";
    }

    public void setNumeroRemision(String numeroRemision) {
        this.numeroRemision = numeroRemision;
    }

    public String getObservaciones() {
        return observaciones != null ? observaciones : "";
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public List<DetalleRecepcionCompra> getDetalles() {
        return detalles;
    }

    public void setDetalles(List<DetalleRecepcionCompra> detalles) {
        this.detalles = detalles != null ? detalles : new ArrayList<>();
    }

    public int getTotalUnidadesRecibidas() {
        if (detalles == null) return 0;
        return detalles.stream().mapToInt(DetalleRecepcionCompra::getCantidadRecibida).sum();
    }
}
