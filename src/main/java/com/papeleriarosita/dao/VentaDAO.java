package com.papeleriarosita.dao;

import com.papeleriarosita.database.ConexionBD;
import com.papeleriarosita.model.DetalleVenta;
import com.papeleriarosita.model.UserSession;
import com.papeleriarosita.model.Venta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Objeto de Acceso a Datos (DAO) para el Módulo de Ventas de Papelería Rosita.
 * Administra el registro atómico de ventas de mostrador, control de existencias,
 * consulta histórica de ventas y consolidado diario de caja.
 *
 * REGLAS DE NEGOCIO Y CONSISTENCIA:
 * 1. Requiere sesión de usuario autenticada (`UserSession`).
 * 2. Consulta y valida stock real en `inventario` antes de confirmar la venta.
 * 3. Utiliza estrictamente el precio de venta vigente en catálogo (`productos.precio_venta`).
 * 4. El descuento de existencias y registro en kárdex (`movimientos_inventario`) es ejecutado
 *    automáticamente por el trigger de base de datos `trg_venta_actualizar_inventario`.
 *    Este DAO NO duplica el UPDATE de inventario para evitar descuadres.
 * 5. Toda la operación se realiza dentro de una única transacción JDBC segura.
 */
public class VentaDAO {

    public record ResumenVentasDiarias(
        int totalVentas,
        BigDecimal totalRecaudado,
        BigDecimal totalEfectivo,
        BigDecimal totalElectronico
    ) {}

    /**
     * =========================================================================
     * REGISTRO ATÓMICO DE VENTA EN MOSTRADOR (SUSTENTACIÓN: MÉTODO PRINCIPAL)
     * =========================================================================
     * ¿Qué hace este método?
     * Registra una venta completa en una única transacción atómica en MySQL.
     *
     * ¿Cómo explicarlo paso a paso al docente?
     * 1. VALIDACIÓN PREVIA EN JAVA:
     *    - Verifica que haya una cajera/administradora autenticada (sesion).
     *    - Valida que el carrito tenga artículos y cantidades válidas (> 0).
     *
     * 2. INICIO DE TRANSACCIÓN ATÓMICA:
     *    - Ejecuta 'conn.setAutoCommit(false)'. Todo lo que ocurra a continuación
     *      se guarda como un bloque único e indivisible. Si algo falla, se ejecuta
     *      'conn.rollback()' y no se descuenta nada ni se crean ventas corruptas.
     *
     * 3. BLOQUEO PESIMISTA Y VALIDACIÓN DE STOCK (FOR UPDATE):
     *    - Consulta la tabla 'inventario' con 'FOR UPDATE' para bloquear la fila
     *      del producto y evitar que dos ventas simultáneas vendan el mismo artículo.
     *    - Si el stock es menor a lo solicitado, detiene la venta con una excepción clara.
     *    - Lee el precio oficial de 'productos.precio_venta' para evitar manipulaciones.
     *
     * 4. INSERCIÓN DE CABECERA:
     *    - Inserta en la tabla 'ventas' (id_cliente, id_usuario, fecha_venta, metodo_pago).
     *    - Recupera el 'id_venta' generado automáticamente por MySQL.
     *
     * 5. INSERCIÓN DE DETALLES Y DELEGACIÓN AL TRIGGER DE MYSQL:
     *    - Inserta en 'detalle_venta' mediante batch (por lote).
     *    - ¡MUY IMPORTANTE!: Java NO actualiza 'inventario' con un UPDATE manual.
     *      En cuanto se inserta cada detalle, el disparador 'trg_venta_actualizar_inventario'
     *      en MySQL ejecuta en milisegundos:
     *         UPDATE inventario SET stock_actual = stock_actual - NEW.cantidad
     *      y crea el asiento en 'movimientos_inventario' (Kárdex).
     *
     * 6. CONFIRMACIÓN FINAL:
     *    - Ejecuta 'conn.commit()' y retorna el número de factura/venta generado.
     * =========================================================================
     */
    public int registrarVenta(Venta venta, UserSession sesion) throws SQLException {
        validarSesion(sesion);
        validarDatosVenta(venta);

        Connection conn = null;
        try {
            conn = ConexionBD.getConnection();
            conn.setAutoCommit(false);

            // 1. Validar existencias actuales y obtener precios oficiales de catálogo con bloqueo pesimista
            String sqlCheckStock = """
                SELECT p.id_producto, p.nombre, p.precio_venta, p.activo,
                       COALESCE(i.stock_actual, 0) AS stock_actual
                FROM productos p
                INNER JOIN inventario i ON p.id_producto = i.id_producto
                WHERE p.id_producto = ?
                FOR UPDATE
                """;

            try (PreparedStatement stmtStock = conn.prepareStatement(sqlCheckStock)) {
                for (DetalleVenta det : venta.getDetalles()) {
                    stmtStock.setInt(1, det.getIdProducto());
                    try (ResultSet rs = stmtStock.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                "El producto con ID " + det.getIdProducto() + " no existe en el catálogo o no tiene inventario."
                            );
                        }

                        boolean activo = rs.getInt("activo") == 1;
                        if (!activo) {
                            throw new IllegalStateException(
                                "El producto '" + rs.getString("nombre") + "' se encuentra inactivo y no puede ser vendido."
                            );
                        }

                        int stockActual = rs.getInt("stock_actual");
                        if (stockActual < det.getCantidad()) {
                            throw new IllegalStateException(
                                "Stock insuficiente para '" + rs.getString("nombre") + "'. " +
                                "Disponible: " + stockActual + " unid., solicitado: " + det.getCantidad() + " unid."
                            );
                        }

                        // Asignar precio de catálogo (no editable por el usuario en Ventas)
                        BigDecimal precioCatalogo = rs.getBigDecimal("precio_venta");
                        det.setPrecioUnitario(precioCatalogo);
                        det.recalcularSubtotal();
                    }
                }
            }

