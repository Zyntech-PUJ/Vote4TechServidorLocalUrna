package com.vote4tech.servidor.couchdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vote4tech.servidor.enums.TipoMesa;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class CouchDbService {

    @Value("${couchdb.url}")
    private String couchDbUrl;

    @Value("${couchdb.username}")
    private String couchDbUsername;

    @Value("${couchdb.password}")
    private String couchDbPassword;

    @Value("${couchdb.database.urna:votos_urna}")
    private String dbUrna;

    private RestClient restClient;
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        String credentials = couchDbUsername + ":" + couchDbPassword;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        this.restClient = RestClient.builder()
                .baseUrl(couchDbUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ensureDatabaseExists(dbUrna);
    }

    private void ensureDatabaseExists(String db) {
        try {
            restClient.put().uri("/" + db).retrieve().toBodilessEntity();
        } catch (Exception ignored) {
            // La base de datos ya existe — se continúa
        }
    }

    public String saveVoto(VotoDocument voto) {
        try {
            Map<String, Object> doc = Map.of(
                    "_id",              voto.getId(),
                    "idEleccion",       voto.getIdEleccion(),
                    "idMesa",           voto.getIdMesa(),
                    "tipoMesa",         voto.getTipoMesa() != null ? voto.getTipoMesa().name() : "URNA",
                    "idCentroVotacion", voto.getIdCentroVotacion() != null ? voto.getIdCentroVotacion() : 0L,
                    "tipoSeleccion",    voto.getTipoSeleccion() != null ? voto.getTipoSeleccion().name() : null,
                    "idSeleccion",      voto.getIdSeleccion(),
                    "timestamp",        voto.getTimestamp() != null ? voto.getTimestamp().toString() : null
            );

            String body = objectMapper.writeValueAsString(doc);
            restClient.put()
                    .uri("/" + dbUrna + "/" + voto.getId())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            return voto.getId();
        } catch (Exception e) {
            throw new RuntimeException("Error al guardar voto en CouchDB: " + e.getMessage(), e);
        }
    }
}
