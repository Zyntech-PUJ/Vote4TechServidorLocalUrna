package com.vote4tech.servidor.dto.sync;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EleccionSyncDto {
    private Long idEleccion;
    private String nombre;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFinalizacion;
    private LocalDateTime fechaCreacion;
    private String tipo;
    private Boolean listaAbierta;
    private String estado;
}
