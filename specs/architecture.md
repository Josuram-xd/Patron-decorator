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
            │ motor          (Combate, turnos, IA,      │  ← casos de uso
            │                 eventos, GestorEfectos)   │
            └───────────────┬───────────────────────────┘
                            │ usa
            ┌───────────────▼───────────────────────────┐
            │ dominio        (Combatiente, Decorators,  │  ← núcleo del patrón
            │                 Stats, catálogos)         │
            └───────────────────────────────────────────┘
            ┌───────────────────────────────────────────┐
            │ infraestructura (RepositorioCombates en   │  ← adaptador de salida
            │                  memoria, Aleatorio)      │
            └───────────────────────────────────────────┘
```

**Regla de dependencias:** `api → motor → dominio`. `infraestructura` implementa interfaces del `motor`.
`dominio` no importa nada de las otras capas. Lo verifica un test de arquitectura simple (T-502).

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
    │   │   │   └── AmuletoVidaDecorator.java
    │   │   └── catalogo/
    │   │       ├── CatalogoClases.java      ← Guerrero, Mago, Arquero
    │   │       ├── CatalogoEnemigos.java    ← Goblin, Esqueleto, Dragón
    │   │       ├── CatalogoEquipo.java      ← id → fábrica de decorador
    │   │       ├── CatalogoEfectos.java     ← id → fábrica de decorador
    │   │       └── Habilidad.java           ← record: id, nombre, enfriamiento, objetivo, efecto
    │   ├── motor/
    │   │   ├── Combate.java                 ← agregado: estado, ronda, combatientes, log
    │   │   ├── EstadoCombate.java           ← EN_CURSO | VICTORIA | DERROTA
    │   │   ├── Accion.java                  ← sealed: Atacar | Defender | UsarHabilidad
    │   │   ├── MotorCombate.java            ← ejecuta una ronda completa
    │   │   ├── GestorEfectos.java           ← aplicar, refrescar, expirar, purgar, reconstruir cadena
    │   │   ├── ReglasInteraccion.java       ← p. ej. Congelado elimina Furia
    │   │   ├── IaEnemigo.java               ← decide la acción del enemigo
    │   │   ├── ContextoTurno.java           ← acceso a eventos + aleatorio durante un turno
    │   │   ├── evento/EventoCombate.java    ← sealed record con los tipos de evento
    │   │   ├── Aleatorio.java               ← interfaz (semilla)
    │   │   └── RepositorioCombates.java     ← interfaz (puerto)
    │   ├── infraestructura/
    │   │   ├── RepositorioCombatesMemoria.java
    │   │   └── AleatorioJdk.java            ← envuelve java.util.random.RandomGenerator
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
    │           ├── CatalogoHandler.java
    │           ├── VistaPreviaHandler.java
    │           └── CombateHandler.java
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
| Concurrencia | `ConcurrentHashMap` + `synchronized` por combate | Suficiente para uso local |
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
    │   ├── SeleccionPantalla.tsx       ← clase + enemigo
    │   ├── PreparacionPantalla.tsx     ← inventario drag & drop + vista previa
    │   ├── ArenaPantalla.tsx           ← combate
    │   └── ResultadoPantalla.tsx
    ├── componentes/
    │   ├── TarjetaCombatiente.tsx      ← retrato, barra de vida, stats
    │   ├── BarraVida.tsx
    │   ├── ListaEfectos.tsx            ← íconos con contador de turnos
    │   ├── InspectorCadena.tsx         ← ★ pila visual de decoradores
    │   ├── PanelAcciones.tsx           ← Atacar / Defender / Habilidades (enfriamientos)
    │   ├── LogCombate.tsx
    │   ├── NumeroFlotante.tsx          ← daño/curación animados
    │   ├── Inventario.tsx, RanuraEquipo.tsx, ItemEquipo.tsx
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
  └─► mutation POST /api/combates/{id}/acciones
        └─► respuesta { combate, eventos[] }
              ├─► store.encolarEventos(eventos)
              │     └─► reproductorEventos: anima uno por uno (≈600 ms c/u)
              │            (número flotante, sacudida, ícono de efecto aparece o se va)
              └─► al terminar la cola → se pinta el `combate` final (fuente de verdad)
                    └─► PanelAcciones se reactiva si estado = EN_CURSO
```

El estado que se pinta siempre es el que devuelve el servidor; los eventos **solo** sirven para animar la transición.

---

## 4. Decisiones registradas

| ADR | Decisión |
|---|---|
| [ADR-001](adr/ADR-001-java-puro.md) | Backend en Java puro; JUnit 5 solo para tests |
| [ADR-002](adr/ADR-002-reconstruccion-cadena.md) | Quitar decoradores del medio reconstruyendo la cadena |
| [ADR-003](adr/ADR-003-rest-eventos.md) | REST síncrono que devuelve estado + eventos; sin WebSocket |
