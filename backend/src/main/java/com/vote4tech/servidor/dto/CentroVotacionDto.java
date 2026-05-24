package com.vote4tech.servidor.dto;

import lombok.Data;

@Data
public class CentroVotacionDto {
    private Long idCentroVotacion;
    private String nombre;
    private String direccion;
    /** DISPONIBLE | PROPIO | OCUPADO */
    private String estado;
    private int totalMesas;
}
