package cl.rednorte.ms_notificaciones.model.entity;

import cl.rednorte.ms_notificaciones.enums.EstadoNotificacion;
import cl.rednorte.ms_notificaciones.enums.TipoNotificacion;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notificaciones")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String rutDestinatario;

    @Column(nullable = false)
    private String emailDestinatario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoNotificacion tipoNotificacion;

    @Column(nullable = false, length = 1000)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoNotificacion estado = EstadoNotificacion.PENDIENTE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column
    private LocalDateTime fechaEnvio;

    @Column(nullable = false)
    private Integer intentos = 0;

    @Column
    private LocalDateTime fechaUltimoIntento;

    @Column
    private String motivoFallo;

    @PrePersist
    protected void onCreate() {
        fechaCreacion = LocalDateTime.now();
    }
}