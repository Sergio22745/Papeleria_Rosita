package com.papeleriarosita.util;

import com.papeleriarosita.model.Compra;
import com.papeleriarosita.model.Inventario;
import com.papeleriarosita.model.MovimientoInventario;
import com.papeleriarosita.model.ProductoMasVendido;
import com.papeleriarosita.model.ResumenCaja;
import com.papeleriarosita.model.ResumenInventarioReporte;
import com.papeleriarosita.model.Venta;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utilidad de exportación a formato CSV compatible con Microsoft Excel en español.
 * Aplica codificación UTF-8 con BOM (\uFEFF) y delimitador punto y coma (;)
 * para asegurar la correcta apertura de caracteres acentuados y números decimales.
 */
public class ExportadorCSV {

    private static final String SEPARADOR = ";";
    private static final DateTimeFormatter FORMATO_FECHA_HORA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static BufferedWriter crearWriterConBOM(File archivo) throws IOException {
        FileOutputStream fos = new FileOutputStream(archivo);
        // Escribir BOM UTF-8 (bytes 0xEF, 0xBB, 0xBF)
        fos.write(0xEF);
        fos.write(0xBB);
        fos.write(0xBF);
        return new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8));
    }

    private static String escapar(Object valor) {
        if (valor == null) return "";
        String texto = valor.toString();
        if (texto.contains(";") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }

    /**
     * Exporta el listado de ventas a un archivo CSV.
     */
    public static void exportarVentas(File archivo, List<Venta> ventas) throws IOException {
        try (BufferedWriter bw = crearWriterConBOM(archivo)) {
            // Encabezado
            bw.write(String.join(SEPARADOR,
                "ID Venta",
                "Fecha y Hora",
                "Cliente",
                "Vendedor / Responsable",
                "Método de Pago",
                "Estado",
                "Total Artículos",
                "Total Venta ($)"
            ));
            bw.newLine();

            for (Venta v : ventas) {
                bw.write(String.join(SEPARADOR,
                    escapar(v.getIdVenta()),
                    escapar(v.getFechaVenta() != null ? v.getFechaVenta().format(FORMATO_FECHA_HORA) : ""),
                    escapar(v.getNombreCliente()),
                    escapar(v.getNombreUsuario()),
                    escapar(v.getMetodoPago()),
                    escapar(v.getEstado()),
                    escapar(v.getTotalArticulos()),
                    escapar(v.getTotal())
                ));
                bw.newLine();
            }
        }
    }

    /**
     * Exporta el ranking de productos más vendidos a CSV.
     */
    public static void exportarProductosMasVendidos(File archivo, List<ProductoMasVendido> productos) throws IOException {
        try (BufferedWriter bw = crearWriterConBOM(archivo)) {
            bw.write(String.join(SEPARADOR,
                "ID Producto",
                "Código",
                "Producto",
                "Categoría",
                "Unidades Vendidas",
                "Total Recaudado ($)",
                "Precio Promedio ($)"
            ));
            bw.newLine();

            for (ProductoMasVendido p : productos) {
                bw.write(String.join(SEPARADOR,
                    escapar(p.getIdProducto()),
                    escapar(p.getCodigo()),
                    escapar(p.getNombre()),
                    escapar(p.getCategoria()),
                    escapar(p.getCantidadVendida()),
                    escapar(p.getTotalRecaudado()),
                    escapar(p.getPrecioPromedio())
                ));
                bw.newLine();
            }
        }
    }

    /**
     * Exporta el reporte de inventario actual y sus indicadores a CSV.
     */
    public static void exportarInventario(File archivo, List<Inventario> inventario,
                                          ResumenInventarioReporte resumen) throws IOException {
        try (BufferedWriter bw = crearWriterConBOM(archivo)) {
            if (resumen != null) {
                bw.write(escapar("RESUMEN DE INVENTARIO - PAPELERÍA ROSITA"));
                bw.newLine();
                bw.write(String.join(SEPARADOR, "Total Productos:", escapar(resumen.getTotalProductos())));
                bw.newLine();
                bw.write(String.join(SEPARADOR, "Total Unidades en Stock:", escapar(resumen.getTotalStockUnidades())));
                bw.newLine();
                bw.write(String.join(SEPARADOR, "Productos Disponibles:", escapar(resumen.getProductosDisponibles())));
                bw.newLine();
                bw.write(String.join(SEPARADOR, "Productos con Stock Bajo:", escapar(resumen.getProductosBajoStock())));
                bw.newLine();
                bw.write(String.join(SEPARADOR, "Productos Agotados:", escapar(resumen.getProductosAgotados())));
                bw.newLine();
                bw.write(String.join(SEPARADOR, "Valorización Total Inventario:", escapar(resumen.getValorTotalFormateado())));
                bw.newLine();
                bw.newLine();
            }

            bw.write(String.join(SEPARADOR,
                "ID Inventario",
                "Código",
                "Producto",
                "Categoría",
                "Stock Actual",
                "Stock Mínimo",
                "Alerta",
                "Precio Venta ($)",
                "Valor Stock ($)"
            ));
            bw.newLine();

            for (Inventario inv : inventario) {
                java.math.BigDecimal valorStock = inv.getPrecioVenta().multiply(
                    java.math.BigDecimal.valueOf(inv.getStockActual())
                );
                bw.write(String.join(SEPARADOR,
                    escapar(inv.getIdInventario()),
                    escapar(inv.getCodigoProducto()),
                    escapar(inv.getNombreProducto()),
                    escapar(inv.getNombreCategoria()),
                    escapar(inv.getStockActual()),
                    escapar(inv.getStockMinimo()),
                    escapar(inv.getEstadoAlerta()),
                    escapar(inv.getPrecioVenta()),
                    escapar(valorStock)
                ));
                bw.newLine();
            }
        }
    }

    /**
     * Exporta el historial de movimientos de inventario (kárdex) a CSV.
     */
    public static void exportarMovimientosKardex(File archivo, List<MovimientoInventario> movimientos) throws IOException {
        try (BufferedWriter bw = crearWriterConBOM(archivo)) {
            bw.write(String.join(SEPARADOR,
                "ID Movimiento",
                "Fecha y Hora",
                "Código Producto",
                "Producto",
                "Tipo de Movimiento",
                "Cantidad",
                "Responsable",
                "Descripción / Motivo"
            ));
            bw.newLine();

            for (MovimientoInventario m : movimientos) {
                bw.write(String.join(SEPARADOR,
                    escapar(m.getIdMovimiento()),
                    escapar(m.getFechaMovimiento() != null ? m.getFechaMovimiento().format(FORMATO_FECHA_HORA) : ""),
                    escapar(m.getCodigoProducto()),
                    escapar(m.getNombreProducto()),
                    escapar(m.getTipoMovimiento()),
                    escapar(m.getCantidad()),
                    escapar(m.getNombreUsuario()),
                    escapar(m.getDescripcion())
                ));
                bw.newLine();
            }
        }
    }

    /**
     * Exporta el historial de órdenes de compra a CSV.
     */
    public static void exportarCompras(File archivo, List<Compra> compras) throws IOException {
        try (BufferedWriter bw = crearWriterConBOM(archivo)) {
            bw.write(String.join(SEPARADOR,
                "ID Compra",
                "Proveedor",
                "Responsable",
                "Fecha de Registro",
                "Estado",
                "Total Compra ($)"
            ));
            bw.newLine();

            for (Compra c : compras) {
                bw.write(String.join(SEPARADOR,
                    escapar(c.getIdCompra()),
                    escapar(c.getNombreProveedor()),
                    escapar(c.getNombreUsuario()),
                    escapar(c.getFechaCompra() != null ? c.getFechaCompra().format(FORMATO_FECHA_HORA) : ""),
                    escapar(c.getEstado()),
                    escapar(c.getTotal())
                ));
                bw.newLine();
            }
        }
    }

    /**
     * Exporta el arqueo consolidado de caja y sus transacciones a CSV.
     */
    public static void exportarCaja(File archivo, ResumenCaja caja, List<Venta> ventas) throws IOException {
        try (BufferedWriter bw = crearWriterConBOM(archivo)) {
            if (caja != null) {
                bw.write(escapar("ARQUEO DE CAJA - PAPELERÍA ROSITA"));
                bw.newLine();
                bw.write(String.join(SEPARADOR, "Total Ventas:", escapar(caja.getTotalVentas())));
                bw.newLine();
                bw.write(String.join(SEPARADOR, "Total Recaudado:", escapar(caja.getTotalRecaudadoFormateado())));
                bw.newLine();
                bw.write(String.join(SEPARADOR, "Efectivo en Caja:", escapar(caja.getTotalEfectivoFormateado())));
                bw.newLine();
                bw.write(String.join(SEPARADOR, "Transferencias:", escapar(caja.getTotalTransferenciaFormateado())));
                bw.newLine();
                bw.write(String.join(SEPARADOR, "Tarjetas:", escapar(caja.getTotalTarjetaFormateado())));
                bw.newLine();
                bw.newLine();
            }

            bw.write(String.join(SEPARADOR,
                "ID Venta",
                "Fecha y Hora",
                "Cliente",
                "Vendedor",
                "Método de Pago",
                "Total Venta ($)"
            ));
            bw.newLine();

            for (Venta v : ventas) {
                bw.write(String.join(SEPARADOR,
                    escapar(v.getIdVenta()),
                    escapar(v.getFechaVenta() != null ? v.getFechaVenta().format(FORMATO_FECHA_HORA) : ""),
                    escapar(v.getNombreCliente()),
                    escapar(v.getNombreUsuario()),
                    escapar(v.getMetodoPago()),
                    escapar(v.getTotal())
                ));
                bw.newLine();
            }
        }
    }
}
