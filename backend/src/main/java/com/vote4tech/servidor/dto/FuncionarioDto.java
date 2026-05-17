package com.vote4tech.servidor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FuncionarioDto {
    private String cedula;
    private String nombre;
    private String password;
    private Boolean activo;
}