            // Recalcular total verificado
            venta.recalcularTotales();

            // 2. Insertar cabecera de la venta
            int idVentaGenerada;
            String sqlVenta = """
                INSERT INTO ventas (id_cliente, id_usuario, fecha_venta, metodo_pago, estado)
                VALUES (?, ?, CURRENT_TIMESTAMP, ?, 'REGISTRADA')
                """;

            try (PreparedStatement stmtVenta = conn.prepareStatement(sqlVenta, Statement.RETURN_GENERATED_KEYS)) {
                if (venta.getIdCliente() != null && venta.getIdCliente() > 0) {
                    stmtVenta.setInt(1, venta.getIdCliente());
                } else {
                    stmtVenta.setNull(1, java.sql.Types.INTEGER);
                }

                stmtVenta.setInt(2, sesion.getIdUsuario());
                stmtVenta.setString(3, venta.getMetodoPago().toUpperCase().trim());
                stmtVenta.executeUpdate();

                try (ResultSet rsKeys = stmtVenta.getGeneratedKeys()) {
                    if (rsKeys.next()) {
                        idVentaGenerada = rsKeys.getInt(1);
                    } else {
                        throw new SQLException("No se pudo obtener el identificador generado para la venta.");
                    }
                }
            }

            // 3. Insertar detalles de la venta
            // NOTA: Al insertar cada detalle, el trigger 'trg_venta_actualizar_inventario' en MySQL
            // descuenta automáticamente el stock y genera el movimiento 'SALIDA' en movimientos_inventario.
            // No se debe duplicar el UPDATE en este código.
            String sqlDetalle = """
                INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario)
                VALUES (?, ?, ?, ?)
                """;

            try (PreparedStatement stmtDet = conn.prepareStatement(sqlDetalle)) {
                for (DetalleVenta det : venta.getDetalles()) {
                    stmtDet.setInt(1, idVentaGenerada);
                    stmtDet.setInt(2, det.getIdProducto());
                    stmtDet.setInt(3, det.getCantidad());
                    stmtDet.setBigDecimal(4, det.getPrecioUnitario());
                    stmtDet.addBatch();
                }
                stmtDet.executeBatch();
            }

