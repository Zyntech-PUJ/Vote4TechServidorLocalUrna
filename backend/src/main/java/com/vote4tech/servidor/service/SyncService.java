package com.vote4tech.servidor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vote4tech.servidor.dto.sync.*;
import com.vote4tech.servidor.entity.*;
import com.vote4tech.servidor.enums.EstadoEleccion;
import com.vote4tech.servidor.enums.TipoEleccion;
import com.vote4tech.servidor.enums.TipoLista;
import com.vote4tech.servidor.enums.TipoMesa;
import com.vote4tech.servidor.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Servicio de sincronización bidireccional entre el ServidorLocalUrna
 * y el sistema central de Vote4Tech.
 *
 * Sincronización de descarga (central → local):
 *   Descarga todos los datos electorales desde VotacionBack central
 *   (GET /sync/datos-electorales) y los persiste en la BD local.
 *
 * Sincronización de subida (local → central):
 *   Usa la API de replicación de CouchDB para copiar todos los votos
 *   locales (votos_urna, votos_domicilio) al CouchDB central.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    @Value("${central.api.url:}")
    private String centralApiUrl;

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

    private final CiudadanoRepository ciudadanoRepo;
    private final EleccionRepository eleccionRepo;
    private final ListaRepository listaRepo;
    private final PartidoRepository partidoRepo;
    private final CandidatoRepository candidatoRepo;
    private final CentroVotacionRepository centroVotacionRepo;
    private final MesaRepository mesaRepo;
    private final RegistradorLocalRepository registradorRepo;
    private final ObjectMapper objectMapper;

    // Timestamps de la última operación (en memoria; se reinician al reiniciar el servicio)
    private LocalDateTime ultimaDescarga = null;
    private LocalDateTime ultimaSubida   = null;

    public SyncService(CiudadanoRepository ciudadanoRepo,
                       EleccionRepository eleccionRepo,
                       ListaRepository listaRepo,
                       PartidoRepository partidoRepo,
                       CandidatoRepository candidatoRepo,
                       CentroVotacionRepository centroVotacionRepo,
                       MesaRepository mesaRepo,
                       RegistradorLocalRepository registradorRepo) {
        this.ciudadanoRepo      = ciudadanoRepo;
        this.eleccionRepo       = eleccionRepo;
        this.listaRepo          = listaRepo;
        this.partidoRepo        = partidoRepo;
        this.candidatoRepo      = candidatoRepo;
        this.centroVotacionRepo = centroVotacionRepo;
        this.mesaRepo           = mesaRepo;
        this.registradorRepo    = registradorRepo;
        this.objectMapper       = new ObjectMapper().findAndRegisterModules();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Estado
    // ─────────────────────────────────────────────────────────────────────────

    public SyncEstadoDto getEstado() {
        boolean accesible = false;
        if (!centralApiUrl.isBlank()) {
            try {
                buildApiClient().get()
                        .uri(centralApiUrl + "/actuator/health")
                        .retrieve()
                        .toBodilessEntity();
                accesible = true;
            } catch (Exception ignored) { }
        }

        int votosUrna = contarDocumentosCouchDb("votos_urna");
        int votosDomicilio = contarDocumentosCouchDb("votos_domicilio");

        return SyncEstadoDto.builder()
                .centralAccesible(accesible)
                .ultimaDescarga(ultimaDescarga != null
                        ? ultimaDescarga.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() : null)
                .ultimaSubida(ultimaSubida != null
                        ? ultimaSubida.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() : null)
                .votosUrnaLocales(votosUrna)
                .votosDomicilioLocales(votosDomicilio)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sincronización de descarga: central API → BD local
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SyncResultDto descargar() {
        if (centralApiUrl == null || centralApiUrl.isBlank()) {
            return SyncResultDto.builder()
                    .exitoso(false)
                    .mensaje("central.api.url no configurado. Verifique las variables de entorno.")
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        try {
            log.info("Iniciando descarga de datos electorales desde {}", centralApiUrl);

            DatosElectoralesDto datos = buildApiClient().get()
                    .uri(centralApiUrl + "/sync/datos-electorales")
                    .retrieve()
                    .body(DatosElectoralesDto.class);

            if (datos == null) {
                return SyncResultDto.builder()
                        .exitoso(false)
                        .mensaje("La API central devolvió una respuesta vacía.")
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            int total = 0;

            // 1. Partidos
            if (datos.getPartidos() != null) {
                for (PartidoSyncDto dto : datos.getPartidos()) {
                    Partido p = partidoRepo.findById(dto.getIdPartido())
                            .orElse(Partido.builder().idPartido(dto.getIdPartido()).build());
                    p.setNombre(dto.getNombre());
                    p.setSigla(dto.getSigla());
                    p.setLogoUrl(dto.getLogoUrl());
                    partidoRepo.save(p);
                    total++;
                }
            }

            // 2. Centros de votación
            if (datos.getCentrosVotacion() != null) {
                for (CentroVotacionSyncDto dto : datos.getCentrosVotacion()) {
                    CentroVotacion cv = centroVotacionRepo.findById(dto.getIdCentroVotacion())
                            .orElse(CentroVotacion.builder().idCentroVotacion(dto.getIdCentroVotacion()).build());
                    cv.setNombre(dto.getNombre());
                    cv.setDireccion(dto.getDireccion() != null ? dto.getDireccion() : "");
                    centroVotacionRepo.save(cv);
                    total++;
                }
            }

            // 3. Mesas
            if (datos.getMesas() != null) {
                for (MesaSyncDto dto : datos.getMesas()) {
                    CentroVotacion cv = centroVotacionRepo.findById(dto.getIdCentroVotacion())
                            .orElse(null);
                    if (cv == null) {
                        log.warn("CentroVotacion {} no encontrado para mesa {}; se omite.", dto.getIdCentroVotacion(), dto.getIdMesa());
                        continue;
                    }
                    Mesa m = mesaRepo.findById(dto.getIdMesa())
                            .orElse(Mesa.builder().idMesa(dto.getIdMesa()).build());
                    m.setNumero(dto.getNumero());
                    m.setTipo(TipoMesa.valueOf(dto.getTipo()));
                    m.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
                    m.setCentroVotacion(cv);
                    mesaRepo.save(m);
                    total++;
                }
            }

            // 4. Elecciones
            if (datos.getElecciones() != null) {
                for (EleccionSyncDto dto : datos.getElecciones()) {
                    Eleccion e = eleccionRepo.findById(dto.getIdEleccion())
                            .orElse(Eleccion.builder().idEleccion(dto.getIdEleccion()).build());
                    e.setNombre(dto.getNombre());
                    e.setFechaInicio(dto.getFechaInicio());
                    e.setFechaFinalizacion(dto.getFechaFinalizacion());
                    e.setFechaCreacion(dto.getFechaCreacion() != null ? dto.getFechaCreacion() : LocalDateTime.now());
                    e.setTipo(TipoEleccion.valueOf(dto.getTipo()));
                    e.setListaAbierta(dto.getListaAbierta() != null ? dto.getListaAbierta() : false);
                    e.setEstado(EstadoEleccion.valueOf(dto.getEstado()));
                    eleccionRepo.save(e);
                    total++;
                }
            }

            // 5. Listas
            if (datos.getListas() != null) {
                for (ListaSyncDto dto : datos.getListas()) {
                    Eleccion eleccion = eleccionRepo.findById(dto.getIdEleccion()).orElse(null);
                    if (eleccion == null) {
                        log.warn("Eleccion {} no encontrada para lista {}; se omite.", dto.getIdEleccion(), dto.getIdLista());
                        continue;
                    }
                    Lista l = listaRepo.findById(dto.getIdLista())
                            .orElse(Lista.builder().idLista(dto.getIdLista()).build());
                    l.setTipo(TipoLista.valueOf(dto.getTipo()));
                    l.setFechaCreacion(dto.getFechaCreacion() != null ? dto.getFechaCreacion() : LocalDateTime.now());
                    l.setEleccion(eleccion);
                    listaRepo.save(l);
                    total++;
                }
            }

            // 6. Candidatos
            if (datos.getCandidatos() != null) {
                for (CandidatoSyncDto dto : datos.getCandidatos()) {
                    Lista lista = listaRepo.findById(dto.getIdLista()).orElse(null);
                    Partido partido = partidoRepo.findById(dto.getIdPartido()).orElse(null);
                    if (lista == null || partido == null) {
                        log.warn("Lista {} o Partido {} no encontrado para candidato {}; se omite.",
                                dto.getIdLista(), dto.getIdPartido(), dto.getIdCandidato());
                        continue;
                    }
                    Candidato c = candidatoRepo.findById(dto.getIdCandidato())
                            .orElse(Candidato.builder().idCandidato(dto.getIdCandidato()).build());
                    c.setNombre(dto.getNombre());
                    c.setNumero(dto.getNumero());
                    c.setFotoUrl(dto.getFotoUrl() != null ? dto.getFotoUrl() : "");
                    c.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
                    c.setLista(lista);
                    c.setPartido(partido);
                    candidatoRepo.save(c);
                    total++;
                }
            }

            // 7. Ciudadanos
            if (datos.getCiudadanos() != null) {
                for (CiudadanoSyncDto dto : datos.getCiudadanos()) {
                    Ciudadano c = ciudadanoRepo.findById(dto.getIdCiudadano())
                            .orElse(Ciudadano.builder().idCiudadano(dto.getIdCiudadano()).build());
                    c.setNombre(dto.getNombre());
                    c.setCedula(dto.getCedula());
                    c.setGenero(dto.getGenero());
                    c.setVotoObligatorio(dto.getVotoObligatorio() != null ? dto.getVotoObligatorio() : true);
                    c.setHabilitadoDomicilio(dto.getHabilitadoDomicilio() != null ? dto.getHabilitadoDomicilio() : false);
                    c.setTipoDocumento("CC");
                    ciudadanoRepo.save(c);
                    total++;
                }
            }

            // 8. Registradores
            if (datos.getRegistradores() != null) {
                for (RegistradorSyncDto dto : datos.getRegistradores()) {
                    RegistradorLocal r = registradorRepo.findByUsernameAndActivoTrue(dto.getUsuario())
                            .orElse(RegistradorLocal.builder().build());
                    r.setUsername(dto.getUsuario());
                    r.setPassword(dto.getPasswordHash());
                    r.setNombre(dto.getNombre() != null ? dto.getNombre() : dto.getUsuario());
                    r.setActivo(true);
                    registradorRepo.save(r);
                    total++;
                }
            }

            ultimaDescarga = LocalDateTime.now();
            log.info("Descarga completada: {} registros procesados.", total);

            return SyncResultDto.builder()
                    .exitoso(true)
                    .mensaje("Descarga completada exitosamente.")
                    .timestamp(ultimaDescarga)
                    .registrosProcesados(total)
                    .build();

        } catch (RestClientException e) {
            log.error("Error de conexión con el sistema central: {}", e.getMessage());
            return SyncResultDto.builder()
                    .exitoso(false)
                    .mensaje("Error de conexión con el sistema central: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Error durante la descarga de datos electorales", e);
            return SyncResultDto.builder()
                    .exitoso(false)
                    .mensaje("Error durante la descarga: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sincronización de subida: CouchDB local → CouchDB central
    // Usa la API de replicación de CouchDB para copiar los votos.
    // ─────────────────────────────────────────────────────────────────────────

    public SyncResultDto subir() {
        if (centralCouchDbUrl == null || centralCouchDbUrl.isBlank()) {
            return SyncResultDto.builder()
                    .exitoso(false)
                    .mensaje("central.couchdb.url no configurado. Verifique las variables de entorno.")
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        try {
            log.info("Iniciando subida de votos de urna al CouchDB central en {}", centralCouchDbUrl);

            // Contar votos locales antes de replicar
            int votosLocales = contarDocumentosCouchDb("votos_urna");

            // Solo replicar votos_urna (los votos de domicilio los sube la app de domicilio)
            boolean okUrna = replicarBaseDatos("votos_urna");

            ultimaSubida = LocalDateTime.now();

            String msg = okUrna
                    ? String.format("Subida completada. votos_urna=%s (%d votos enviados al central)", "OK", votosLocales)
                    : "Subida fallida. No se pudo replicar votos_urna al central.";

            log.info(msg);

            return SyncResultDto.builder()
                    .exitoso(okUrna)
                    .mensaje(msg)
                    .timestamp(ultimaSubida)
                    .registrosProcesados(okUrna ? votosLocales : 0)
                    .build();

        } catch (Exception e) {
            log.error("Error durante la subida de votos", e);
            return SyncResultDto.builder()
                    .exitoso(false)
                    .mensaje("Error durante la subida: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers privados
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Usa la API _replicate de CouchDB para copiar una BD local a la BD central.
     * Se usa el CouchDB local como punto de inicio de la replicación.
     */
    private boolean replicarBaseDatos(String dbName) {
        try {
            String sourceUrl = buildCouchDbAuthUrl(localCouchDbUrl, localCouchDbUser, localCouchDbPassword, dbName);
            String targetUrl = buildCouchDbAuthUrl(centralCouchDbUrl, centralCouchDbUser, centralCouchDbPassword, dbName);

            Map<String, Object> body = new HashMap<>();
            body.put("source", sourceUrl);
            body.put("target", targetUrl);
            body.put("create_target", true);

            String json = objectMapper.writeValueAsString(body);

            String authLocal = Base64.getEncoder().encodeToString(
                    (localCouchDbUser + ":" + localCouchDbPassword).getBytes(StandardCharsets.UTF_8));

            String response = RestClient.builder()
                    .baseUrl(localCouchDbUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + authLocal)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .uri("/_replicate")
                    .body(json)
                    .retrieve()
                    .body(String.class);

            JsonNode result = objectMapper.readTree(response);
            boolean ok = result.has("ok") && result.get("ok").asBoolean();
            log.info("Replicación {}: {}", dbName, ok ? "exitosa" : "falló → " + response);
            return ok;

        } catch (Exception e) {
            log.error("Error al replicar {}: {}", dbName, e.getMessage());
            return false;
        }
    }

    /**
     * Construye URL de CouchDB con credenciales embebidas:
     * http://user:pass@host:port/db
     */
    private String buildCouchDbAuthUrl(String baseUrl, String user, String password, String db) {
        // Insertar credenciales después del esquema
        String url = baseUrl;
        if (!url.contains("@")) {
            int schemeEnd = url.indexOf("://") + 3;
            url = url.substring(0, schemeEnd) + user + ":" + password + "@" + url.substring(schemeEnd);
        }
        return url.endsWith("/") ? url + db : url + "/" + db;
    }

    private RestClient buildApiClient() {
        return RestClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
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
}
