package com.vote4tech.servidor.controller;

import com.vote4tech.servidor.dto.ServerInfoDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las apps Android consultan este endpoint al conectarse a la LAN
 * para verificar que el servidor está activo y obtener su versión.
 */
@RestController
@RequestMapping("/config")
@Tag(name = "Config", description = "Información del servidor LAN")
public class ConfigController {

    @GetMapping("/ping")
    @Operation(summary = "Verificar que el servidor está activo")
    public ResponseEntity<ServerInfoDto> ping() {
        return ResponseEntity.ok(ServerInfoDto.builder()
                .version("1.0.0")
                .status("OK")
                .timestamp(System.currentTimeMillis())
                .build());
    }
}
