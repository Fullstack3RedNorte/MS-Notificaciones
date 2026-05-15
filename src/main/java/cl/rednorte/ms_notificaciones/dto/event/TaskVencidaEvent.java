package cl.rednorte.ms_notificaciones.dto.event;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskVencidaEvent {

    private String evento;
    private LocalDateTime fechaEvento;
    private String rutAdministrativo;
    private String emailAdministrativo;
    private String especialidad;
    private String medico;
    private LocalDateTime fechaHoraInicio;
    private LocalDateTime fechaHoraFin;
    private Long taskId;
}