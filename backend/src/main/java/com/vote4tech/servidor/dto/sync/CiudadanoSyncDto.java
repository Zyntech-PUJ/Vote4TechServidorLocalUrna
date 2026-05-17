package com.vote4tech.servidor.dto.sync;

import lombok.Data;

@Data
public class CiudadanoSyncDto {
    private Long idCiudadano;
    private String nombre;
    private String cedula;
    private String genero;
    private Boolean votoObligatorio;
    private Boolean habilitadoDomicilio;
}
