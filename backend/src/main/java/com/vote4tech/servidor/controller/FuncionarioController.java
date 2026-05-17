package com.vote4tech.servidor.controller;

import com.vote4tech.servidor.dto.FuncionarioDto;
import com.vote4tech.servidor.entity.FuncionarioLocal;
import com.vote4tech.servidor.repository.FuncionarioLocalRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/funcionario")
@RequiredArgsConstructor
@Tag(name = "Funcionario", description = "Funcionarios de voto domiciliario")
public class FuncionarioController {

    private final FuncionarioLocalRepository funcionarioRepo;

    @GetMapping("/activos")
    @Operation(summary = "Listar funcionarios activos (para sincronización domicilio)")
    public ResponseEntity<List<FuncionarioDto>> getActivos() {
        List<FuncionarioDto> result = funcionarioRepo.findByActivoTrue()
                .stream()
                .map(f -> new FuncionarioDto(f.getCedula(), f.getNombre(), f.getPassword(), f.getActivo()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
