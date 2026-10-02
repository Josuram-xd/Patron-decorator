# Arquitectura — RPG Decorator

## 1. Vista general

```
┌──────────────────────────────┐        HTTP/JSON         ┌──────────────────────────────────────┐
│  FRONTEND  (React + Vite)    │  ───────────────────►    │  BACKEND  (Java 25 puro)             │
│                              │   /api/... (REST)        │                                      │
│  Pantallas ─ Store (Zustand) │  ◄───────────────────    │  api ─► motor ─► dominio             │
│  Animaciones (Framer Motion) │   CombateDTO + eventos[] │            (Decorator aquí)          │
└──────────────────────────────┘                          │  infraestructura (repos en memoria)  │
                                                          └──────────────────────────────────────┘
```

- Comunicación **REST síncrona**. Cada acción del jugador = 1 petición que devuelve el **estado completo** del combate + la **lista de eventos** ocurridos (para animar).
- En desarrollo, Vite hace de **proxy** de `/api` → `http://localhost:8080`, así no se necesita CORS. Igual el backend responde con cabeceras CORS para `http://localhost:5173`.

---

## 2. Backend — capas (arquitectura hexagonal ligera)

```
            ┌───────────────────────────────────────────┐
            │ api            (HTTP, JSON, DTOs, rutas)  │  ← adaptador de entrada
            └───────────────┬───────────────────────────┘
                            │ usa
            ┌───────────────▼───────────────────────────┐
            │ motor          (Expedición, Combate,      │  ← casos de uso
            │                 turnos, IA, GestorEfectos)│
            └───────────────┬───────────────────────────┘
                            │ usa
            ┌───────────────▼───────────────────────────┐
            │ dominio        (Combatiente, Decorators,  │  ← núcleo del patrón
            │                 Stats, catálogos)         │
            └───────────────────────────────────────────┘
            ┌───────────────────────────────────────────┐
            │ infraestructura (RepositorioExpediciones  │  ← adaptador de salida
            │                  en memoria, AleatorioJdk)│
            └───────────────────────────────────────────┘
```

**Regla de dependencias:** `api → motor → dominio`. `infraestructura` implementa interfaces del `motor`.
`dominio` no importa nada de las otras capas. Lo verifica un test de arquitectura simple (T-503).

### 2.1 Estructura de carpetas del backend

