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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VotoService {

    private final CiudadanoRepository ciudadanoRepository;
    private final EleccionRepository eleccionRepository;
    private final MesaRepository mesaRepository;
    private final YaVotoRepository yaVotoRepository;
    private final CouchDbService couchDbService;

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

        // 5. Verificar si ya votó
        if (yaVotoRepository.existsByCedulaAndIdEleccion(dto.getCedula(), dto.getIdEleccion()))
            throw new BusinessException("La cédula " + dto.getCedula() + " ya votó en esta elección.");

        String votoId = UUID.randomUUID().toString();
        LocalDateTime ahora = LocalDateTime.now();

        // 6. Guardar voto anónimo en CouchDB
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

        // 7. Registrar que ya votó en PostgreSQL local
        yaVotoRepository.save(YaVoto.builder()
                .cedula(dto.getCedula())
                .idEleccion(dto.getIdEleccion())
                .timestamp(ahora)
                .build());

        return votoId;
    }

    public boolean yaVoto(String cedula, Long idEleccion) {
        return yaVotoRepository.existsByCedulaAndIdEleccion(cedula, idEleccion);
    }
}
