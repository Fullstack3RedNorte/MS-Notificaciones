package cl.rednorte.ms_notificaciones.consumer;

import cl.rednorte.ms_notificaciones.dto.event.CitaAsignadaEvent;
import cl.rednorte.ms_notificaciones.dto.event.CitaCanceladaEvent;
import cl.rednorte.ms_notificaciones.dto.event.TaskVencidaEvent;
import cl.rednorte.ms_notificaciones.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacionConsumer {

    private final NotificacionService notificacionService;

    @RabbitListener(queues = "notificaciones.queue")
    public void recibirCitaAsignada(CitaAsignadaEvent evento) {
        log.info("Evento recibido: CitaAsignada para paciente {}", evento.getRutPaciente());
        notificacionService.procesarCitaAsignada(evento);
    }

    @RabbitListener(queues = "notificaciones.queue")
    public void recibirCitaCancelada(CitaCanceladaEvent evento) {
        log.info("Evento recibido: CitaCancelada para paciente {}", evento.getRutPaciente());
        notificacionService.procesarCitaCancelada(evento);
    }

    @RabbitListener(queues = "notificaciones.queue")
    public void recibirTaskVencida(TaskVencidaEvent evento) {
        log.info("Evento recibido: TaskVencida para administrativo {}", evento.getRutAdministrativo());
        notificacionService.procesarTaskVencida(evento);
    }
}