package com.papeleriarosita.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * =============================================================================
 * GESTOR CENTRALIZADO DE CONEXIÓN A BASE DE DATOS (JDBC) - PAPELERÍA ROSITA
 * =============================================================================
 * Responsabilidad:
 * Administra y suministra las conexiones JDBC activas hacia el servidor MySQL
 * para la base de datos 'papeleria_rosita'.
 *
 * ¿CÓMO EXPLICARLO EN LA SUSTENTACIÓN?
 * 1. ¿Cómo se conecta la aplicación con MySQL?
 *    A través de la API estándar de Java JDBC (Java Database Connectivity) y el
 *    driver oficial Connector/J (com.mysql.cj.jdbc.Driver).
 *
 * 2. ¿Cómo protege el proyecto las contraseñas para GitHub?
 *    Implementa una JERARQUÍA DE SEGURIDAD de menor a mayor prioridad:
 *    a) Valores por defecto en código (localhost:3306).
 *    b) Plantilla base en el classpath ('/database.properties' sin contraseñas).
 *    c) Archivo externo local ('database.local.properties' en la raíz, ignorado
 *       por .gitignore). Aquí es donde cada desarrollador coloca su clave privada.
 *    d) Variables de entorno del sistema operativo (PAPELERIA_DB_USER,
 *       PAPELERIA_DB_PASSWORD) ideales para servidores de despliegue.
 *
 * 3. ¿Por qué no se comparten conexiones globales?
 *    Cada operación solicita una nueva conexión mediante getConnection() y la
 *    cierra inmediatamente al terminar (usando try-with-resources o bloques finally),
 *    evitando fugas de memoria o bloqueos de sockets en el servidor MySQL.
 * =============================================================================
 */
public class ConexionBD {

    private static final String CLASSPATH_CONFIG = "/database.properties";
    private static final String LOCAL_CONFIG_FILENAME = "database.local.properties";

    private static String host = "localhost";
    private static String port = "3306";
    private static String database = "papeleria_rosita";
    private static String user = "root";
    private static String password = "";
    private static String url;
    private static String origenConfiguracion = "Valores predeterminados";

    static {
        cargarConfiguracion();
    }

    /**
     * Carga y unifica los parámetros de conexión respetando la jerarquía de seguridad.
     * Se ejecuta de forma estática una sola vez al cargar la clase en memoria.
     */
    public static void cargarConfiguracion() {
        // 1. Cargar valores base desde el classpath (plantilla empaquetada en el JAR)
        try (InputStream is = ConexionBD.class.getResourceAsStream(CLASSPATH_CONFIG)) {
            if (is != null) {
                Properties baseProps = new Properties();
                baseProps.load(is);
                if (baseProps.containsKey("db.host")) host = baseProps.getProperty("db.host").trim();
                if (baseProps.containsKey("db.port")) port = baseProps.getProperty("db.port").trim();
                if (baseProps.containsKey("db.name")) database = baseProps.getProperty("db.name").trim();
                if (baseProps.containsKey("db.user")) user = baseProps.getProperty("db.user").trim();
                // Nota de seguridad: la contraseña NO debe almacenarse en el classpath
                if (baseProps.containsKey("db.password")) password = baseProps.getProperty("db.password").trim();
                origenConfiguracion = "Classpath (" + CLASSPATH_CONFIG + ")";
            }
        } catch (IOException e) {
            System.err.println("[ADVERTENCIA ConexionBD] No se pudo leer '" + CLASSPATH_CONFIG + "': " + e.getMessage());
        }

        // 2. Cargar desde archivo externo local (fuera del empaquetado JAR y protegido en .gitignore)
        File localFile = buscarArchivoConfiguracionLocal();

        if (localFile != null && localFile.exists() && localFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                Properties localProps = new Properties();
                localProps.load(fis);
                if (localProps.containsKey("db.host")) host = localProps.getProperty("db.host").trim();
                if (localProps.containsKey("db.port")) port = localProps.getProperty("db.port").trim();
                if (localProps.containsKey("db.name")) database = localProps.getProperty("db.name").trim();
                if (localProps.containsKey("db.user")) user = localProps.getProperty("db.user").trim();
                if (localProps.containsKey("db.password")) {
                    String passProp = localProps.getProperty("db.password");
                    password = (passProp != null) ? passProp.trim() : "";
                }
                origenConfiguracion = "Archivo local externo (" + localFile.getAbsolutePath() + ")";
            } catch (IOException e) {
                System.err.println("[ADVERTENCIA ConexionBD] Error al leer archivo local '" + localFile.getAbsolutePath() + "': " + e.getMessage());
            }
        }

