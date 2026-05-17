package com.vote4tech.servidor.dto;

import lombok.Data;

@Data
public class LoginRequestDto {
    private String username; // cedula para jurado/funcionario, username para registrador
    private String password;
}
