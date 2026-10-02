# Tareas — RPG Decorator

> Plan de trabajo para los agentes. Lee `AGENTS.md §2` antes de tomar una tarea.
>
> **Estados:** `[ ]` Pendiente · `[~]` En curso (agente) · `[x]` Hecha · `[!]` Bloqueada
> **⚡** = se puede hacer en paralelo con las otras ⚡ de su fase una vez cumplidas las dependencias.
> **Agentes:** `ORQ` orquestador · `DOM` dominio · `MOT` motor · `API` api · `FE` frontend · `QA` calidad

---

## Grafo de fases

```
F0 Setup ──► F1 Dominio ──► F2 Motor ──► F3 API ──┐
   │                                              ├──► F5 Integración y QA
   └──────► F4 Frontend (con mocks) ──────────────┘
```
El frontend **no espera** al backend: trabaja contra mocks (T-402) que cumplen `api-contract.md`, y en F5 se conecta al backend real.

---

## F0 — Setup

### T-001 [ ] Esqueleto del backend · `ORQ`
- **Depende de:** —
- **Specs:** architecture §2.1, §2.2, ADR-001
- **Hacer:** `backend/pom.xml` (groupId `com.rpgdecorator`, Java 25, JUnit 5 en scope test, surefire, exec-maven-plugin con `mainClass=com.rpgdecorator.App`, jar ejecutable `rpg-decorator.jar`). El árbol de paquetes **ya existe** con `.gitkeep`: reemplázalos por un `package-info.java` que documente cada paquete. `App.java` que imprime "RPG Decorator" y termina.
- **Aceptación:**
  - `mvn -q test` pasa (con un test de humo).
  - `pom.xml` no tiene dependencias fuera de scope `test`.

### T-002 [ ] Esqueleto del frontend · `FE` ⚡
- **Depende de:** —
- **Specs:** architecture §3
- **Hacer:** Vite + React + TS strict; Tailwind 4, Framer Motion (`motion`), Zustand, TanStack Query, dnd-kit, lucide-react, sonner, ESLint + Prettier. Proxy `/api → http://localhost:8080` en `vite.config.ts`. Las carpetas de architecture §3.1 **ya existen** con `.gitkeep`: genera el proyecto Vite en una carpeta temporal y copia sus archivos a `frontend/` sin borrarlas. `tokens.css` con las variables de design §7.5 (solo los nombres y unos valores iniciales).
- **Aceptación:** `npm run dev` muestra "RPG Decorator"; `npm run build` y `npm run lint` pasan.

### T-003 [ ] Raíz del repo · `ORQ` ⚡
- **Depende de:** —
- **Hacer:** `.gitignore` (target/, node_modules/, dist/, .idea/, *.iml, .vscode/), `.editorconfig`, sección "Cómo correrlo" del README.
- **Aceptación:** `git status` limpio tras el build de back y front.

---

## F1 — Dominio (el patrón Decorator)

### T-101 [ ] Tipos de valor · `DOM`
- **Depende de:** T-001
- **Specs:** design §2.1 · RNF-02
- **Hacer:** `Stats` (con métodos `con…` y límites ≥ 0, crítico ≤ 100), `Danio`, `ResultadoDanio`, `TipoDanio`, `Bando`, `Categoria`, `Duracion` (PERMANENTE, `decrementar`, `expirada`).
- **Aceptación:** tests de `Stats` (inmutabilidad, límites) y de `Duracion` (permanente nunca expira; 1 → 0 expira).

### T-102 [ ] Contratos: `Combatiente`, `ContextoTurno`, `Aleatorio`, eventos · `DOM`
- **Depende de:** T-101
- **Specs:** design §2.2, §2.3, §6
- **Hacer:** interfaz `Combatiente`; interfaz `ContextoTurno`; interfaz `Aleatorio` (`entero(min, max)`, `probabilidad(int porcentaje)`); `EventoCombate` como `sealed interface` con un `record` por tipo de design §6.
- **Aceptación:** compila; `switch` exhaustivo sobre `EventoCombate` sin `default` (un test lo demuestra).

