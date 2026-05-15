# MS Notificaciones — Documentación Técnica
**RedNorte | Fullstack III | DuocUC**

---

## Índice

1. [Descripción general](#1-descripción-general)
2. [Arquetipo del microservicio](#2-arquetipo-del-microservicio)
3. [Arquitectura](#3-arquitectura)
4. [Patrones de diseño de software](#4-patrones-de-diseño-de-software)
5. [Stack tecnológico](#5-stack-tecnológico)
6. [Modelo de datos](#6-modelo-de-datos)
7. [Eventos RabbitMQ](#7-eventos-rabbitmq)
8. [Endpoints REST](#8-endpoints-rest)
9. [Configuración](#9-configuración)

---

## 1. Descripción general

El **MS Notificaciones** es el microservicio encargado de gestionar todas las comunicaciones hacia los usuarios del sistema RedNorte. A diferencia de los demás microservicios, es un **consumer puro de RabbitMQ** — no recibe peticiones HTTP directamente del BFF, sino que escucha eventos publicados por otros microservicios y reacciona enviando correos electrónicos.

El canal de comunicación es exclusivamente **email**, ya que durante el proceso de reasignación se asume que el administrativo operativo realizó el llamado telefónico al paciente para confirmar la hora.

### Responsabilidades principales

- Consumir eventos de la cola `notificaciones.queue`
- Enviar correos electrónicos a pacientes y administrativos según el tipo de evento
- Registrar historial completo de notificaciones para auditoría
- Gestionar reintentos automáticos ante fallos técnicos de envío
- Exponer un endpoint de auditoría para consultar el historial de notificaciones

### Lo que NO hace este microservicio

- No recibe peticiones HTTP del frontend directamente
- No gestiona identidades de usuario
- No escribe en otros microservicios
- No envía SMS ni notificaciones push

---

## 2. Arquetipo del microservicio

### Estructura de carpetas

```
ms-notificaciones/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── cl/rednorte/ms_notificaciones/
│   │   │       ├── consumer/           # Listeners de RabbitMQ
│   │   │       ├── controller/         # Endpoint de auditoría
│   │   │       ├── service/            # Interfaz de servicio
│   │   │       │   └── impl/           # Implementación de servicio
│   │   │       ├── repository/         # Interfaz JPA — acceso a datos
│   │   │       ├── model/
│   │   │       │   └── entity/         # Entidad JPA — mapeo a tabla MySQL
│   │   │       ├── dto/
│   │   │       │   └── event/          # DTOs de eventos RabbitMQ
│   │   │       ├── enums/              # Enumeraciones del dominio
│   │   │       ├── config/             # Configuración RabbitMQ
│   │   │       └── MsNotificacionesApplication.java
│   │   └── resources/
│   │       ├── application.yaml        # Configuración principal
│   │       └── application-dev.yaml   # Configuración local (no va a GitHub)
│   └── test/
│       └── java/cl/rednorte/ms_notificaciones/
│           └── service/
├── .gitignore
├── pom.xml
└── README.md
```

### Diferencia clave con otros microservicios

| Carpeta | MS Lista de Espera | MS Notificaciones |
|---------|-------------------|-------------------|
| `controller/` | ✅ Endpoints REST completos | ✅ Solo auditoría |
| `consumer/` | ❌ No aplica | ✅ Listeners RabbitMQ |
| `dto/request/` | ✅ Datos de entrada HTTP | ❌ No aplica |
| `dto/response/` | ✅ Datos de salida HTTP | ❌ No aplica |
| `dto/event/` | ❌ No aplica | ✅ Eventos RabbitMQ |

### Convención de nombres

| Tipo | Convención | Ejemplo |
|------|-----------|---------|
| Entidades | PascalCase | `Notificacion` |
| Repositorios | Entidad + Repository | `NotificacionRepository` |
| Servicios (interfaz) | Entidad + Service | `NotificacionService` |
| Servicios (impl) | Entidad + ServiceImpl | `NotificacionServiceImpl` |
| Consumers | Entidad + Consumer | `NotificacionConsumer` |
| DTOs de evento | Nombre + Event | `CitaAsignadaEvent` |
| Configuraciones | Tecnología + Config | `RabbitMQConfig` |

---

## 3. Arquitectura

### Posición en la arquitectura global

```
MS Reasignación
      │ publica evento
      ▼
notificaciones.queue (RabbitMQ)
      │ consume evento
      ▼
MS Notificaciones (puerto 8087)
      │
      ├── MySQL — db_notificaciones
      └── Gmail SMTP — envío de emails
```

### Flujo interno de un evento

```
Evento RabbitMQ llega a notificaciones.queue
      │
      ▼
NotificacionConsumer     # @RabbitListener detecta el mensaje
      │ deserializa JSON a DTO de evento
      ▼
NotificacionService      # Construye el mensaje de email
      │ llama a enviarEmail()
      ▼
JavaMailSender           # Envía el correo via Gmail SMTP
      │
      ├── Éxito → estado ENVIADO, registra fechaEnvio
      └── Fallo → incrementa intentos, estado FALLIDO si >= 3
      │
      ▼
NotificacionRepository   # Persiste el registro en MySQL
```

### Política de reintentos

```
Intento 1 → fallo técnico (SMTP caído, timeout)
Intento 2 → reintento automático
Intento 3 → reintento automático
Intento 4 → estado FALLIDO, sin más reintentos

Caso especial:
Email no existe → FALLIDO directo sin reintentar
```

### Comunicación con otros microservicios

| Dirección | Origen | Mecanismo | Evento |
|-----------|--------|-----------|--------|
| Entrada | MS Reasignación | RabbitMQ — notificaciones.queue | CitaAsignada |
| Entrada | MS Reasignación | RabbitMQ — notificaciones.queue | CitaCancelada |
| Entrada | MS Reasignación | RabbitMQ — notificaciones.queue | TaskVencida |
| Salida | Gmail SMTP | Spring Mail | Email al destinatario |

---

## 4. Patrones de diseño de software

### 4.1. Repository Pattern

**¿Qué es?**
Crea una capa de abstracción entre la lógica de negocio y el acceso a datos.

**¿Dónde se aplica?**
En `NotificacionRepository.java`:

```java
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
```

**¿Por qué se usa?**
- Permite consultar el historial de notificaciones por destinatario y estado
- El método `findByEstadoAndIntentosLessThan` permite identificar notificaciones que pueden reintentarse
- Spring Data JPA genera las queries automáticamente sin necesidad de SQL manual

---

### 4.2. Service Layer Pattern

**¿Qué es?**
Separa la lógica de negocio en una capa dedicada entre el Consumer y el Repository.

**¿Dónde se aplica?**
En `NotificacionService.java` e `NotificacionServiceImpl.java`:

```java
// Interfaz — define el contrato
public interface NotificacionService {
    void procesarCitaAsignada(CitaAsignadaEvent evento);
    void procesarCitaCancelada(CitaCanceladaEvent evento);
    void procesarTaskVencida(TaskVencidaEvent evento);
    Page<Notificacion> listar(String rutDestinatario, EstadoNotificacion estado, Pageable pageable);
}
```

**¿Por qué se usa?**
- El Consumer solo sabe que debe llamar al Service — no sabe cómo se envía el correo
- La lógica de construcción del mensaje, envío y registro está encapsulada en el Service
- Facilita los tests — se puede mockear el Service sin necesitar RabbitMQ ni Gmail

---

### 4.3. Message Queue Pattern (Consumer)

**¿Qué es?**
Permite la comunicación asíncrona entre microservicios a través de una cola de mensajes. El productor publica sin esperar respuesta y el consumer procesa cuando puede.

**¿Dónde se aplica?**
En `NotificacionConsumer.java`:

```java
@Component
@RequiredArgsConstructor
public class NotificacionConsumer {

    @RabbitListener(queues = "notificaciones.queue")
    public void recibirCitaAsignada(CitaAsignadaEvent evento) {
        log.info("Evento recibido: CitaAsignada para paciente {}", evento.getRutPaciente());
        notificacionService.procesarCitaAsignada(evento);
    }

    @RabbitListener(queues = "notificaciones.queue")
    public void recibirCitaCancelada(CitaCanceladaEvent evento) {
        notificacionService.procesarCitaCancelada(evento);
    }

    @RabbitListener(queues = "notificaciones.queue")
    public void recibirTaskVencida(TaskVencidaEvent evento) {
        notificacionService.procesarTaskVencida(evento);
    }
}
```

**¿Por qué se usa?**
- El MS Reasignación y el MS Notificaciones operan de forma completamente desacoplada
- Si el MS Notificaciones está caído, los mensajes se acumulan en la cola y se procesan cuando vuelve
- Absorbe picos de alta demanda — por ejemplo envíos masivos por cancelaciones simultáneas
- RabbitMQ garantiza persistencia del mensaje — si el broker se reinicia, los mensajes no se pierden

---

### 4.4. DTO Pattern (Event Objects)

**¿Qué es?**
Objetos que representan los mensajes que llegan desde RabbitMQ. Son el contrato entre el MS Reasignación y el MS Notificaciones.

**¿Dónde se aplica?**
En la carpeta `dto/event/`:

| DTO | Origen | Destinatario del email |
|-----|--------|----------------------|
| `CitaAsignadaEvent` | MS Reasignación | Paciente |
| `CitaCanceladaEvent` | MS Reasignación | Paciente |
| `TaskVencidaEvent` | MS Reasignación | Administrativo operativo |

**¿Cómo se implementa?**
```java
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
```

**¿Por qué se usa?**
- Define el contrato exacto entre microservicios — ambos lados deben respetar la misma estructura JSON
- Spring deserializa automáticamente el JSON de RabbitMQ al objeto Java usando `Jackson2JsonMessageConverter`
- Si el contrato cambia, solo se actualiza el DTO en ambos microservicios

---

### 4.5. Enum Pattern

**¿Qué es?**
Define conjuntos fijos de valores válidos para atributos del dominio.

**¿Dónde se aplica?**
En la carpeta `enums/`:

```java
public enum TipoNotificacion {
    CITA_ASIGNADA,
    CITA_CANCELADA,
    TASK_VENCIDA
}

public enum EstadoNotificacion {
    PENDIENTE,
    ENVIADO,
    FALLIDO
}
```

**¿Por qué se usa?**
- `TipoNotificacion` permite identificar el tipo de evento y construir el asunto del correo correspondiente
- `EstadoNotificacion` permite filtrar notificaciones por su resultado para auditoría
- `EnumType.STRING` guarda el texto legible en MySQL facilitando la consulta directa a la BD

---

### 4.6. Audit Pattern

**¿Qué es?**
Registra automáticamente cuándo se creó un registro usando `@PrePersist`.

**¿Dónde se aplica?**
En `Notificacion.java`:

```java
@PrePersist
protected void onCreate() {
    fechaCreacion = LocalDateTime.now();
}
```

**¿Por qué se usa?**
- Garantiza trazabilidad completa — se sabe exactamente cuándo se intentó enviar cada notificación
- Cumple con los requisitos de la Ley N°19.628 sobre Protección de la Vida Privada
- El campo `fechaUltimoIntento` permite saber cuándo fue el último intento de reenvío

---

## 5. Stack tecnológico

| Tecnología | Versión | Propósito |
|-----------|---------|-----------|
| Java | 21 LTS | Lenguaje de programación |
| Spring Boot | 3.5.14 | Framework principal |
| Spring Data JPA | 3.5.14 | Persistencia y acceso a datos |
| Spring AMQP | 3.5.14 | Mensajería con RabbitMQ |
| Spring Mail | 3.5.14 | Envío de emails via SMTP |
| Spring Web | 3.5.14 | Endpoint de auditoría REST |
| Hibernate | 6.6.49 | ORM — mapeo objeto-relacional |
| MySQL | 8.0 | Base de datos relacional |
| RabbitMQ | 3.13 | Broker de mensajería |
| Lombok | latest | Reducción de código boilerplate |
| Maven | 3.9.15 | Gestión de dependencias y build |

---

## 6. Modelo de datos

### Diagrama de tabla

```
notificaciones
├── id (PK, BIGINT, AUTO_INCREMENT)
├── rut_destinatario (VARCHAR, NOT NULL)
├── email_destinatario (VARCHAR, NOT NULL)
├── tipo_notificacion (ENUM, NOT NULL)
├── mensaje (VARCHAR(1000), NOT NULL)
├── estado (ENUM, NOT NULL, default PENDIENTE)
├── fecha_creacion (DATETIME, NOT NULL)
├── fecha_envio (DATETIME, nullable)
├── intentos (INT, NOT NULL, default 0)
├── fecha_ultimo_intento (DATETIME, nullable)
└── motivo_fallo (VARCHAR, nullable)
```

### Ciclo de vida de una notificación

```
[Evento llega desde RabbitMQ]
         │
         ▼
      PENDIENTE ──── envío exitoso ──────► ENVIADO (terminal)
         │
         └── fallo técnico
               │
               ▼
          intentos < 3 ──────────────────► reintento
               │
               └── intentos >= 3
                     │
                     ▼
                  FALLIDO (terminal)

Caso especial:
Email no existe ────────────────────────► FALLIDO directo
```

### Asuntos de email por tipo

| TipoNotificacion | Asunto del email |
|-----------------|-----------------|
| CITA_ASIGNADA | RedNorte — Nueva cita médica asignada |
| CITA_CANCELADA | RedNorte — Cita médica cancelada |
| TASK_VENCIDA | RedNorte — Alerta: Task de reasignación vencida |

---

## 7. Eventos RabbitMQ

### Cola: notificaciones.queue

**CitaAsignadaEvent**
```json
{
  "evento": "CitaAsignada",
  "fechaEvento": "2026-05-13T10:00:00",
  "rutPaciente": "12345678-9",
  "emailPaciente": "paciente@email.com",
  "especialidad": "Cardiología",
  "medico": "Juan Andrades",
  "fechaHoraInicio": "2026-05-20T09:00:00",
  "fechaHoraFin": "2026-05-20T09:30:00",
  "solicitudId": 100
}
```

**CitaCanceladaEvent**
```json
{
  "evento": "CitaCancelada",
  "fechaEvento": "2026-05-13T11:00:00",
  "rutPaciente": "12345678-9",
  "emailPaciente": "paciente@email.com",
  "especialidad": "Cardiología",
  "medico": "Juan Andrades",
  "fechaHoraInicio": "2026-05-20T09:00:00",
  "fechaHoraFin": "2026-05-20T09:30:00",
  "motivo": "Paciente solicitó cancelación",
  "solicitudId": 100
}
```

**TaskVencidaEvent**
```json
{
  "evento": "TaskVencida",
  "fechaEvento": "2026-05-14T11:00:00",
  "rutAdministrativo": "11111111-1",
  "emailAdministrativo": "admin@rednorte.cl",
  "especialidad": "Cardiología",
  "medico": "Juan Andrades",
  "fechaHoraInicio": "2026-05-20T09:00:00",
  "fechaHoraFin": "2026-05-20T09:30:00",
  "taskId": 55
}
```

---

## 8. Endpoints REST

### Base URL
```
http://localhost:8087
```

### GET /notificaciones
Retorna el historial de notificaciones de un destinatario. Solo para auditoría interna.

**Query params:**

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| rutDestinatario | String | Sí | RUT del destinatario |
| estado | EstadoNotificacion | No | Filtrar por estado |
| page | int | No (default 0) | Número de página |
| size | int | No (default 20) | Tamaño de página |

**Request:**
```
GET /notificaciones?rutDestinatario=12345678-9&estado=ENVIADO
```

**Response 200:**
```json
{
  "content": [
    {
      "id": 1,
      "rutDestinatario": "12345678-9",
      "emailDestinatario": "paciente@email.com",
      "tipoNotificacion": "CITA_ASIGNADA",
      "mensaje": "Estimado/a paciente...",
      "estado": "ENVIADO",
      "fechaCreacion": "2026-05-13T10:00:00",
      "fechaEnvio": "2026-05-13T10:00:05",
      "intentos": 1,
      "fechaUltimoIntento": "2026-05-13T10:00:05",
      "motivoFallo": null
    }
  ],
  "totalElements": 3,
  "totalPages": 1,
  "number": 0
}
```

---

## 9. Configuración

### application.yaml

```yaml
spring:
  application:
    name: ms-notificaciones
  datasource:
    url: jdbc:mysql://localhost:3306/db_notificaciones?createDatabaseIfNotExist=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
server:
  port: 8087
```

### Variables de entorno

| Variable | Descripción | Default |
|----------|-------------|---------|
| DB_USERNAME | Usuario MySQL | root |
| DB_PASSWORD | Contraseña MySQL | (vacío) |
| RABBITMQ_HOST | Host de RabbitMQ | localhost |
| RABBITMQ_PORT | Puerto de RabbitMQ | 5672 |
| RABBITMQ_USERNAME | Usuario RabbitMQ | guest |
| RABBITMQ_PASSWORD | Contraseña RabbitMQ | guest |
| MAIL_HOST | Servidor SMTP | smtp.gmail.com |
| MAIL_PORT | Puerto SMTP | 587 |
| MAIL_USERNAME | Email de origen | (vacío) |
| MAIL_PASSWORD | App Password Gmail | (vacío) |

### Cómo ejecutar localmente

```bash
# 1. Clonar el repositorio
git clone https://github.com/Fullstack3RedNorte/MS-Notificaciones.git

# 2. Iniciar XAMPP (MySQL) y Docker (RabbitMQ)
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management

# 3. La base de datos se crea automáticamente al levantar el proyecto

# 4. Configurar application-dev.yaml con credenciales locales

# 5. Ejecutar el proyecto
cd MS-Notificaciones
mvn spring-boot:run
```

### Configurar Gmail App Password

```
1. Ir a cuenta Google → Seguridad
2. Activar verificación en dos pasos
3. Buscar "Contraseñas de aplicaciones"
4. Seleccionar app: Correo, dispositivo: Windows
5. Copiar la contraseña de 16 caracteres
6. Pegarla en application-dev.yaml → spring.mail.password
```

---

*Documentación generada para el proyecto semestral Fullstack III — RedNorte — DuocUC 2026*
