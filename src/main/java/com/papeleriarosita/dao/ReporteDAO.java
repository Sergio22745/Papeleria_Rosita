package com.papeleriarosita.dao;

import com.papeleriarosita.database.ConexionBD;
import com.papeleriarosita.model.ProductoMasVendido;
import com.papeleriarosita.model.ResumenComprasReporte;
import com.papeleriarosita.model.ResumenInventarioReporte;
import com.papeleriarosita.model.UserSession;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Objeto de Acceso a Datos (DAO) para el Módulo de Reportes de Papelería Rosita.
 * Realiza consultas analíticas y consolidados en tiempo real contra MySQL 8.4
 * sobre Ventas, Inventario, Compras y Caja.
 *
 * REGLAS DE NEGOCIO Y CONSISTENCIA:
 * 1. Excluye estrictamente las ventas con estado 'ANULADA' de los resultados financieros.
 * 2. Consulta datos reales de MySQL (sin datos ficticios ni simulados).
 * 3. Valida la sesión del usuario autenticado.
 * 4. La valorización del inventario se computa sobre stock_actual * precio_venta oficial.
 */
public class ReporteDAO {

    private static volatile Boolean columnaRecibidaExiste = null;

    /**
     * =========================================================================
     * GENERACIÓN DE REPORTES Y CONSULTAS ANALÍTICAS (SUSTENTACIÓN)
     * =========================================================================
     * ¿Qué hace este método?
     * Obtiene el ranking de los productos más vendidos en un rango de fechas.
     *
     * ¿Cómo explicar al docente cómo se generan los reportes en el sistema?
     * 1. CONSULTA SQL CON AGREGACIÓN:
     *    - Une 'detalle_venta', 'ventas', 'productos' y 'categorias'.
     *    - Utiliza 'SUM(dv.cantidad)' para total de unidades y 'SUM(dv.subtotal)'
     *      para total recaudado en dinero.
     * 2. REGLA DE CONSISTENCIA FINANCIERA:
     *    - Filtra obligatoriamente con 'WHERE v.estado = 'REGISTRADA''.
     *    - De esta forma, las ventas ANULADAS quedan 100% excluidas y no inflan
     *      las ganancias de la papelería ni falsean los libros contables.
     * 3. VINCULACIÓN CON JAVAFX:
     *    - Los resultados se encapsulan en DTOs (Data Transfer Objects) como
     *      'ProductoMasVendido' y son consumidos por 'ReportesController'
     *      para renderizar tablas y gráficas dinámicas (BarChart y PieChart).
     * =========================================================================
     */
    public List<ProductoMasVendido> obtenerProductosMasVendidos(LocalDate fechaDesde,
                                                               LocalDate fechaHasta,
                                                               Integer idCategoria,
                                                               int limite) throws SQLException {
        List<ProductoMasVendido> lista = new ArrayList<>();
        int max = (limite > 0) ? limite : 20;

        StringBuilder sql = new StringBuilder("""
            SELECT p.id_producto, p.codigo, p.nombre, c.nombre AS categoria,
                   COALESCE(SUM(dv.cantidad), 0) AS cantidad_vendida,
                   COALESCE(SUM(dv.subtotal), 0) AS total_recaudado
            FROM detalle_venta dv
            INNER JOIN ventas v ON dv.id_venta = v.id_venta
            INNER JOIN productos p ON dv.id_producto = p.id_producto
            INNER JOIN categorias c ON p.id_categoria = c.id_categoria
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

        if (idCategoria != null && idCategoria > 0) {
            sql.append(" AND c.id_categoria = ?");
            params.add(idCategoria);
        }

        sql.append("""
            GROUP BY p.id_producto, p.codigo, p.nombre, c.nombre
            ORDER BY cantidad_vendida DESC, total_recaudado DESC
            LIMIT ?
            """);
        params.add(max);

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(new ProductoMasVendido(
                        rs.getInt("id_producto"),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getString("categoria"),
                        rs.getInt("cantidad_vendida"),
                        rs.getBigDecimal("total_recaudado")
                    ));
                }
            }
        }
        return lista;
    }

    /**
     * Consulta las ventas consolidadas por mes para un año específico (para gráficos de barras).
     * Retorna un mapa del 1 al 12 con el total recaudado en cada mes.
     */
    public Map<Integer, BigDecimal> obtenerVentasMensuales(int anio) throws SQLException {
        Map<Integer, BigDecimal> resultado = new LinkedHashMap<>();
        for (int mes = 1; mes <= 12; mes++) {
            resultado.put(mes, BigDecimal.ZERO);
        }

        String sql = """
            SELECT MONTH(v.fecha_venta) AS mes,
                   COALESCE(SUM(dv.subtotal), 0) AS total
            FROM ventas v
            INNER JOIN detalle_venta dv ON v.id_venta = dv.id_venta
            WHERE v.estado = 'REGISTRADA'
              AND YEAR(v.fecha_venta) = ?
            GROUP BY MONTH(v.fecha_venta)
            ORDER BY mes ASC
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, anio);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int mes = rs.getInt("mes");
                    BigDecimal total = rs.getBigDecimal("total");
                    resultado.put(mes, total != null ? total : BigDecimal.ZERO);
                }
            }
        }
        return resultado;
    }

    /**
     * Obtiene el total recaudado agrupado por método de pago (para gráfico circular).
     */
    public Map<String, BigDecimal> obtenerDistribucionMetodosPago(LocalDate fechaDesde,
                                                                  LocalDate fechaHasta) throws SQLException {
        Map<String, BigDecimal> resultado = new LinkedHashMap<>();

        StringBuilder sql = new StringBuilder("""
            SELECT v.metodo_pago,
                   COALESCE(SUM(dv.subtotal), 0) AS total
            FROM ventas v
            INNER JOIN detalle_venta dv ON v.id_venta = dv.id_venta
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

        sql.append(" GROUP BY v.metodo_pago ORDER BY total DESC");

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String metodo = rs.getString("metodo_pago");
                    BigDecimal total = rs.getBigDecimal("total");
                    resultado.put(metodo, total != null ? total : BigDecimal.ZERO);
                }
            }
        }
        return resultado;
    }

    /**
     * Calcula los KPIs cuantitativos generales de inventario actual en MySQL.
     */
    public ResumenInventarioReporte obtenerResumenInventario() throws SQLException {
        String sql = """
            SELECT 
                COUNT(*) AS total_productos,
                COALESCE(SUM(i.stock_actual), 0) AS total_stock,
                COALESCE(SUM(CASE WHEN i.stock_actual > i.stock_minimo THEN 1 ELSE 0 END), 0) AS disponibles,
                COALESCE(SUM(CASE WHEN i.stock_actual > 0 AND i.stock_actual <= i.stock_minimo THEN 1 ELSE 0 END), 0) AS bajo_stock,
                COALESCE(SUM(CASE WHEN i.stock_actual = 0 THEN 1 ELSE 0 END), 0) AS agotados,
                COALESCE(SUM(i.stock_actual * p.precio_venta), 0) AS valor_inventario
            FROM productos p
            INNER JOIN inventario i ON p.id_producto = i.id_producto
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return new ResumenInventarioReporte(
                    rs.getInt("total_productos"),
                    rs.getInt("total_stock"),
                    rs.getInt("disponibles"),
                    rs.getInt("bajo_stock"),
                    rs.getInt("agotados"),
                    rs.getBigDecimal("valor_inventario")
                );
            }
        }
        return new ResumenInventarioReporte();
    }

    /**
     * Obtiene la distribución de unidades de inventario agrupadas por categoría (para gráfico circular).
     */
    public Map<String, Integer> obtenerDistribucionStockCategorias() throws SQLException {
        Map<String, Integer> resultado = new LinkedHashMap<>();

        String sql = """
            SELECT c.nombre AS categoria,
                   COALESCE(SUM(i.stock_actual), 0) AS unidades
            FROM categorias c
            LEFT JOIN productos p ON c.id_categoria = p.id_categoria
            LEFT JOIN inventario i ON p.id_producto = i.id_producto
            GROUP BY c.id_categoria, c.nombre
            HAVING unidades > 0
            ORDER BY unidades DESC
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                resultado.put(rs.getString("categoria"), rs.getInt("unidades"));
            }
        }
        return resultado;
    }

    /**
     * Calcula los KPIs generales del módulo de compras en un rango de fechas.
     */
    public ResumenComprasReporte obtenerResumenCompras(LocalDate fechaDesde,
                                                      LocalDate fechaHasta) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                COUNT(c.id_compra) AS total_compras,
                COALESCE(SUM(CASE WHEN c.estado != 'ANULADA' THEN tot.total_compra ELSE 0 END), 0) AS total_monto,
                COALESCE(SUM(CASE WHEN c.estado = 'REGISTRADA' THEN 1 ELSE 0 END), 0) AS cant_registrada,
                COALESCE(SUM(CASE WHEN c.estado = 'PARCIAL' THEN 1 ELSE 0 END), 0) AS cant_parcial,
                COALESCE(SUM(CASE WHEN c.estado = 'RECIBIDA' THEN 1 ELSE 0 END), 0) AS cant_recibida,
                COALESCE(SUM(CASE WHEN c.estado = 'ANULADA' THEN 1 ELSE 0 END), 0) AS cant_anulada
            FROM compras c
            LEFT JOIN (
                SELECT id_compra, COALESCE(SUM(subtotal), 0) AS total_compra
                FROM detalle_compra
                GROUP BY id_compra
            ) tot ON c.id_compra = tot.id_compra
            WHERE 1=1
            """);

        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND c.fecha_compra >= ?");
            params.add(Timestamp.valueOf(fechaDesde.atStartOfDay()));
        }

        if (fechaHasta != null) {
            sql.append(" AND c.fecha_compra <= ?");
            params.add(Timestamp.valueOf(fechaHasta.atTime(23, 59, 59)));
        }

        int totalCompras = 0;
        BigDecimal totalMonto = BigDecimal.ZERO;
        int registradas = 0, parciales = 0, recibidas = 0, anuladas = 0;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    totalCompras = rs.getInt("total_compras");
                    totalMonto = rs.getBigDecimal("total_monto");
                    registradas = rs.getInt("cant_registrada");
                    parciales = rs.getInt("cant_parcial");
                    recibidas = rs.getInt("cant_recibida");
                    anuladas = rs.getInt("cant_anulada");
                }
            }
        }

        // Consultar unidades solicitadas y recibidas
        int unidadesSolicitadas = 0;
        int unidadesRecibidas = 0;

        try (Connection conn = ConexionBD.getConnection()) {
            boolean tieneColumnaRecibida = checkColumnaRecibidaExiste(conn);

            StringBuilder sqlUnidades = new StringBuilder();
            if (tieneColumnaRecibida) {
                sqlUnidades.append("""
                    SELECT COALESCE(SUM(dc.cantidad), 0) AS solicitadas,
                           COALESCE(SUM(dc.cantidad_recibida), 0) AS recibidas
                    FROM detalle_compra dc
                    INNER JOIN compras c ON dc.id_compra = c.id_compra
                    WHERE c.estado != 'ANULADA'
                    """);
            } else {
                sqlUnidades.append("""
                    SELECT COALESCE(SUM(dc.cantidad), 0) AS solicitadas,
                           0 AS recibidas
                    FROM detalle_compra dc
                    INNER JOIN compras c ON dc.id_compra = c.id_compra
                    WHERE c.estado != 'ANULADA'
                    """);
            }

            List<Object> paramsUnidades = new ArrayList<>();
            if (fechaDesde != null) {
                sqlUnidades.append(" AND c.fecha_compra >= ?");
                paramsUnidades.add(Timestamp.valueOf(fechaDesde.atStartOfDay()));
            }
            if (fechaHasta != null) {
                sqlUnidades.append(" AND c.fecha_compra <= ?");
                paramsUnidades.add(Timestamp.valueOf(fechaHasta.atTime(23, 59, 59)));
            }

            try (PreparedStatement stmtU = conn.prepareStatement(sqlUnidades.toString())) {
                for (int i = 0; i < paramsUnidades.size(); i++) {
                    stmtU.setObject(i + 1, paramsUnidades.get(i));
                }
                try (ResultSet rsU = stmtU.executeQuery()) {
                    if (rsU.next()) {
                        unidadesSolicitadas = rsU.getInt("solicitadas");
                        unidadesRecibidas = rsU.getInt("recibidas");
                    }
                }
            }
        }

        return new ResumenComprasReporte(
            totalCompras,
            totalMonto != null ? totalMonto : BigDecimal.ZERO,
            registradas,
            parciales,
            recibidas,
            anuladas,
            unidadesSolicitadas,
            unidadesRecibidas
        );
    }

    /**
     * Obtiene el monto invertido en compras agrupado por proveedor (para gráficos).
     */
    public Map<String, BigDecimal> obtenerDistribucionComprasProveedor(LocalDate fechaDesde,
                                                                       LocalDate fechaHasta) throws SQLException {
        Map<String, BigDecimal> resultado = new LinkedHashMap<>();

        StringBuilder sql = new StringBuilder("""
            SELECT p.nombre AS proveedor,
                   COALESCE(SUM(dc.subtotal), 0) AS total
            FROM compras c
            INNER JOIN proveedores p ON c.id_proveedor = p.id_proveedor
            LEFT JOIN detalle_compra dc ON c.id_compra = dc.id_compra
            WHERE c.estado != 'ANULADA'
            """);

        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND c.fecha_compra >= ?");
            params.add(Timestamp.valueOf(fechaDesde.atStartOfDay()));
        }

        if (fechaHasta != null) {
            sql.append(" AND c.fecha_compra <= ?");
            params.add(Timestamp.valueOf(fechaHasta.atTime(23, 59, 59)));
        }

        sql.append("""
            GROUP BY p.id_proveedor, p.nombre
            ORDER BY total DESC
            LIMIT 10
            """);

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String prov = rs.getString("proveedor");
                    BigDecimal total = rs.getBigDecimal("total");
                    resultado.put(prov, total != null ? total : BigDecimal.ZERO);
                }
            }
        }
        return resultado;
    }

    private boolean checkColumnaRecibidaExiste(Connection conn) {
        if (columnaRecibidaExiste != null) {
            return columnaRecibidaExiste;
        }
        synchronized (ReporteDAO.class) {
            if (columnaRecibidaExiste == null) {
                try {
                    DatabaseMetaData meta = conn.getMetaData();
                    try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, "detalle_compra", "cantidad_recibida")) {
                        columnaRecibidaExiste = rs.next();
                    }
                } catch (SQLException e) {
                    columnaRecibidaExiste = false;
                }
            }
        }
        return columnaRecibidaExiste;
    }

    public void validarSesion(UserSession sesion) {
        if (sesion == null) {
            throw new SecurityException("Operación rechazada: No hay una sesión de usuario activa en el sistema.");
        }
    }
}