### T-103 [ ] `PersonajeBase` (Componente concreto) · `DOM`
- **Depende de:** T-102
- **Specs:** design §2.2 (columna PersonajeBase), §3.1
- **Aceptación:**
  - `modificarVida` respeta `[0, vidaMaxEfectiva]`.
  - `recibirDanio` resta `fisico + elemental` y devuelve un `ResultadoDanio` correcto.
  - `describirCadena()` devuelve el nombre.

### T-104 [ ] `EfectoDecorator` (Decorador base) · `DOM`
- **Depende de:** T-103
- **Specs:** design §2.4, §3.6 · RNF-02
- **Hacer:** clase abstracta que **delega todos** los métodos; `envuelto()`, `debeRetirarse()`, `refrescar()`, `avanzarTurno()` con `recienAplicado`, `copiarSobre()` abstracto, `describirCadena()`.
- **Aceptación:**
  - Un decorador de prueba "vacío" (en tests) es indistinguible del base en todos los métodos.
  - `id()` es el del base en cualquier profundidad.
  - `avanzarTurno()`: el primer llamado no decrementa; los siguientes sí.

### T-105 [ ] Decoradores de equipo · `DOM` ⚡
- **Depende de:** T-104
- **Specs:** design §4.6
- **Hacer:** los 8 decoradores de `dominio/equipo`.
- **Aceptación:** un test por pieza; un test de orden `Furia(Espada(base))` ≠ `Espada(Furia(base))` (design §3.4) **cuando exista Furia** (puede usar un decorador de prueba ×1.5).

### T-106a [ ] Efectos: Veneno y Regeneración · `DOM` ⚡
- **Depende de:** T-104
- **Specs:** design §4.2 · RF-12
- **Aceptación:** al llamar `alIniciarTurno`, piden `danioDirecto` o `curar` al contexto (usar un `ContextoTurno` falso en el test) y luego delegan.

### T-106b [ ] Efectos: Escudo y Espinas · `DOM` ⚡
- **Depende de:** T-104
- **Specs:** design §4.2, §4.3 · RF-14, RF-16
- **Aceptación:**
  - Escudo 20 vs 30 de daño → absorbe 20, delega 10, `debeRetirarse() = true`.
  - Escudo `refrescar` suma la absorción con tope 40.
  - Espinas: `reflejado = 30 %` de `recibido`; 0 si `danio.reflejable == false`.
  - Escudo(Espinas(base)): el reflejo se calcula sobre lo que **pasó** el escudo.

### T-106c [ ] Efectos: Furia, Defensa y Congelado · `DOM` ⚡
- **Depende de:** T-104
- **Specs:** design §4.2 · RF-13, RF-15
- **Aceptación:** stats modificadas con redondeo hacia abajo; `Congelado.puedeActuar() == false` sin importar las capas internas.

### T-106d [ ] Efecto: Vampirismo · `DOM` ⚡
- **Depende de:** T-104
- **Specs:** design §4.2
- **Aceptación:** `alInfligirDanio` con `recibido = 20` → `curar(6)`; con `recibido = 0` → no cura.

### T-107 [ ] Catálogos · `DOM`
- **Depende de:** T-105, T-106a–d
- **Specs:** design §4.2, §4.6, §4.7, §4.8 · RF-01, RF-04
- **Hacer:** `CatalogoEfectos`, `CatalogoEquipo` (con `Ranura`), `CatalogoClases`, `CatalogoEnemigos` (8 enemigos + grupos por nivel), `Habilidad`, `AplicacionEfecto`, `DefinicionEnemigo` (con condiciones de IA como `Predicate`).
- **Aceptación:** los valores coinciden **exactamente** con las tablas de design (test que recorre cada catálogo); ids únicos; cada nivel 1–3 tiene ≥ 2 enemigos.

### T-108 [ ] Tests didácticos de las "trampas del Decorator" · `DOM`
- **Depende de:** T-107
- **Specs:** design §3
- **Aceptación:** un test por trampa (§3.1–§3.6) con un nombre que la explique, p. ej. `selfCallEnBaseNoVeLaArmadura()`. Cada test lleva un comentario de 2–3 líneas de por qué existe.

---

## F2 — Motor

