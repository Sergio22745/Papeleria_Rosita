-- ==============================================================================
-- ETAPA 4: MIGRACIÓN DE ESTRUCTURA Y TRIGGERS PARA PROVEEDORES Y COMPRAS
-- Proyecto: Papelería Rosita
-- Versión: 8.2 AUDITADA Y BLINDADA INTEGRALMENTE
-- Motor objetivo: MySQL Server 8.4.11 LTS (InnoDB)
-- ==============================================================================
-- AVISO DE SEGURIDAD OPERATIVA:
-- Este script realiza modificaciones DDL sobre el esquema 'papeleria_rosita'.
-- En MySQL, las instrucciones DDL causan COMMIT implícito y NO son reversibles
-- mediante transacciones convencionales.
-- NO ejecutar este script sin autorización expresa y respaldo previo completo.
-- ==============================================================================

USE papeleria_rosita_prueba_etapa4;

-- ==============================================================================
-- 1. ACTUALIZACIÓN DE LA TABLA 'compras'
--    - Se amplía el dominio de estados permitidos incorporando 'PARCIAL'.
--    - El estado por defecto para nuevas órdenes pasa a ser 'REGISTRADA'.
-- ==============================================================================
ALTER TABLE compras DROP CONSTRAINT chk_compra_estado;
ALTER TABLE compras ALTER COLUMN estado SET DEFAULT 'REGISTRADA';
ALTER TABLE compras ADD CONSTRAINT chk_compra_estado
    CHECK (estado IN ('REGISTRADA', 'PARCIAL', 'RECIBIDA', 'ANULADA'));

-- ==============================================================================
-- 2. ACTUALIZACIÓN DE LA TABLA 'detalle_compra'
--    - Se incorpora 'cantidad_recibida' (acumulador estricto de entregas físicas).
--    - Restricción CHECK de dominio: 0 <= cantidad_recibida <= cantidad.
-- ==============================================================================
ALTER TABLE detalle_compra 
    ADD COLUMN cantidad_recibida INT NOT NULL DEFAULT 0;

ALTER TABLE detalle_compra 
    ADD CONSTRAINT chk_detalle_compra_recibida
    CHECK (cantidad_recibida >= 0 AND cantidad_recibida <= cantidad);

-- Regularización preventiva de compras históricas ya cerradas
UPDATE detalle_compra dc
INNER JOIN compras c ON dc.id_compra = c.id_compra
SET dc.cantidad_recibida = dc.cantidad
WHERE c.estado = 'RECIBIDA';

