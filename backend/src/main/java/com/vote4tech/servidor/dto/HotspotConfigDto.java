package com.vote4tech.servidor.dto;

import lombok.Data;

@Data
public class HotspotConfigDto {
    private String ssid;
    private String password;
    private Integer canal;
    private Integer puerto;
}