### T-201 [ ] `GestorEfectos` + `ReglasInteraccion` · `MOT`
- **Depende de:** T-107
- **Specs:** design §3.2, §3.3, §3.5, §4.4, §4.5, §5.4 · RF-10, RF-11, RF-16, RF-17, RF-18 · ADR-002
- **Aceptación:**
  - `retirar` del medio: `Furia(Veneno(Espada(base)))` sin Veneno → `Furia(Espada(base))`, Furia conserva sus turnos y el base es **la misma instancia**.
  - `aplicar` dos veces → una sola capa + `EFECTO_REFRESCADO`.
  - Reglas de §4.4 (un test por fila).
  - `purgar` deja solo el equipo.
  - `avanzarTurno`: ciclo completo de Veneno (3 turnos) y de un efecto `recienAplicado`.
  - `capas()` devuelve las stats efectivas por capa.
  - `equipar` respeta la invariante del orden (equipo por dentro).

### T-202 [ ] `CalculadoraDanio` · `MOT` ⚡
- **Depende de:** T-107
- **Specs:** design §4.1
- **Aceptación:** tests con un `Aleatorio` fijo: crítico, evasión (tope 25 %), mitigación mínima 1, elemental sin mitigar.

### T-203 [ ] `Combate`, `Accion`, `ContextoTurnoImpl` · `MOT`
- **Depende de:** T-201
- **Specs:** design §5.1, §5.2, §2.3
- **Aceptación:** `ContextoTurnoImpl.danioDirecto` y `curar` resuelven la **cadena exterior** por id y usan su `vidaMax` efectiva (test con AmuletoVida: curar puede superar la vida máxima base).

### T-204 [ ] `MotorCombate.ejecutarRonda` · `MOT`
- **Depende de:** T-202, T-203
- **Specs:** design §5.3, §4.7 · RF-05–RF-09, RF-15
- **Aceptación:**
  - Orden de eventos de una ronda exactamente como §5.3.
  - Veneno puede matar al inicio del turno → no actúa.
  - Congelado → `TURNO_PERDIDO`; `PASAR` solo es válido congelado.
  - Enfriamientos (cd 3: se usa en el turno 1 → disponible en el turno 4).
  - El orden de resolución de habilidades de §4.7; los efectos sobre el rival no se aplican si hubo evasión.
  - `VICTORIA` / `DERROTA` + `COMBATE_TERMINADO`; acción posterior → `AccionInvalidaException`.

### T-205 [ ] `IaEnemigo` · `MOT` ⚡
- **Depende de:** T-203
- **Specs:** design §4.8
- **Aceptación:** por cada enemigo, un test que fuerza la condición de cada habilidad y verifica la elección; sin habilidades disponibles → `Atacar`.

### T-206 [ ] Expedición: agregado y sorteos · `MOT`
- **Depende de:** T-204, T-205
- **Specs:** design §5.5 · RF-02, RF-04, RF-24–RF-27
- **Hacer:** `Expedicion`, `EstadoExpedicion`, `SorteoEnemigos`, `SorteoRecompensas`, `Estadisticas`, `RepositorioExpediciones` (interfaz).
- **Aceptación:**
  - Misma semilla → mismos enemigos y mismas recompensas.
  - Enemigos de cada nivel de su grupo correcto; nivel 4 siempre Dragón.
  - Recompensas: 3 distintas, ninguna ya equipada.

### T-207 [ ] `ServicioExpedicion` · `MOT`
- **Depende de:** T-206
- **Specs:** design §5.5 · RF-24, RF-25, RF-26
- **Hacer:** `crear`, `actuar`, `elegirRecompensa`, `vistaPrevia` (ambas formas de api-contract §4), `obtener`, `eliminar`. Sincronización por expedición.
- **Aceptación:**
  - Escenario Gherkin "Pasar al siguiente nivel…" de requirements §5.
  - Reemplazo de pieza en una ranura ocupada.
  - Las transiciones inválidas lanzan una excepción con su código (`ESTADO_INVALIDO`, etc.).
  - Las estadísticas se acumulan entre encuentros.

---

## F3 — API (HTTP + JSON a mano)

