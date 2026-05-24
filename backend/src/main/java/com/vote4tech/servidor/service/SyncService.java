package com.vote4tech.servidor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vote4tech.servidor.dto.sync.DatosElectoralesDto;
import com.vote4tech.servidor.dto.sync.RegistradorSyncDto;
import com.vote4tech.servidor.dto.sync.SyncEstadoDto;
import com.vote4tech.servidor.dto.sync.SyncResultDto;
import com.vote4tech.servidor.entity.RegistradorLocal;
import com.vote4tech.servidor.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sincronización bidireccional entre el ServidorLocalUrna y el sistema central.
 *
 * Descarga (VotacionBack REST → local PostgreSQL):
 *   Llama a GET {central.back.url}/sync/datos-electorales y copia los datos
 *   electorales al PostgreSQL local. No requiere acceso directo a la BD central.
 *
 * Subida (local CouchDB → central CouchDB):
 *   Usa la API de replicación de CouchDB para copiar votos_urna al central.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    @Value("${central.back.url:}")
    private String centralBackUrl;

    @Value("${couchdb.url}")
    private String localCouchDbUrl;

    @Value("${couchdb.username}")
    private String localCouchDbUser;

    @Value("${couchdb.password}")
    private String localCouchDbPassword;

    @Value("${central.couchdb.url:}")
    private String centralCouchDbUrl;

    @Value("${central.couchdb.username:admin}")
    private String centralCouchDbUser;

    @Value("${central.couchdb.password:admin123}")
    private String centralCouchDbPassword;

    private final MesaRepository mesaRepo;
    private final RegistradorLocalRepository registradorRepo;
    private final JdbcTemplate jdbcTemplate;
    private final com.vote4tech.servidor.repository.ServidorConfigRepository servidorConfigRepo;
    private final ObjectMapper objectMapper;

    private LocalDateTime ultimaDescarga = null;
    private LocalDateTime ultimaSubida   = null;

    public SyncService(MesaRepository mesaRepo,
                       RegistradorLocalRepository registradorRepo,
                       JdbcTemplate jdbcTemplate,
                       com.vote4tech.servidor.repository.ServidorConfigRepository servidorConfigRepo) {
        this.mesaRepo          = mesaRepo;
        this.registradorRepo   = registradorRepo;
        this.jdbcTemplate      = jdbcTemplate;
        this.servidorConfigRepo = servidorConfigRepo;
        this.objectMapper      = new ObjectMapper().findAndRegisterModules();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Estado
    // ─────────────────────────────────────────────────────────────────────────

    public SyncEstadoDto getEstado() {
        boolean accesible = false;
        if (centralBackUrl != null && !centralBackUrl.isBlank()) {
            try {
                RestClient.builder()
                        .baseUrl(centralBackUrl)
                        .build()
                        .get()
                        .uri("/eleccion/activas")
                        .retrieve()
                        .toBodilessEntity();
                accesible = true;
            } catch (Exception ignored) {}
        }

        int votosUrna = contarDocumentosCouchDb("votos_urna");
        List<Map<String, Object>> votosPorMesa = contarVotosPorMesa();

        return SyncEstadoDto.builder()
                .centralAccesible(accesible)
                .ultimaDescarga(ultimaDescarga != null
                        ? ultimaDescarga.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() : null)
                .ultimaSubida(ultimaSubida != null
                        ? ultimaSubida.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() : null)
                .votosUrnaLocales(votosUrna)
                .votosPorMesa(votosPorMesa)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Descarga: VotacionBack REST API → PostgreSQL local
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SyncResultDto descargar() {
        if (centralBackUrl == null || centralBackUrl.isBlank()) {
            return SyncResultDto.builder()
                    .exitoso(false)
                    .mensaje("CENTRAL_BACK_URL no configurado. Verifique las variables de entorno.")
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        try {
            log.info("Descargando datos electorales desde VotacionBack: {}", centralBackUrl);

            String responseBody = RestClient.builder()
                    .baseUrl(centralBackUrl)
                    .build()
                    .get()
                    .uri("/sync/datos-electorales")
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return SyncResultDto.builder()
                        .exitoso(false)
                        .mensaje("VotacionBack devolvió respuesta vacía en /sync/datos-electorales.")
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            DatosElectoralesDto datos = objectMapper.readValue(responseBody, DatosElectoralesDto.class);

            // Eliminar docs CouchDB locales que ya existen en central (duplicados previos)
            int dupEliminados = limpiarDocumentosLocalesDuplicados();
            if (dupEliminados > 0) log.info("Previo a descarga: {} docs duplicados eliminados del CouchDB local.", dupEliminados);

            // Preservar la asignación de este servidor antes del truncado
            String myServerId = servidorConfigRepo.findById(1L)
                    .map(c -> c.getServerId()).orElse(null);
            Long myCentroAsignado = servidorConfigRepo.findById(1L)
                    .map(c -> c.getIdCentroAsignado()).orElse(null);

            // Limpiar tablas electorales locales y reiniciar secuencias.
            // ya_voto y tablas de auth no se tocan.
            jdbcTemplate.execute(
                "TRUNCATE TABLE candidato, lista, eleccion, mesa, centro_votacion, partido, ciudadano RESTART IDENTITY CASCADE"
            );
            log.info("Tablas locales truncadas.");

            int total = 0;

            // 1. Partidos
            if (datos.getPartidos() != null) {
                for (var p : datos.getPartidos()) {
                    jdbcTemplate.update(
                        "INSERT INTO partido (id_partido, nombre, sigla, logo_url) VALUES (?, ?, ?, ?)",
                        p.getIdPartido(), p.getNombre(), p.getSigla(),
                        p.getLogoUrl() != null ? p.getLogoUrl() : "");
                    total++;
                }
                log.info("Partidos: {}", datos.getPartidos().size());
            }

            // 2. Centros de votación
            if (datos.getCentrosVotacion() != null) {
                for (var cv : datos.getCentrosVotacion()) {
                    jdbcTemplate.update(
                        "INSERT INTO centro_votacion (id_centro_votacion, nombre, direccion, servidor_id) VALUES (?, ?, ?, NULL)",
                        cv.getIdCentroVotacion(), cv.getNombre(),
                        cv.getDireccion() != null ? cv.getDireccion() : "");
                    total++;
                }
                // Restaurar la asignación propia después del truncado
                if (myServerId != null && myCentroAsignado != null) {
                    jdbcTemplate.update(
                        "UPDATE centro_votacion SET servidor_id = ? WHERE id_centro_votacion = ?",
                        myServerId, myCentroAsignado);
                }
                log.info("Centros: {}", datos.getCentrosVotacion().size());
            }

            // 3. Mesas
            if (datos.getMesas() != null) {
                for (var m : datos.getMesas()) {
                    try {
                        jdbcTemplate.update(
                            "INSERT INTO mesa (id_mesa, numero, tipo, activo, id_centro_votacion) VALUES (?, ?, ?, ?, ?)",
                            m.getIdMesa(), m.getNumero(), m.getTipo(),
                            m.getActivo() != null ? m.getActivo() : true,
                            m.getIdCentroVotacion());
                        total++;
                    } catch (Exception e) {
                        log.warn("Mesa {} omitida: {}", m.getIdMesa(), e.getMessage());
                    }
                }
                log.info("Mesas: {}", datos.getMesas().size());
            }

            // 4. Elecciones
            if (datos.getElecciones() != null) {
                for (var e : datos.getElecciones()) {
                    jdbcTemplate.update(
                        "INSERT INTO eleccion (id_eleccion, nombre, fecha_inicio, fecha_finalizacion, " +
                        "fecha_creacion, tipo, lista_abierta, estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        e.getIdEleccion(), e.getNombre(),
                        e.getFechaInicio(), e.getFechaFinalizacion(),
                        e.getFechaCreacion() != null ? e.getFechaCreacion() : LocalDateTime.now(),
                        e.getTipo(), e.getListaAbierta(), e.getEstado());
                    total++;
                }
                log.info("Elecciones: {}", datos.getElecciones().size());
            }

            // 5. Listas
            if (datos.getListas() != null) {
                for (var l : datos.getListas()) {
                    try {
                        jdbcTemplate.update(
                            "INSERT INTO lista (id_lista, tipo, fecha_creacion, id_eleccion) VALUES (?, ?, ?, ?)",
                            l.getIdLista(), l.getTipo(),
                            l.getFechaCreacion() != null ? l.getFechaCreacion() : LocalDateTime.now(),
                            l.getIdEleccion());
                        total++;
                    } catch (Exception e) {
                        log.warn("Lista {} omitida: {}", l.getIdLista(), e.getMessage());
                    }
                }
                log.info("Listas: {}", datos.getListas().size());
            }

            // 6. Candidatos
            if (datos.getCandidatos() != null) {
                for (var c : datos.getCandidatos()) {
                    try {
                        jdbcTemplate.update(
                            "INSERT INTO candidato (id_candidato, nombre, numero, foto_url, activo, id_lista, id_partido) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            c.getIdCandidato(), c.getNombre(), c.getNumero(),
                            c.getFotoUrl() != null ? c.getFotoUrl() : "",
                            c.getActivo() != null ? c.getActivo() : true,
                            c.getIdLista(), c.getIdPartido());
                        total++;
                    } catch (Exception e) {
                        log.warn("Candidato {} omitido: {}", c.getIdCandidato(), e.getMessage());
                    }
                }
                log.info("Candidatos: {}", datos.getCandidatos().size());
            }

            // 7. Ciudadanos
            if (datos.getCiudadanos() != null) {
                for (var c : datos.getCiudadanos()) {
                    jdbcTemplate.update(
                        "INSERT INTO ciudadano (id_ciudadano, nombre, cedula, genero, voto_obligatorio, habilitado_domicilio, tipo_documento) VALUES (?, ?, ?, ?, ?, ?, 'CC')",
                        c.getIdCiudadano(), c.getNombre(), c.getCedula(), c.getGenero(),
                        c.getVotoObligatorio() != null ? c.getVotoObligatorio() : false,
                        c.getHabilitadoDomicilio() != null ? c.getHabilitadoDomicilio() : false);
                    total++;
                }
                log.info("Ciudadanos: {}", datos.getCiudadanos().size());
            }

            // 8. Registradores (upsert por username, no se truncan)
            if (datos.getRegistradores() != null) {
                for (RegistradorSyncDto r : datos.getRegistradores()) {
                    RegistradorLocal reg = registradorRepo.findByUsernameAndActivoTrue(r.getUsuario())
                            .orElse(RegistradorLocal.builder().build());
                    reg.setUsername(r.getUsuario());
                    reg.setPassword(r.getPasswordHash());
                    reg.setNombre(r.getNombre());
                    reg.setActivo(true);
                    registradorRepo.save(reg);
                    total++;
                }
                log.info("Registradores: {}", datos.getRegistradores().size());
            }

            // 9. Votos del CouchDB central → CouchDB local
            int votosDescargados = 0;
            String msgVotos = "";
            if (centralCouchDbUrl != null && !centralCouchDbUrl.isBlank()) {
                try {
                    votosDescargados = descargarVotosDelCentral();
                    msgVotos = String.format(" Votos del central: %d traídos al local.", votosDescargados);
                    log.info("Votos del CouchDB central descargados al local: {}", votosDescargados);
                } catch (Exception ex) {
                    msgVotos = " (Votos del central no disponibles: " + ex.getMessage() + ")";
                    log.warn("No se pudieron descargar votos del central CouchDB: {}", ex.getMessage());
                }
            }

            ultimaDescarga = LocalDateTime.now();
            log.info("Descarga completada: {} registros procesados.", total);

            return SyncResultDto.builder()
                    .exitoso(true)
                    .mensaje("Descarga completada desde VotacionBack." + msgVotos)
                    .timestamp(ultimaDescarga)
                    .registrosProcesados(total)
                    .build();

        } catch (Exception e) {
            log.error("Error durante la descarga desde VotacionBack", e);
            return SyncResultDto.builder()
                    .exitoso(false)
                    .mensaje(clasificarError(e, "descarga"))
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Descarga todos los votos de votos_urna en el CouchDB central al CouchDB local.
     * Idempotente: si un doc ya existe localmente (conflicto), se ignora.
     */
    private int descargarVotosDelCentral() throws Exception {
        String authCentral = Base64.getEncoder().encodeToString(
                (centralCouchDbUser + ":" + centralCouchDbPassword).getBytes(StandardCharsets.UTF_8));

        String docsJson = RestClient.builder()
                .baseUrl(centralCouchDbUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + authCentral)
                .build()
                .get()
                .uri("/votos_urna/_all_docs?include_docs=true")
                .retrieve()
                .body(String.class);

        if (docsJson == null) return 0;

        JsonNode rows = objectMapper.readTree(docsJson).get("rows");
        if (rows == null || !rows.isArray() || rows.isEmpty()) return 0;

        List<Map<String, Object>> docs = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode doc = row.get("doc");
            if (doc == null) continue;
            String id = doc.has("_id") ? doc.get("_id").asText() : "";
            if (id.startsWith("_design")) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> docMap = objectMapper.convertValue(doc, Map.class);
            docMap.remove("_rev");
            docs.add(docMap);
        }

        if (docs.isEmpty()) return 0;

        String authLocal = Base64.getEncoder().encodeToString(
                (localCouchDbUser + ":" + localCouchDbPassword).getBytes(StandardCharsets.UTF_8));

        Map<String, Object> bulkBody = new HashMap<>();
        bulkBody.put("docs", docs);
        String bulkJson = objectMapper.writeValueAsString(bulkBody);

        String bulkResponse = RestClient.builder()
                .baseUrl(localCouchDbUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + authLocal)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri("/votos_urna/_bulk_docs")
                .body(bulkJson)
                .retrieve()
                .body(String.class);

        JsonNode results = objectMapper.readTree(bulkResponse);
        int nuevos = 0, yaExistian = 0;
        if (results.isArray()) {
            for (JsonNode item : results) {
                if (item.has("error") && "conflict".equals(item.get("error").asText())) {
                    yaExistian++;
                } else if (!item.has("error")) {
                    nuevos++;
                }
            }
        }
        log.info("Votos central→local: {} nuevos, {} ya existían localmente", nuevos, yaExistian);
        return nuevos + yaExistian;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Subida: CouchDB local → CouchDB central
    // ─────────────────────────────────────────────────────────────────────────

    public SyncResultDto subir() {
        if (centralCouchDbUrl == null || centralCouchDbUrl.isBlank()) {
            return SyncResultDto.builder()
                    .exitoso(false)
                    .mensaje("central.couchdb.url no configurado — CouchDB central no disponible en este entorno.")
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        try {
            log.info("Iniciando subida de votos al CouchDB central en {}", centralCouchDbUrl);

            // Eliminar docs CouchDB locales que ya existen en central antes de subir
            int dupEliminados = limpiarDocumentosLocalesDuplicados();
            if (dupEliminados > 0) log.info("Previo a subida: {} docs duplicados eliminados del CouchDB local.", dupEliminados);

            int subidos = subirVotosManualmente();
            ultimaSubida = LocalDateTime.now();

            String msg = String.format("Subida completada: %d voto(s) enviados al central.", subidos);
            log.info(msg);

            return SyncResultDto.builder()
                    .exitoso(true)
                    .mensaje(msg)
                    .timestamp(ultimaSubida)
                    .registrosProcesados(subidos)
                    .build();

        } catch (Exception e) {
            log.error("Error durante la subida de votos", e);
            return SyncResultDto.builder()
                    .exitoso(false)
                    .mensaje(clasificarError(e, "subida"))
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Lee todos los docs de votos_urna local y los sube directamente al CouchDB central
     * usando _bulk_docs. Spring Boot hace las llamadas HTTP (no el contenedor CouchDB),
     * lo que evita problemas de red del contenedor hacia el túnel del host.
     *
     * @return número de votos que quedaron en central (nuevos + ya existían)
     * @throws Exception si hay error de red/IO (para que subir() lo clasifique)
     */
    private int subirVotosManualmente() throws Exception {
        // 1. Leer todos los docs del CouchDB local
        String authLocal = Base64.getEncoder().encodeToString(
                (localCouchDbUser + ":" + localCouchDbPassword).getBytes(StandardCharsets.UTF_8));

        String docsJson = RestClient.builder()
                .baseUrl(localCouchDbUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + authLocal)
                .build()
                .get()
                .uri("/votos_urna/_all_docs?include_docs=true")
                .retrieve()
                .body(String.class);

        if (docsJson == null) throw new RuntimeException("CouchDB local no devolvió respuesta");

        JsonNode rows = objectMapper.readTree(docsJson).get("rows");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            log.info("No hay votos locales para subir al central");
            return 0;
        }

        // 2. Filtrar design docs y preparar payload (sin _rev para inserción limpia)
        List<Map<String, Object>> docs = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode doc = row.get("doc");
            if (doc == null) continue;
            String id = doc.has("_id") ? doc.get("_id").asText() : "";
            if (id.startsWith("_design")) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> docMap = objectMapper.convertValue(doc, Map.class);
            docMap.remove("_rev"); // Sin _rev → insert; si ya existe en central → conflict (OK)
            docs.add(docMap);
        }

        if (docs.isEmpty()) return 0;

        // 3. Asegurar que votos_urna existe en el central
        String authCentral = Base64.getEncoder().encodeToString(
                (centralCouchDbUser + ":" + centralCouchDbPassword).getBytes(StandardCharsets.UTF_8));
        try {
            RestClient.builder()
                    .baseUrl(centralCouchDbUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + authCentral)
                    .build()
                    .put()
                    .uri("/votos_urna")
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            String m = ex.getMessage() != null ? ex.getMessage() : "";
            if (!m.contains("412") && !m.contains("file_exists") && !m.contains("already")) {
                log.warn("No se pudo crear votos_urna en central (probablemente ya existe): {}", m);
            }
        }

        // 4. Subir via _bulk_docs
        Map<String, Object> bulkBody = new HashMap<>();
        bulkBody.put("docs", docs);
        String bulkJson = objectMapper.writeValueAsString(bulkBody);

        String bulkResponse = RestClient.builder()
                .baseUrl(centralCouchDbUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + authCentral)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri("/votos_urna/_bulk_docs")
                .body(bulkJson)
                .retrieve()
                .body(String.class);

        // 5. Analizar resultado
        JsonNode results = objectMapper.readTree(bulkResponse);
        int nuevos = 0, yaExistian = 0, errores = 0;
        if (results.isArray()) {
            for (JsonNode item : results) {
                if (item.has("error")) {
                    if ("conflict".equals(item.get("error").asText())) {
                        yaExistian++; // Ya estaba en central — OK
                    } else {
                        errores++;
                        log.warn("Error al subir doc {}: {} — {}",
                                item.has("id") ? item.get("id").asText() : "?",
                                item.get("error").asText(),
                                item.has("reason") ? item.get("reason").asText() : "");
                    }
                } else {
                    nuevos++;
                }
            }
        }
        log.info("_bulk_docs al central: {} nuevos, {} ya existían, {} errores", nuevos, yaExistian, errores);

        if (errores > 0) throw new RuntimeException(
                errores + " voto(s) no pudieron subirse al central (ver logs para detalles)");

        return nuevos + yaExistian;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conflictos: votos locales que ya existen en el CouchDB central
    // ─────────────────────────────────────────────────────────────────────────

    /** Devuelve cuántos documentos de votos_urna local ya existen en el CouchDB central (mismo _id). */
    public int contarConflictos() {
        if (centralCouchDbUrl == null || centralCouchDbUrl.isBlank()) return 0;
        try {
            Set<String> centralIds = getIdsEnCouchDb(centralCouchDbUrl, centralCouchDbUser, centralCouchDbPassword);
            if (centralIds.isEmpty()) return 0;
            Set<String> localIds = getIdsEnCouchDb(localCouchDbUrl, localCouchDbUser, localCouchDbPassword);
            int count = 0;
            for (String id : localIds) { if (centralIds.contains(id)) count++; }
            return count;
        } catch (Exception e) {
            log.warn("No se pudo verificar conflictos: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Elimina del CouchDB local los documentos de votos_urna cuyo _id ya existe en el CouchDB central.
     * Retorna la cantidad eliminada. El ya_voto local NO se toca (sigue protegiendo contra doble voto).
     */
    private int limpiarDocumentosLocalesDuplicados() {
        if (centralCouchDbUrl == null || centralCouchDbUrl.isBlank()) return 0;
        try {
            Set<String> centralIds = getIdsEnCouchDb(centralCouchDbUrl, centralCouchDbUser, centralCouchDbPassword);
            if (centralIds.isEmpty()) return 0;

            String auth = Base64.getEncoder().encodeToString(
                    (localCouchDbUser + ":" + localCouchDbPassword).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            RestClient localClient = RestClient.builder()
                    .baseUrl(localCouchDbUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                    .build();

            String response = localClient.get()
                    .uri("/votos_urna/_all_docs?include_docs=true")
                    .retrieve().body(String.class);
            if (response == null) return 0;

            JsonNode rows = objectMapper.readTree(response).get("rows");
            if (rows == null || !rows.isArray()) return 0;

            int deleted = 0;
            Set<Long> electionsAffected = new HashSet<>();
            Set<String> idsEliminados = new HashSet<>();
            for (JsonNode row : rows) {
                JsonNode doc = row.get("doc");
                if (doc == null) continue;
                String id  = doc.has("_id")  ? doc.get("_id").asText()  : "";
                String rev = doc.has("_rev") ? doc.get("_rev").asText() : "";
                if (id.startsWith("_design") || !centralIds.contains(id)) continue;
                try {
                    localClient.delete()
                            .uri("/votos_urna/" + id + "?rev=" + rev)
                            .retrieve().toBodilessEntity();
                    deleted++;
                    idsEliminados.add(id);
                    if (doc.has("idEleccion") && !doc.get("idEleccion").isNull())
                        electionsAffected.add(doc.get("idEleccion").asLong());
                } catch (Exception ex) {
                    log.warn("No se pudo eliminar doc local {}: {}", id, ex.getMessage());
                }
            }
            if (deleted > 0) log.info("Docs CouchDB locales eliminados por duplicidad con central: {}", deleted);

            // Para cada elección afectada, eliminar ya_voto local (la global tiene prioridad)
            for (Long idEleccion : electionsAffected) {
                int yv = jdbcTemplate.update("DELETE FROM ya_voto WHERE id_eleccion = ?", idEleccion);
                if (yv > 0) log.info("ya_voto local eliminado para elección {} ({} registros) — la BD global tiene prioridad.", idEleccion, yv);
            }

            // Descargar del central las versiones definitivas de los docs que se eliminaron localmente
            if (!idsEliminados.isEmpty()) {
                try {
                    String authCentral = Base64.getEncoder().encodeToString(
                            (centralCouchDbUser + ":" + centralCouchDbPassword).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    List<Map<String, Object>> docsACopiar = new ArrayList<>();
                    for (String id : idsEliminados) {
                        try {
                            String docJson = RestClient.builder()
                                    .baseUrl(centralCouchDbUrl)
                                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + authCentral)
                                    .build()
                                    .get()
                                    .uri("/votos_urna/" + id)
                                    .retrieve()
                                    .body(String.class);
                            if (docJson != null) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> docMap = objectMapper.readValue(docJson, Map.class);
                                docMap.remove("_rev");
                                docsACopiar.add(docMap);
                            }
                        } catch (Exception ex) {
                            log.warn("No se pudo obtener doc {} del central: {}", id, ex.getMessage());
                        }
                    }
                    if (!docsACopiar.isEmpty()) {
                        Map<String, Object> bulk = new HashMap<>();
                        bulk.put("docs", docsACopiar);
                        localClient.post()
                                .uri("/votos_urna/_bulk_docs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(objectMapper.writeValueAsString(bulk))
                                .retrieve()
                                .toBodilessEntity();
                        log.info("Versiones del central restauradas en local tras resolución de conflictos: {}", docsACopiar.size());
                    }
                } catch (Exception ex) {
                    log.warn("No se pudieron restaurar votos del central tras resolución: {}", ex.getMessage());
                }
            }

            return deleted;
        } catch (Exception e) {
            log.warn("Error al limpiar documentos duplicados: {}", e.getMessage());
            return 0;
        }
    }

    private Set<String> getIdsEnCouchDb(String baseUrl, String user, String password) {
        try {
            String auth = Base64.getEncoder().encodeToString(
                    (user + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String response = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                    .build()
                    .get()
                    .uri("/votos_urna/_all_docs?include_docs=false")
                    .retrieve().body(String.class);
            if (response == null) return Set.of();
            JsonNode rows = objectMapper.readTree(response).get("rows");
            Set<String> ids = new HashSet<>();
            if (rows != null && rows.isArray()) {
                for (JsonNode row : rows) {
                    String id = row.has("id") ? row.get("id").asText() : "";
                    if (!id.startsWith("_design")) ids.add(id);
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("No se pudo obtener IDs de CouchDB {}: {}", baseUrl, e.getMessage());
            return Set.of();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String clasificarError(Exception e, String operacion) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        Throwable cause = e.getCause();
        String causeMsg = cause != null && cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
        String full = msg + " " + causeMsg;
        if (full.contains("connection refused") || full.contains("connect") || full.contains("timeout")
                || full.contains("i/o error") || full.contains("network") || full.contains("unreachable")) {
            return "Sin conexión al sistema central durante " + operacion +
                   ". Verifique que VotacionBack esté accesible. (" + e.getClass().getSimpleName() + ")";
        }
        if (full.contains("password") || full.contains("authentication") || full.contains("unauthorized")
                || full.contains("401") || full.contains("auth")) {
            return "Error de autenticación con el sistema central durante " + operacion +
                   ". Verifique las credenciales configuradas.";
        }
        if (full.contains("database") && (full.contains("does not exist") || full.contains("unknown"))) {
            return "Base de datos no encontrada en el sistema central durante " + operacion +
                   ". Verifique el nombre de la BD en la configuración.";
        }
        return "Error durante " + operacion + ": " + e.getMessage();
    }

    private int contarDocumentosCouchDb(String db) {
        try {
            String auth = Base64.getEncoder().encodeToString(
                    (localCouchDbUser + ":" + localCouchDbPassword).getBytes(StandardCharsets.UTF_8));
            String response = RestClient.builder()
                    .baseUrl(localCouchDbUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                    .build()
                    .get()
                    .uri("/" + db)
                    .retrieve()
                    .body(String.class);
            if (response == null) return 0;
            JsonNode node = objectMapper.readTree(response);
            return node.has("doc_count") ? node.get("doc_count").asInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<Map<String, Object>> contarVotosPorMesa() {
        try {
            String auth = Base64.getEncoder().encodeToString(
                    (localCouchDbUser + ":" + localCouchDbPassword).getBytes(StandardCharsets.UTF_8));
            String response = RestClient.builder()
                    .baseUrl(localCouchDbUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                    .build()
                    .get()
                    .uri("/votos_urna/_all_docs?include_docs=true")
                    .retrieve()
                    .body(String.class);
            if (response == null) return List.of();

            JsonNode root = objectMapper.readTree(response);
            JsonNode rows = root.get("rows");
            if (rows == null || !rows.isArray()) return List.of();

            Map<Long, Integer> countByMesa = new LinkedHashMap<>();
            for (JsonNode row : rows) {
                JsonNode doc = row.get("doc");
                if (doc == null) continue;
                String id = doc.has("_id") ? doc.get("_id").asText() : "";
                if (id.startsWith("_design")) continue;
                JsonNode idMesaNode = doc.get("idMesa");
                if (idMesaNode == null || idMesaNode.isNull()) continue;
                countByMesa.merge(idMesaNode.asLong(), 1, Integer::sum);
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<Long, Integer> entry : countByMesa.entrySet()) {
                Map<String, Object> m = new HashMap<>();
                m.put("idMesa", entry.getKey());
                m.put("votos", entry.getValue());
                mesaRepo.findById(entry.getKey()).ifPresent(mesa -> {
                    m.put("numero", mesa.getNumero());
                    m.put("tipo", mesa.getTipo() != null ? mesa.getTipo().name() : "");
                    m.put("centro", mesa.getCentroVotacion() != null ? mesa.getCentroVotacion().getNombre() : "");
                });
                result.add(m);
            }
            return result;
        } catch (Exception e) {
            log.error("Error al contar votos por mesa: {}", e.getMessage());
            return List.of();
        }
    }
}
