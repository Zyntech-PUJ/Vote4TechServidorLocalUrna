-- =============================================================
-- Script de inicialización — PostgreSQL Local (Réplica de Urna)
-- =============================================================
-- Este script crea el esquema que replica la BD central.
-- Los datos de ciudadanos, candidatos, elecciones deben
-- importarse desde la BD central antes de la jornada electoral.
-- =============================================================

CREATE TABLE IF NOT EXISTS partido (
    id_partido BIGSERIAL PRIMARY KEY,
    nombre     VARCHAR(64)  NOT NULL,
    sigla      VARCHAR(16)  NOT NULL,
    logo_url   VARCHAR(512)
);

CREATE TABLE IF NOT EXISTS eleccion (
    id_eleccion         BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR(64)  NOT NULL,
    fecha_inicio        TIMESTAMP    NOT NULL,
    fecha_finalizacion  TIMESTAMP    NOT NULL,
    fecha_creacion      TIMESTAMP    NOT NULL DEFAULT NOW(),
    tipo                VARCHAR(32)  NOT NULL,
    lista_abierta       BOOLEAN      NOT NULL DEFAULT FALSE,
    estado              VARCHAR(32)  NOT NULL DEFAULT 'PROGRAMADA'
);

CREATE TABLE IF NOT EXISTS lista (
    id_lista       BIGSERIAL PRIMARY KEY,
    tipo           VARCHAR(32)  NOT NULL,
    fecha_creacion TIMESTAMP    NOT NULL DEFAULT NOW(),
    id_eleccion    BIGINT       NOT NULL REFERENCES eleccion(id_eleccion)
);

CREATE TABLE IF NOT EXISTS candidato (
    id_candidato BIGSERIAL PRIMARY KEY,
    nombre       VARCHAR(64)   NOT NULL,
    numero       VARCHAR(16)   NOT NULL,
    foto_url     VARCHAR(512)  NOT NULL,
    activo       BOOLEAN       NOT NULL DEFAULT TRUE,
    id_lista     BIGINT        NOT NULL REFERENCES lista(id_lista),
    id_partido   BIGINT        NOT NULL REFERENCES partido(id_partido)
);

CREATE TABLE IF NOT EXISTS centro_votacion (
    id_centro_votacion BIGSERIAL PRIMARY KEY,
    nombre             VARCHAR(128) NOT NULL,
    direccion          VARCHAR(256) NOT NULL
);

CREATE TABLE IF NOT EXISTS mesa (
    id_mesa            BIGSERIAL PRIMARY KEY,
    numero             INTEGER     NOT NULL,
    tipo               VARCHAR(16) NOT NULL,
    activo             BOOLEAN     NOT NULL DEFAULT TRUE,
    id_centro_votacion BIGINT      NOT NULL REFERENCES centro_votacion(id_centro_votacion)
);

CREATE TABLE IF NOT EXISTS ciudadano (
    id_ciudadano         BIGSERIAL PRIMARY KEY,
    nombre               VARCHAR(64)  NOT NULL,
    cedula               VARCHAR(32)  NOT NULL UNIQUE,
    genero               VARCHAR(1),
    voto_obligatorio     BOOLEAN      NOT NULL DEFAULT FALSE,
    habilitado_domicilio BOOLEAN      NOT NULL DEFAULT FALSE,
    tipo_documento       VARCHAR(32)  NOT NULL DEFAULT 'CC',
    direccion            VARCHAR(256)
);

-- Registro de quién ya votó (se escribe localmente durante la jornada)
CREATE TABLE IF NOT EXISTS ya_voto (
    id_ya_voto  BIGSERIAL PRIMARY KEY,
    cedula      VARCHAR(32) NOT NULL,
    id_eleccion BIGINT      NOT NULL,
    timestamp   TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ya_voto UNIQUE (cedula, id_eleccion)
);

-- =============================================================
-- DATOS DE PRUEBA (seed)
-- =============================================================

-- Partidos
INSERT INTO partido (nombre, sigla, logo_url) VALUES
    ('Partido Progresista Nacional', 'PPN', 'https://example.com/logos/ppn.png'),
    ('Alianza Democrática', 'AD',  'https://example.com/logos/ad.png'),
    ('Movimiento Ciudadano Unido', 'MCU', 'https://example.com/logos/mcu.png')
ON CONFLICT DO NOTHING;

-- Centro de votación
INSERT INTO centro_votacion (nombre, direccion) VALUES
    ('Colegio Distrital San Martín', 'Calle 45 # 12-30, Bogotá'),
    ('Universidad Nacional - Bloque B', 'Carrera 30 # 45-03, Bogotá')
ON CONFLICT DO NOTHING;

-- Mesas: 2 de urna y 1 de domicilio por centro
INSERT INTO mesa (numero, tipo, activo, id_centro_votacion) VALUES
    (1,  'URNA',      TRUE, 1),
    (2,  'URNA',      TRUE, 1),
    (10, 'DOMICILIO', TRUE, 1),
    (1,  'URNA',      TRUE, 2),
    (2,  'URNA',      TRUE, 2),
    (10, 'DOMICILIO', TRUE, 2)
ON CONFLICT DO NOTHING;

-- Elección presidencial EN_CURSO
INSERT INTO eleccion (nombre, fecha_inicio, fecha_finalizacion, tipo, lista_abierta, estado) VALUES
    ('Elección Presidencial 2025', NOW() - INTERVAL '1 hour', NOW() + INTERVAL '8 hours', 'PRESIDENCIAL', FALSE, 'EN_CURSO')
ON CONFLICT DO NOTHING;