### T-301 [ ] JSON mínimo · `API` ⚡
- **Depende de:** T-001
- **Specs:** architecture §2.2 · RNF-01
- **Hacer:** `JsonValor` (sealed) + `Json.escribir(JsonValor)` + `Json.parsear(String)`. Escapes `\" \\ \n \t \uXXXX`, números enteros y decimales, anidación.
- **Aceptación:** tests ida y vuelta; un JSON inválido lanza `JsonInvalidoException` con su posición; caracteres con tilde y ñ se escriben y leen bien.

### T-302 [ ] Servidor, router, CORS y errores · `API` ⚡
- **Depende de:** T-001
- **Specs:** api-contract §1, §8
- **Hacer:** `Servidor` (HttpServer en :8080, puerto configurable con la variable `PORT`), `Router` con parámetros de ruta (`/api/expediciones/{id}`), `Cors`, `ErrorHttp`, y `/api/salud`.
- **Aceptación:** test de integración con `HttpClient`: 200 en salud, 404 en una ruta desconocida, 405 en un método incorrecto, 204 en `OPTIONS` con cabeceras CORS.

### T-303 [ ] DTOs y mappers · `API`
- **Depende de:** T-207, T-301
- **Specs:** api-contract §3–§7
- **Aceptación:** test por DTO que compara el JSON generado con el ejemplo del contrato (estructura y nombres de campos); los eventos omiten los campos que no aplican.

### T-304 [ ] Endpoints de catálogo y vista previa · `API`
- **Depende de:** T-302, T-303
- **Specs:** api-contract §2, §3, §4
- **Aceptación:** tests de integración de los 4 GET de catálogo y de las 2 formas de vista previa.

### T-305 [ ] Endpoints de expedición · `API`
- **Depende de:** T-304
- **Specs:** api-contract §2, §5, §6, §7
- **Aceptación:** test de integración que juega una expedición completa por HTTP con una semilla fija (bucle de `ATACAR` + elegir siempre la primera recompensa) hasta `COMPLETADA` o `FRACASADA`; casos de error 400, 404 y 409 cubiertos.

### T-306 [ ] `App` + infraestructura · `API`
- **Depende de:** T-305
- **Hacer:** `RepositorioExpedicionesMemoria`, `AleatorioJdk`, armado de dependencias en `App.main`, log de arranque con la URL.
- **Aceptación:** `mvn -q exec:java` arranca; `curl localhost:8080/api/salud` responde `{"estado":"OK"}`.

---

## F4 — Frontend

### T-401 [ ] Tipos y cliente HTTP · `FE`
- **Depende de:** T-002
- **Specs:** api-contract (completo)
- **Hacer:** `api/tipos.ts` (1:1 con el contrato, uniones discriminadas para `EventoDTO` por `tipo`), `api/cliente.ts` (fetch tipado; un error del backend → `ErrorApi` con su `codigo`), `api/consultas.ts` (hooks de TanStack Query).
- **Aceptación:** `tsc` sin errores; un `switch` sobre `evento.tipo` es exhaustivo (`never`).

### T-402 [ ] Mocks · `FE`
- **Depende de:** T-401
- **Hacer:** `mocks/` con fixtures JSON del contrato y un cliente mock que simula: crear expedición, unas cuantas rondas con eventos variados (daño, crítico, escudo, veneno, congelado, retiro), victoria → recompensa → nivel 2. Se activa con `VITE_USE_MOCKS=true`.
- **Aceptación:** con mocks se puede recorrer Selección → Preparación → Mapa → Arena → Recompensa → Mapa sin backend.

### T-403 [ ] Store y tema · `FE` ⚡
- **Depende de:** T-401
- **Specs:** architecture §3.3, design §7.1, §7.5
- **Hacer:** `juegoStore` (pantalla derivada del estado, selección, cola de eventos, velocidad de animación); `tokens.css` completo y componentes `ui/` (Boton, Panel, Tooltip, Insignia).
- **Aceptación:** la pantalla cambia sola según `expedicion.estado`; el contraste del texto es ≥ 4.5:1.

### T-404 [ ] Selección de clase · `FE` ⚡
- **Depende de:** T-403
- **Specs:** design §7.2 · RF-01

### T-405 [ ] Preparación: inventario con drag & drop + vista previa · `FE` ⚡
- **Depende de:** T-403
- **Specs:** design §7.3 · RF-02, RF-03
- **Aceptación:** solo se suelta en la ranura correcta; la vista previa usa `POST /api/vista-previa` con debounce; se muestran el diff de stats y la cadena.

