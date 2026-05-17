package com.vote4tech.servidor.dto.sync;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ListaSyncDto {
    private Long idLista;
    private String tipo;
    private LocalDateTime fechaCreacion;
    private Long idEleccion;
}