-- ==============================================================================
-- 3. NUEVA TABLA: 'recepciones_compra' (Cabecera de Remisiones / Entregas Físicas)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS recepciones_compra (
    id_recepcion INT AUTO_INCREMENT PRIMARY KEY,
    id_compra INT NOT NULL,
    id_usuario INT NOT NULL,
    fecha_recepcion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    numero_remision VARCHAR(50) NULL,
    observaciones TEXT NULL,
    CONSTRAINT fk_recepciones_compra
        FOREIGN KEY (id_compra) REFERENCES compras (id_compra)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_recepciones_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 4. NUEVA TABLA: 'detalle_recepcion_compra' (Detalle Físico de Mercancía Recibida)
--    Incluye restricción de unicidad para evitar duplicar el mismo renglón en una remisión.
-- ==============================================================================
CREATE TABLE IF NOT EXISTS detalle_recepcion_compra (
    id_detalle_recepcion INT AUTO_INCREMENT PRIMARY KEY,
    id_recepcion INT NOT NULL,
    id_detalle_compra INT NOT NULL,
    id_producto INT NOT NULL,
    cantidad_recibida INT NOT NULL,
    observaciones VARCHAR(255) NULL,
    CONSTRAINT fk_det_rec_recepcion
        FOREIGN KEY (id_recepcion) REFERENCES recepciones_compra (id_recepcion)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_det_rec_detalle_compra
        FOREIGN KEY (id_detalle_compra) REFERENCES detalle_compra (id_detalle_compra)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_det_rec_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id_producto)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_det_rec_cantidad
        CHECK (cantidad_recibida > 0),
    CONSTRAINT uq_recepcion_detalle_compra
        UNIQUE (id_recepcion, id_detalle_compra)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 5. REEMPLAZO DE TRIGGERS ANTERIORES POR NUEVOS TRIGGERS DE SEGURIDAD
-- ==============================================================================
DELIMITER $$

-- ------------------------------------------------------------------------------
-- A. Eliminar triggers antiguos del esquema original
-- ------------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_compra_actualizar_inventario$$
DROP TRIGGER IF EXISTS trg_compra_validar_estado$$

-- ------------------------------------------------------------------------------
-- B. Trigger 'trg_compra_validar_estado' (BEFORE INSERT ON detalle_compra)
--    Bloquea con FOR UPDATE y restringe la inserción de ítems a compras REGISTRADA.
--    Rechaza explícitamente estados PARCIAL, RECIBIDA o ANULADA.
-- ------------------------------------------------------------------------------
CREATE TRIGGER trg_compra_validar_estado
BEFORE INSERT ON detalle_compra
FOR EACH ROW
BEGIN
    DECLARE v_estado VARCHAR(20);

    -- Bloqueo pesimista de fila para evitar condiciones de carrera
    SELECT estado
    INTO v_estado
    FROM compras
    WHERE id_compra = NEW.id_compra
    FOR UPDATE;

    IF v_estado IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: La orden de compra especificada no existe.';
    END IF;

    IF v_estado <> 'REGISTRADA' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: Solo se pueden agregar productos a compras en estado REGISTRADA.';
    END IF;
END$$

-- ------------------------------------------------------------------------------
-- C. Trigger 'trg_recepcion_cabecera_validar' (BEFORE INSERT ON recepciones_compra)
--    Verifica que la compra exista y esté en un estado receptivo (REGISTRADA o PARCIAL).
-- ------------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_recepcion_cabecera_validar$$

CREATE TRIGGER trg_recepcion_cabecera_validar
BEFORE INSERT ON recepciones_compra
FOR EACH ROW
BEGIN
    DECLARE v_estado_compra VARCHAR(20);

    -- Bloqueo pesimista en la cabecera de compra
    SELECT estado
    INTO v_estado_compra
    FROM compras
    WHERE id_compra = NEW.id_compra
    FOR UPDATE;

    IF v_estado_compra IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: La compra vinculada a la recepción no existe.';
    END IF;

    IF v_estado_compra = 'ANULADA' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: No se pueden registrar recepciones en una compra ANULADA.';
    END IF;

    IF v_estado_compra = 'RECIBIDA' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: La compra ya se encuentra RECIBIDA en su totalidad y cerrada.';
    END IF;
END$$

-- ------------------------------------------------------------------------------
-- D. Trigger 'trg_recepcion_validar_cantidad' (BEFORE INSERT ON detalle_recepcion_compra)
--    Bloquea con FOR UPDATE y valida correspondencia y saldo pendiente sin excedentes.
-- ------------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_recepcion_validar_cantidad$$

CREATE TRIGGER trg_recepcion_validar_cantidad
BEFORE INSERT ON detalle_recepcion_compra
FOR EACH ROW
BEGIN
    DECLARE v_id_compra_recepcion INT;
    DECLARE v_id_compra_detalle INT;
    DECLARE v_id_producto_detalle INT;
    DECLARE v_cantidad_solicitada INT;
    DECLARE v_cantidad_ya_recibida INT;
    DECLARE v_saldo_pendiente INT;

    -- 1. Obtener la orden de compra a la que pertenece la remisión
    SELECT id_compra
    INTO v_id_compra_recepcion
    FROM recepciones_compra
    WHERE id_recepcion = NEW.id_recepcion;

    -- 2. Consultar y bloquear con FOR UPDATE la fila correspondiente en 'detalle_compra'
    SELECT id_compra, id_producto, cantidad, cantidad_recibida
    INTO v_id_compra_detalle, v_id_producto_detalle, v_cantidad_solicitada, v_cantidad_ya_recibida
    FROM detalle_compra
    WHERE id_detalle_compra = NEW.id_detalle_compra
    FOR UPDATE;

    -- Validar existencia
    IF v_id_compra_detalle IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: El ítem de compra especificado no existe.';
    END IF;

    -- Validar coincidencia de compra
    IF v_id_compra_recepcion <> v_id_compra_detalle THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: El ítem a recibir no pertenece a la orden de compra vinculada a la recepción.';
    END IF;

    -- Validar coincidencia de producto
    IF NEW.id_producto <> v_id_producto_detalle THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: El producto a recibir no coincide con el producto pactado en la orden de compra.';
    END IF;

    -- 3. Validar no exceder el saldo pendiente
    SET v_saldo_pendiente = v_cantidad_solicitada - v_cantidad_ya_recibida;

    IF NEW.cantidad_recibida > v_saldo_pendiente THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: La cantidad recibida supera el saldo pendiente de entrega para este producto.';
    END IF;
END$$

-- ------------------------------------------------------------------------------
-- E. Trigger 'trg_recepcion_actualizar_inventario' (AFTER INSERT ON detalle_recepcion_compra)
--    Motor exclusivo de incremento de stock, kárdex y acumulador de compra.
-- ------------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_recepcion_actualizar_inventario$$

CREATE TRIGGER trg_recepcion_actualizar_inventario
AFTER INSERT ON detalle_recepcion_compra
FOR EACH ROW
BEGIN
    DECLARE v_id_usuario INT;
    DECLARE v_id_compra INT;
    DECLARE v_filas_inventario INT DEFAULT 0;

    -- Identificar usuario responsable y compra
    SELECT id_usuario, id_compra
    INTO v_id_usuario, v_id_compra
    FROM recepciones_compra
    WHERE id_recepcion = NEW.id_recepcion;

    -- 1. Actualizar acumulador en detalle_compra
    UPDATE detalle_compra
    SET cantidad_recibida = cantidad_recibida + NEW.cantidad_recibida
    WHERE id_detalle_compra = NEW.id_detalle_compra;

    -- 2. Incrementar stock en inventario
    UPDATE inventario
    SET stock_actual = stock_actual + NEW.cantidad_recibida
    WHERE id_producto = NEW.id_producto;

    -- Verificar que el registro de inventario existía
    SET v_filas_inventario = ROW_COUNT();
    IF v_filas_inventario = 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Error de integridad: El producto no tiene registro inicial en la tabla de inventario.';
    END IF;

    -- 3. Registrar movimiento en kárdex
    INSERT INTO movimientos_inventario (
        id_producto,
        id_usuario,
        tipo_movimiento,
        cantidad,
        descripcion
    )
    VALUES (
        NEW.id_producto,
        v_id_usuario,
        'ENTRADA',
        NEW.cantidad_recibida,
        CONCAT('Entrada por recepción #', NEW.id_recepcion, ' de compra #', v_id_compra)
    );
END$$

-- ------------------------------------------------------------------------------
-- F. Trigger 'trg_recepcion_actualizar_estado_compra' (AFTER INSERT ON detalle_recepcion_compra)
--    Actualiza el estado de la compra automáticamente:
--    - A 'RECIBIDA' si todos los productos del pedido han sido cubiertos en su totalidad.
--    - A 'PARCIAL' si aún quedan unidades pendientes por recibir.
-- ------------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_recepcion_actualizar_estado_compra$$

CREATE TRIGGER trg_recepcion_actualizar_estado_compra
AFTER INSERT ON detalle_recepcion_compra
FOR EACH ROW
FOLLOWS trg_recepcion_actualizar_inventario
BEGIN
    DECLARE v_id_compra INT;
    DECLARE v_total_items INT;
    DECLARE v_items_completos INT;

    -- Identificar la compra vinculada
    SELECT id_compra
    INTO v_id_compra
    FROM recepciones_compra
    WHERE id_recepcion = NEW.id_recepcion;

    -- Contar total de ítems en el pedido
    SELECT COUNT(*)
    INTO v_total_items
    FROM detalle_compra
    WHERE id_compra = v_id_compra;

    -- Contar ítems cuya cantidad_recibida ya igualó la cantidad solicitada
    SELECT COUNT(*)
    INTO v_items_completos
    FROM detalle_compra
    WHERE id_compra = v_id_compra
      AND cantidad_recibida >= cantidad;

    -- Actualizar estado de la compra
    IF v_items_completos >= v_total_items AND v_total_items > 0 THEN
        UPDATE compras
        SET estado = 'RECIBIDA'
        WHERE id_compra = v_id_compra;
    ELSE
        UPDATE compras
        SET estado = 'PARCIAL'
        WHERE id_compra = v_id_compra;
    END IF;
END$$

-- ------------------------------------------------------------------------------
-- G. Trigger 'trg_compra_validar_anulacion_o_cierre' (BEFORE UPDATE ON compras)
--    1. Impide anular una compra que ya tenga mercancía recibida en bodega.
--    2. Impide marcar manualmente una compra como 'RECIBIDA' si aún tiene saldos pendientes.
-- ------------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_compra_validar_anulacion_o_cierre$$

CREATE TRIGGER trg_compra_validar_anulacion_o_cierre
BEFORE UPDATE ON compras
FOR EACH ROW
BEGIN
    DECLARE v_total_items INT DEFAULT 0;
    DECLARE v_items_completos INT DEFAULT 0;
    DECLARE v_total_recibido INT DEFAULT 0;

    -- 1. Regla de anulación segura: No se puede anular si ya tiene recepciones parciales o totales
    IF NEW.estado = 'ANULADA' AND OLD.estado <> 'ANULADA' THEN
        SELECT COALESCE(SUM(cantidad_recibida), 0)
        INTO v_total_recibido
        FROM detalle_compra
        WHERE id_compra = NEW.id_compra;

        IF v_total_recibido > 0 OR OLD.estado IN ('PARCIAL', 'RECIBIDA') THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Operación bloqueada: No se puede anular una compra que ya tiene mercancía recibida en bodega.';
        END IF;
    END IF;

    -- 2. Regla de cierre manual: No se puede marcar como RECIBIDA si quedan cantidades pendientes
    IF NEW.estado = 'RECIBIDA' AND OLD.estado <> 'RECIBIDA' THEN
        SELECT COUNT(*)
        INTO v_total_items
        FROM detalle_compra
        WHERE id_compra = NEW.id_compra;

        SELECT COUNT(*)
        INTO v_items_completos
        FROM detalle_compra
        WHERE id_compra = NEW.id_compra
          AND cantidad_recibida >= cantidad;

        IF v_items_completos < v_total_items OR v_total_items = 0 THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Operación bloqueada: La compra no puede marcarse como RECIBIDA porque aún tiene productos con cantidades pendientes de entrega física.';
        END IF;
    END IF;
END$$

-- ------------------------------------------------------------------------------
-- H. Trigger 'trg_detalle_compra_validar_modificacion' (BEFORE UPDATE ON detalle_compra)
--    Protege la trazabilidad física y coherencia de inventario:
--    1. Inmutabilidad de referencias maestras (id_compra, id_producto).
--    2. Integridad de cantidades (no negativas, recibida <= solicitada).
--    3. Protección de compras en curso: Si ya existen entregas recibidas:
--       - Impide alterar costo_unitario (preserva costo histórico).
--       - Impide reducir la cantidad solicitada por debajo de lo recibido.
--    4. Conciliación estricta del acumulador:
--       Cualquier mutación de cantidad_recibida debe corresponder exactamente
--       a la suma real registrada en detalle_recepcion_compra, garantizando
--       que trg_recepcion_actualizar_inventario opere sin obstáculos mientras
--       se bloquea cualquier manipulación directa o arbitraria.
-- ------------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_detalle_compra_validar_modificacion$$

CREATE TRIGGER trg_detalle_compra_validar_modificacion
BEFORE UPDATE ON detalle_compra
FOR EACH ROW
BEGIN
    DECLARE v_suma_recepciones INT DEFAULT 0;

    -- 1. Inmutabilidad de referencias maestras
    IF NEW.id_compra <> OLD.id_compra THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación bloqueada: No se puede cambiar la compra asociada a un ítem de detalle.';
    END IF;

    IF NEW.id_producto <> OLD.id_producto THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación bloqueada: No se puede cambiar el producto de un ítem de detalle existente.';
    END IF;

    -- 2. Integridad de cantidades
    IF NEW.cantidad <= 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación bloqueada: La cantidad solicitada debe ser mayor a cero.';
    END IF;

    IF NEW.cantidad_recibida < 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación bloqueada: La cantidad recibida no puede ser negativa.';
    END IF;

    IF NEW.cantidad_recibida > NEW.cantidad THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación bloqueada: La cantidad recibida no puede superar la cantidad solicitada en la orden.';
    END IF;

    -- 3. Protección de compras con recepciones en curso o finalizadas
    IF OLD.cantidad_recibida > 0 THEN
        IF NEW.costo_unitario <> OLD.costo_unitario THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Operación bloqueada: No se puede modificar el costo unitario de un ítem que ya tiene recepciones físicas registradas.';
        END IF;

        IF NEW.cantidad < OLD.cantidad_recibida THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Operación bloqueada: No se puede reducir la cantidad solicitada por debajo de lo ya recibido.';
        END IF;
    END IF;

    -- 4. Conciliación estricta del acumulador
    IF NEW.cantidad_recibida <> OLD.cantidad_recibida THEN
        SELECT COALESCE(SUM(cantidad_recibida), 0)
        INTO v_suma_recepciones
        FROM detalle_recepcion_compra
        WHERE id_detalle_compra = OLD.id_detalle_compra;

        IF NEW.cantidad_recibida <> v_suma_recepciones THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Operación bloqueada: El campo cantidad_recibida solo puede modificarse mediante registros oficiales en detalle_recepcion_compra.';
        END IF;
    END IF;
END$$

-- ------------------------------------------------------------------------------
-- I. Trigger 'trg_detalle_compra_validar_eliminacion' (BEFORE DELETE ON detalle_compra)
--    Impide eliminar ítems si ya tienen entregas físicas recibidas o si la compra no está REGISTRADA.
-- ------------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_detalle_compra_validar_eliminacion$$

CREATE TRIGGER trg_detalle_compra_validar_eliminacion
BEFORE DELETE ON detalle_compra
FOR EACH ROW
BEGIN
    DECLARE v_estado_compra VARCHAR(20);

    IF OLD.cantidad_recibida > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación bloqueada: No se puede eliminar un ítem de compra que ya tiene entregas físicas recibidas en bodega.';
    END IF;

    SELECT estado
    INTO v_estado_compra
    FROM compras
    WHERE id_compra = OLD.id_compra;

    IF v_estado_compra IS NOT NULL AND v_estado_compra <> 'REGISTRADA' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación bloqueada: Solo se pueden eliminar ítems de órdenes de compra en estado REGISTRADA.';
    END IF;
END$$

-- ==============================================================================
-- 6. PROCEDIMIENTO ALMACENADO PARA RECEPCIÓN ATÓMICA COMPLETA O MULTIPRODUCTO
--    DEFINICIÓN EXPRESA DE RESPONSABILIDAD TRANSACCIONAL:
--    - En MySQL no existen transacciones anidadas. Ejecutar 'START TRANSACTION'
--      dentro de un procedimiento invocado desde una transacción JDBC activa
--      provoca un COMMIT implícito irreversible de la transacción externa.
--    - Por diseño y arquitectura limpia:
--      La transacción es responsabilidad exclusiva del llamador (la capa Java
--      mediante CompraDAO o un script SQL externo con delimitadores BEGIN/COMMIT).
--    - Este procedimiento NO ejecuta 'START TRANSACTION', 'COMMIT' ni 'ROLLBACK'.
--      Si cualquier validación o inserción falla, lanza un SIGNAL SQLSTATE '45000'
--      permitiendo que el bloque transaccional externo (ej. Java catch conn.rollback())
--      revierta todas las operaciones sin comprometer la atomicidad.
-- ==============================================================================
DROP PROCEDURE IF EXISTS sp_registrar_recepcion_completa$$

CREATE PROCEDURE sp_registrar_recepcion_completa(
    IN  p_id_compra         INT,
    IN  p_id_usuario        INT,
    IN  p_numero_remision   VARCHAR(50),
    IN  p_observaciones     VARCHAR(255),
    IN  p_detalles_json     JSON,
    OUT p_id_recepcion      INT
)
proc: BEGIN
    DECLARE v_total_items INT DEFAULT 0;
    DECLARE v_items_unicos INT DEFAULT 0;
    DECLARE v_campos_invalidos INT DEFAULT 0;
    DECLARE v_tipos_no_integer INT DEFAULT 0;
    DECLARE v_cantidades_invalidas INT DEFAULT 0;
    DECLARE v_ids_invalidos INT DEFAULT 0;
    DECLARE v_items_ajenos INT DEFAULT 0;
    DECLARE v_productos_discrepantes INT DEFAULT 0;
    DECLARE v_estado_compra VARCHAR(20);

    -- --------------------------------------------------------------------------
    -- A. VALIDACIÓN ESTRICTA DE TIPOS Y FORMATOS DEL DOCUMENTO JSON
    --    Valida que id_detalle_compra, id_producto y cantidad_recibida sean valores
    --    JSON de tipo INTEGER exacto (rechaza textos, decimales, arreglos, objetos y nulos).
    -- --------------------------------------------------------------------------
    -- 1. Validar presencia y tipo raíz de documento JSON
    IF p_detalles_json IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: El documento JSON de detalles no puede ser nulo.';
    END IF;

    IF JSON_TYPE(p_detalles_json) <> 'ARRAY' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: El parámetro de detalles debe ser un arreglo JSON [ {...}, {...} ].';
    END IF;

    -- 2. Validar que el arreglo contenga al menos un detalle
    IF JSON_LENGTH(p_detalles_json) = 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: La recepción debe contener al menos un producto recibido.';
    END IF;

    -- 3. Validar presencia estricta de campos obligatorios (sin nulos)
    SELECT COUNT(*)
    INTO v_campos_invalidos
    FROM JSON_TABLE(
        p_detalles_json,
        '$[*]' COLUMNS (
            id_det_raw   JSON PATH '$.id_detalle_compra',
            id_prod_raw  JSON PATH '$.id_producto',
            cant_raw     JSON PATH '$.cantidad_recibida'
        )
    ) AS jt
    WHERE jt.id_det_raw IS NULL OR JSON_TYPE(jt.id_det_raw) = 'NULL'
       OR jt.id_prod_raw IS NULL OR JSON_TYPE(jt.id_prod_raw) = 'NULL'
       OR jt.cant_raw IS NULL OR JSON_TYPE(jt.cant_raw) = 'NULL';

    IF v_campos_invalidos > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: Todos los ítems deben especificar id_detalle_compra, id_producto y cantidad_recibida sin valores nulos.';
    END IF;

    -- 4. Validar tipo JSON estricto: id_detalle_compra, id_producto y cantidad_recibida deben ser INTEGER
    --    Rechaza explícitamente cadenas (ej. "5"), decimales (DOUBLE/DECIMAL ej. 5.5), arreglos y objetos
    SELECT COUNT(*)
    INTO v_tipos_no_integer
    FROM JSON_TABLE(
        p_detalles_json,
        '$[*]' COLUMNS (
            id_det_json   JSON PATH '$.id_detalle_compra',
            id_prod_json  JSON PATH '$.id_producto',
            cant_json     JSON PATH '$.cantidad_recibida'
        )
    ) AS jt
    WHERE JSON_TYPE(jt.id_det_json) <> 'INTEGER'
       OR JSON_TYPE(jt.id_prod_json) <> 'INTEGER'
       OR JSON_TYPE(jt.cant_json) <> 'INTEGER';

    IF v_tipos_no_integer > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: id_detalle_compra, id_producto y cantidad_recibida deben ser valores JSON de tipo INTEGER exacto (no se permiten textos, decimales ni estructuras).';
    END IF;

    -- 5. Validar que los identificadores enteros sean estrictamente positivos (> 0)
    SELECT COUNT(*)
    INTO v_ids_invalidos
    FROM JSON_TABLE(
        p_detalles_json,
        '$[*]' COLUMNS (
            id_det_val    INT PATH '$.id_detalle_compra',
            id_prod_val   INT PATH '$.id_producto'
        )
    ) AS jt
    WHERE jt.id_det_val <= 0 OR jt.id_prod_val <= 0;

    IF v_ids_invalidos > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: Los identificadores id_detalle_compra e id_producto deben ser enteros mayores a cero.';
    END IF;

    -- 6. Validar que las cantidades enteras sean estrictamente mayores a cero (> 0)
    SELECT COUNT(*)
    INTO v_cantidades_invalidas
    FROM JSON_TABLE(
        p_detalles_json,
        '$[*]' COLUMNS (
            cantidad_recibida INT PATH '$.cantidad_recibida'
        )
    ) AS jt
    WHERE jt.cantidad_recibida <= 0;

    IF v_cantidades_invalidas > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: La cantidad_recibida debe ser un entero estrictamente mayor a cero.';
    END IF;

    -- 7. Validar que no existan identificadores de detalle duplicados en el documento
    SELECT COUNT(*), COUNT(DISTINCT jt.id_detalle_compra)
    INTO v_total_items, v_items_unicos
    FROM JSON_TABLE(
        p_detalles_json,
        '$[*]' COLUMNS (
            id_detalle_compra INT PATH '$.id_detalle_compra'
        )
    ) AS jt;

    IF v_total_items <> v_items_unicos THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: No se permiten ítems duplicados de detalle_compra en una misma recepción.';
    END IF;

    -- 8. Validar que todos los detalles pertenezcan estrictamente a la compra especificada
    SELECT COUNT(*)
    INTO v_items_ajenos
    FROM JSON_TABLE(
        p_detalles_json,
        '$[*]' COLUMNS (
            id_detalle_compra INT PATH '$.id_detalle_compra'
        )
    ) AS jt
    LEFT JOIN detalle_compra dc ON jt.id_detalle_compra = dc.id_detalle_compra
    WHERE dc.id_detalle_compra IS NULL OR dc.id_compra <> p_id_compra;

    IF v_items_ajenos > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: Uno o más ítems no pertenecen a la orden de compra especificada.';
    END IF;

    -- 9. Validar correspondencia exacta entre id_producto y el ítem de detalle_compra
    SELECT COUNT(*)
    INTO v_productos_discrepantes
    FROM JSON_TABLE(
        p_detalles_json,
        '$[*]' COLUMNS (
            id_detalle_compra INT PATH '$.id_detalle_compra',
            id_producto       INT PATH '$.id_producto'
        )
    ) AS jt
    INNER JOIN detalle_compra dc ON jt.id_detalle_compra = dc.id_detalle_compra
    WHERE jt.id_producto <> dc.id_producto;

    IF v_productos_discrepantes > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: Discrepancia entre el producto indicado y el registrado en la orden de compra.';
    END IF;

    -- --------------------------------------------------------------------------
    -- B. OPERACIONES DE RECEPCIÓN (Participante de la transacción del llamador)
    -- --------------------------------------------------------------------------
    -- 1. Nivel 1 de bloqueo: Cabecera 'compras' con FOR UPDATE
    SELECT estado
    INTO v_estado_compra
    FROM compras
    WHERE id_compra = p_id_compra
    FOR UPDATE;

    IF v_estado_compra IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: La compra especificada no existe.';
    END IF;

    IF v_estado_compra = 'ANULADA' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: No se pueden registrar recepciones en una compra ANULADA.';
    END IF;

    IF v_estado_compra = 'RECIBIDA' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operación rechazada: La compra ya se encuentra RECIBIDA en su totalidad y cerrada.';
    END IF;

    -- 2. Inserción de cabecera en recepciones_compra
    INSERT INTO recepciones_compra (
        id_compra,
        id_usuario,
        numero_remision,
        observaciones
    )
    VALUES (
        p_id_compra,
        p_id_usuario,
        p_numero_remision,
        p_observaciones
    );

    SET p_id_recepcion = LAST_INSERT_ID();

    -- 3. Inserción de detalles ordenados por id_detalle_compra ASC para evitar interbloqueos
    --    Cada fila insertada activa en cascada:
    --    - trg_recepcion_validar_cantidad (bloqueo FOR UPDATE de detalle_compra y validación de saldo),
    --    - trg_recepcion_actualizar_inventario (UPDATE inventario, verificación ROW_COUNT() = 1 y kárdex),
    --    - trg_recepcion_actualizar_estado_compra (transición automática a PARCIAL o RECIBIDA).
    INSERT INTO detalle_recepcion_compra (
        id_recepcion,
        id_detalle_compra,
        id_producto,
        cantidad_recibida,
        observaciones
    )
    SELECT
        p_id_recepcion,
        jt.id_detalle_compra,
        jt.id_producto,
        jt.cantidad_recibida,
        jt.observaciones
    FROM JSON_TABLE(
        p_detalles_json,
        '$[*]' COLUMNS (
            id_detalle_compra INT          PATH '$.id_detalle_compra',
            id_producto       INT          PATH '$.id_producto',
            cantidad_recibida INT          PATH '$.cantidad_recibida',
            observaciones     VARCHAR(255) PATH '$.observaciones'
        )
    ) AS jt
    ORDER BY jt.id_detalle_compra ASC;

END$$

DELIMITER ;
