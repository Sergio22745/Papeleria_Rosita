package com.papeleriarosita.dao;

import com.papeleriarosita.database.ConexionBD;
import com.papeleriarosita.model.Producto;
import com.papeleriarosita.model.UserSession;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Objeto de Acceso a Datos (DAO) para la entidad Producto.
 * Administra el catálogo de artículos, códigos, categorías y precios de venta.
 *
 * REGLAS DE NEGOCIO:
 * 1. El precio de venta lo establece manualmente Rosita (Administradora).
 * 2. Al registrar un producto nuevo, el trigger 'trg_producto_crear_inventario'
 *    en MySQL crea automáticamente la fila en la tabla 'inventario' con stock 0.
 *    NO se debe insertar manualmente una segunda fila en 'inventario'.
 * 3. Valida unicidad de código y campos obligatorios antes de enviar a MySQL.
 */
public class ProductoDAO {

    /**
     * Consulta todos los productos con su respectiva categoría y existencias de inventario.
     */
    public List<Producto> listarTodos() throws SQLException {
        List<Producto> lista = new ArrayList<>();
        String sql = """
            SELECT p.id_producto, p.codigo, p.nombre, p.descripcion, p.id_categoria,
                   c.nombre AS nombre_categoria, p.precio_venta, p.activo,
                   COALESCE(i.stock_actual, 0) AS stock_actual,
                   COALESCE(i.stock_minimo, 0) AS stock_minimo
            FROM productos p
            INNER JOIN categorias c ON p.id_categoria = c.id_categoria
            LEFT JOIN inventario i ON p.id_producto = i.id_producto
            ORDER BY p.nombre ASC
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                lista.add(mapearProducto(rs));
            }
        }
        return lista;
    }

    /**
     * Busca productos por coincidencia en código o nombre.
     */
    public List<Producto> buscarPorNombreOCodigo(String criterio) throws SQLException {
        if (criterio == null || criterio.trim().isEmpty()) {
            return listarTodos();
        }

        List<Producto> lista = new ArrayList<>();
        String sql = """
            SELECT p.id_producto, p.codigo, p.nombre, p.descripcion, p.id_categoria,
                   c.nombre AS nombre_categoria, p.precio_venta, p.activo,
                   COALESCE(i.stock_actual, 0) AS stock_actual,
                   COALESCE(i.stock_minimo, 0) AS stock_minimo
            FROM productos p
            INNER JOIN categorias c ON p.id_categoria = c.id_categoria
            LEFT JOIN inventario i ON p.id_producto = i.id_producto
            WHERE LOWER(p.codigo) LIKE LOWER(?) OR LOWER(p.nombre) LIKE LOWER(?)
            ORDER BY p.nombre ASC
            """;

        String patron = "%" + criterio.trim() + "%";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, patron);
            stmt.setString(2, patron);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearProducto(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Busca un producto por su clave primaria.
     */
    public Optional<Producto> buscarPorId(int idProducto) throws SQLException {
        String sql = """
            SELECT p.id_producto, p.codigo, p.nombre, p.descripcion, p.id_categoria,
                   c.nombre AS nombre_categoria, p.precio_venta, p.activo,
                   COALESCE(i.stock_actual, 0) AS stock_actual,
                   COALESCE(i.stock_minimo, 0) AS stock_minimo
            FROM productos p
            INNER JOIN categorias c ON p.id_categoria = c.id_categoria
            LEFT JOIN inventario i ON p.id_producto = i.id_producto
            WHERE p.id_producto = ?
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idProducto);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearProducto(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Comprueba si un código de producto ya está en uso.
     */
    public boolean existeCodigo(String codigo) throws SQLException {
        if (codigo == null || codigo.trim().isEmpty()) return false;
        String sql = "SELECT COUNT(*) AS total FROM productos WHERE LOWER(TRIM(codigo)) = LOWER(TRIM(?))";

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codigo);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total") > 0;
                }
            }
        }
        return false;
    }

    /**
     * Comprueba si un código de producto ya está en uso por otro registro distinto.
     */
    public boolean existeCodigoEnOtroProducto(String codigo, int idProductoActual) throws SQLException {
        if (codigo == null || codigo.trim().isEmpty()) return false;
        String sql = "SELECT COUNT(*) AS total FROM productos WHERE LOWER(TRIM(codigo)) = LOWER(TRIM(?)) AND id_producto <> ?";

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codigo);
            stmt.setInt(2, idProductoActual);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total") > 0;
                }
            }
        }
        return false;
    }

    /**
     * Registra un nuevo producto en la base de datos.
     *
     * IMPORTANTE SOBRE EL TRIGGER DE INVENTARIO:
     * Al insertar en 'productos', el trigger 'trg_producto_crear_inventario'
     * crea automáticamente la fila en 'inventario' con stock_actual = 0 y stock_minimo = 0.
     * Esta función NUNCA realiza INSERT en la tabla 'inventario'.
     * Si se especificó un stock mínimo inicial > 0, se realiza un UPDATE posterior.
     *
     * @param producto Objeto con los datos del producto a registrar
     * @param stockMinimoInicial Stock mínimo a configurar
     * @param sesion Sesión activa que realiza la operación
     * @return Producto creado con su ID asignado
     */
    public Producto registrar(Producto producto, int stockMinimoInicial, UserSession sesion) throws SQLException {
        validarProducto(producto);

        // Regla: Rosita (Administradora) establece los precios
        if (sesion != null && !sesion.isAdmin()) {
            throw new SecurityException("Acceso restringido: Solo la Administradora (Rosita) puede registrar productos y establecer sus precios.");
        }

        if (existeCodigo(producto.getCodigo())) {
            throw new IllegalArgumentException("El código de producto '" + producto.getCodigo().trim() + "' ya está registrado.");
        }

        String sqlInsert = """
            INSERT INTO productos (codigo, nombre, descripcion, id_categoria, precio_venta, activo)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        Connection conn = null;
        try {
            conn = ConexionBD.getConnection();
            conn.setAutoCommit(false); // Transacción para consistencia

            int idGenerado = 0;
            try (PreparedStatement stmt = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, producto.getCodigo().trim());
                stmt.setString(2, producto.getNombre().trim());
                stmt.setString(3, producto.getDescripcion() != null ? producto.getDescripcion().trim() : null);
                stmt.setInt(4, producto.getIdCategoria());
                stmt.setBigDecimal(5, producto.getPrecioVenta());
                stmt.setInt(6, producto.isActivo() ? 1 : 0);

                int filas = stmt.executeUpdate();
                if (filas == 0) {
                    throw new SQLException("No se pudo insertar el producto en la base de datos.");
                }

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        idGenerado = keys.getInt(1);
                    }
                }
            }

            // El trigger 'trg_producto_crear_inventario' ya insertó la fila en 'inventario'.
            // Si el usuario especificó un stock mínimo inicial > 0, lo actualizamos:
            if (stockMinimoInicial > 0 && idGenerado > 0) {
                String sqlUpdateStockMin = "UPDATE inventario SET stock_minimo = ? WHERE id_producto = ?";
                try (PreparedStatement stmtStock = conn.prepareStatement(sqlUpdateStockMin)) {
                    stmtStock.setInt(1, stockMinimoInicial);
                    stmtStock.setInt(2, idGenerado);
                    stmtStock.executeUpdate();
                }
            }

            conn.commit();

            producto.setIdProducto(idGenerado);
            producto.setStockActual(0);
            producto.setStockMinimo(Math.max(0, stockMinimoInicial));
            return producto;

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
     * Actualiza los datos de un producto existente.
     * Rosita establece y modifica el precio de venta; ningún cálculo automático lo altera.
     */
    public void actualizar(Producto producto, UserSession sesion) throws SQLException {
        if (producto.getIdProducto() <= 0) {
            throw new IllegalArgumentException("ID de producto no válido para actualización.");
        }
        validarProducto(producto);

        // Si la sesión no es administradora, verificar si intentó cambiar el precio de venta
        if (sesion != null && !sesion.isAdmin()) {
            Optional<Producto> existente = buscarPorId(producto.getIdProducto());
            if (existente.isPresent() && existente.get().getPrecioVenta().compareTo(producto.getPrecioVenta()) != 0) {
                throw new SecurityException("Acceso restringido: Solo la Administradora (Rosita) puede modificar el precio de venta de los productos.");
            }
        }

        if (existeCodigoEnOtroProducto(producto.getCodigo(), producto.getIdProducto())) {
            throw new IllegalArgumentException("El código '" + producto.getCodigo().trim() + "' ya está siendo usado por otro producto.");
        }

        String sql = """
            UPDATE productos
            SET codigo = ?, nombre = ?, descripcion = ?, id_categoria = ?, precio_venta = ?, activo = ?
            WHERE id_producto = ?
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, producto.getCodigo().trim());
            stmt.setString(2, producto.getNombre().trim());
            stmt.setString(3, producto.getDescripcion() != null ? producto.getDescripcion().trim() : null);
            stmt.setInt(4, producto.getIdCategoria());
            stmt.setBigDecimal(5, producto.getPrecioVenta());
            stmt.setInt(6, producto.isActivo() ? 1 : 0);
            stmt.setInt(7, producto.getIdProducto());

            int filas = stmt.executeUpdate();
            if (filas == 0) {
                throw new SQLException("No se encontró el producto con ID " + producto.getIdProducto() + " para actualizar.");
            }
        }
    }

    /**
     * Activa o inactiva un producto en el sistema.
     */
    public void cambiarEstadoActivo(int idProducto, boolean activo) throws SQLException {
        String sql = "UPDATE productos SET activo = ? WHERE id_producto = ?";

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, activo ? 1 : 0);
            stmt.setInt(2, idProducto);

            int filas = stmt.executeUpdate();
            if (filas == 0) {
                throw new SQLException("No se encontró el producto con ID " + idProducto);
            }
        }
    }

    // =========================================================================
    // VALIDACIONES Y MAPEO
    // =========================================================================

    private void validarProducto(Producto p) {
        if (p == null) {
            throw new IllegalArgumentException("Los datos del producto no pueden ser nulos.");
        }
        if (p.getCodigo() == null || p.getCodigo().trim().isEmpty()) {
            throw new IllegalArgumentException("El código del producto es obligatorio.");
        }
        if (p.getCodigo().length() > 40) {
            throw new IllegalArgumentException("El código del producto no puede exceder 40 caracteres.");
        }
        if (p.getNombre() == null || p.getNombre().trim().length() < 2) {
            throw new IllegalArgumentException("El nombre del producto debe contener al menos 2 caracteres.");
        }
        if (p.getNombre().length() > 120) {
            throw new IllegalArgumentException("El nombre del producto no puede exceder 120 caracteres.");
        }
        if (p.getIdCategoria() <= 0) {
            throw new IllegalArgumentException("Debes asociar el producto a una categoría válida.");
        }
        if (p.getPrecioVenta() == null || p.getPrecioVenta().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio de venta debe ser mayor o igual a 0.");
        }
    }

    private Producto mapearProducto(ResultSet rs) throws SQLException {
        return new Producto(
            rs.getInt("id_producto"),
            rs.getString("codigo"),
            rs.getString("nombre"),
            rs.getString("descripcion"),
            rs.getInt("id_categoria"),
            rs.getString("nombre_categoria"),
            rs.getBigDecimal("precio_venta"),
            rs.getInt("activo") == 1,
            rs.getInt("stock_actual"),
            rs.getInt("stock_minimo")
        );
    }
}
