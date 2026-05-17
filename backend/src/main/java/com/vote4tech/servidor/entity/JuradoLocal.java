package com.vote4tech.servidor.entity;

import jakarta.persistence.*;
import lombok.*;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "jurado_local")
public class JuradoLocal {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_jurado")
    private Long idJurado;

    @Column(name = "cedula", nullable = false, unique = true, length = 32)
    private String cedula;

    @Column(name = "nombre", nullable = false, length = 64)
    private String nombre;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
