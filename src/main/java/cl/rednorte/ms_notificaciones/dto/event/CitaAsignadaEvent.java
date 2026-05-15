package cl.rednorte.ms_notificaciones.dto.event;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CitaAsignadaEvent {

    private String evento;
    private LocalDateTime fechaEvento;
    private String rutPaciente;
    private String emailPaciente;
    private String especialidad;
    private String medico;
    private LocalDateTime fechaHoraInicio;
    private LocalDateTime fechaHoraFin;
    private Long solicitudId;
}