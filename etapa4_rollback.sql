-- ==============================================================================
-- ETAPA 4: SCRIPT DE REVERSIÓN SEGURA (ROLLBACK) - PAPELERÍA ROSITA
-- Versión revisada: 8.2 (Resiliencia ante Migraciones Incompletas, DDL MySQL 8.4 y Protección Integral)
-- Objetivo: Revertir la migración de la Etapa 4 sin destruir registros silenciosamente.
-- ==============================================================================
-- ADVERTENCIA CRÍTICA SOBRE ATOMICIDAD DDL EN MYSQL:
-- En el motor MySQL (incluyendo versión 8.4 LTS), las operaciones DDL (tales como
-- ALTER TABLE, DROP TABLE, DROP TRIGGER, DROP PROCEDURE) provocan un COMMIT implícito
-- y NO son transaccionales ni reversibles mediante ROLLBACK estándar.
-- Por tal razón, este script ejecuta obligatoriamente un procedimiento de guardia
-- 'sp_verificar_seguridad_rollback()' ANTES de cualquier instrucción DDL.
-- Si cualquiera de las condiciones de seguridad falla, el script se cancela
-- de inmediato con SIGNAL '45000' SIN haber alterado ninguna estructura ni dato.
-- ==============================================================================
-- CONDICIONES DE SEGURIDAD ESTRICTAS:
-- El procedimiento 'sp_verificar_seguridad_rollback()' detendrá la ejecución
-- mediante SIGNAL SQLSTATE '45000' si detecta CUALQUIERA de las siguientes condiciones:
--   1. Existen recepciones registradas en 'recepciones_compra'.
--   2. Existen detalles registrados en 'detalle_recepcion_compra'.
--   3. Existen compras en estado 'PARCIAL'.
--   4. Existen compras en estado 'REGISTRADA' con productos asociados en 'detalle_compra'
--      (el modelo original impedía insertar detalles en compras no recibidas).
--   5. Existen registros en 'detalle_compra' con 'cantidad_recibida > 0'.
-- ==============================================================================

USE papeleria_rosita;

DELIMITER $$

-- ==============================================================================
-- 1. VERIFICACIÓN DE PROTECCIÓN CONTRA PÉRDIDA DE DATOS E INCOMPATIBILIDAD
-- ==============================================================================
DROP PROCEDURE IF EXISTS sp_verificar_seguridad_rollback$$

