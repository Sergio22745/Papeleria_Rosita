package com.papeleriarosita.dao;

import com.papeleriarosita.database.ConexionBD;
import com.papeleriarosita.model.Proveedor;
import com.papeleriarosita.model.UserSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Objeto de Acceso a Datos (DAO) para la entidad Proveedor.
 * Administra el catálogo de distribuidores, condiciones comerciales y contactos de abastecimiento.
 *
 * REGLAS DE NEGOCIO Y CONSISTENCIA:
 * 1. El nombre del proveedor es obligatorio (máximo 120 caracteres).
 * 2. No se permiten nombres de proveedores duplicados.
 * 3. Si se proporciona correo electrónico, debe cumplir con un formato válido.
 * 4. Si se proporciona teléfono, debe tener longitud y caracteres válidos.
 * 5. Requiere sesión de usuario autenticada para operaciones de registro y edición.
 */
public class ProveedorDAO {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    private static final Pattern TELEFONO_PATTERN = Pattern.compile(
        "^[0-9+()\\s-]{7,25}$"
    );

    /**
     * Consulta el listado completo de proveedores ordenados alfabéticamente por nombre.
     */
    public List<Proveedor> listarTodos() throws SQLException {
        List<Proveedor> lista = new ArrayList<>();
        String sql = """
            SELECT id_proveedor, nombre, telefono, correo, direccion
            FROM proveedores
            ORDER BY nombre ASC
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                lista.add(mapearProveedor(rs));
            }
        }
        return lista;
    }

    /**
     * Busca un proveedor por su identificador primario.
     */
    public Optional<Proveedor> buscarPorId(int idProveedor) throws SQLException {
        String sql = """
            SELECT id_proveedor, nombre, telefono, correo, direccion
            FROM proveedores
            WHERE id_proveedor = ?
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idProveedor);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearProveedor(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Busca proveedores por coincidencia en nombre o teléfono.
     */
    public List<Proveedor> buscarPorNombre(String criterio) throws SQLException {
        if (criterio == null || criterio.trim().isEmpty()) {
            return listarTodos();
        }

        List<Proveedor> lista = new ArrayList<>();
        String sql = """
            SELECT id_proveedor, nombre, telefono, correo, direccion
            FROM proveedores
            WHERE nombre LIKE ? OR telefono LIKE ?
            ORDER BY nombre ASC
            """;

        String patron = "%" + criterio.trim() + "%";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, patron);
            stmt.setString(2, patron);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearProveedor(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Verifica si ya existe un proveedor registrado con el mismo nombre.
     */
    public boolean existePorNombre(String nombre) throws SQLException {
        if (nombre == null || nombre.trim().isEmpty()) {
            return false;
        }

        String sql = "SELECT COUNT(*) FROM proveedores WHERE LOWER(TRIM(nombre)) = LOWER(TRIM(?))";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nombre.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Verifica si existe otro proveedor con el mismo nombre excluyendo un ID (para edición).
     */
    public boolean existePorNombreExcluyendoId(String nombre, int idProveedor) throws SQLException {
        if (nombre == null || nombre.trim().isEmpty()) {
            return false;
        }

        String sql = """
            SELECT COUNT(*)
            FROM proveedores
            WHERE LOWER(TRIM(nombre)) = LOWER(TRIM(?))
              AND id_proveedor <> ?
            """;
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nombre.trim());
            stmt.setInt(2, idProveedor);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Registra un nuevo proveedor en la base de datos MySQL.
     *
     * @param proveedor Datos del proveedor a registrar
     * @param sesion Sesión del usuario autenticado que realiza la operación
     * @return Identificador autogenerado del nuevo proveedor
     */
    public int registrarProveedor(Proveedor proveedor, UserSession sesion)
            throws SQLException, SecurityException, IllegalArgumentException {

        // 1. Control de acceso
        validarSesionYPermisos(sesion, "registrar");

        // 2. Validaciones de negocio
        validarDatosProveedor(proveedor);

        // 3. Comprobar duplicidad de nombre
        if (existePorNombre(proveedor.getNombre())) {
            throw new IllegalArgumentException(
                "Ya existe un proveedor registrado con el nombre '" + proveedor.getNombre().trim() + "'."
            );
        }

        String sql = """
            INSERT INTO proveedores (nombre, telefono, correo, direccion)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, proveedor.getNombre().trim());
            stmt.setString(2, normalizarTexto(proveedor.getTelefono()));
            stmt.setString(3, normalizarTexto(proveedor.getCorreo()));
            stmt.setString(4, normalizarTexto(proveedor.getDireccion()));

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int idGenerado = rs.getInt(1);
                    proveedor.setIdProveedor(idGenerado);
                    return idGenerado;
                }
            }
            throw new SQLException("No se pudo obtener el identificador generado para el proveedor.");
        }
    }

    /**
     * Actualiza la información de un proveedor existente en MySQL.
     *
     * @param proveedor Datos actualizados del proveedor
     * @param sesion Sesión del usuario autenticado
     * @return true si se actualizó el registro
     */
    public boolean editarProveedor(Proveedor proveedor, UserSession sesion)
            throws SQLException, SecurityException, IllegalArgumentException {

        // 1. Control de acceso
        validarSesionYPermisos(sesion, "editar");

        if (proveedor == null || proveedor.getIdProveedor() <= 0) {
            throw new IllegalArgumentException("El identificador del proveedor a editar no es válido.");
        }

        // 2. Validaciones de negocio
        validarDatosProveedor(proveedor);

        // 3. Comprobar duplicidad de nombre excluyendo el registro actual
        if (existePorNombreExcluyendoId(proveedor.getNombre(), proveedor.getIdProveedor())) {
            throw new IllegalArgumentException(
                "Ya existe otro proveedor registrado con el nombre '" + proveedor.getNombre().trim() + "'."
            );
        }

        String sql = """
            UPDATE proveedores
            SET nombre = ?, telefono = ?, correo = ?, direccion = ?
            WHERE id_proveedor = ?
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, proveedor.getNombre().trim());
            stmt.setString(2, normalizarTexto(proveedor.getTelefono()));
            stmt.setString(3, normalizarTexto(proveedor.getCorreo()));
            stmt.setString(4, normalizarTexto(proveedor.getDireccion()));
            stmt.setInt(5, proveedor.getIdProveedor());

            int filas = stmt.executeUpdate();
            return filas > 0;
        }
    }

    // =========================================================================
    // VALIDACIONES INTERNAS
    // =========================================================================

    private void validarSesionYPermisos(UserSession sesion, String accion) {
        if (sesion == null || sesion.getIdUsuario() <= 0) {
            throw new SecurityException("Acceso denegado: Se requiere una sesión de usuario autenticada.");
        }
        if (!sesion.isAdmin() && !sesion.isEmpleado()) {
            throw new SecurityException("Acceso restringido: El rol '" + sesion.getRole() + "' no está autorizado para " + accion + " proveedores.");
        }
    }

    private void validarDatosProveedor(Proveedor proveedor) {
        if (proveedor == null) {
            throw new IllegalArgumentException("Los datos del proveedor no pueden ser nulos.");
        }

        String nombre = proveedor.getNombre();
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del proveedor es obligatorio.");
        }
        if (nombre.trim().length() > 120) {
            throw new IllegalArgumentException("El nombre del proveedor no puede exceder los 120 caracteres.");
        }

        String telefono = proveedor.getTelefono();
        if (telefono != null && !telefono.trim().isEmpty()) {
            if (telefono.trim().length() > 25) {
                throw new IllegalArgumentException("El teléfono no puede exceder los 25 caracteres.");
            }
            if (!TELEFONO_PATTERN.matcher(telefono.trim()).matches()) {
                throw new IllegalArgumentException("El teléfono ingresado tiene un formato no válido (solo se permiten números, espacios, guiones o prefijos +).");
            }
        }

        String correo = proveedor.getCorreo();
        if (correo != null && !correo.trim().isEmpty()) {
            if (correo.trim().length() > 120) {
                throw new IllegalArgumentException("El correo electrónico no puede exceder los 120 caracteres.");
            }
            if (!EMAIL_PATTERN.matcher(correo.trim()).matches()) {
                throw new IllegalArgumentException("El formato del correo electrónico es inválido (ejemplo: contacto@editorial.com).");
            }
        }

        String direccion = proveedor.getDireccion();
        if (direccion != null && direccion.trim().length() > 200) {
            throw new IllegalArgumentException("La dirección no puede exceder los 200 caracteres.");
        }
    }

    private String normalizarTexto(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        return valor.trim();
    }

    private Proveedor mapearProveedor(ResultSet rs) throws SQLException {
        return new Proveedor(
            rs.getInt("id_proveedor"),
            rs.getString("nombre"),
            rs.getString("telefono"),
            rs.getString("correo"),
            rs.getString("direccion")
        );
    }
}
