package com.vote4tech.servidor.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EleccionDto {
    private Long idEleccion;
    private String nombre;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFinalizacion;
    private String tipo;
    private Boolean listaAbierta;
    private String estado;
    private List<CandidatoDto> candidatos;
}
