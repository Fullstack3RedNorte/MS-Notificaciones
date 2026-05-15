package cl.rednorte.ms_notificaciones.controller;

import cl.rednorte.ms_notificaciones.enums.EstadoNotificacion;
import cl.rednorte.ms_notificaciones.model.entity.Notificacion;
import cl.rednorte.ms_notificaciones.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    @GetMapping
    public ResponseEntity<Page<Notificacion>> listar(
            @RequestParam String rutDestinatario,
            @RequestParam(required = false) EstadoNotificacion estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(
            page, size, Sort.by("fechaCreacion").descending());

        return ResponseEntity.ok(
            notificacionService.listar(rutDestinatario, estado, pageable)
        );
    }
}