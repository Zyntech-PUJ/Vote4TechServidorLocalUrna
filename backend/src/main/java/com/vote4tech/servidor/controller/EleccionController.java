package com.vote4tech.servidor.controller;

import com.vote4tech.servidor.dto.CandidatoDto;
import com.vote4tech.servidor.dto.EleccionDto;
import com.vote4tech.servidor.service.EleccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/eleccion")
@RequiredArgsConstructor
@Tag(name = "Eleccion", description = "Consulta de elecciones y candidatos")
public class EleccionController {

    private final EleccionService eleccionService;

    @GetMapping("/activas")
    @Operation(summary = "Obtener elecciones en curso")
    public ResponseEntity<List<EleccionDto>> getActivas() {
        return ResponseEntity.ok(eleccionService.findActivas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una elección con sus candidatos")
    public ResponseEntity<EleccionDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(eleccionService.findById(id));
    }

    @GetMapping("/{id}/candidatos")
    @Operation(summary = "Obtener candidatos de una elección")
    public ResponseEntity<List<CandidatoDto>> getCandidatos(@PathVariable Long id) {
        return ResponseEntity.ok(eleccionService.getCandidatos(id));
    }
}
