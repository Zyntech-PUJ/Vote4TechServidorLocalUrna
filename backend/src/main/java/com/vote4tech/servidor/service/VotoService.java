package com.vote4tech.servidor.service;

import com.vote4tech.servidor.couchdb.CouchDbService;
import com.vote4tech.servidor.couchdb.VotoDocument;
import com.vote4tech.servidor.dto.CreateVotoDto;
import com.vote4tech.servidor.entity.Ciudadano;
import com.vote4tech.servidor.entity.Eleccion;
import com.vote4tech.servidor.entity.Mesa;
import com.vote4tech.servidor.entity.YaVoto;
import com.vote4tech.servidor.enums.EstadoEleccion;
import com.vote4tech.servidor.enums.TipoSeleccion;
import com.vote4tech.servidor.exception.BusinessException;
import com.vote4tech.servidor.exception.ResourceNotFoundException;
import com.vote4tech.servidor.repository.CiudadanoRepository;
import com.vote4tech.servidor.repository.EleccionRepository;
import com.vote4tech.servidor.repository.MesaRepository;
import com.vote4tech.servidor.repository.YaVotoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VotoService {

    private static final Logger log = LoggerFactory.getLogger(VotoService.class);

    private final CiudadanoRepository ciudadanoRepository;
    private final EleccionRepository eleccionRepository;
    private final MesaRepository mesaRepository;
    private final YaVotoRepository yaVotoRepository;
    private final CouchDbService couchDbService;

    @Value("${central.back.url:}")
    private String centralBackUrl;

    @Transactional
    public String votar(CreateVotoDto dto) {
        // 1. Validar ciudadano
        Ciudadano ciudadano = ciudadanoRepository.findByCedula(dto.getCedula())
                .orElseThrow(() -> new ResourceNotFoundException("Ciudadano no encontrado con cédula: " + dto.getCedula()));

        if (Boolean.TRUE.equals(ciudadano.getHabilitadoDomicilio()))
            throw new BusinessException("El ciudadano está habilitado para voto en domicilio, no en urna.");

        // 2. Validar elección activa
        Eleccion eleccion = eleccionRepository.findById(dto.getIdEleccion())
                .orElseThrow(() -> new ResourceNotFoundException("Elección no encontrada: " + dto.getIdEleccion()));

        if (eleccion.getEstado() != EstadoEleccion.EN_CURSO)
            throw new BusinessException("La elección no está en curso. Estado: " + eleccion.getEstado());

        // 3. Validar mesa
        Mesa mesa = mesaRepository.findById(dto.getIdMesa())
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada: " + dto.getIdMesa()));

        // 4. Tipo selección
        TipoSeleccion tipoSeleccion;
        try {
            tipoSeleccion = TipoSeleccion.valueOf(dto.getTipoSeleccion().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Tipo de selección inválido: " + dto.getTipoSeleccion());
        }

        // 5. Verificar doble votación — local + central (cubre todos los casos: reinicio, domicilio cruzado, histórico)
        if (yaVotoRepository.existsByCedulaAndIdEleccion(dto.getCedula(), dto.getIdEleccion()))
            throw new BusinessException("La cédula " + dto.getCedula() + " ya votó en esta elección.");

        if (yaVotoCentral(dto.getCedula(), dto.getIdEleccion()))
            throw new BusinessException("La cédula " + dto.getCedula() + " ya votó en esta elección (registrado en el sistema central).");

        String votoId = UUID.randomUUID().toString();
        LocalDateTime ahora = LocalDateTime.now();

        // 6. Guardar voto anónimo en CouchDB local
        VotoDocument doc = VotoDocument.builder()
                .id(votoId)
                .idEleccion(eleccion.getIdEleccion())
                .idMesa(mesa.getIdMesa())
                .tipoMesa(mesa.getTipo())
                .idCentroVotacion(mesa.getCentroVotacion() != null
                        ? mesa.getCentroVotacion().getIdCentroVotacion() : null)
                .tipoSeleccion(tipoSeleccion)
                .idSeleccion(dto.getIdSeleccion())
                .timestamp(ahora)
                .build();

        couchDbService.saveVoto(doc);

        // 7. Registrar en ya_voto local (protección inmediata sin red)
        yaVotoRepository.save(YaVoto.builder()
                .cedula(dto.getCedula())
                .idEleccion(dto.getIdEleccion())
                .timestamp(ahora)
                .votoId(votoId)
                .build());

        // 8. Registrar en ya_voto central (protección cruzada urna↔domicilio y entre reinicios)
        registrarYaVotoCentral(dto.getCedula(), dto.getIdEleccion());

        return votoId;
    }

    public boolean yaVoto(String cedula, Long idEleccion) {
        if (yaVotoRepository.existsByCedulaAndIdEleccion(cedula, idEleccion)) return true;
        return yaVotoCentral(cedula, idEleccion);
    }

    // Consulta el ya_voto del sistema central. Retorna false si no hay red (fail-open).
    private boolean yaVotoCentral(String cedula, Long idEleccion) {
        if (centralBackUrl == null || centralBackUrl.isBlank()) return false;
        try {
            Boolean result = RestClient.builder()
                    .baseUrl(centralBackUrl)
                    .build()
                    .get()
                    .uri("/voto/ya-voto/" + cedula + "/" + idEleccion)
                    .retrieve()
                    .body(Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("No se pudo verificar ya_voto central para {}: {}", cedula, e.getMessage());
            return false;
        }
    }

    // Registra en central de forma best-effort; si falla, el sync lo corregirá después.
    private void registrarYaVotoCentral(String cedula, Long idEleccion) {
        if (centralBackUrl == null || centralBackUrl.isBlank()) return;
        try {
            RestClient.builder()
                    .baseUrl(centralBackUrl)
                    .build()
                    .post()
                    .uri("/voto/registrar-ya-voto")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"cedula\":\"" + cedula + "\",\"idEleccion\":" + idEleccion + "}")
                    .retrieve()
                    .toBodilessEntity();
            log.info("Ya_voto registrado en central — cedula {} eleccion {}", cedula, idEleccion);
        } catch (Exception e) {
            log.warn("No se pudo registrar ya_voto en central para {} (se reintentará en sync): {}", cedula, e.getMessage());
        }
    }
}