        // 3. Sobrescritura mediante variables de entorno del sistema operativo (máxima prioridad)
        String envHost = System.getenv("PAPELERIA_DB_HOST");
        if (envHost != null && !envHost.isBlank()) {
            host = envHost.trim();
            origenConfiguracion = "Variables de entorno";
        }
        String envPort = System.getenv("PAPELERIA_DB_PORT");
        if (envPort != null && !envPort.isBlank()) {
            port = envPort.trim();
            origenConfiguracion = "Variables de entorno";
        }
        String envName = System.getenv("PAPELERIA_DB_NAME");
        if (envName != null && !envName.isBlank()) {
            database = envName.trim();
            origenConfiguracion = "Variables de entorno";
        }
        String envUser = System.getenv("PAPELERIA_DB_USER");
        if (envUser != null && !envUser.isBlank()) {
            user = envUser.trim();
            origenConfiguracion = "Variables de entorno";
        }
        String envPass = System.getenv("PAPELERIA_DB_PASSWORD");
        if (envPass != null) {
            password = envPass;
            origenConfiguracion = "Variables de entorno";
        }

        // Construcción de la URL JDBC optimizada para MySQL 8.4+
        // - useSSL=false: conexión local sin certificado SSL forzado
        // - allowPublicKeyRetrieval=true: permite autenticación caching_sha2_password de MySQL 8
        // - serverTimezone=America/Bogota: sincroniza fechas y horas con Colombia
        // - characterEncoding=UTF-8: soporta tildes, ñ y caracteres especiales
        url = String.format(
            "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Bogota&characterEncoding=UTF-8",
            host, port, database
        );
    }

    /**
     * Obtiene una nueva conexión activa con la base de datos MySQL.
     *
     * ¿Cuándo se invoca?
     * Cada vez que un DAO (VentaDAO, CompraDAO, ProductoDAO, etc.) necesita
     * ejecutar consultas SELECT o transacciones INSERT/UPDATE.
     *
     * @return Connection abierta y lista para operar
     * @throws SQLException si falla la autenticación (error 1045) o el servidor está apagado
     */
    public static Connection getConnection() throws SQLException {
        try {
            Connection conn = DriverManager.getConnection(url, user, password);

            System.out.println("========== DIAGNÓSTICO DE CONEXIÓN ==========");
            System.out.println("Base de datos: " + conn.getCatalog());
            System.out.println("Servidor: " + conn.getMetaData().getURL());
            System.out.println("Usuario: " + conn.getMetaData().getUserName());
            System.out.println("Configuración: " + origenConfiguracion);
            System.out.println("=============================================");

            return conn;

        } catch (SQLException e) {
            String mensajeDetallado = interpretarError(e);
            throw new SQLException(mensajeDetallado, e.getSQLState(), e.getErrorCode(), e);
        }
    }

    /**
     * Comprueba si la conexión con la base de datos funciona correctamente.
     * Abre y cierra la conexión de inmediato para verificar conectividad en el Login.
     *
     * @return ResultadoConexion con el estado booleano y mensaje descriptivo
     */
    public static ResultadoConexion probarConexion() {
        try (Connection conn = getConnection()) {
            if (conn != null && !conn.isClosed()) {
                return new ResultadoConexion(true, "Conexión establecida exitosamente con la base de datos '" + database + "' en " + host + ":" + port + ".");
            } else {
                return new ResultadoConexion(false, "No se pudo establecer la conexión: objeto de conexión cerrado o nulo.");
            }
        } catch (SQLException e) {
            return new ResultadoConexion(false, e.getMessage());
        }
    }

    /**
     * Interpreta los códigos de error comunes de MySQL (Error 1045, 1049, etc.)
     * y genera un mensaje pedagógico para orientar al usuario en pantalla.
     */
    private static String interpretarError(SQLException e) {
        int errorCode = e.getErrorCode();
        return switch (errorCode) {
            case 1045 -> {
                String causa = isPasswordConfigurada()
                    ? "Contraseña incorrecta para el usuario '" + user + "' en MySQL. Verifique la clave ingresada en 'database.local.properties' o PAPELERIA_DB_PASSWORD."
                    : "No se ha configurado una contraseña para el usuario '" + user + "' (el campo db.password está vacío). Debe ingresar su contraseña de MySQL en 'database.local.properties' o en la variable de entorno PAPELERIA_DB_PASSWORD.";
                yield "Error de autenticación MySQL (1045): " + causa;
            }
            case 1049 -> "Base de datos no encontrada (1049): No existe la base de datos '" + database + "' en el servidor MySQL.";
            case 0 -> "Error de red/comunicación: No fue posible conectar con el servidor MySQL en " + host + ":" + port + ". Verifica que el servicio MySQL84 esté iniciado.";
            default -> "Error de MySQL [" + errorCode + "]: " + e.getMessage();
        };
    }

    /**
     * Localiza el archivo 'database.local.properties' en el entorno de ejecución:
     * 1. Directorio actual de trabajo (user.dir).
     * 2. Directorios padre en caso de ejecución desde subcarpetas.
     * 3. Raíz del proyecto detectada a través del CodeSource de Maven.
     */
    private static File buscarArchivoConfiguracionLocal() {
        // 1. En la ruta directa del proceso actual
        File f = new File(LOCAL_CONFIG_FILENAME);
        if (f.exists() && f.isFile()) return f;

        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            f = new File(userDir, LOCAL_CONFIG_FILENAME);
            if (f.exists() && f.isFile()) return f;

            File parent = new File(userDir).getParentFile();
            if (parent != null) {
                f = new File(parent, LOCAL_CONFIG_FILENAME);
                if (f.exists() && f.isFile()) return f;
            }
        }

        // 2. A través de la ubicación del classpath compilado
        try {
            var codeSource = ConexionBD.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                File path = new File(codeSource.getLocation().toURI());
                File dir = path.isDirectory() ? path : path.getParentFile();
                while (dir != null && dir.exists()) {
                    File candidate = new File(dir, LOCAL_CONFIG_FILENAME);
                    if (candidate.exists() && candidate.isFile()) {
                        return candidate;
                    }
                    if (new File(dir, "pom.xml").exists()) {
                        File candidateRoot = new File(dir, LOCAL_CONFIG_FILENAME);
                        if (candidateRoot.exists() && candidateRoot.isFile()) {
                            return candidateRoot;
                        }
                    }
                    dir = dir.getParentFile();
                }
            }
        } catch (Exception ignored) {
            // Fallback silencioso si el SecurityManager restringe el acceso
        }

        return null;
    }

    /**
     * Indica si existe una contraseña configurada sin exponerla en texto plano.
     */
    public static boolean isPasswordConfigurada() {
        return password != null && !password.isEmpty();
    }

    /**
     * Cierra de forma segura una conexión JDBC si aún está abierta.
     */
    public static void cerrarConexion(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("[ADVERTENCIA ConexionBD] Error al cerrar conexión: " + e.getMessage());
            }
        }
    }

    // Métodos informativos de diagnóstico (sin exponer la contraseña)
    public static String getHost() { return host; }
    public static String getPort() { return port; }
    public static String getDatabase() { return database; }
    public static String getUser() { return user; }
    public static String getUrl() { return url; }
    public static String getOrigenConfiguracion() { return origenConfiguracion; }

    /**
     * Registro inmutable (Record) para el resultado de prueba de conexión.
     */
    public record ResultadoConexion(boolean exitoso, String mensaje) {}
}
