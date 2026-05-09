package com.vote4tech.servidor.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ServerInfoDto {
    private String version;
    private String centrNombre;
    private String status;
    private Long timestamp;
}