CREATE PROCEDURE sp_verificar_seguridad_rollback()
BEGIN
    DECLARE v_total_recepciones INT DEFAULT 0;
    DECLARE v_total_detalles_rec INT DEFAULT 0;
    DECLARE v_compras_parciales INT DEFAULT 0;
    DECLARE v_compras_reg_con_detalle INT DEFAULT 0;
    DECLARE v_detalles_con_recibidos INT DEFAULT 0;
    DECLARE v_tabla_recepciones_existe INT DEFAULT 0;
    DECLARE v_tabla_detalles_rec_existe INT DEFAULT 0;
    DECLARE v_columna_recibida_existe INT DEFAULT 0;

    -- A1. Verificar existencia y registros en 'recepciones_compra' de forma aislada
    SELECT COUNT(*) INTO v_tabla_recepciones_existe
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'papeleria_rosita' AND TABLE_NAME = 'recepciones_compra';

    IF v_tabla_recepciones_existe > 0 THEN
        SELECT COUNT(*) INTO v_total_recepciones FROM recepciones_compra;
        IF v_total_recepciones > 0 THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ROLLBACK DETENIDO (DATOS EXISTENTES): Existen recepciones registradas en recepciones_compra. Exporte y respalde estos registros antes de revertir.';
        END IF;
    END IF;

    -- A2. Verificar existencia y registros en 'detalle_recepcion_compra' de forma aislada
    SELECT COUNT(*) INTO v_tabla_detalles_rec_existe
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'papeleria_rosita' AND TABLE_NAME = 'detalle_recepcion_compra';

    IF v_tabla_detalles_rec_existe > 0 THEN
        SELECT COUNT(*) INTO v_total_detalles_rec FROM detalle_recepcion_compra;
        IF v_total_detalles_rec > 0 THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ROLLBACK DETENIDO (DATOS EXISTENTES): Existen detalles registrados en detalle_recepcion_compra. Exporte y respalde estos registros antes de revertir.';
        END IF;
    END IF;

    -- B. Verificar compras en estado 'PARCIAL' (estado no soportado por el modelo original)
    SELECT COUNT(*) INTO v_compras_parciales
    FROM compras
    WHERE estado = 'PARCIAL';

    IF v_compras_parciales > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK DETENIDO (COMPRAS PARCIALES): Existen compras en estado PARCIAL que no pueden representarse en el modelo original. Debe completar o conciliar estas compras antes de revertir.';
    END IF;

    -- C. Verificar compras 'REGISTRADA' que tienen detalles asociados
    -- En el modelo original, el trigger 'trg_compra_validar_estado' impedía que una compra 'REGISTRADA' tuviera detalles
    SELECT COUNT(DISTINCT c.id_compra) INTO v_compras_reg_con_detalle
    FROM compras c
    INNER JOIN detalle_compra dc ON c.id_compra = dc.id_compra
    WHERE c.estado = 'REGISTRADA';

    IF v_compras_reg_con_detalle > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ROLLBACK DETENIDO (PEDIDOS PENDIENTES): Existen compras pendientes (REGISTRADA) con productos en detalle_compra. El modelo original no permite pedidos pendientes con ítems. Exporte y concilie estas órdenes antes de revertir.';
    END IF;

    -- D. Verificar si existe la columna 'cantidad_recibida' en 'detalle_compra' antes de consultarla
    SELECT COUNT(*) INTO v_columna_recibida_existe
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'papeleria_rosita' 
      AND TABLE_NAME = 'detalle_compra' 
      AND COLUMN_NAME = 'cantidad_recibida';

    IF v_columna_recibida_existe > 0 THEN
        SELECT COUNT(*) INTO v_detalles_con_recibidos
        FROM detalle_compra
        WHERE cantidad_recibida > 0;

        IF v_detalles_con_recibidos > 0 THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ROLLBACK DETENIDO (ENTREGAS PARCIALES EN DETALLES): Existen detalles de compra con cantidades recibidas registradas. Revierta o archive estas compras antes de eliminar la columna de control.';
        END IF;
    END IF;
END$$

DELIMITER ;

-- ==============================================================================
-- 2. EJECUTAR LA VERIFICACIÓN OBLIGATORIA
-- ==============================================================================
-- Si falla alguna de las condiciones de seguridad, el script se aborta de inmediato aquí
-- sin haber ejecutado ninguna operación destructiva (DROP o ALTER).
CALL sp_verificar_seguridad_rollback();

-- Si la verificación pasó (0 incompatibilidades), eliminar el procedimiento de guardia
DROP PROCEDURE IF EXISTS sp_verificar_seguridad_rollback;

-- ==============================================================================
-- 3. ELIMINACIÓN DE PROCEDIMIENTOS ALMACENADOS DE LA ETAPA 4
-- ==============================================================================
DROP PROCEDURE IF EXISTS sp_registrar_recepcion_completa;

-- ==============================================================================
-- 4. ELIMINACIÓN DE TRIGGERS CREADOS EN LA ETAPA 4
--    Elimina todos los disparadores añadidos por la migración v8.2
-- ==============================================================================
DROP TRIGGER IF EXISTS trg_detalle_compra_validar_eliminacion;
DROP TRIGGER IF EXISTS trg_detalle_compra_validar_modificacion;
DROP TRIGGER IF EXISTS trg_compra_validar_anulacion_o_cierre;
DROP TRIGGER IF EXISTS trg_recepcion_actualizar_estado_compra;
DROP TRIGGER IF EXISTS trg_recepcion_actualizar_inventario;
DROP TRIGGER IF EXISTS trg_recepcion_validar_cantidad;
DROP TRIGGER IF EXISTS trg_recepcion_cabecera_validar;

-- ==============================================================================
-- 5. ELIMINACIÓN DE TABLAS DE RECEPCIÓN (Orden estricto de claves foráneas)
--    Primero se elimina la tabla dependiente 'detalle_recepcion_compra',
--    y luego la tabla principal 'recepciones_compra'.
-- ==============================================================================
DROP TABLE IF EXISTS detalle_recepcion_compra;
DROP TABLE IF EXISTS recepciones_compra;

