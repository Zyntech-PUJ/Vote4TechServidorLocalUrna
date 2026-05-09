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
    id_ciudadano    BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(64) NOT NULL,
    cedula          VARCHAR(32) NOT NULL UNIQUE,
    genero          VARCHAR(1),
    voto_obligatorio BOOLEAN    NOT NULL DEFAULT FALSE
);

-- Registro de quién ya votó (se escribe localmente durante la jornada)
CREATE TABLE IF NOT EXISTS ya_voto (
    id_ya_voto  BIGSERIAL PRIMARY KEY,
    cedula      VARCHAR(32) NOT NULL,
    id_eleccion BIGINT      NOT NULL,
    timestamp   TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ya_voto UNIQUE (cedula, id_eleccion)
);