```
backend/
├── pom.xml                                  ← Java 25, JUnit 5 (test), plugin exec/jar
└── src/
    ├── main/java/com/rpgdecorator/
    │   ├── App.java                         ← main: arma dependencias y arranca HttpServer
    │   ├── dominio/
    │   │   ├── Combatiente.java             ← COMPONENTE (interfaz)
    │   │   ├── PersonajeBase.java           ← COMPONENTE CONCRETO
    │   │   ├── Stats.java                   ← record inmutable
    │   │   ├── Danio.java, ResultadoDanio.java, TipoDanio.java
    │   │   ├── Bando.java                   ← HEROE | ENEMIGO
    │   │   ├── ContextoTurno.java           ← interfaz que usan los decoradores (la implementa motor)
    │   │   ├── Aleatorio.java               ← interfaz (semilla) para tests deterministas
    │   │   ├── evento/EventoCombate.java    ← sealed interface + records de cada tipo de evento
    │   │   ├── decorador/
    │   │   │   ├── EfectoDecorator.java     ← DECORADOR BASE (abstracta, delega todo)
    │   │   │   ├── Categoria.java           ← EQUIPO | BUFF | DEBUFF | CONTROL
    │   │   │   └── Duracion.java            ← turnos restantes o PERMANENTE
    │   │   ├── efectos/                     ← DECORADORES CONCRETOS temporales
    │   │   │   ├── VenenoDecorator.java
    │   │   │   ├── RegeneracionDecorator.java
    │   │   │   ├── EscudoDecorator.java
    │   │   │   ├── EspinasDecorator.java
    │   │   │   ├── FuriaDecorator.java
    │   │   │   ├── CongeladoDecorator.java
    │   │   │   ├── VampirismoDecorator.java
    │   │   │   └── DefensaDecorator.java
    │   │   ├── equipo/                      ← DECORADORES CONCRETOS permanentes
    │   │   │   ├── EspadaDecorator.java
    │   │   │   ├── HachaDecorator.java
    │   │   │   ├── BastonDecorator.java
    │   │   │   ├── ArmaduraCueroDecorator.java
    │   │   │   ├── ArmaduraDragonDecorator.java
    │   │   │   ├── AnilloFuegoDecorator.java
    │   │   │   ├── AmuletoVidaDecorator.java
    │   │   │   └── BotasVientoDecorator.java
    │   │   └── catalogo/
    │   │       ├── CatalogoClases.java      ← Guerrero, Mago, Arquero
    │   │       ├── CatalogoEnemigos.java    ← 8 enemigos + grupos por nivel (design §4.8)
    │   │       ├── DefinicionEnemigo.java   ← record: stats, habilidades, condiciones de IA
    │   │       ├── CatalogoEquipo.java      ← id → (ranura, fábrica de decorador)
    │   │       ├── Ranura.java              ← ARMA | ARMADURA | ACCESORIO
    │   │       ├── CatalogoEfectos.java     ← id → fábrica de decorador
    │   │       ├── Habilidad.java           ← record (design §4.7)
    │   │       └── AplicacionEfecto.java    ← record: efectoId + Objetivo (PROPIO | RIVAL)
    │   ├── motor/
    │   │   ├── combate/
    │   │   │   ├── Combate.java             ← agregado: estado, ronda, combatientes, log
    │   │   │   ├── EstadoCombate.java       ← EN_CURSO | VICTORIA | DERROTA
    │   │   │   ├── Accion.java              ← sealed: Atacar | Defender | UsarHabilidad | Pasar
    │   │   │   ├── MotorCombate.java        ← ejecuta una ronda completa
    │   │   │   ├── ContextoTurnoImpl.java   ← implementa dominio.ContextoTurno
    │   │   │   ├── CalculadoraDanio.java    ← fórmulas de design §4.1
    │   │   │   ├── IaEnemigo.java           ← decide la acción del enemigo
    │   │   │   └── AccionInvalidaException.java
    │   │   ├── efectos/
    │   │   │   ├── GestorEfectos.java       ← aplicar, refrescar, expirar, purgar, reconstruir cadena
    │   │   │   ├── ReglasInteraccion.java   ← tabla: Congelado elimina Furia, etc.
    │   │   │   ├── Capa.java                ← record para inspeccionar la cadena
    │   │   │   └── MotivoRetiro.java
    │   │   ├── expedicion/
    │   │   │   ├── Expedicion.java          ← agregado: niveles, enemigos, equipo, combate actual
    │   │   │   ├── EstadoExpedicion.java    ← EN_CURSO | ESPERANDO_RECOMPENSA | COMPLETADA | FRACASADA
    │   │   │   ├── ServicioExpedicion.java  ← casos de uso: crear, actuar, elegirRecompensa, vistaPrevia
    │   │   │   ├── SorteoEnemigos.java      ← elige 1 enemigo por nivel con la semilla
    │   │   │   ├── SorteoRecompensas.java   ← 3 piezas distintas no equipadas
    │   │   │   └── Estadisticas.java
    │   │   └── RepositorioExpediciones.java ← interfaz (puerto)
    │   ├── infraestructura/
    │   │   ├── RepositorioExpedicionesMemoria.java
    │   │   └── AleatorioJdk.java            ← implementa dominio.Aleatorio con java.util.random
    │   └── api/
    │       ├── Servidor.java                ← HttpServer + registro de rutas
    │       ├── Router.java                  ← método + patrón de ruta → handler
    │       ├── Cors.java, ErrorHttp.java
    │       ├── json/
    │       │   ├── Json.java                ← escritor y parser mínimos (a mano)
    │       │   └── JsonValor.java           ← sealed: objeto, arreglo, texto, número, bool, null
    │       ├── dto/                         ← records que reflejan api-contract.md
    │       ├── mapper/                      ← dominio/motor → DTO
    │       └── handlers/
    │           ├── CatalogoHandler.java     ← clases, equipo, efectos, enemigos
    │           ├── VistaPreviaHandler.java
    │           └── ExpedicionHandler.java   ← crear, consultar, acciones, recompensa, inspector
    └── test/java/com/rpgdecorator/          ← mismo árbol de paquetes
        ├── dominio/...                      ← tests unitarios por decorador
        ├── motor/...                        ← tests de motor y escenarios
        ├── api/...                          ← tests de JSON y de integración HTTP
        └── ArquitecturaTest.java            ← verifica la regla de dependencias
```

### 2.2 Stack backend

| Pieza | Elección | Motivo |
|---|---|---|
| Lenguaje | **Java 25** (LTS, instalado) | `record`, `sealed`, pattern matching en `switch` |
| Build | **Maven 3.9** | Estándar; corre los tests y empaqueta un jar ejecutable |
| HTTP | `com.sun.net.httpserver.HttpServer` | Viene en el JDK (módulo `jdk.httpserver`) |
| JSON | Escrito a mano (`api/json`) | Requisito de Java puro; el contrato es pequeño |
| Concurrencia | `ConcurrentHashMap` + `synchronized` por expedición | Suficiente para uso local |
| Tests | **JUnit 5** (solo scope `test`) | Ver ADR-001 |
| HTTP en tests | `java.net.http.HttpClient` (JDK) | Tests de integración sin librerías |

