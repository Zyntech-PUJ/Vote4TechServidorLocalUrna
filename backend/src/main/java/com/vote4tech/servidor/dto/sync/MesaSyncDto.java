package com.vote4tech.servidor.dto.sync;

import lombok.Data;

@Data
public class MesaSyncDto {
    private Long idMesa;
    private Integer numero;
    private String tipo;
    private Boolean activo;
    private Long idCentroVotacion;
}
