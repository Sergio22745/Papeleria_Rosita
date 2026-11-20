# ✿ Papelería Rosita – Sistema de Gestión Comercial e Inventario

Sistema integral de escritorio para la administración de punto de venta, compras a proveedores, control físico de inventario, turnos de caja y reportes analíticos de la tienda **Papelería Rosita**.

Desarrollado en **Java 21**, **JavaFX 21**, **Maven**, **JDBC** y **MySQL Server 8.4 LTS**, diseñado con un enfoque cálido, accesible y seguro, priorizando la facilidad de uso para **Rosita** (Administradora) y **Gladys** (Empleada).

---

## 📋 Tabla de Contenidos

1. [Objetivo del Sistema](#-objetivo-del-sistema)
2. [Tecnologías Utilizadas](#-tecnologías-utilizadas)
3. [Arquitectura del Software (MVC + DAO)](#-arquitectura-del-software-mvc--dao)
4. [Estructura de Carpetas y Paquetes](#-estructura-de-carpetas-y-paquetes)
5. [Módulos del Sistema](#-módulos-del-sistema)
6. [Lógica de Negocio e Integridad de Inventario](#-lógica-de-negocio-e-integridad-de-inventario)
   - [Ciclo de Venta y Descuento de Stock](#ciclo-de-venta-y-descuento-de-stock)
   - [Ciclo de Compras y Recepción Física](#ciclo-de-compras-y-recepción-física)
   - [Triggers y Reglas en MySQL](#triggers-y-reglas-en-mysql)
7. [Perfiles de Usuario y Roles](#-perfiles-de-usuario-y-roles)
8. [Requisitos Previos](#-requisitos-previos)
9. [Instalación y Configuración](#-instalación-y-configuración)
   - [Paso 1: Restauración de la Base de Datos](#paso-1-restauración-de-la-base-de-datos)
   - [Paso 2: Configuración Segura de Credenciales](#paso-2-configuración-segura-de-credenciales)
10. [Compilación y Ejecución](#-compilación-y-ejecución)
11. [Medidas de Seguridad Implementadas](#-medidas-de-seguridad-implementadas)
12. [Estado de Pruebas y Verificación](#-estado-de-pruebas-y-verificación)

---

## 🎯 Objetivo del Sistema

Automatizar y optimizar las operaciones comerciales de la papelería física:
- **Agilizar el mostrador:** Registro rápido de ventas cobradas exclusivamente en efectivo con cálculo automático de cambio.
- **Proteger las existencias:** Garantizar que el stock físico en bodega coincida exactamente con los registros contables, delegando la actualización a disparadores (*triggers*) atómicos en MySQL.
- **Diferenciar pedidos de recepciones:** Registrar órdenes de compra como pedidos pendientes sin aumentar el inventario hasta que la mercancía llegue físicamente con su remisión.
- **Ofrecer accesibilidad real:** Interfaz con botones amplios, tipografía de alta legibilidad, contrastes amigables y navegación clara para prevenir errores de digitación.

---

## 🛠 Tecnologías Utilizadas

- **Lenguaje:** Java 21 (LTS) – *OpenJDK 21*
- **Interfaz Gráfica:** JavaFX 21 (OpenJFX) con vistas desacopladas en FXML y estilos personalizados en CSS.
- **Gestor de Construcción y Dependencias:** Apache Maven 3.9+.
- **Motor de Base de Datos:** MySQL Server 8.4 LTS con motor transaccional **InnoDB**.
- **Acceso a Datos:** JDBC nativo a través de `mysql-connector-j` 8.4.0.
- **Seguridad y Criptografía:** Algoritmo PBKDF2WithHmacSHA256 con Salt aleatorio y 600.000 iteraciones.

---

## 🏛 Arquitectura del Software (MVC + DAO)

El proyecto sigue una separación rigurosa de responsabilidades en tres capas:

1. **Capa Vista (FXML + CSS):**  
   Define la estructura visual de forma declarativa (`*.fxml`) y la identidad estética (`styles.css`). La interfaz no contiene consultas SQL ni lógica de persistencia.
2. **Capa Controlador (`com.papeleriarosita.controller`):**  
   Gestiona los eventos del usuario (clics, textos, selecciones), valida los datos de entrada en pantalla y coordina la actualización visual.
3. **Capa DAO (`com.papeleriarosita.dao`):**  
   Encapsula todas las operaciones SQL (`SELECT`, `INSERT`, `UPDATE`). Administra las transacciones JDBC (`conn.setAutoCommit(false)`) y delega la integridad de Kárdex al motor relacional.
4. **Capa Modelo (`com.papeleriarosita.model`):**  
   Clases POJO y Records que representan las entidades del negocio (Producto, Venta, Compra, Inventario, Usuario, etc.).

---

## 📁 Estructura de Carpetas y Paquetes

```text
Papeleria Rosita/
├── pom.xml                                  # Configuración de dependencias y plugins Maven
├── .gitignore                               # Exclusión de archivos binarios y credenciales privadas
├── database.local.properties.example        # Plantilla segura de ejemplo para conexión local
├── etapa4_migracion.sql                     # Script oficial de estructura de compras y recepciones
├── etapa4_rollback.sql                      # Script seguro de reversión con procedimientos de guardia
│
├── backup/                                  # Respaldos de estructura SQL y triggers originales
│   ├── backup_papeleria_rosita_original.sql
│   └── backup_triggers_originales.sql
│
└── src/main/
    ├── java/com/papeleriarosita/
    │   ├── App.java                         # Punto de entrada de la aplicación JavaFX
    │   ├── Main.java                        # Lanzador alternativo de JVM
    │   │
    │   ├── controller/                      # Controladores de interfaz de usuario
    │   │   ├── MainController.java          # Inicio de sesión (Login) y autenticación
    │   │   ├── DashboardController.java     # Panel principal, menú lateral y navegación
    │   │   ├── VentasController.java        # Punto de venta en mostrador (efectivo y cambio)
    │   │   ├── InventarioController.java    # Monitor de stock, alertas y entradas físicas
    │   │   ├── ProductosController.java     # Catálogo de productos, categorías y precios
    │   │   ├── ProveedoresController.java   # Directorio comercial de proveedores
    │   │   ├── ComprasController.java       # Órdenes de abastecimiento y remisiones
    │   │   ├── CajaController.java          # Turnos, apertura, gastos menores y arqueo
    │   │   ├── ReportesController.java      # Estadísticas, gráficos y exportación
    │   │   └── SetupController.java         # Asistente de primer arranque
    │   │
    │   ├── dao/                             # Capa de acceso a datos (JDBC)
    │   │   ├── UsuarioDAO.java              # Gestión de usuarios y contraseñas PBKDF2
    │   │   ├── VentaDAO.java                # Transacciones atómicas de venta
    │   │   ├── ProductoDAO.java             # Catálogo y validaciones de producto
    │   │   ├── CategoriaDAO.java            # Categorías comerciales
    │   │   ├── InventarioDAO.java           # Existencias y kárdex de movimientos
    │   │   ├── CompraDAO.java               # Órdenes de compra y recepción física
    │   │   ├── ProveedorDAO.java            # Directorio comercial
    │   │   ├── CajaDAO.java                 # Movimientos de caja física
    │   │   └── ReporteDAO.java              # Consultas analíticas consolidadas
    │   │
    │   ├── database/
    │   │   └── ConexionBD.java              # Gestor centralizado JDBC con jerarquía segura
    │   │
    │   ├── model/                           # Entidades del negocio y DTOs
    │   │   ├── Usuario.java, UserSession.java
    │   │   ├── Producto.java, Categoria.java
    │   │   ├── Inventario.java, MovimientoInventario.java
    │   │   ├── Venta.java, DetalleVenta.java
    │   │   ├── Compra.java, DetalleCompra.java
    │   │   ├── RecepcionCompra.java, DetalleRecepcionCompra.java
    │   │   └── Proveedor.java, ResumenCaja.java
    │   │
    │   └── util/                            # Funciones de soporte
    │       ├── SeguridadUtil.java           # Generación de hash PBKDF2 y validaciones
    │       └── ExportadorCSV.java           # Exportador estándar de datos a CSV
    │
    └── resources/
        ├── database.properties              # Configuración base en el classpath (sin contraseñas)
        └── com/papeleriarosita/
            ├── css/
            │   └── styles.css               # Hoja de estilos con paleta de colores oficial
            └── fxml/                        # Vistas declarativas JavaFX
                ├── main-view.fxml           # Pantalla de Login
                ├── dashboard-view.fxml      # Dashboard principal
                ├── ventas-view.fxml         # Mostrador de ventas
                ├── inventario-view.fxml     # Inventario y kárdex
                ├── productos-view.fxml      # Catálogo de productos
                ├── proveedores-view.fxml    # Directorio de proveedores
                ├── compras-view.fxml        # Compras y remisiones
                ├── caja-view.fxml           # Módulo de caja
                ├── reportes-view.fxml       # Módulo de reportes
                └── setup-view.fxml          # Asistente de configuración
```

---

## 📦 Módulos del Sistema

1. **Ventas:** Mostrador de cobro ágil. Permite buscar por código de barras o nombre, agregar al carrito con validación de stock disponible, cobrar exclusivamente en efectivo con cálculo del cambio y generar comprobante.
2. **Inventario:** Tablero semaforizado de stock (óptimo, bajo, crítico/agotado). Permite consultar el historial de movimientos (Kárdex) y registrar entradas físicas directas de mercancía con responsable asignado.
3. **Productos:** Maestro de artículos, categorías y precios de venta fijados por la Administradora.
4. **Proveedores:** Directorio comercial con teléfonos, correos y direcciones de distribuidores.
5. **Compras:** Generación de órdenes de compra con estado `REGISTRADA` y registro de recepciones físicas parciales o totales vinculadas a guías de remisión.
6. **Caja:** Apertura de turno con fondo inicial en efectivo, registro de entradas y salidas de mostrador, y cuadre/arqueo final de turno.
7. **Reportes:** Consolidados financieros con gráficos de barras y sectores, productos más vendidos, balance de ingresos frente a egresos y exportación a archivos CSV.
8. **Configuración y Accesibilidad:** Ajustes visuales para comodidad de Rosita (tamaño de tipografía, alto contraste), administración de empleadas y copias de seguridad locales.

---

## ⚙️ Lógica de Negocio e Integridad de Inventario

### Ciclo de Venta y Descuento de Stock
1. **Validación:** `VentasController` comprueba que la cajera esté autenticada y el carrito tenga productos.
2. **Transacción atómica:** `VentaDAO.registrarVenta()` inicia `conn.setAutoCommit(false)`. Bloquea la fila del producto con `SELECT ... FOR UPDATE` para verificar existencias en tiempo real.
3. **Inserción de factura:** Inserta en `ventas` y luego en `detalle_venta`.
4. **Descuento en MySQL:** Java **no** realiza un `UPDATE` manual a inventario. Al insertar en `detalle_venta`, el disparador `trg_venta_actualizar_inventario` descuenta las unidades de `inventario.stock_actual` y crea el asiento de `SALIDA` en `movimientos_inventario`.
5. **Cierre:** Se ejecuta `conn.commit()`. Si algo falla, `conn.rollback()` deja la base de datos intacta.

### Ciclo de Compras y Recepción Física
1. **Orden de compra (Pedido):** `CompraDAO.registrarOrdenCompra()` guarda la orden en `compras` con estado `'REGISTRADA'`. **Regla:** no aumenta el stock porque el producto aún está en tránsito.
2. **Recepción en bodega (Remisión):** Cuando el proveedor entrega el pedido, `CompraDAO.registrarRecepcionFisica()` registra la entrega física en `recepciones_compra` y `detalle_recepcion_compra`.
3. **Entrada a inventario:** El disparador `trg_recepcion_actualizar_inventario` incrementa `inventario.stock_actual`, asienta el movimiento `'ENTRADA'` en el Kárdex y suma `cantidad_recibida` en `detalle_compra`.
4. **Actualización de estado:** El trigger `trg_recepcion_actualizar_estado_compra` evalúa si el pedido quedó completamente cubierto (`'RECIBIDA'`) o si quedan unidades pendientes (`'PARCIAL'`).

---

## 👥 Perfiles de Usuario y Roles

El sistema implementa control de acceso basado en roles (**RBAC**):

| Característica / Módulo | Administradora (Rosita) | Empleada (Gladys) |
| :--- | :---: | :---: |
| Punto de Venta (Cobro en mostrador) | ✅ Total | ✅ Total |
| Consulta de Catálogo e Inventario | ✅ Total | ✅ Total |
| Fijación y Modificación de Precios de Venta | ✅ Habilitado | 🔒 Bloqueado |
| Registro de Proveedores y Compras | ✅ Total | ✅ Consulta |
| Apertura y Cierre de Caja | ✅ Total | ✅ Turno asignado |
| Reportes y Balances Financieros | ✅ Total | 🔒 Restringido |
| Registro de Empleados y Copias de Seguridad | ✅ Habilitado | 🔒 Bloqueado |

---

## 💻 Requisitos Previos

- **Java Development Kit (JDK):** Versión 21 o superior.
- **Apache Maven:** Versión 3.9 o superior.
- **MySQL Server:** Versión 8.4 LTS (o superior) en ejecución local en el puerto `3306`.
- **Git:** Para clonar el repositorio.

---

## 🚀 Instalación y Configuración

### Paso 1: Restauración de la Base de Datos
Abra su terminal o MySQL Workbench como usuario administrador (`root`) y ejecute:

```sql
-- 1. Crear base de datos
CREATE DATABASE papeleria_rosita CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE papeleria_rosita;

-- 2. Ejecutar el respaldo base
SOURCE backup/backup_papeleria_rosita_original.sql;

-- 3. Aplicar las mejoras de compras y recepciones físicas (Etapa 4)
SOURCE etapa4_migracion.sql;
```

### Paso 2: Configuración Segura de Credenciales
Para proteger las contraseñas privadas fuera del repositorio Git, el proyecto utiliza `database.local.properties`.

1. Copie la plantilla de ejemplo en la raíz del proyecto:
   ```bash
   cp database.local.properties.example database.local.properties
   ```
2. Abra `database.local.properties` e ingrese sus credenciales locales:
   ```properties
   db.host=localhost
   db.port=3306
   db.name=papeleria_rosita
   db.user=root
   db.password=TuClaveDeMySQLAqui
   ```
> [!NOTE]
> `database.local.properties` se encuentra en `.gitignore`, por lo que sus claves personales nunca se subirán a GitHub.

---

## 🔨 Compilación y Ejecución

Para compilar el proyecto sin errores y ejecutar las pruebas automáticas:
```bash
mvn clean compile
mvn test
```

Para iniciar la aplicación gráfica:
```bash
mvn javafx:run
```

### Primer Acceso y Cuentas de Acceso
Si es la primera vez que inicia el sistema con una base de datos vacía, la aplicación abrirá el **Asistente de Configuración Inicial** (`setup-view.fxml`) para registrar a la Administradora (**Rosita**) con una contraseña segura.

Posteriormente, desde el módulo de **Configuración**, Rosita podrá crear la cuenta de su empleada (**Gladys**).

> **Modo Vista Previa:** La pantalla de inicio de sesión dispone de un acceso de *Vista Previa*, el cual permite navegar por todas las pantallas en modo solo lectura sin requerir conexión inmediata a MySQL.

---

## 🔒 Medidas de Seguridad Implementadas

1. **Cero credenciales en el código fuente:** Ni contraseñas ni cadenas de conexión con credenciales están incrustadas en clases Java o repositorios Git.
2. **Hash criptográfico robusto:** Las contraseñas de usuario se almacenan con PBKDF2WithHmacSHA256, salting aleatorio criptográfico y 600.000 rondas de cómputo, imposibilitando ataques por tablas arcoíris.
3. **Sentencias preparadas (PreparedStatement):** 100% de las consultas utilizan parámetros parametrizados para neutralizar cualquier riesgo de inyección SQL.
4. **Transacciones atómicas:** Bloques `conn.setAutoCommit(false)` con `rollback` en caso de error, asegurando que ninguna factura o recepción quede guardada a medias.
5. **Validación de integridad en base de datos:** El Kárdex y las existencias no dependen exclusivamente del software cliente; los triggers de MySQL impiden que existan inconsistencias de inventario.

---

## 📊 Estado de Pruebas y Verificación

- **Compilación Maven:** Verificada con éxito (`BUILD SUCCESS`) en Java 21 y JavaFX 21.
- **Conectividad JDBC:** Verificada con compatibilidad hacia MySQL 8.4 LTS con zona horaria de Bogotá (`America/Bogota`).
- **Navegación:** Los 7 módulos operan de forma limpia y fluida desde la pantalla de Inicio y el menú lateral simplificado.
- **Punto de Venta:** Módulo probado con cobro en efectivo, cálculo en tiempo real de cambio y actualización inmediata del catálogo.
