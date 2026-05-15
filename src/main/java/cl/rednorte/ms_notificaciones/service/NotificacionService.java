package cl.rednorte.ms_notificaciones.service;

import cl.rednorte.ms_notificaciones.dto.event.CitaAsignadaEvent;
import cl.rednorte.ms_notificaciones.dto.event.CitaCanceladaEvent;
import cl.rednorte.ms_notificaciones.dto.event.TaskVencidaEvent;
import cl.rednorte.ms_notificaciones.enums.EstadoNotificacion;
import cl.rednorte.ms_notificaciones.model.entity.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificacionService {

    void procesarCitaAsignada(CitaAsignadaEvent evento);
    void procesarCitaCancelada(CitaCanceladaEvent evento);
    void procesarTaskVencida(TaskVencidaEvent evento);
    Page<Notificacion> listar(String rutDestinatario, EstadoNotificacion estado, Pageable pageable);
}