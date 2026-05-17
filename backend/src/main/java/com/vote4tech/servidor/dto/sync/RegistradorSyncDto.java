package com.vote4tech.servidor.dto.sync;

import lombok.Data;

@Data
public class RegistradorSyncDto {
    private Long idRegistrador;
    private String nombre;
    private String usuario;
    private String passwordHash;
}
