package com.vote4tech.servidor.dto;

import lombok.Data;

@Data
public class CreateVotoDto {
    private String cedula;
    private Long idEleccion;
    private Long idMesa;
    private String tipoSeleccion;   // "CANDIDATO" | "LISTA"
    private Long idSeleccion;
}
