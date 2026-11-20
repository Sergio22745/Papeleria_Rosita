package com.papeleriarosita.model;

/**
 * Representa la cantidad física ingresada de un ítem de compra en una recepción específica.
 */
public class DetalleRecepcionCompra {

    private int idDetalleRecepcion;
    private int idRecepcion;
    private int idDetalleCompra;
    private int idProducto;
    private String codigoProducto;
    private String nombreProducto;
    private int cantidadRecibida;
    private String observaciones;

    public DetalleRecepcionCompra() {
    }

    public DetalleRecepcionCompra(int idDetalleCompra, int idProducto, int cantidadRecibida, String observaciones) {
        this.idDetalleCompra = idDetalleCompra;
        this.idProducto = idProducto;
        this.cantidadRecibida = cantidadRecibida;
        this.observaciones = observaciones;
    }

    public DetalleRecepcionCompra(int idDetalleRecepcion, int idRecepcion, int idDetalleCompra,
                                  int idProducto, String codigoProducto, String nombreProducto,
                                  int cantidadRecibida, String observaciones) {
        this.idDetalleRecepcion = idDetalleRecepcion;
        this.idRecepcion = idRecepcion;
        this.idDetalleCompra = idDetalleCompra;
        this.idProducto = idProducto;
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.cantidadRecibida = cantidadRecibida;
        this.observaciones = observaciones;
    }

    public int getIdDetalleRecepcion() {
        return idDetalleRecepcion;
    }

    public void setIdDetalleRecepcion(int idDetalleRecepcion) {
        this.idDetalleRecepcion = idDetalleRecepcion;
    }

    public int getIdRecepcion() {
        return idRecepcion;
    }

    public void setIdRecepcion(int idRecepcion) {
        this.idRecepcion = idRecepcion;
    }

    public int getIdDetalleCompra() {
        return idDetalleCompra;
    }

    public void setIdDetalleCompra(int idDetalleCompra) {
        this.idDetalleCompra = idDetalleCompra;
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

    public int getCantidadRecibida() {
        return cantidadRecibida;
    }

    public void setCantidadRecibida(int cantidadRecibida) {
        this.cantidadRecibida = cantidadRecibida;
    }

    public String getObservaciones() {
        return observaciones != null ? observaciones : "";
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
