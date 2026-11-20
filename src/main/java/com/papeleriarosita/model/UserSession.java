package com.papeleriarosita.model;

/**
 * Modelo inmutable para almacenar la sesión activa del usuario autenticado en Papelería Rosita.
 * Roles oficiales:
 * - Rosita: ADMINISTRADORA
 * - Gladys: EMPLEADA
 */
public class UserSession {

    public static final String ROL_ADMINISTRADORA = "ADMINISTRADORA";
    public static final String ROL_EMPLEADA = "EMPLEADA";

    private final int idUsuario;
    private final String username;
    private final String email;
    private final String role;
    private final boolean modoVistaPrevia;

    public UserSession(int idUsuario, String username, String email, String role) {
        this(idUsuario, username, email, role, false);
    }

    public UserSession(int idUsuario, String username, String email, String role, boolean modoVistaPrevia) {
        this.idUsuario = idUsuario;
        this.username = username;
        this.email = email;
        this.role = normalizarRol(role);
        this.modoVistaPrevia = modoVistaPrevia;
    }

    public UserSession(String username, String role) {
        this(0, username, "", role, false);
    }

    /**
     * Construye una sesión especial en Modo Vista Previa (Solo Lectura)
     * para permitir el recorrido de todos los módulos sin conexión activa a MySQL.
     */
    public static UserSession crearModoVistaPrevia(String role) {
        String rolOficial = normalizarRol(role);
        String nombre = ROL_ADMINISTRADORA.equalsIgnoreCase(rolOficial) ? "Rosita (Vista Previa)" : "Gladys (Vista Previa)";
        String email = ROL_ADMINISTRADORA.equalsIgnoreCase(rolOficial) ? "rosita.preview@papeleriarosita.local" : "gladys.preview@papeleriarosita.local";
        return new UserSession(-1, nombre, email, rolOficial, true);
    }

    /**
     * Normaliza los roles provenientes de la base de datos o pantalla a los roles oficiales del proyecto:
     * - 'ADMINISTRADOR', 'ADMINISTRADORA' -> 'ADMINISTRADORA'
     * - 'VENDEDOR', 'VENDEDORA', 'EMPLEADA' -> 'EMPLEADA'
     */
    public static String normalizarRol(String rol) {
        if (rol == null) {
            return "DESCONOCIDO";
        }
        String r = rol.trim().toUpperCase();
        if (r.contains("ADMIN")) {
            return ROL_ADMINISTRADORA;
        }
        if (r.contains("VEND") || r.contains("EMP")) {
            return ROL_EMPLEADA;
        }
        return r;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return ROL_ADMINISTRADORA.equalsIgnoreCase(role);
    }

    public boolean isEmpleado() {
        return ROL_EMPLEADA.equalsIgnoreCase(role);
    }

    public boolean isModoVistaPrevia() {
        return modoVistaPrevia;
    }

    /**
     * Valida que la sesión no esté en modo vista previa.
     * Si está en modo vista previa, bloquea de inmediato la operación de escritura.
     */
    public void validarModoOperativo() {
        if (modoVistaPrevia) {
            throw new SecurityException("Operación no permitida: El sistema se encuentra en Modo Vista Previa (Solo Lectura). No se modifican ni guardan datos reales.");
        }
    }
}
