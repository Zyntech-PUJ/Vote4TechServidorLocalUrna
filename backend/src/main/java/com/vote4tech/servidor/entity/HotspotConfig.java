package com.vote4tech.servidor.entity;

import jakarta.persistence.*;
import lombok.*;

@Data @Entity @Builder @AllArgsConstructor @NoArgsConstructor
@Table(name = "hotspot_config")
public class HotspotConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ssid", length = 64)
    private String ssid;

    @Column(name = "password", length = 128)
    private String password;

    @Column(name = "canal", nullable = false)
    private Integer canal = 6;

    @Column(name = "puerto", nullable = false)
    private Integer puerto = 8081;
}
