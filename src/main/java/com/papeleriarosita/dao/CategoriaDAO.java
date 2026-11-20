package com.papeleriarosita.dao;

import com.papeleriarosita.database.ConexionBD;
import com.papeleriarosita.model.Categoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO para la entidad Categoria.
 * Gestiona consultas y operaciones sobre la tabla 'categorias' de MySQL.
 */
public class CategoriaDAO {

    /**
     * Obtiene la lista completa de categorías registradas en el sistema.
     */
    public List<Categoria> listarTodas() throws SQLException {
        List<Categoria> lista = new ArrayList<>();
        String sql = "SELECT id_categoria, nombre, descripcion FROM categorias ORDER BY nombre ASC";

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                lista.add(new Categoria(
                    rs.getInt("id_categoria"),
                    rs.getString("nombre"),
                    rs.getString("descripcion")
                ));
            }
        }
        return lista;
    }

    /**
     * Busca una categoría por su ID.
     */
    public Optional<Categoria> buscarPorId(int idCategoria) throws SQLException {
        String sql = "SELECT id_categoria, nombre, descripcion FROM categorias WHERE id_categoria = ?";

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idCategoria);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Categoria(
                        rs.getInt("id_categoria"),
                        rs.getString("nombre"),
                        rs.getString("descripcion")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Verifica si ya existe una categoría con el nombre indicado (ignora mayúsculas/minúsculas).
     */
    public boolean existeNombre(String nombre) throws SQLException {
        if (nombre == null || nombre.trim().isEmpty()) return false;
        String sql = "SELECT COUNT(*) AS total FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM(?))";

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nombre);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total") > 0;
                }
            }
        }
        return false;
    }

    /**
     * Registra una categoría en la base de datos si no existe.
     */
    public Categoria registrar(String nombre, String descripcion) throws SQLException {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la categoría no puede estar vacío.");
        }
        if (existeNombre(nombre)) {
            throw new IllegalArgumentException("Ya existe una categoría con el nombre: " + nombre);
        }

        String sql = "INSERT INTO categorias (nombre, descripcion) VALUES (?, ?)";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, nombre.trim());
            stmt.setString(2, descripcion != null ? descripcion.trim() : null);
            stmt.executeUpdate();

            int idGenerado = 0;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    idGenerado = keys.getInt(1);
                }
            }
            return new Categoria(idGenerado, nombre.trim(), descripcion);
        }
    }

    /**
     * Comprueba si la tabla de categorías está vacía e inserta las categorías iniciales de Papelería Rosita.
     * Contempla todo lo que comercializa la papelería:
     * - Útiles escolares, cuadernos, lápices y materiales
     * - Peluches
     * - Regalos románticos
     * - Tarjetas
     * - Bolsas de regalo y envolturas
     * - Variedades
     */
    public void asegurarCategoriasIniciales() {
        try (Connection conn = ConexionBD.getConnection()) {
            String checkSql = "SELECT COUNT(*) AS total FROM categorias";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                 ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt("total") > 0) {
                    return; // Ya existen categorías en la base de datos
                }
            }

            // Insertar catálogo de categorías base de Papelería Rosita
            String insertSql = "INSERT INTO categorias (nombre, descripcion) VALUES (?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                String[][] categoriasBase = {
                    {"Útiles Escolares", "Cuadernos, lápices, lapiceros, borradores y reglas"},
                    {"Colores y Marcadores", "Colores, plumones, resaltadores y pinturas escolares"},
                    {"Peluches", "Peluches de diversos tamaños, personajes y motivos especiales"},
                    {"Regalos Románticos", "Detalles, chocolates, cojines y regalos para enamorados"},
                    {"Tarjetas y Expresión Social", "Tarjetas de cumpleaños, amor, amistad y ocasiones"},
                    {"Bolsas y Envolturas", "Bolsas de regalo decorativas, papel de regalo y moños"},
                    {"Variedades y Accesorios", "Artículos de belleza, papelería fina, juguetería y novedades"}
                };

                for (String[] cat : categoriasBase) {
                    insertStmt.setString(1, cat[0]);
                    insertStmt.setString(2, cat[1]);
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("[CategoriaDAO] No fue posible comprobar o insertar categorías iniciales: " + e.getMessage());
        }
    }
}
