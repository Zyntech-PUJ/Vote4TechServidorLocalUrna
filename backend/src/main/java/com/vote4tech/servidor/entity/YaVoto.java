package com.vote4tech.servidor.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "ya_voto", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"cedula", "id_eleccion"})
})
public class YaVoto {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ya_voto")
    private Long idYaVoto;

    @Column(name = "cedula", nullable = false, length = 32)
    private String cedula;

    @Column(name = "id_eleccion", nullable = false)
    private Long idEleccion;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
}
