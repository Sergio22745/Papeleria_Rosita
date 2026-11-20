package com.papeleriarosita.dao;

import com.papeleriarosita.database.ConexionBD;
import com.papeleriarosita.model.Compra;
import com.papeleriarosita.model.DetalleCompra;
import com.papeleriarosita.model.DetalleRecepcionCompra;
import com.papeleriarosita.model.RecepcionCompra;
import com.papeleriarosita.model.UserSession;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
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
 * Objeto de Acceso a Datos (DAO) para el Módulo de Compras y Abastecimiento.
 * Administra órdenes de compra, control de pedidos pendientes, consulta de saldos
 * y recepción física de mercancía al inventario (entregas parciales).
 *
 * REGLA FUNDAMENTAL DE INVENTARIO:
 * 1. Registrar una orden de compra NO aumenta el inventario.
 * 2. El inventario aumenta exclusivamente cuando se registra la recepción física de mercancía.
 * 3. Se admiten entregas parciales; no se puede recibir más de lo solicitado.
 */
public class CompraDAO {

    // Caché de detección dinámica de capacidades de base de datos
    private static volatile Boolean columnaRecibidaExiste = null;
    private static volatile Boolean tablaRecepcionesExiste = null;

    /**
     * Consulta el listado de compras con filtros opcionales por proveedor, estado y rango de fechas.
     */
    public List<Compra> listarCompras(Integer idProveedorFiltro, String estadoFiltro,
                                      LocalDate fechaDesde, LocalDate fechaHasta) throws SQLException {
        List<Compra> lista = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT c.id_compra, c.id_proveedor, p.nombre AS nombre_proveedor,
                   c.id_usuario, u.nombre AS nombre_usuario, c.fecha_compra,
                   COALESCE((SELECT SUM(dc.subtotal) FROM detalle_compra dc WHERE dc.id_compra = c.id_compra), 0) AS total,
                   c.estado
            FROM compras c
            INNER JOIN proveedores p ON c.id_proveedor = p.id_proveedor
            INNER JOIN usuarios u ON c.id_usuario = u.id_usuario
            WHERE 1=1
            """);

        List<Object> parametros = new ArrayList<>();

        if (idProveedorFiltro != null && idProveedorFiltro > 0) {
            sql.append(" AND c.id_proveedor = ?");
            parametros.add(idProveedorFiltro);
        }

        if (estadoFiltro != null && !estadoFiltro.trim().isEmpty() && !"TODOS".equalsIgnoreCase(estadoFiltro.trim())) {
            sql.append(" AND c.estado = ?");
            parametros.add(estadoFiltro.trim().toUpperCase());
        }

        if (fechaDesde != null) {
            sql.append(" AND c.fecha_compra >= ?");
            parametros.add(Timestamp.valueOf(fechaDesde.atStartOfDay()));
        }

        if (fechaHasta != null) {
            sql.append(" AND c.fecha_compra <= ?");
            parametros.add(Timestamp.valueOf(fechaHasta.atTime(23, 59, 59)));
        }

        sql.append(" ORDER BY c.fecha_compra DESC, c.id_compra DESC");

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parametros.size(); i++) {
                stmt.setObject(i + 1, parametros.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearCompra(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Busca una compra por su identificador primario e incluye la lista de sus detalles.
     */
    public Optional<Compra> buscarPorId(int idCompra) throws SQLException {
        if (idCompra <= 0) return Optional.empty();

        String sql = """
            SELECT c.id_compra, c.id_proveedor, p.nombre AS nombre_proveedor,
                   c.id_usuario, u.nombre AS nombre_usuario, c.fecha_compra,
                   COALESCE((SELECT SUM(dc.subtotal) FROM detalle_compra dc WHERE dc.id_compra = c.id_compra), 0) AS total,
                   c.estado
            FROM compras c
            INNER JOIN proveedores p ON c.id_proveedor = p.id_proveedor
            INNER JOIN usuarios u ON c.id_usuario = u.id_usuario
            WHERE c.id_compra = ?
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idCompra);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Compra compra = mapearCompra(rs);
                    compra.setDetalles(listarDetalles(idCompra));
                    return Optional.of(compra);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Consulta los ítems asociados a una orden de compra, con cantidades solicitadas,
     * recibidas y pendientes calculadas en tiempo real.
     */
    public List<DetalleCompra> listarDetalles(int idCompra) throws SQLException {
        List<DetalleCompra> lista = new ArrayList<>();
        if (idCompra <= 0) return lista;

        try (Connection conn = ConexionBD.getConnection()) {
            boolean tieneColumnaRecibida = checkColumnaRecibidaExiste(conn);

            String sql;
            if (tieneColumnaRecibida) {
                sql = """
                    SELECT dc.id_detalle_compra, dc.id_compra, dc.id_producto,
                           p.codigo AS codigo_producto, p.nombre AS nombre_producto,
                           dc.cantidad, dc.costo_unitario, dc.subtotal,
                           dc.cantidad_recibida
                    FROM detalle_compra dc
                    INNER JOIN productos p ON dc.id_producto = p.id_producto
                    WHERE dc.id_compra = ?
                    ORDER BY dc.id_detalle_compra ASC
                    """;
            } else {
                sql = """
                    SELECT dc.id_detalle_compra, dc.id_compra, dc.id_producto,
                           p.codigo AS codigo_producto, p.nombre AS nombre_producto,
                           dc.cantidad, dc.costo_unitario, dc.subtotal,
                           0 AS cantidad_recibida
                    FROM detalle_compra dc
                    INNER JOIN productos p ON dc.id_producto = p.id_producto
                    WHERE dc.id_compra = ?
                    ORDER BY dc.id_detalle_compra ASC
                    """;
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, idCompra);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        lista.add(new DetalleCompra(
                            rs.getInt("id_detalle_compra"),
                            rs.getInt("id_compra"),
                            rs.getInt("id_producto"),
                            rs.getString("codigo_producto"),
                            rs.getString("nombre_producto"),
                            rs.getInt("cantidad"),
                            rs.getBigDecimal("costo_unitario"),
                            rs.getBigDecimal("subtotal"),
                            rs.getInt("cantidad_recibida")
                        ));
                    }
                }
            }
        }
        return lista;
    }

    /**
     * =========================================================================
     * 1. REGISTRO DE ORDEN DE COMPRA (PEDIDO PENDIENTE)
     * =========================================================================
     * ¿Qué hace este método?
     * Guarda una orden de pedido dirigida a un proveedor en estado 'REGISTRADA'.
     *
     * ¿Cómo explicarlo en la sustentación?
     * - REGLA FUNDAMENTAL DE INVENTARIO: Registrar una orden de compra NO aumenta
     *   las existencias en bodega, porque la mercancía todavía no ha sido entregada
     *   por el camión o distribuidor.
     * - Guarda la cabecera en 'compras' con estado 'REGISTRADA' y los ítems
     *   pactados en 'detalle_compra' con su costo unitario negociado.
     * =========================================================================
     */
    public int registrarOrdenCompra(Compra compra, UserSession sesion) throws SQLException {
        validarSesion(sesion);
        validarDatosOrdenCompra(compra);

        compra.recalcularTotal();

        String sqlCompra = """
            INSERT INTO compras (id_proveedor, id_usuario, fecha_compra, estado)
            VALUES (?, ?, CURRENT_TIMESTAMP, ?)
            """;

        Connection conn = null;
        try {
            conn = ConexionBD.getConnection();
            conn.setAutoCommit(false);

            boolean tieneColumnaRecibida = checkColumnaRecibidaExiste(conn);

            int idCompraGenerado;
            try (PreparedStatement stmtCompra = conn.prepareStatement(sqlCompra, Statement.RETURN_GENERATED_KEYS)) {
                stmtCompra.setInt(1, compra.getIdProveedor());
                stmtCompra.setInt(2, sesion.getIdUsuario());
                stmtCompra.setString(3, "REGISTRADA");

                stmtCompra.executeUpdate();

                try (ResultSet rsKeys = stmtCompra.getGeneratedKeys()) {
                    if (rsKeys.next()) {
                        idCompraGenerado = rsKeys.getInt(1);
                    } else {
                        throw new SQLException("No se pudo obtener el identificador generado para la compra.");
                    }
                }
            }

            // Insertar detalles
            String sqlDetalle;
            if (tieneColumnaRecibida) {
                sqlDetalle = """
                    INSERT INTO detalle_compra (id_compra, id_producto, cantidad, costo_unitario, cantidad_recibida)
                    VALUES (?, ?, ?, ?, 0)
                    """;
            } else {
                sqlDetalle = """
                    INSERT INTO detalle_compra (id_compra, id_producto, cantidad, costo_unitario)
                    VALUES (?, ?, ?, ?)
                    """;
            }

            try (PreparedStatement stmtDetalle = conn.prepareStatement(sqlDetalle)) {
                for (DetalleCompra det : compra.getDetalles()) {
                    stmtDetalle.setInt(1, idCompraGenerado);
                    stmtDetalle.setInt(2, det.getIdProducto());
                    stmtDetalle.setInt(3, det.getCantidad());
                    stmtDetalle.setBigDecimal(4, det.getPrecioUnitario()); // mapped to costo_unitario
                    // subtotal is a generated column – do NOT include it as a parameter
                    stmtDetalle.addBatch();
                }
                stmtDetalle.executeBatch();
            }

            conn.commit();
            return idCompraGenerado;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            // Diagnóstico amigable si el trigger antiguo bloquea órdenes 'REGISTRADA'
            if ("45000".equals(e.getSQLState()) || e.getMessage().contains("trg_compra_validar_estado")) {
                throw new IllegalStateException(
                    "Operación detenida por validación de base de datos: El esquema actual de MySQL " +
                    "requiere la migración de la Etapa 4 ('etapa4_migracion.sql') para permitir pedidos " +
                    "en estado 'REGISTRADA' sin afectar inventario inmediatamente. Detalle: " + e.getMessage(), e
                );
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
     * Anula una orden de compra registrada que aún no tenga recepciones físicas.
     */
    public void anularCompra(int idCompra, String motivo, UserSession sesion) throws SQLException {
        validarSesion(sesion);
        if (idCompra <= 0) {
            throw new IllegalArgumentException("Identificador de compra no válido.");
        }

        Optional<Compra> opt = buscarPorId(idCompra);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("La compra #" + idCompra + " no existe.");
        }

        Compra compra = opt.get();
        if ("ANULADA".equalsIgnoreCase(compra.getEstado())) {
            throw new IllegalStateException("La compra #" + idCompra + " ya se encuentra anulada.");
        }

        if (compra.getTotalCantidadRecibida() > 0 || "PARCIAL".equalsIgnoreCase(compra.getEstado()) || "RECIBIDA".equalsIgnoreCase(compra.getEstado())) {
            throw new IllegalStateException(
                "Operación rechazada: No es posible anular la compra #" + idCompra +
                " porque ya cuenta con unidades físicas recibidas e ingresadas al inventario."
            );
        }

        String sql = "UPDATE compras SET estado = 'ANULADA' WHERE id_compra = ?";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idCompra);
            int filas = stmt.executeUpdate();
            if (filas == 0) {
                throw new SQLException("No se pudo actualizar el estado de la compra #" + idCompra);
            }
        }
    }

    /**
     * =========================================================================
     * 2. REGISTRO DE RECEPCIÓN FÍSICA DE MERCANCÍA (ENTRADA AL INVENTARIO)
     * =========================================================================
     * ¿Qué hace este método?
     * Da entrada formal en bodega a los artículos que llegaron físicamente con su
     * número de guía o remisión comercial. Admite entregas parciales y completas.
     *
     * ¿Cómo explicarlo paso a paso en la sustentación?
     * 1. VERIFICACIÓN Y BLOQUEO:
     *    - Bloquea la compra con 'FOR UPDATE' en MySQL para asegurar que no esté
     *      ANULADA ni previamente RECIBIDA en su totalidad.
     *
     * 2. INSERCIÓN DE REMISIÓN:
     *    - Inserta en 'recepciones_compra' con el número de guía, fecha y usuario.
     *
     * 3. INSERCIÓN DE DETALLE Y DISPARADORES DE MYSQL:
     *    - Inserta en 'detalle_recepcion_compra' cada ítem y cantidad recibida.
     *    - ¡ARQUITECTURA DE RESPONSABILIDAD ÚNICA!:
     *      Java NO actualiza manualmente el stock ni el kárdex.
     *      Al insertar en 'detalle_recepcion_compra', los triggers de MySQL se
     *      activan en cascada automática:
     *      a) 'trg_recepcion_validar_cantidad': Valida que la cantidad recibida
     *         no supere el saldo pendiente de entrega de la orden.
     *      b) 'trg_recepcion_actualizar_inventario': Incrementa 'stock_actual'
     *         en 'inventario', registra el asiento 'ENTRADA' en el Kárdex
     *         ('movimientos_inventario') y acumula 'cantidad_recibida' en 'detalle_compra'.
     *      c) 'trg_recepcion_actualizar_estado_compra': Si todos los productos
     *         se recibieron completos, cambia la compra a 'RECIBIDA'. Si faltan
     *         unidades por llegar, la cambia a 'PARCIAL'.
     * =========================================================================
     */
    public int registrarRecepcionFisica(int idCompra, String numeroRemision, String observaciones,
                                        List<DetalleRecepcionCompra> entregas, UserSession sesion) throws SQLException {
        validarSesion(sesion);
        if (idCompra <= 0) {
            throw new IllegalArgumentException("Identificador de compra no válido.");
        }
        if (entregas == null || entregas.isEmpty()) {
            throw new IllegalArgumentException("Debe ingresar al menos un ítem para registrar la recepción.");
        }

        // Verificar si la base de datos ya cuenta con las tablas de la Etapa 4
        Connection conn = null;
        try {
            conn = ConexionBD.getConnection();

            if (!checkTablaRecepcionesExiste(conn) || !checkColumnaRecibidaExiste(conn)) {
                throw new IllegalStateException(
                    "MIGRACIÓN PENDIENTE: La recepción física de compras con entregas parciales requiere " +
                    "la ejecución previa del script 'etapa4_migracion.sql' en MySQL (tablas 'recepciones_compra' " +
                    "y columna 'cantidad_recibida' en 'detalle_compra')."
                );
            }

            conn.setAutoCommit(false);

            // 1. Bloqueo y verificación de la compra
            String sqlCheckCompra = "SELECT estado FROM compras WHERE id_compra = ? FOR UPDATE";
            String estadoActual;
            try (PreparedStatement stmtCheck = conn.prepareStatement(sqlCheckCompra)) {
                stmtCheck.setInt(1, idCompra);
                try (ResultSet rs = stmtCheck.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("La compra #" + idCompra + " no existe.");
                    }
                    estadoActual = rs.getString("estado");
                }
            }

            if ("ANULADA".equalsIgnoreCase(estadoActual)) {
                throw new IllegalStateException("Operación bloqueada: No se pueden registrar recepciones en una compra ANULADA.");
            }
            if ("RECIBIDA".equalsIgnoreCase(estadoActual)) {
                throw new IllegalStateException("Operación bloqueada: La compra ya fue RECIBIDA en su totalidad y está cerrada.");
            }

            // 2. Insertar cabecera de recepción
            int idRecepcionGenerado;
            String sqlRecep = """
                INSERT INTO recepciones_compra (id_compra, id_usuario, fecha_recepcion, numero_remision, observaciones)
                VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?)
                """;
            try (PreparedStatement stmtRecep = conn.prepareStatement(sqlRecep, Statement.RETURN_GENERATED_KEYS)) {
                stmtRecep.setInt(1, idCompra);
                stmtRecep.setInt(2, sesion.getIdUsuario());
                stmtRecep.setString(3, numeroRemision != null ? numeroRemision.trim() : null);
                stmtRecep.setString(4, observaciones != null ? observaciones.trim() : null);
                stmtRecep.executeUpdate();

                try (ResultSet rsK = stmtRecep.getGeneratedKeys()) {
                    if (rsK.next()) {
                        idRecepcionGenerado = rsK.getInt(1);
                    } else {
                        throw new SQLException("No se pudo obtener el identificador de la recepción.");
                    }
                }
            }

            // 3. Procesar cada detalle recibido
            // NOTA DE ARQUITECTURA ETAPA 4:
            // La inserción en 'detalle_recepcion_compra' dispara automáticamente los triggers en MySQL:
            //  - trg_recepcion_validar_cantidad: valida saldo pendiente con bloqueo FOR UPDATE.
            //  - trg_recepcion_actualizar_inventario: actualiza cantidad_recibida en detalle_compra,
            //    incrementa stock_actual en inventario y genera el registro en movimientos_inventario (kárdex).
            //  - trg_recepcion_actualizar_estado_compra: actualiza el estado de la compra a PARCIAL o RECIBIDA.
            // Por lo tanto, Java NO debe realizar actualizaciones manuales redundantes de inventario ni kárdex.
            String sqlDetRecep = """
                INSERT INTO detalle_recepcion_compra (id_recepcion, id_detalle_compra, id_producto, cantidad_recibida, observaciones)
                VALUES (?, ?, ?, ?, ?)
                """;

            try (PreparedStatement stmtDetRecep = conn.prepareStatement(sqlDetRecep)) {
                for (DetalleRecepcionCompra item : entregas) {
                    if (item.getCantidadRecibida() <= 0) continue;

                    // Insertar detalle de recepción (los triggers de MySQL ejecutan las actualizaciones de stock y kárdex)
                    stmtDetRecep.setInt(1, idRecepcionGenerado);
                    stmtDetRecep.setInt(2, item.getIdDetalleCompra());
                    stmtDetRecep.setInt(3, item.getIdProducto());
                    stmtDetRecep.setInt(4, item.getCantidadRecibida());
                    stmtDetRecep.setString(5, item.getObservaciones());
                    stmtDetRecep.executeUpdate();
                }
            }

            conn.commit();
            return idRecepcionGenerado;

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
     * Consulta el historial de recepciones físicas de una compra.
     */
    public List<RecepcionCompra> listarRecepciones(int idCompra) throws SQLException {
        List<RecepcionCompra> lista = new ArrayList<>();
        if (idCompra <= 0) return lista;

        try (Connection conn = ConexionBD.getConnection()) {
            if (!checkTablaRecepcionesExiste(conn)) {
                return lista;
            }

            String sql = """
                SELECT r.id_recepcion, r.id_compra, r.id_usuario, u.nombre AS nombre_usuario,
                       r.fecha_recepcion, r.numero_remision, r.observaciones
                FROM recepciones_compra r
                INNER JOIN usuarios u ON r.id_usuario = u.id_usuario
                WHERE r.id_compra = ?
                ORDER BY r.fecha_recepcion DESC
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, idCompra);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Timestamp ts = rs.getTimestamp("fecha_recepcion");
                        LocalDateTime fecha = (ts != null) ? ts.toLocalDateTime() : null;

                        lista.add(new RecepcionCompra(
                            rs.getInt("id_recepcion"),
                            rs.getInt("id_compra"),
                            rs.getInt("id_usuario"),
                            rs.getString("nombre_usuario"),
                            fecha,
                            rs.getString("numero_remision"),
                            rs.getString("observaciones")
                        ));
                    }
                }
            }
        }
        return lista;
    }

    /**
     * Verifica dinámicamente si la columna 'cantidad_recibida' existe en 'detalle_compra'.
     */
    public boolean isColumnaCantidadRecibidaDisponible() {
        try (Connection conn = ConexionBD.getConnection()) {
            return checkColumnaRecibidaExiste(conn);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Verifica dinámicamente si las estructuras de recepciones (Etapa 4) están presentes en MySQL.
     */
    public boolean isEstructuraRecepcionesDisponible() {
        try (Connection conn = ConexionBD.getConnection()) {
            return checkTablaRecepcionesExiste(conn) && checkColumnaRecibidaExiste(conn);
        } catch (SQLException e) {
            return false;
        }
    }

    // =========================================================================
    // MÉTODOS AUXILIARES DE VALIDACIÓN Y DETECCIÓN
    // =========================================================================

    private boolean checkColumnaRecibidaExiste(Connection conn) {
        if (columnaRecibidaExiste != null) return columnaRecibidaExiste;
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, "detalle_compra", "cantidad_recibida")) {
                columnaRecibidaExiste = rs.next();
            }
        } catch (SQLException e) {
            columnaRecibidaExiste = false;
        }
        return columnaRecibidaExiste != null && columnaRecibidaExiste;
    }

    private boolean checkTablaRecepcionesExiste(Connection conn) {
        if (tablaRecepcionesExiste != null) return tablaRecepcionesExiste;
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "recepciones_compra", new String[]{"TABLE"})) {
                tablaRecepcionesExiste = rs.next();
            }
        } catch (SQLException e) {
            tablaRecepcionesExiste = false;
        }
        return tablaRecepcionesExiste != null && tablaRecepcionesExiste;
    }

    private void validarSesion(UserSession sesion) {
        if (sesion == null) {
            throw new SecurityException("Acceso denegado: Se requiere una sesión de usuario activa para operar en compras.");
        }
        if (sesion.isModoVistaPrevia()) {
            throw new SecurityException("Operación rechazada: El sistema está en Modo Vista Previa (Solo Lectura). No se registran órdenes ni recepciones.");
        }
    }

    private void validarDatosOrdenCompra(Compra compra) {
        if (compra == null) {
            throw new IllegalArgumentException("Los datos de la compra no pueden ser nulos.");
        }
        if (compra.getIdProveedor() <= 0) {
            throw new IllegalArgumentException("Debe seleccionar un proveedor existente para asociar la compra.");
        }
        if (compra.getDetalles() == null || compra.getDetalles().isEmpty()) {
            throw new IllegalArgumentException("La orden de compra debe contener al menos un producto.");
        }

        for (DetalleCompra det : compra.getDetalles()) {
            if (det.getIdProducto() <= 0) {
                throw new IllegalArgumentException("Uno de los ítems no tiene un producto válido asociado.");
            }
            if (det.getCantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad solicitada para el producto '" + det.getNombreProducto() + "' debe ser mayor a cero.");
            }
            if (det.getPrecioUnitario() == null || det.getPrecioUnitario().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El costo unitario para el producto '" + det.getNombreProducto() + "' no puede ser negativo.");
            }
        }
    }

    private Compra mapearCompra(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("fecha_compra");
        LocalDateTime fecha = (ts != null) ? ts.toLocalDateTime() : null;

        return new Compra(
            rs.getInt("id_compra"),
            rs.getInt("id_proveedor"),
            rs.getString("nombre_proveedor"),
            rs.getInt("id_usuario"),
            rs.getString("nombre_usuario"),
            fecha,
            rs.getBigDecimal("total"),
            rs.getString("estado")
        );
    }
}
