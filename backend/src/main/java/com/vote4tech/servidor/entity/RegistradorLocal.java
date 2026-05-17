package com.vote4tech.servidor.entity;

import jakarta.persistence.*;
import lombok.*;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "registrador_local")
public class RegistradorLocal {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registrador")
    private Long idRegistrador;

    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "nombre", nullable = false, length = 64)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
