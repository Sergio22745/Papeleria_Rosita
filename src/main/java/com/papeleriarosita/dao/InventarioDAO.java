package com.papeleriarosita.dao;

import com.papeleriarosita.database.ConexionBD;
import com.papeleriarosita.model.Inventario;
import com.papeleriarosita.model.MovimientoInventario;
import com.papeleriarosita.model.UserSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Objeto de Acceso a Datos (DAO) para la gestión de Inventario y Movimientos.
 * Administra las existencias físicas, alertas de stock, entradas físicas y trazabilidad.
 */
public class InventarioDAO {

    /**
     * Consulta el inventario completo de todos los productos (activos e inactivos).
     */
    public List<Inventario> listarTodo() throws SQLException {
        List<Inventario> lista = new ArrayList<>();
        String sql = """
            SELECT i.id_inventario, i.id_producto, p.codigo AS codigo_producto,
                   p.nombre AS nombre_producto, c.nombre AS nombre_categoria,
                   p.precio_venta, p.activo AS activo_producto,
                   i.stock_actual, i.stock_minimo, i.fecha_actualizacion
            FROM inventario i
            INNER JOIN productos p ON i.id_producto = p.id_producto
            INNER JOIN categorias c ON p.id_categoria = c.id_categoria
            ORDER BY p.nombre ASC
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                lista.add(mapearInventario(rs));
            }
        }
        return lista;
    }

    /**
     * Consulta únicamente los productos agotados (stock_actual = 0).
     */
    public List<Inventario> listarAgotados() throws SQLException {
        List<Inventario> lista = new ArrayList<>();
        String sql = """
            SELECT i.id_inventario, i.id_producto, p.codigo AS codigo_producto,
                   p.nombre AS nombre_producto, c.nombre AS nombre_categoria,
                   p.precio_venta, p.activo AS activo_producto,
                   i.stock_actual, i.stock_minimo, i.fecha_actualizacion
            FROM inventario i
            INNER JOIN productos p ON i.id_producto = p.id_producto
            INNER JOIN categorias c ON p.id_categoria = c.id_categoria
            WHERE i.stock_actual = 0
            ORDER BY p.nombre ASC
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                lista.add(mapearInventario(rs));
            }
        }
        return lista;
    }

    /**
     * Consulta los productos con existencias bajas (stock_actual > 0 AND stock_actual <= stock_minimo).
     */
    public List<Inventario> listarStockBajo() throws SQLException {
        List<Inventario> lista = new ArrayList<>();
        String sql = """
            SELECT i.id_inventario, i.id_producto, p.codigo AS codigo_producto,
                   p.nombre AS nombre_producto, c.nombre AS nombre_categoria,
                   p.precio_venta, p.activo AS activo_producto,
                   i.stock_actual, i.stock_minimo, i.fecha_actualizacion
            FROM inventario i
            INNER JOIN productos p ON i.id_producto = p.id_producto
            INNER JOIN categorias c ON p.id_categoria = c.id_categoria
            WHERE i.stock_actual > 0 AND i.stock_actual <= i.stock_minimo
            ORDER BY i.stock_actual ASC
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                lista.add(mapearInventario(rs));
            }
        }
        return lista;
    }

    /**
     * Busca inventario por criterio de búsqueda (código de producto, nombre o categoría).
     */
    public List<Inventario> buscarPorFiltro(String criterio) throws SQLException {
        if (criterio == null || criterio.trim().isEmpty()) {
            return listarTodo();
        }

        List<Inventario> lista = new ArrayList<>();
        String sql = """
            SELECT i.id_inventario, i.id_producto, p.codigo AS codigo_producto,
                   p.nombre AS nombre_producto, c.nombre AS nombre_categoria,
                   p.precio_venta, p.activo AS activo_producto,
                   i.stock_actual, i.stock_minimo, i.fecha_actualizacion
            FROM inventario i
            INNER JOIN productos p ON i.id_producto = p.id_producto
            INNER JOIN categorias c ON p.id_categoria = c.id_categoria
            WHERE LOWER(p.codigo) LIKE LOWER(?)
               OR LOWER(p.nombre) LIKE LOWER(?)
               OR LOWER(c.nombre) LIKE LOWER(?)
            ORDER BY p.nombre ASC
            """;

        String patron = "%" + criterio.trim() + "%";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, patron);
            stmt.setString(2, patron);
            stmt.setString(3, patron);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearInventario(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Registra una entrada física de mercancía al inventario.
     *
     * REGLAS DE SEGURIDAD Y CONSISTENCIA:
     * 1. Requiere una sesión de usuario autenticada con rol autorizado (Rosita o Gladys).
     * 2. El movimiento manual debe registrar obligatoriamente el id_usuario del responsable (no NULL).
     * 3. Si el producto no tiene registro en la tabla 'inventario', se lanza IllegalStateException
     *    y se revierte la transacción. No se insertan datos que oculten una falla del trigger
     *    'trg_producto_crear_inventario'.
     *
     * @param idProducto ID del producto que ingresa
     * @param cantidad Cantidad física a ingresar (debe ser > 0)
     * @param motivo Descripción o justificación de la entrada física
     * @param sesion Sesión del usuario autenticado que realiza la entrada
     */
    public void registrarEntradaFisica(int idProducto, int cantidad, String motivo, UserSession sesion)
            throws SQLException, SecurityException, IllegalStateException {

        // 1. Control de acceso y sesión autenticada obligatoria
        if (sesion == null || sesion.getIdUsuario() <= 0 || sesion.isModoVistaPrevia()) {
            throw new SecurityException("Acceso denegado: Se requiere una sesión de usuario autenticada (no vista previa) para registrar entradas físicas de mercancía.");
        }

        if (!sesion.isAdmin() && !sesion.isEmpleado()) {
            throw new SecurityException("Acceso restringido: El rol '" + sesion.getRole() + "' no está autorizado para registrar movimientos de inventario.");
        }

        if (idProducto <= 0) {
            throw new IllegalArgumentException("El producto seleccionado no es válido.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de entrada física debe ser mayor a cero.");
        }
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("Es obligatorio especificar el motivo o descripción de la entrada de mercancía.");
        }

        int idUsuarioResponsable = sesion.getIdUsuario();

        Connection conn = null;
        try {
            conn = ConexionBD.getConnection();
            conn.setAutoCommit(false);

            // 1. Aumentar stock_actual en inventario
            String sqlUpdate = """
                UPDATE inventario
                SET stock_actual = stock_actual + ?
                WHERE id_producto = ?
                """;

            int filasActualizadas = 0;
            try (PreparedStatement stmtUpdate = conn.prepareStatement(sqlUpdate)) {
                stmtUpdate.setInt(1, cantidad);
                stmtUpdate.setInt(2, idProducto);
                filasActualizadas = stmtUpdate.executeUpdate();
            }

            // Si el producto no tiene fila de inventario, se aborta con error de inconsistencia.
            // NO se realiza un INSERT manual para no ocultar fallas del trigger 'trg_producto_crear_inventario'.
            if (filasActualizadas == 0) {
                throw new IllegalStateException(
                    "Inconsistencia en inventario: El producto con ID " + idProducto +
                    " no tiene un registro asociado en la tabla 'inventario'. " +
                    "Verifique que el producto exista y que el trigger 'trg_producto_crear_inventario' haya operado."
                );
            }

            // 2. Registrar en movimientos_inventario con el usuario responsable (nunca NULL)
            String sqlMov = """
                INSERT INTO movimientos_inventario (id_producto, id_usuario, tipo_movimiento, cantidad, descripcion)
                VALUES (?, ?, 'ENTRADA', ?, ?)
                """;

            try (PreparedStatement stmtMov = conn.prepareStatement(sqlMov)) {
                stmtMov.setInt(1, idProducto);
                stmtMov.setInt(2, idUsuarioResponsable);
                stmtMov.setInt(3, cantidad);
                stmtMov.setString(4, motivo.trim());
                stmtMov.executeUpdate();
            }

            conn.commit();

        } catch (IllegalStateException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw e;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
                ConexionBD.cerrarConexion(conn);
            }
        }
    }

    /**
     * Establece o modifica el stock mínimo de un producto en el inventario.
     */
    public void actualizarStockMinimo(int idProducto, int nuevoStockMinimo) throws SQLException {
        if (idProducto <= 0) {
            throw new IllegalArgumentException("ID de producto no válido.");
        }
        if (nuevoStockMinimo < 0) {
            throw new IllegalArgumentException("El stock mínimo no puede ser negativo.");
        }

        String sql = "UPDATE inventario SET stock_minimo = ? WHERE id_producto = ?";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, nuevoStockMinimo);
            stmt.setInt(2, idProducto);

            int filas = stmt.executeUpdate();
            if (filas == 0) {
                throw new IllegalStateException(
                    "Inconsistencia en inventario: No se encontró registro en 'inventario' para el producto con ID " + idProducto
                );
            }
        }
    }

    /**
     * Consulta los movimientos históricos de inventario.
     *
     * @param idProductoFiltro Si es != null, filtra por ese producto; si es null, muestra de todos
     * @param limite Máximo de registros a retornar (ej. 100)
     */
    public List<MovimientoInventario> listarMovimientos(Integer idProductoFiltro, int limite) throws SQLException {
        List<MovimientoInventario> lista = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT m.id_movimiento, m.id_producto, p.codigo AS codigo_producto,
                   p.nombre AS nombre_producto, m.id_usuario, u.nombre AS nombre_usuario,
                   m.fecha_movimiento, m.tipo_movimiento, m.cantidad, m.descripcion
            FROM movimientos_inventario m
            INNER JOIN productos p ON m.id_producto = p.id_producto
            LEFT JOIN usuarios u ON m.id_usuario = u.id_usuario
            """);

        if (idProductoFiltro != null && idProductoFiltro > 0) {
            sql.append(" WHERE m.id_producto = ? ");
        }

        sql.append(" ORDER BY m.fecha_movimiento DESC LIMIT ?");

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (idProductoFiltro != null && idProductoFiltro > 0) {
                stmt.setInt(paramIndex++, idProductoFiltro);
            }
            stmt.setInt(paramIndex, Math.max(1, limite));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("fecha_movimiento");
                    LocalDateTime fecha = (ts != null) ? ts.toLocalDateTime() : null;

                    int idUser = rs.getInt("id_usuario");
                    Integer idUsuario = rs.wasNull() ? null : idUser;

                    lista.add(new MovimientoInventario(
                        rs.getInt("id_movimiento"),
                        rs.getInt("id_producto"),
                        rs.getString("codigo_producto"),
                        rs.getString("nombre_producto"),
                        idUsuario,
                        rs.getString("nombre_usuario"),
                        fecha,
                        rs.getString("tipo_movimiento"),
                        rs.getInt("cantidad"),
                        rs.getString("descripcion")
                    ));
                }
            }
        }
        return lista;
    }

    private Inventario mapearInventario(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("fecha_actualizacion");
        LocalDateTime fecha = (ts != null) ? ts.toLocalDateTime() : null;

        return new Inventario(
            rs.getInt("id_inventario"),
            rs.getInt("id_producto"),
            rs.getString("codigo_producto"),
            rs.getString("nombre_producto"),
            rs.getString("nombre_categoria"),
            rs.getBigDecimal("precio_venta"),
            rs.getInt("activo_producto") == 1,
            rs.getInt("stock_actual"),
            rs.getInt("stock_minimo"),
            fecha
        );
    }
}
