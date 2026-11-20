package com.papeleriarosita.dao;

import com.papeleriarosita.database.ConexionBD;
import com.papeleriarosita.model.DetalleVenta;
import com.papeleriarosita.model.ResumenCaja;
import com.papeleriarosita.model.UserSession;
import com.papeleriarosita.model.Venta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Objeto de Acceso a Datos (DAO) para el Módulo de Control de Caja.
 * Realiza el cuadre de caja de mostrador, cálculo de ingresos por método de pago
 * (Efectivo, Transferencia, Tarjeta) y consolidado de ventas reales en MySQL.
 *
 * REGLAS DE NEGOCIO:
 * 1. Excluye estrictamente las ventas con estado 'ANULADA'.
 * 2. Requiere sesión de usuario autenticada (`UserSession`).
 * 3. No duplica ni altera transacciones de ventas o inventario.
 * 4. Consulta directamente los valores consolidados desde `ventas` y `detalle_venta`.
 */
public class CajaDAO {

    /**
     * Calcula el resumen consolidado de caja para un rango de fechas y usuario opcional.
     * Excluye ventas anuladas y separa efectivo de pagos electrónicos.
     */
    public ResumenCaja obtenerResumenCaja(LocalDate fechaDesde, LocalDate fechaHasta,
                                          Integer idUsuario, UserSession sesion) throws SQLException {
        validarSesion(sesion);

        LocalDate fDesde = (fechaDesde != null) ? fechaDesde : LocalDate.now();
        LocalDate fHasta = (fechaHasta != null) ? fechaHasta : LocalDate.now();

        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(DISTINCT v.id_venta) AS total_ventas,
                   COALESCE(SUM(dv.subtotal), 0) AS total_recaudado,
                   COALESCE(SUM(CASE WHEN v.metodo_pago = 'EFECTIVO' THEN dv.subtotal ELSE 0 END), 0) AS total_efectivo,
                   COALESCE(SUM(CASE WHEN v.metodo_pago = 'TRANSFERENCIA' THEN dv.subtotal ELSE 0 END), 0) AS total_transferencia,
                   COALESCE(SUM(CASE WHEN v.metodo_pago = 'TARJETA' THEN dv.subtotal ELSE 0 END), 0) AS total_tarjeta,
                   COALESCE(SUM(dv.cantidad), 0) AS total_articulos
            FROM ventas v
            INNER JOIN detalle_venta dv ON v.id_venta = dv.id_venta
            WHERE v.estado = 'REGISTRADA'
              AND v.fecha_venta >= ?
              AND v.fecha_venta <= ?
            """);

        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(fDesde.atStartOfDay()));
        params.add(Timestamp.valueOf(fHasta.atTime(23, 59, 59)));

        if (idUsuario != null && idUsuario > 0) {
            sql.append(" AND v.id_usuario = ?");
            params.add(idUsuario);
        }

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ResumenCaja(
                        fDesde,
                        fHasta,
                        rs.getInt("total_ventas"),
                        rs.getBigDecimal("total_recaudado"),
                        rs.getBigDecimal("total_efectivo"),
                        rs.getBigDecimal("total_transferencia"),
                        rs.getBigDecimal("total_tarjeta"),
                        rs.getInt("total_articulos")
                    );
                }
            }
        }

        return new ResumenCaja(fDesde, fHasta, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    /**
     * Consulta el listado de ventas activas que conforman los ingresos de caja,
     * excluyendo ventas anuladas.
     */
    public List<Venta> listarVentasCaja(LocalDate fechaDesde, LocalDate fechaHasta,
                                        Integer idUsuario, String metodoPago,
                                        UserSession sesion) throws SQLException {
        validarSesion(sesion);

        List<Venta> lista = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT v.id_venta, v.id_cliente, c.nombre AS nombre_cliente,
                   v.id_usuario, u.nombre AS nombre_usuario, v.fecha_venta,
                   v.metodo_pago, v.estado,
                   COALESCE(SUM(dv.subtotal), 0) AS total,
                   COALESCE(SUM(dv.cantidad), 0) AS total_articulos
            FROM ventas v
            LEFT JOIN clientes c ON v.id_cliente = c.id_cliente
            LEFT JOIN usuarios u ON v.id_usuario = u.id_usuario
            LEFT JOIN detalle_venta dv ON v.id_venta = dv.id_venta
            WHERE v.estado = 'REGISTRADA'
            """);

        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND v.fecha_venta >= ?");
            params.add(Timestamp.valueOf(fechaDesde.atStartOfDay()));
        }

        if (fechaHasta != null) {
            sql.append(" AND v.fecha_venta <= ?");
            params.add(Timestamp.valueOf(fechaHasta.atTime(23, 59, 59)));
        }

        if (idUsuario != null && idUsuario > 0) {
            sql.append(" AND v.id_usuario = ?");
            params.add(idUsuario);
        }

        if (metodoPago != null && !metodoPago.trim().isEmpty() && !"TODOS".equalsIgnoreCase(metodoPago.trim())) {
            sql.append(" AND v.metodo_pago = ?");
            params.add(metodoPago.trim().toUpperCase());
        }

        sql.append("""
            GROUP BY v.id_venta, v.id_cliente, c.nombre, v.id_usuario, u.nombre,
                     v.fecha_venta, v.metodo_pago, v.estado
            ORDER BY v.fecha_venta DESC, v.id_venta DESC
            """);

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("fecha_venta");
                    LocalDateTime fecha = (ts != null) ? ts.toLocalDateTime() : null;

                    int idCli = rs.getInt("id_cliente");
                    Integer idCliente = rs.wasNull() ? null : idCli;

                    lista.add(new Venta(
                        rs.getInt("id_venta"),
                        idCliente,
                        rs.getString("nombre_cliente"),
                        rs.getInt("id_usuario"),
                        rs.getString("nombre_usuario"),
                        fecha,
                        rs.getString("metodo_pago"),
                        rs.getString("estado"),
                        rs.getBigDecimal("total"),
                        rs.getInt("total_articulos")
                    ));
                }
            }
        }
        return lista;
    }

    /**
     * Consulta los ítems vendidos en una venta específica para inspección de mostrador.
     */
    public List<DetalleVenta> listarDetallesVenta(int idVenta) throws SQLException {
        List<DetalleVenta> lista = new ArrayList<>();
        if (idVenta <= 0) return lista;

        String sql = """
            SELECT dv.id_detalle_venta, dv.id_venta, dv.id_producto,
                   p.codigo AS codigo_producto, p.nombre AS nombre_producto,
                   dv.cantidad, dv.precio_unitario, dv.subtotal
            FROM detalle_venta dv
            INNER JOIN productos p ON dv.id_producto = p.id_producto
            WHERE dv.id_venta = ?
            ORDER BY dv.id_detalle_venta ASC
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idVenta);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(new DetalleVenta(
                        rs.getInt("id_detalle_venta"),
                        rs.getInt("id_venta"),
                        rs.getInt("id_producto"),
                        rs.getString("codigo_producto"),
                        rs.getString("nombre_producto"),
                        rs.getInt("cantidad"),
                        rs.getBigDecimal("precio_unitario"),
                        rs.getBigDecimal("subtotal")
                    ));
                }
            }
        }
        return lista;
    }

    /**
     * Inspecciona dinámicamente si MySQL dispone de tablas dedicadas para persistencia
     * formal de aperturas y cierres de caja (`caja`, `arqueos_caja`).
     * Actualmente retorna false, documentando que no existen en el esquema original.
     */
    public boolean isPersistenciaAperturaCierreDisponible() {
        try (Connection conn = ConexionBD.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "caja", new String[]{"TABLE"})) {
                if (rs.next()) return true;
            }
            try (ResultSet rs2 = meta.getTables(null, null, "arqueos_caja", new String[]{"TABLE"})) {
                return rs2.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void validarSesion(UserSession sesion) {
        if (sesion == null) {
            throw new SecurityException("Acceso denegado: Se requiere una sesión de usuario activa para consultar la caja.");
        }
    }
}
