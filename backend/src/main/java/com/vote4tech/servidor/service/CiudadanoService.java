package com.vote4tech.servidor.service;

import com.vote4tech.servidor.dto.CiudadanoDto;
import com.vote4tech.servidor.entity.Ciudadano;
import com.vote4tech.servidor.exception.ResourceNotFoundException;
import com.vote4tech.servidor.repository.CiudadanoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CiudadanoService {

    private final CiudadanoRepository ciudadanoRepository;

    public CiudadanoDto findByCedula(String cedula) {
        Ciudadano c = ciudadanoRepository.findByCedula(cedula)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ciudadano no encontrado con cédula: " + cedula));
        CiudadanoDto dto = new CiudadanoDto();
        dto.setIdCiudadano(c.getIdCiudadano());
        dto.setNombre(c.getNombre());
        dto.setCedula(c.getCedula());
        dto.setGenero(c.getGenero());
        dto.setVotoObligatorio(c.getVotoObligatorio());
        return dto;
    }
}
