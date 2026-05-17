package com.vote4tech.servidor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponseDto {
    private boolean exito;
    private String nombre;
    private String mensaje;
}
