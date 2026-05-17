package com.vote4tech.servidor.service;

import com.vote4tech.servidor.dto.CiudadanoDto;
import com.vote4tech.servidor.entity.Ciudadano;
import com.vote4tech.servidor.exception.ResourceNotFoundException;
import com.vote4tech.servidor.repository.CiudadanoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CiudadanoService {

    private final CiudadanoRepository ciudadanoRepository;

    public CiudadanoDto findByCedula(String cedula) {
        Ciudadano c = ciudadanoRepository.findByCedula(cedula)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ciudadano no encontrado con cédula: " + cedula));
        return toDto(c);
    }

    public List<CiudadanoDto> findAllDomicilio() {
        return ciudadanoRepository.findByHabilitadoDomicilioTrue()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<CiudadanoDto> findAllUrna() {
        return ciudadanoRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<CiudadanoDto> findAll() {
        return ciudadanoRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private CiudadanoDto toDto(Ciudadano c) {
        CiudadanoDto dto = new CiudadanoDto();
        dto.setIdCiudadano(c.getIdCiudadano());
        dto.setNombre(c.getNombre());
        dto.setCedula(c.getCedula());
        dto.setGenero(c.getGenero());
        dto.setVotoObligatorio(c.getVotoObligatorio());
        dto.setHabilitadoDomicilio(c.getHabilitadoDomicilio());
        dto.setTipoDocumento(c.getTipoDocumento());
        dto.setDireccion(c.getDireccion());
        return dto;
    }
}