### T-406 [ ] Mapa de la expedición · `FE` ⚡
- **Depende de:** T-403
- **Specs:** design §7.2.b · RF-04, RF-23

### T-407 [ ] Arena: tarjetas, barras, efectos y acciones · `FE`
- **Depende de:** T-403
- **Specs:** design §7.4 · RF-06, RF-07, RF-19
- **Aceptación:** habilidades en enfriamiento deshabilitadas con su contador; si el héroe está congelado solo aparece "Pasar turno"; atajos de teclado 1–4.

### T-408 [ ] ★ Inspector de cadena · `FE`
- **Depende de:** T-407
- **Specs:** design §7.4 · RF-20
- **Aceptación:** cajas anidadas de afuera hacia adentro con color por categoría, turnos y `statsEnCapa`; las capas entran y salen animadas; texto de `cadena` debajo; pestañas Héroe / Enemigo.

### T-409 [ ] Log y reproductor de eventos con animaciones · `FE`
- **Depende de:** T-407
- **Specs:** design §6 (columna UI), §7.6, §7.7 · RF-21, RF-22
- **Aceptación:** cada tipo de evento tiene su animación; botón ⏩; con `prefers-reduced-motion` solo hay cambios de opacidad; avisos con sonner para TURNO_PERDIDO y crítico; el log es `aria-live`.

### T-410 [ ] Recompensa y Resumen · `FE` ⚡
- **Depende de:** T-405 (reutiliza el inventario)
- **Specs:** design §7.2.c, §7.2.d · RF-25, RF-26, RF-27

### T-411 [ ] Responsive y pulido · `FE`
- **Depende de:** T-404–T-410
- **Specs:** RNF-08, design §7.4 (móvil)
- **Aceptación:** usable a 375 px sin scroll horizontal; atribución de game-icons.net en el pie.

---

## F5 — Integración y QA

### T-501 [ ] Conectar el front al backend real · `FE` + `API`
- **Depende de:** T-306, T-411
- **Aceptación:** con `VITE_USE_MOCKS=false` se juega una expedición completa; las diferencias entre mocks y backend se corrigen **en el lado que no cumple el contrato**.

### T-502 [ ] Escenarios de combate de punta a punta (backend) · `QA`
- **Depende de:** T-207
- **Specs:** requirements §5 (todos los Gherkin)
- **Aceptación:** un test por escenario Gherkin, con semilla fija.

### T-503 [ ] Test de arquitectura · `QA` ⚡
- **Depende de:** T-207
- **Specs:** architecture §2 (regla de dependencias), AGENTS §3
- **Hacer:** test que recorre los `.java` de `src/main` y falla si `dominio` importa `motor`/`api`/`infraestructura`, si `motor` importa `api`, o si hay un `import` de librerías prohibidas o de `java.lang.reflect.Proxy`.

### T-504 [ ] Checklist manual de QA · `QA`
- **Depende de:** T-501
- **Hacer:** `specs/qa-checklist.md` con un recorrido por cada RF y su resultado (✔/✘ + nota).

### T-505 [ ] Documentación final · `ORQ`
- **Depende de:** T-504
- **Hacer:** README con capturas o un GIF, "cómo agregar un efecto nuevo en 3 pasos" (demuestra RNF-03) y el diagrama de clases final.

---

## Paralelismo sugerido (3 agentes a la vez)

| Ola | Agente A (DOM → MOT) | Agente B (API) | Agente C (FE) |
|---|---|---|---|
| 1 | T-001, T-101 → T-104 | T-301, T-302 | T-002, T-401, T-402 |
| 2 | T-105, T-106a–d, T-107, T-108 | (espera) / revisa specs | T-403 → T-406 |
| 3 | T-201 → T-207 | — | T-407 → T-409 |
| 4 | T-502, T-503 (QA) | T-303 → T-306 | T-410, T-411 |
| 5 | T-504 | T-501 | T-501 |

---

## Registro

| Fecha | Tarea | Agente | Nota |
|---|---|---|---|
| 2026-10-02 | — | ORQ | Specs iniciales creadas |
