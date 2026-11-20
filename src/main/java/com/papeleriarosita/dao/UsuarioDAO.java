package com.papeleriarosita.dao;

import com.papeleriarosita.database.ConexionBD;
import com.papeleriarosita.model.UserSession;
import com.papeleriarosita.model.Usuario;
import com.papeleriarosita.util.SeguridadUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Objeto de Acceso a Datos (DAO) para la entidad Usuario en la tabla 'usuarios' de MySQL.
 *
 * ROLES EN BASE DE DATOS (restricción CHECK existente):
 *   - 'ADMINISTRADOR'  → se almacena en MySQL, se interpreta en Java como ADMINISTRADORA
 *   - 'VENDEDOR'       → se almacena en MySQL, se interpreta en Java como EMPLEADA
 *
 * No se persisten los valores 'ADMINISTRADORA' ni 'EMPLEADA' en la columna 'rol' porque
 * el CHECK de la tabla solo acepta 'ADMINISTRADOR' y 'VENDEDOR'.
 * La normalización a los roles oficiales del proyecto ocurre en UserSession.normalizarRol().
 */
public class UsuarioDAO {

    // Valores exactos aceptados por el CHECK de la tabla 'usuarios' en MySQL
    private static final String ROL_BD_ADMIN    = "ADMINISTRADOR";
    private static final String ROL_BD_VENDEDOR = "VENDEDOR";

    // =========================================================================
    // CONSULTAS DE ESTADO INICIAL
    // =========================================================================

