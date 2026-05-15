package cl.rednorte.ms_notificaciones.service.impl;

import cl.rednorte.ms_notificaciones.dto.event.CitaAsignadaEvent;
import cl.rednorte.ms_notificaciones.dto.event.CitaCanceladaEvent;
import cl.rednorte.ms_notificaciones.dto.event.TaskVencidaEvent;
import cl.rednorte.ms_notificaciones.enums.EstadoNotificacion;
import cl.rednorte.ms_notificaciones.enums.TipoNotificacion;
import cl.rednorte.ms_notificaciones.model.entity.Notificacion;
import cl.rednorte.ms_notificaciones.repository.NotificacionRepository;
import cl.rednorte.ms_notificaciones.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionServiceImpl implements NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final JavaMailSender mailSender;

    private static final int MAX_INTENTOS = 3;
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Value("${spring.mail.username}")
    private String emailOrigen;

    @Override
    @Transactional
    public void procesarCitaAsignada(CitaAsignadaEvent evento) {
        String mensaje = String.format(
            "Estimado/a paciente,\n\n" +
            "Le informamos que tiene una cita asignada:\n\n" +
            "Especialidad: %s\n" +
            "Médico: %s\n" +
            "Fecha y hora: %s\n\n" +
            "Por favor preséntese con su cédula de identidad.\n\n" +
            "Servicio de Salud RedNorte",
            evento.getEspecialidad(),
            evento.getMedico(),
            evento.getFechaHoraInicio().format(FORMATTER)
        );

        Notificacion notificacion = crearNotificacion(
            evento.getRutPaciente(),
            evento.getEmailPaciente(),
            TipoNotificacion.CITA_ASIGNADA,
            mensaje
        );

        enviarEmail(notificacion);
    }

    @Override
    @Transactional
    public void procesarCitaCancelada(CitaCanceladaEvent evento) {
        String mensaje = String.format(
            "Estimado/a paciente,\n\n" +
            "Le informamos que su cita ha sido cancelada:\n\n" +
            "Especialidad: %s\n" +
            "Médico: %s\n" +
            "Fecha y hora: %s\n" +
            "Motivo: %s\n\n" +
            "Será contactado/a para asignarle una nueva hora.\n\n" +
            "Servicio de Salud RedNorte",
            evento.getEspecialidad(),
            evento.getMedico(),
            evento.getFechaHoraInicio().format(FORMATTER),
            evento.getMotivo()
        );

        Notificacion notificacion = crearNotificacion(
            evento.getRutPaciente(),
            evento.getEmailPaciente(),
            TipoNotificacion.CITA_CANCELADA,
            mensaje
        );

        enviarEmail(notificacion);
    }

    @Override
    @Transactional
    public void procesarTaskVencida(TaskVencidaEvent evento) {
        String mensaje = String.format(
            "Estimado/a administrativo/a,\n\n" +
            "Le informamos que una task de reasignación ha vencido:\n\n" +
            "Especialidad: %s\n" +
            "Médico: %s\n" +
            "Hora disponible: %s\n" +
            "Task ID: %s\n\n" +
            "Por favor gestione la reasignación manualmente.\n\n" +
            "Sistema RedNorte",
            evento.getEspecialidad(),
            evento.getMedico(),
            evento.getFechaHoraInicio().format(FORMATTER),
            evento.getTaskId()
        );

        Notificacion notificacion = crearNotificacion(
            evento.getRutAdministrativo(),
            evento.getEmailAdministrativo(),
            TipoNotificacion.TASK_VENCIDA,
            mensaje
        );

        enviarEmail(notificacion);
    }

    @Override
    public Page<Notificacion> listar(
            String rutDestinatario,
            EstadoNotificacion estado,
            Pageable pageable) {

        if (estado != null) {
            return notificacionRepository.findByRutDestinatarioAndEstado(
                rutDestinatario, estado, pageable);
        }
        return notificacionRepository.findByRutDestinatario(rutDestinatario, pageable);
    }

    // MÉTODOS PRIVADOS
    private Notificacion crearNotificacion(
            String rutDestinatario,
            String emailDestinatario,
            TipoNotificacion tipo,
            String mensaje) {

        Notificacion notificacion = new Notificacion();
        notificacion.setRutDestinatario(rutDestinatario);
        notificacion.setEmailDestinatario(emailDestinatario);
        notificacion.setTipoNotificacion(tipo);
        notificacion.setMensaje(mensaje);
        notificacion.setEstado(EstadoNotificacion.PENDIENTE);
        notificacion.setIntentos(0);
        return notificacionRepository.save(notificacion);
    }

    private void enviarEmail(Notificacion notificacion) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(emailOrigen);
            mail.setTo(notificacion.getEmailDestinatario());
            mail.setSubject(obtenerAsunto(notificacion.getTipoNotificacion()));
            mail.setText(notificacion.getMensaje());

            mailSender.send(mail);

            notificacion.setEstado(EstadoNotificacion.ENVIADO);
            notificacion.setFechaEnvio(LocalDateTime.now());
            notificacion.setIntentos(notificacion.getIntentos() + 1);
            notificacion.setFechaUltimoIntento(LocalDateTime.now());
            notificacionRepository.save(notificacion);

            log.info("Email enviado correctamente a: {}", notificacion.getEmailDestinatario());

        } catch (Exception e) {
            notificacion.setIntentos(notificacion.getIntentos() + 1);
            notificacion.setFechaUltimoIntento(LocalDateTime.now());
            notificacion.setMotivoFallo(e.getMessage());

            if (notificacion.getIntentos() >= MAX_INTENTOS) {
                notificacion.setEstado(EstadoNotificacion.FALLIDO);
                log.error("Email fallido después de {} intentos: {}",
                    MAX_INTENTOS, notificacion.getEmailDestinatario());
            }

            notificacionRepository.save(notificacion);
            log.error("Error enviando email: {}", e.getMessage());
        }
    }

    private String obtenerAsunto(TipoNotificacion tipo) {
        return switch (tipo) {
            case CITA_ASIGNADA -> "RedNorte — Nueva cita médica asignada";
            case CITA_CANCELADA -> "RedNorte — Cita médica cancelada";
            case TASK_VENCIDA -> "RedNorte — Alerta: Task de reasignación vencida";
        };
    }
}