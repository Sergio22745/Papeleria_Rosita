package com.papeleriarosita.model;

import java.util.Objects;

/**
 * Modelo de dominio que representa a un Proveedor de Papelería Rosita.
 * Mapea directamente la tabla 'proveedores' de la base de datos MySQL.
 */
public class Proveedor {

    private int idProveedor;
    private String nombre;
    private String telefono;
    private String correo;
    private String direccion;

    public Proveedor() {
    }

    public Proveedor(int idProveedor, String nombre, String telefono, String correo, String direccion) {
        this.idProveedor = idProveedor;
        this.nombre = nombre;
        this.telefono = telefono;
        this.correo = correo;
        this.direccion = direccion;
    }

    public Proveedor(String nombre, String telefono, String correo, String direccion) {
        this(0, nombre, telefono, correo, direccion);
    }

    public int getIdProveedor() {
        return idProveedor;
    }

    public void setIdProveedor(int idProveedor) {
        this.idProveedor = idProveedor;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Proveedor proveedor = (Proveedor) o;
        return idProveedor == proveedor.idProveedor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idProveedor);
    }

    @Override
    public String toString() {
        return (nombre != null ? nombre : "") + (telefono != null && !telefono.isBlank() ? " (" + telefono + ")" : "");
    }
}