    /**
     * Comprueba si la tabla 'usuarios' está completamente vacía (0 registros de cualquier tipo).
     * Esta es la condición que habilita el formulario público de configuración inicial.
     *
     * @return true si no hay ningún usuario; false si ya existe al menos uno
     * @throws SQLException Si ocurre un error de comunicación con MySQL
     */
    public boolean estaVacia() throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM usuarios";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("total") == 0;
            }
        }
        return true;
    }

    /**
     * Verifica si ya existe al menos un usuario con rol de Administrador en la tabla.
     * Comprueba únicamente el valor 'ADMINISTRADOR' (el único que MySQL acepta y almacena).
     *
     * @return true si ya existe un Administrador; false en caso contrario
     * @throws SQLException Si ocurre un error de comunicación con MySQL
     */
    public boolean existeAdministradora() throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM usuarios WHERE rol = 'ADMINISTRADOR'";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("total") > 0;
            }
        }
        return false;
    }

    /**
     * Comprueba si un correo electrónico ya está registrado en la tabla.
     */
    public boolean existeCorreo(String correo) throws SQLException {
        if (correo == null || correo.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT COUNT(*) AS total FROM usuarios WHERE LOWER(correo) = LOWER(?)";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, correo.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total") > 0;
                }
            }
        }
        return false;
    }

    // =========================================================================
    // REGISTRO DE LA PRIMERA ADMINISTRADORA
    // =========================================================================

    /**
     * Registra la PRIMERA y ÚNICA cuenta de Administradora durante la configuración inicial.
     *
     * Protecciones implementadas contra condiciones de carrera:
     * 1. Toda la operación se ejecuta dentro de una única transacción con autoCommit=false.
     * 2. Se adquiere un bloqueo exclusivo en la tabla 'usuarios' (LOCK TABLES) antes de verificar
     *    e insertar, impidiendo que dos hilos simultáneos creen dos administradoras.
     * 3. Se verifica que la tabla esté COMPLETAMENTE VACÍA dentro de la misma transacción,
     *    no sólo que no exista otra administradora.
     * 4. El rol almacenado es 'ADMINISTRADOR' (único valor aceptado por el CHECK de MySQL).
     *
     * @param nombre          Nombre de la administradora (ej. "Rosita")
     * @param correo          Correo electrónico único
     * @param contrasenaPlana Contraseña en texto plano a proteger
     * @return Objeto Usuario creado
     * @throws SQLException          Si ocurre un error en la base de datos
     * @throws IllegalStateException Si ya existe cualquier usuario (tabla no vacía)
     * @throws IllegalArgumentException Si los datos ingresados no son válidos
     */
    public Usuario registrarPrimeraAdministradora(String nombre, String correo, String contrasenaPlana)
            throws SQLException {

        validarCamposRegistro(nombre, correo, contrasenaPlana);

        // Hashear ANTES de abrir la transacción para no mantener el lock durante 600k iteraciones
        String hashSeguro = SeguridadUtil.hashearContrasena(contrasenaPlana);

        Connection conn = null;
        try {
            conn = ConexionBD.getConnection();
            conn.setAutoCommit(false);

            // Bloqueo exclusivo a nivel de tabla para prevenir inserciones concurrentes
            try (Statement lockStmt = conn.createStatement()) {
                lockStmt.execute("LOCK TABLES usuarios WRITE");
            }

            try {
                // Verificación atómica: la tabla debe estar COMPLETAMENTE VACÍA
                int totalUsuarios = 0;
                try (PreparedStatement countStmt = conn.prepareStatement(
                        "SELECT COUNT(*) AS total FROM usuarios")) {
                    try (ResultSet rs = countStmt.executeQuery()) {
                        if (rs.next()) {
                            totalUsuarios = rs.getInt("total");
                        }
                    }
                }

                if (totalUsuarios > 0) {
                    throw new IllegalStateException(
                        "Acción bloqueada: La tabla de usuarios no está vacía. " +
                        "La configuración inicial solo está disponible cuando no existe ningún usuario registrado."
                    );
                }

                // Verificar correo duplicado dentro de la misma transacción
                try (PreparedStatement correoStmt = conn.prepareStatement(
                        "SELECT COUNT(*) AS total FROM usuarios WHERE LOWER(correo) = LOWER(?)")) {
                    correoStmt.setString(1, correo.trim());
                    try (ResultSet rs = correoStmt.executeQuery()) {
                        if (rs.next() && rs.getInt("total") > 0) {
                            throw new IllegalArgumentException(
                                "El correo electrónico '" + correo.trim() + "' ya se encuentra registrado."
                            );
                        }
                    }
                }

                // Insertar con rol 'ADMINISTRADOR' (único valor válido por el CHECK de MySQL)
                String sqlInsert = """
                    INSERT INTO usuarios (nombre, correo, contrasena, rol)
                    VALUES (?, ?, ?, 'ADMINISTRADOR')
                    """;

                int idGenerado = 0;
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        sqlInsert, Statement.RETURN_GENERATED_KEYS)) {

                    insertStmt.setString(1, nombre.trim());
                    insertStmt.setString(2, correo.trim());
                    insertStmt.setString(3, hashSeguro);

                    int filas = insertStmt.executeUpdate();
                    if (filas == 0) {
                        throw new SQLException("No se pudo registrar la administradora en la base de datos.");
                    }

                    try (ResultSet keys = insertStmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            idGenerado = keys.getInt(1);
                        }
                    }
                }

                conn.commit();
                return new Usuario(idGenerado, nombre.trim(), correo.trim(), hashSeguro, ROL_BD_ADMIN, LocalDateTime.now());

            } finally {
                // Liberar el bloqueo de tabla siempre, independientemente del resultado
                try (Statement unlockStmt = conn.createStatement()) {
                    unlockStmt.execute("UNLOCK TABLES");
                }
            }

        } catch (IllegalStateException | IllegalArgumentException e) {
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

    // =========================================================================
    // REGISTRO AUTORIZADO DE EMPLEADA
    // =========================================================================

    /**
     * Registra la cuenta de una Empleada (ej. Gladys) como función exclusiva de la Administradora.
     * El rol almacenado en MySQL es 'VENDEDOR' (único valor válido para empleadas por el CHECK).
     *
     * @param nombre          Nombre de la empleada (ej. "Gladys")
     * @param correo          Correo electrónico de la empleada
     * @param contrasenaPlana Contraseña asignada
     * @param sesionActual    Sesión activa que DEBE pertenecer a la Administradora
     * @return Objeto Usuario de la empleada creada
     * @throws SQLException          Si ocurre un error en la base de datos
     * @throws SecurityException     Si quien ejecuta la acción no tiene rol de Administradora
     * @throws IllegalArgumentException Si los datos ingresados no son válidos
     */
    public Usuario registrarEmpleado(String nombre, String correo, String contrasenaPlana, UserSession sesionActual)
            throws SQLException, SecurityException {

        // Verificación de autorización: solo la Administradora puede registrar empleados
        if (sesionActual == null || !sesionActual.isAdmin()) {
            throw new SecurityException(
                "Acceso denegado: Solo la Administradora (Rosita) tiene autorización para registrar cuentas de empleados."
            );
        }

        validarCamposRegistro(nombre, correo, contrasenaPlana);

        if (existeCorreo(correo)) {
            throw new IllegalArgumentException(
                "El correo electrónico '" + correo.trim() + "' ya está registrado por otro usuario."
            );
        }

        // Hashear con PBKDF2WithHmacSHA256 y 600.000 iteraciones
        String hashSeguro = SeguridadUtil.hashearContrasena(contrasenaPlana);

        // Almacenar con rol 'VENDEDOR' (único valor válido para empleadas por el CHECK de MySQL)
        String sql = """
            INSERT INTO usuarios (nombre, correo, contrasena, rol)
            VALUES (?, ?, ?, 'VENDEDOR')
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, nombre.trim());
            stmt.setString(2, correo.trim());
            stmt.setString(3, hashSeguro);

            int filas = stmt.executeUpdate();
            if (filas == 0) {
                throw new SQLException("No se pudo registrar la cuenta de empleada en la base de datos.");
            }

            int idGenerado = 0;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    idGenerado = keys.getInt(1);
                }
            }

            return new Usuario(idGenerado, nombre.trim(), correo.trim(), hashSeguro, ROL_BD_VENDEDOR, LocalDateTime.now());
        }
    }

    // =========================================================================
    // AUTENTICACIÓN
    // =========================================================================

    /**
     * Autentica a un usuario mediante sus credenciales (correo electrónico y contraseña).
     * La contraseña se recupera de la BD y se verifica en Java con SeguridadUtil.
     * No se incluye la contraseña en ninguna comparación SQL.
     *
     * @param correo     Correo electrónico registrado del usuario
     * @param contrasena Contraseña en texto plano ingresada en la pantalla de inicio de sesión
     * @return Optional con el Usuario si las credenciales son válidas; Optional.empty() si no coinciden
     * @throws SQLException Si ocurre un error de comunicación con MySQL
     */
    public Optional<Usuario> autenticarPorCredenciales(String correo, String contrasena) throws SQLException {
        if (correo == null || correo.trim().isEmpty() || contrasena == null || contrasena.isEmpty()) {
            return Optional.empty();
        }

        // SQL: solo busca por correo — la contraseña NUNCA se envía ni compara en SQL
        String sql = """
            SELECT id_usuario, nombre, correo, contrasena, rol, fecha_registro
            FROM usuarios
            WHERE LOWER(correo) = LOWER(?)
            LIMIT 1
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, correo.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hashAlmacenado = rs.getString("contrasena");
                    // Verificación criptográfica en Java, resistente a ataques de tiempo
                    if (SeguridadUtil.verificarContrasena(contrasena, hashAlmacenado)) {
                        return Optional.of(mapearUsuario(rs));
                    }
                }
            }
        }

        return Optional.empty();
    }

    // =========================================================================
    // CONSULTAS
    // =========================================================================

    /**
     * Busca un usuario por su clave primaria id_usuario.
     */
    public Optional<Usuario> buscarPorId(int idUsuario) throws SQLException {
        String sql = """
            SELECT id_usuario, nombre, correo, contrasena, rol, fecha_registro
            FROM usuarios
            WHERE id_usuario = ?
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idUsuario);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearUsuario(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Busca un usuario por su correo electrónico.
     */
    public Optional<Usuario> buscarPorCorreo(String correo) throws SQLException {
        if (correo == null || correo.trim().isEmpty()) {
            return Optional.empty();
        }

        String sql = """
            SELECT id_usuario, nombre, correo, contrasena, rol, fecha_registro
            FROM usuarios
            WHERE LOWER(correo) = LOWER(?)
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, correo.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearUsuario(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Obtiene la lista de todos los usuarios registrados en el sistema.
     */
    public List<Usuario> listarTodos() throws SQLException {
        List<Usuario> usuarios = new ArrayList<>();
        String sql = """
            SELECT id_usuario, nombre, correo, contrasena, rol, fecha_registro
            FROM usuarios
            ORDER BY id_usuario ASC
            """;

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                usuarios.add(mapearUsuario(rs));
            }
        }

        return usuarios;
    }

    // =========================================================================
    // UTILIDADES PRIVADAS
    // =========================================================================

    private void validarCamposRegistro(String nombre, String correo, String contrasenaPlana) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío.");
        }
        if (correo == null || correo.trim().isEmpty() || !correo.contains("@")) {
            throw new IllegalArgumentException("El correo electrónico ingresado no tiene un formato válido.");
        }
        if (contrasenaPlana == null || contrasenaPlana.length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
        }
    }

    /**
     * Mapea un registro del ResultSet a un objeto de dominio Usuario.
     * El campo 'rol' conserva el valor de la BD ('ADMINISTRADOR' o 'VENDEDOR').
     * La conversión a roles oficiales de la aplicación ('ADMINISTRADORA' / 'EMPLEADA')
     * ocurre posteriormente en UserSession.normalizarRol().
     */
    private Usuario mapearUsuario(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("fecha_registro");
        LocalDateTime fechaRegistro = (ts != null) ? ts.toLocalDateTime() : null;

        return new Usuario(
            rs.getInt("id_usuario"),
            rs.getString("nombre"),
            rs.getString("correo"),
            rs.getString("contrasena"),
            rs.getString("rol"),        // 'ADMINISTRADOR' o 'VENDEDOR'
            fechaRegistro
        );
    }
}