-- Elección congresional EN_CURSO
INSERT INTO eleccion (nombre, fecha_inicio, fecha_finalizacion, tipo, lista_abierta, estado) VALUES
    ('Elección Congresional 2025', NOW() - INTERVAL '1 hour', NOW() + INTERVAL '8 hours', 'CONGRESIONAL', TRUE, 'EN_CURSO')
ON CONFLICT DO NOTHING;

-- Listas para elección presidencial (id=1, lista cerrada)
INSERT INTO lista (tipo, id_eleccion) VALUES
    ('CERRADA', 1),
    ('CERRADA', 1),
    ('CERRADA', 1)
ON CONFLICT DO NOTHING;

-- Listas para elección congresional (id=2, lista abierta)
INSERT INTO lista (tipo, id_eleccion) VALUES
    ('ABIERTA', 2),
    ('ABIERTA', 2)
ON CONFLICT DO NOTHING;

-- Candidatos presidenciales
INSERT INTO candidato (nombre, numero, foto_url, activo, id_lista, id_partido) VALUES
    ('Carlos Mendoza Torres',  '1', 'https://example.com/fotos/mendoza.png',  TRUE, 1, 1),
    ('Luisa Fernández Vargas', '2', 'https://example.com/fotos/fernandez.png', TRUE, 2, 2),
    ('Andrés Salazar Muñoz',   '3', 'https://example.com/fotos/salazar.png',   TRUE, 3, 3)
ON CONFLICT DO NOTHING;

-- Candidatos congresionales
INSERT INTO candidato (nombre, numero, foto_url, activo, id_lista, id_partido) VALUES
    ('María Camila Rojas',     '101', 'https://example.com/fotos/rojas.png',     TRUE, 4, 1),
    ('Felipe Guerrero Leal',   '102', 'https://example.com/fotos/guerrero.png',   TRUE, 4, 1),
    ('Sandra Patricia Ossa',   '201', 'https://example.com/fotos/ossa.png',       TRUE, 5, 2),
    ('Jorge Elicer Niño',      '202', 'https://example.com/fotos/nino.png',       TRUE, 5, 2)
ON CONFLICT DO NOTHING;

-- Ciudadanos: 4 habilitados para URNA, 4 habilitados para DOMICILIO
INSERT INTO ciudadano (nombre, cedula, genero, voto_obligatorio, habilitado_domicilio, tipo_documento, direccion) VALUES
    ('Ana María García López',   '1000100001', 'F', TRUE,  TRUE,  'CC', 'Calle 12 # 45-67, Bogotá'),
    ('Carlos Eduardo Ramírez',  '1000100002', 'M', FALSE, TRUE,  'CC', 'Carrera 9 # 23-10, Bogotá'),
    ('Luisa Fernánda Ospina',   '1000100003', 'F', TRUE,  TRUE,  'CC', 'Avenida 68 # 11-22, Bogotá'),
    ('Juan Sebastián Morales',  '1000100004', 'M', FALSE, TRUE,  'CC', 'Diagonal 45 # 3-15, Bogotá'),
    ('María Alejandra Torres',  '1000200001', 'F', TRUE,  FALSE, 'CC', 'Calle 80 # 55-20, Bogotá'),
    ('Andrés Felipe Castillo',  '1000200002', 'M', FALSE, FALSE, 'CC', 'Carrera 15 # 88-40, Bogotá'),
    ('Diana Milena Vargas',     '1000200003', 'F', TRUE,  FALSE, 'CC', 'Calle 100 # 14-05, Bogotá'),
    ('Roberto Carlos Niño',     '1000200004', 'M', FALSE, FALSE, 'CC', 'Transversal 20 # 30-11, Bogotá')
ON CONFLICT (cedula) DO NOTHING;

-- =============================================================
-- TABLAS DE AUTENTICACIÓN LOCAL
-- =============================================================

CREATE TABLE IF NOT EXISTS jurado_local (
    id_jurado  BIGSERIAL   PRIMARY KEY,
    cedula     VARCHAR(32) NOT NULL UNIQUE,
    nombre     VARCHAR(64) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    activo     BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS registrador_local (
    id_registrador BIGSERIAL   PRIMARY KEY,
    username       VARCHAR(64) NOT NULL UNIQUE,
    password       VARCHAR(255) NOT NULL,
    nombre         VARCHAR(64) NOT NULL,
    activo         BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS funcionario_local (
    id_funcionario BIGSERIAL   PRIMARY KEY,
    cedula         VARCHAR(32) NOT NULL UNIQUE,
    nombre         VARCHAR(64) NOT NULL,
    password       VARCHAR(255) NOT NULL,
    activo         BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS hotspot_config (
    id       BIGSERIAL    PRIMARY KEY,
    ssid     VARCHAR(64),
    password VARCHAR(128),
    canal    INTEGER      NOT NULL DEFAULT 6,
    puerto   INTEGER      NOT NULL DEFAULT 8081
);

-- Datos de prueba: jurado, registrador y funcionarios
INSERT INTO jurado_local (cedula, nombre, password, activo) VALUES
    ('9999000001', 'Jurado Principal Mesa 1', 'jurado123', TRUE),
    ('9999000002', 'Jurado Principal Mesa 2', 'jurado123', TRUE)
ON CONFLICT (cedula) DO NOTHING;

INSERT INTO registrador_local (username, password, nombre, activo) VALUES
    ('registrador', 'registrador123', 'Registrador Electoral', TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO funcionario_local (cedula, nombre, password, activo) VALUES
    ('8888000001', 'Funcionario Domicilio 1', 'func123', TRUE),
    ('8888000002', 'Funcionario Domicilio 2', 'func123', TRUE)
ON CONFLICT (cedula) DO NOTHING;

INSERT INTO hotspot_config (ssid, password, canal, puerto) VALUES
    ('VotoLocal', 'voto2025', 6, 8081);
