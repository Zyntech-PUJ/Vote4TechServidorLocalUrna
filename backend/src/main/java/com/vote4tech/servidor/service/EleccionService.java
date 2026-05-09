package com.vote4tech.servidor.service;

import com.vote4tech.servidor.dto.CandidatoDto;
import com.vote4tech.servidor.dto.EleccionDto;
import com.vote4tech.servidor.entity.Candidato;
import com.vote4tech.servidor.entity.Eleccion;
import com.vote4tech.servidor.enums.EstadoEleccion;
import com.vote4tech.servidor.exception.ResourceNotFoundException;
import com.vote4tech.servidor.repository.CandidatoRepository;
import com.vote4tech.servidor.repository.EleccionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EleccionService {

    private final EleccionRepository eleccionRepository;
    private final CandidatoRepository candidatoRepository;

    public List<EleccionDto> findActivas() {
        return eleccionRepository.findByEstado(EstadoEleccion.EN_CURSO)
                .stream().map(this::toDto).toList();
    }

    public EleccionDto findById(Long id) {
        Eleccion e = eleccionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Elección no encontrada: " + id));
        EleccionDto dto = toDto(e);
        dto.setCandidatos(getCandidatos(id));
        return dto;
    }

    public List<CandidatoDto> getCandidatos(Long idEleccion) {
        if (!eleccionRepository.existsById(idEleccion))
            throw new ResourceNotFoundException("Elección no encontrada: " + idEleccion);
        return candidatoRepository.findByLista_Eleccion_IdEleccionAndActivoTrue(idEleccion)
                .stream().map(this::toCandidatoDto).toList();
    }

    private EleccionDto toDto(Eleccion e) {
        EleccionDto dto = new EleccionDto();
        dto.setIdEleccion(e.getIdEleccion());
        dto.setNombre(e.getNombre());
        dto.setFechaInicio(e.getFechaInicio());
        dto.setFechaFinalizacion(e.getFechaFinalizacion());
        dto.setTipo(e.getTipo() != null ? e.getTipo().name() : null);
        dto.setListaAbierta(e.getListaAbierta());
        dto.setEstado(e.getEstado() != null ? e.getEstado().name() : null);
        return dto;
    }

    private CandidatoDto toCandidatoDto(Candidato c) {
        CandidatoDto dto = new CandidatoDto();
        dto.setIdCandidato(c.getIdCandidato());
        dto.setNombre(c.getNombre());
        dto.setNumero(c.getNumero());
        dto.setFotoUrl(c.getFotoUrl());
        dto.setActivo(c.getActivo());
        if (c.getLista() != null) dto.setIdLista(c.getLista().getIdLista());
        if (c.getPartido() != null) {
            dto.setIdPartido(c.getPartido().getIdPartido());
            dto.setNombrePartido(c.getPartido().getNombre());
            dto.setSiglaPartido(c.getPartido().getSigla());
            dto.setLogoPartido(c.getPartido().getLogoUrl());
        }
        return dto;
    }
}
