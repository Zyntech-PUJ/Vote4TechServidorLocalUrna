package com.vote4tech.servidor.dto;

import lombok.Data;

@Data
public class CiudadanoDto {
    private Long idCiudadano;
    private String nombre;
    private String cedula;
    private String genero;
    private Boolean votoObligatorio;
}
