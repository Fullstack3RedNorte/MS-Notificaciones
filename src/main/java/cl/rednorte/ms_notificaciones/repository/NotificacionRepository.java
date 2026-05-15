package cl.rednorte.ms_notificaciones.repository;

import cl.rednorte.ms_notificaciones.enums.EstadoNotificacion;
import cl.rednorte.ms_notificaciones.model.entity.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    Page<Notificacion> findByRutDestinatario(String rutDestinatario, Pageable pageable);

    Page<Notificacion> findByRutDestinatarioAndEstado(
        String rutDestinatario,
        EstadoNotificacion estado,
        Pageable pageable
    );

    List<Notificacion> findByEstadoAndIntentosLessThan(
        EstadoNotificacion estado,
        Integer maxIntentos
    );
}