            conn.commit();
            return idVentaGenerada;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            if ("45000".equals(e.getSQLState())) {
                throw new IllegalStateException("Error de validación de base de datos: " + e.getMessage(), e);
            }
            throw e;
        } catch (RuntimeException e) {
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
     * Consulta el historial de ventas con filtros opcionales de rango de fechas, responsable y método de pago.
     */
    public List<Venta> listarVentas(LocalDate fechaDesde, LocalDate fechaHasta,
                                    Integer idUsuario, String metodoPago) throws SQLException {
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
            WHERE 1=1
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
                    lista.add(mapearVenta(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Consulta los productos correspondientes a una venta específica.
     */
    public List<DetalleVenta> listarDetalles(int idVenta) throws SQLException {
        List<DetalleVenta> lista = new ArrayList<>();
        if (idVenta <= 0) return lista;

        String sql = """
            SELECT dv.id_detalle_venta, dv.id_venta, dv.id_producto,
                   p.codigo AS codigo_producto, p.nombre AS nombre_producto,
                   dv.cantidad, dv.precio_unitario, dv.subtotal,
                   COALESCE(i.stock_actual, 0) AS stock_disponible
            FROM detalle_venta dv
            INNER JOIN productos p ON dv.id_producto = p.id_producto
            LEFT JOIN inventario i ON p.id_producto = i.id_producto
            WHERE dv.id_venta = ?
            ORDER BY dv.id_detalle_venta ASC
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idVenta);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DetalleVenta det = new DetalleVenta(
                        rs.getInt("id_detalle_venta"),
                        rs.getInt("id_venta"),
                        rs.getInt("id_producto"),
                        rs.getString("codigo_producto"),
                        rs.getString("nombre_producto"),
                        rs.getInt("cantidad"),
                        rs.getBigDecimal("precio_unitario"),
                        rs.getBigDecimal("subtotal")
                    );
                    det.setStockDisponible(rs.getInt("stock_disponible"));
                    lista.add(det);
                }
            }
        }
        return lista;
    }

    /**
     * Calcula en tiempo real el consolidado de ventas del día desde los registros reales de MySQL.
     */
    public ResumenVentasDiarias obtenerResumenDiario(LocalDate fecha) throws SQLException {
        LocalDate f = (fecha != null) ? fecha : LocalDate.now();

        String sql = """
            SELECT COUNT(DISTINCT v.id_venta) AS total_ventas,
                   COALESCE(SUM(dv.subtotal), 0) AS total_recaudado,
                   COALESCE(SUM(CASE WHEN v.metodo_pago = 'EFECTIVO' THEN dv.subtotal ELSE 0 END), 0) AS total_efectivo,
                   COALESCE(SUM(CASE WHEN v.metodo_pago IN ('TRANSFERENCIA', 'TARJETA') THEN dv.subtotal ELSE 0 END), 0) AS total_electronico
            FROM ventas v
            INNER JOIN detalle_venta dv ON v.id_venta = dv.id_venta
            WHERE DATE(v.fecha_venta) = ? AND v.estado = 'REGISTRADA'
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, java.sql.Date.valueOf(f));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ResumenVentasDiarias(
                        rs.getInt("total_ventas"),
                        rs.getBigDecimal("total_recaudado"),
                        rs.getBigDecimal("total_efectivo"),
                        rs.getBigDecimal("total_electronico")
                    );
                }
            }
        }

        return new ResumenVentasDiarias(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Busca una venta por su identificador primario e incluye su lista de ítems.
     */
    public Optional<Venta> buscarPorId(int idVenta) throws SQLException {
        if (idVenta <= 0) return Optional.empty();

        String sql = """
            SELECT v.id_venta, v.id_cliente, c.nombre AS nombre_cliente,
                   v.id_usuario, u.nombre AS nombre_usuario, v.fecha_venta,
                   v.metodo_pago, v.estado,
                   COALESCE(SUM(dv.subtotal), 0) AS total,
                   COALESCE(SUM(dv.cantidad), 0) AS total_articulos
            FROM ventas v
            LEFT JOIN clientes c ON v.id_cliente = c.id_cliente
            LEFT JOIN usuarios u ON v.id_usuario = u.id_usuario
            LEFT JOIN detalle_venta dv ON v.id_venta = dv.id_venta
            WHERE v.id_venta = ?
            GROUP BY v.id_venta, v.id_cliente, c.nombre, v.id_usuario, u.nombre,
                     v.fecha_venta, v.metodo_pago, v.estado
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idVenta);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Venta venta = mapearVenta(rs);
                    venta.setDetalles(listarDetalles(idVenta));
                    return Optional.of(venta);
                }
            }
        }
        return Optional.empty();
    }

    private void validarSesion(UserSession sesion) {
        if (sesion == null) {
            throw new SecurityException("Acceso denegado: Se requiere una sesión de usuario activa para registrar ventas.");
        }
        if (sesion.isModoVistaPrevia()) {
            throw new SecurityException("Operación rechazada: El sistema está en Modo Vista Previa (Solo Lectura). No se registran ventas reales.");
        }
    }

    private void validarDatosVenta(Venta venta) {
        if (venta == null) {
            throw new IllegalArgumentException("Los datos de la venta no pueden ser nulos.");
        }
        if (venta.getDetalles() == null || venta.getDetalles().isEmpty()) {
            throw new IllegalArgumentException("La venta debe contener al menos un producto en el carrito.");
        }

        String metodo = venta.getMetodoPago();
        if (metodo == null || (!metodo.equalsIgnoreCase("EFECTIVO") &&
                               !metodo.equalsIgnoreCase("TRANSFERENCIA") &&
                               !metodo.equalsIgnoreCase("TARJETA"))) {
            throw new IllegalArgumentException("Método de pago no válido: " + metodo + ". Permitidos: EFECTIVO, TRANSFERENCIA, TARJETA.");
        }

        for (DetalleVenta det : venta.getDetalles()) {
            if (det.getIdProducto() <= 0) {
                throw new IllegalArgumentException("Uno de los ítems no tiene un producto válido asociado.");
            }
            if (det.getCantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad a vender para '" + det.getNombreProducto() + "' debe ser mayor a cero.");
            }
        }
    }

    private Venta mapearVenta(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("fecha_venta");
        LocalDateTime fecha = (ts != null) ? ts.toLocalDateTime() : null;

        int idCli = rs.getInt("id_cliente");
        Integer idCliente = rs.wasNull() ? null : idCli;

        return new Venta(
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
        );
    }
}