-- ==============================================================================
-- 6. RESTAURACIÓN CONDICIONAL DE 'detalle_compra' (Compatibilidad MySQL 8.4)
-- ==============================================================================
DELIMITER $$

DROP PROCEDURE IF EXISTS sp_restaurar_detalle_compra_rollback$$

CREATE PROCEDURE sp_restaurar_detalle_compra_rollback()
BEGIN
    DECLARE v_chk_existe INT DEFAULT 0;
    DECLARE v_col_existe INT DEFAULT 0;

    -- Verificar y eliminar restricción CHECK si existe
    SELECT COUNT(*) INTO v_chk_existe
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = 'papeleria_rosita'
      AND TABLE_NAME = 'detalle_compra'
      AND CONSTRAINT_NAME = 'chk_detalle_compra_recibida';

    IF v_chk_existe > 0 THEN
        ALTER TABLE detalle_compra DROP CONSTRAINT chk_detalle_compra_recibida;
    END IF;

    -- Verificar y eliminar columna cantidad_recibida si existe
    SELECT COUNT(*) INTO v_col_existe
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'papeleria_rosita'
      AND TABLE_NAME = 'detalle_compra'
      AND COLUMN_NAME = 'cantidad_recibida';

    IF v_col_existe > 0 THEN
        ALTER TABLE detalle_compra DROP COLUMN cantidad_recibida;
    END IF;
END$$

DELIMITER ;

CALL sp_restaurar_detalle_compra_rollback();
DROP PROCEDURE IF EXISTS sp_restaurar_detalle_compra_rollback;

-- ==============================================================================
-- 7. RESTAURACIÓN DE LA TABLA 'compras' A SU ESQUEMA ORIGINAL
-- ==============================================================================
ALTER TABLE compras DROP CONSTRAINT chk_compra_estado;
ALTER TABLE compras ALTER COLUMN estado SET DEFAULT 'RECIBIDA';
ALTER TABLE compras ADD CONSTRAINT chk_compra_estado
    CHECK (estado IN ('REGISTRADA', 'RECIBIDA', 'ANULADA'));

-- ==============================================================================
-- 8. RESTAURACIÓN DE LOS TRIGGERS ORIGINALES DE COMPRA
--    Restaura exactamente la lógica previa a la Etapa 4
-- ==============================================================================
DELIMITER $$

-- Restaurar Trigger 1 original: Solo permite detalles si la compra está 'RECIBIDA'
DROP TRIGGER IF EXISTS trg_compra_validar_estado$$

CREATE TRIGGER trg_compra_validar_estado
BEFORE INSERT ON detalle_compra
FOR EACH ROW
BEGIN
    DECLARE v_estado VARCHAR(20);

    SELECT estado
    INTO v_estado
    FROM compras
    WHERE id_compra = NEW.id_compra;

    IF v_estado <> 'RECIBIDA' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT =
        'La compra debe estar RECIBIDA para agregar sus productos al inventario';
    END IF;
END$$

-- Restaurar Trigger 2 original: Aumenta inventario y crea movimiento al insertar detalle
DROP TRIGGER IF EXISTS trg_compra_actualizar_inventario$$

CREATE TRIGGER trg_compra_actualizar_inventario
AFTER INSERT ON detalle_compra
FOR EACH ROW
BEGIN
    DECLARE v_usuario INT;

    SELECT id_usuario
    INTO v_usuario
    FROM compras
    WHERE id_compra = NEW.id_compra;

    UPDATE inventario
    SET stock_actual = stock_actual + NEW.cantidad
    WHERE id_producto = NEW.id_producto;

    INSERT INTO movimientos_inventario (
        id_producto,
        id_usuario,
        tipo_movimiento,
        cantidad,
        descripcion
    )
    VALUES (
        NEW.id_producto,
        v_usuario,
        'ENTRADA',
        NEW.cantidad,
        CONCAT('Entrada por compra número ', NEW.id_compra)
    );
END$$

DELIMITER ;
