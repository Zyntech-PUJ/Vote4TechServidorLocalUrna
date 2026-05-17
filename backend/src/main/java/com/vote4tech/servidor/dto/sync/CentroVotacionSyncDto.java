package com.vote4tech.servidor.dto.sync;

import lombok.Data;

@Data
public class CentroVotacionSyncDto {
    private Long idCentroVotacion;
    private String nombre;
    private String direccion;
    private String ciudad;
    private String departamento;
}