Comandos (definidos en T-001):
```
cd backend
mvn test                          # tests
mvn -q exec:java                  # arranca en :8080   (o: java -jar target/rpg-decorator.jar)
```

---

## 3. Frontend

### 3.1 Estructura de carpetas

```
frontend/
├── package.json, vite.config.ts (proxy /api), tsconfig.json, tailwind config
├── public/icons/                       ← SVG de game-icons.net (CC BY 3.0, con atribución)
└── src/
    ├── main.tsx, App.tsx               ← enrutado simple por estado de pantalla
    ├── api/
    │   ├── tipos.ts                    ← tipos TS = api-contract.md
    │   ├── cliente.ts                  ← fetch tipado + manejo de errores
    │   └── consultas.ts                ← hooks de TanStack Query
    ├── mocks/                          ← fixtures JSON + cliente mock (VITE_USE_MOCKS=true)
    ├── store/
    │   └── juegoStore.ts               ← Zustand: pantalla, selección, cola de eventos
    ├── pantallas/
    │   ├── SeleccionPantalla.tsx       ← clase
    │   ├── PreparacionPantalla.tsx     ← pieza inicial (drag & drop + vista previa)
    │   ├── MapaPantalla.tsx            ← progreso de la expedición (4 niveles)
    │   ├── ArenaPantalla.tsx           ← combate
    │   ├── RecompensaPantalla.tsx      ← elegir 1 de 3 piezas
    │   └── ResumenPantalla.tsx         ← COMPLETADA / FRACASADA
    ├── componentes/
    │   ├── TarjetaCombatiente.tsx      ← retrato, barra de vida, stats
    │   ├── BarraVida.tsx
    │   ├── ListaEfectos.tsx            ← íconos con contador de turnos
    │   ├── InspectorCadena.tsx         ← ★ pila visual de decoradores
    │   ├── PanelAcciones.tsx           ← Atacar / Defender / Habilidades (enfriamientos)
    │   ├── LogCombate.tsx
    │   ├── NumeroFlotante.tsx          ← daño/curación animados
    │   ├── Inventario.tsx, RanuraEquipo.tsx, ItemEquipo.tsx
    │   ├── NodoMapa.tsx                ← nivel del mapa (vencido / actual / oculto / jefe)
    │   └── ui/                         ← botones, tooltip, panel
    ├── animacion/
    │   └── reproductorEventos.ts       ← reproduce eventos[] en secuencia con delays
    └── estilos/tokens.css              ← variables de color (tema oscuro "fantasía")
```

### 3.2 Stack frontend

| Librería | Uso |
|---|---|
| **React 19 + Vite** | Base |
| **TypeScript** (strict) | Tipos del contrato |
| **Tailwind CSS 4** | Estilos |
| **Framer Motion** (`motion`) | Daño flotante, sacudidas, entrada/salida de efectos, capas del inspector |
| **Zustand** | Estado de UI (pantalla actual, selección, cola de animaciones) |
| **TanStack Query** | Llamadas al backend, carga y errores |
| **dnd-kit** | Arrastrar equipo a las ranuras |
| **lucide-react** | Íconos de interfaz |
| **sonner** | Avisos ("¡Congelado! Pierdes el turno") |
| **ESLint + Prettier** | Calidad |

Íconos de efectos y equipo: **game-icons.net** (CC BY 3.0) → atribución en el pie de página.

### 3.3 Flujo de datos en el frontend

```
Click "Atacar"
  └─► mutation POST /api/expediciones/{id}/acciones
        └─► respuesta { expedicion, eventos[] }
              ├─► store.encolarEventos(eventos)
              │     └─► reproductorEventos: anima uno por uno (≈600 ms c/u)
              │            (número flotante, sacudida, ícono de efecto aparece o se va)
              └─► al terminar la cola → se pinta la `expedicion` final (fuente de verdad)
                    └─► la pantalla se deriva de expedicion.estado
                        (EN_CURSO → Arena · ESPERANDO_RECOMPENSA → Recompensa · COMPLETADA/FRACASADA → Resumen)
```

El estado que se pinta siempre es el que devuelve el servidor; los eventos **solo** sirven para animar la transición.

---

## 4. Decisiones registradas

| ADR | Decisión |
|---|---|
| [ADR-001](adr/ADR-001-java-puro.md) | Backend en Java puro; JUnit 5 solo para tests |
| [ADR-002](adr/ADR-002-reconstruccion-cadena.md) | Quitar decoradores del medio reconstruyendo la cadena |
| [ADR-003](adr/ADR-003-rest-eventos.md) | REST síncrono que devuelve estado + eventos; sin WebSocket |
