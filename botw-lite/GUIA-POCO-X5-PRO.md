# BOTWRecompAndroid en POCO X5 Pro 5G (Snapdragon 778G / Adreno 642L)

## Por qué va a ~10 fps

Todo esto sale del propio código y de la documentación del proyecto:

1. **Rutas "seguras" fuera de Adreno 8xx.** En `Automático`, las opciones de *Compatibilidad del driver*
   (`inplace_reads`, `turnip_lrz`) solo se activan en Adreno 8xx (`vk_adreno_8xx` en `runtime/nvn/vulkan/device.c`).
   Tu Adreno 642L cae en las rutas seguras: copias de pantalla completa en vez de lecturas en el sitio y LRZ apagado.
   En las pruebas del autor, esas rutas bajaron el juego de **35 a 15 fps** (`docs/DESEMPENHO-60FPS-2026-10-23.md`).
2. **El hilo de render puede caer en un núcleo pequeño (A55).** Solo se fija a los núcleos grandes en equipos con poca
   RAM. Con 6/8 GB no se fijaba. En una Galaxy Tab A9+ (misma mezcla A78 + A55) eso costaba **90 ms por cuadro**.
3. **El límite real es la CPU.** En un Snapdragon 8 Elite la lógica del juego ya cuesta ~16 ms por cuadro. Los A78 del
   778G son unas 2–3 veces más lentos. El modo Recompilado añade otro 1,5–3x sobre el código original.
4. **El widescreen añade draws.** En una pantalla 20:9, `aspect = unlocked` ensancha el frustum y la culling: más
   objetos dibujados, en la GPU y en el hilo de render.
5. **Bajar la resolución casi no ayuda.** Según las mediciones del autor, el coste es por draw (~2 µs de GPU y ~6 µs del
   hilo de render), no por píxel. Sirve para temperatura y batería, no para fps.

## Paso 1: qué cambiar hoy, sin recompilar el APK

**Driver**
- Instala un **Mesa Turnip** reciente (Ajustes > Archivos > Driver de Vulkan). Con el driver de Qualcomm todas las
  rutas rápidas quedan apagadas.

**Código del juego**
- Usa la versión **1.9.0** (o 1.8.2): es la única con reloj VFR (20 fps no va en cámara lenta), con los hooks de cámara
  y probada en modo Nativo (la 1.0.0 también).
- **Código del juego: Nativo (experimental).** Es la mayor ganancia de CPU posible.

**Compatibilidad del driver** (Ajustes > Prelanzamiento)
- Memoria del juego en la GPU: **Activado**.
- Lecturas en el sitio: **Activado**. Si ves el terreno a rayas (issue #129 en Adreno 750), vuelve a Automático.
- LRZ de Turnip: **Activado**. Si desaparecen objetos, apágalo.
- Variables del driver: `LARECOMP_RENDER_BIG=1`

  El campo hace `setenv` antes de arrancar el juego, así que también sirve para variables `LARECOMP_*`. Con el APK
  actual fija el hilo de render al núcleo de 2,4 GHz.

**Vídeo**
- Relación de aspecto: **Original (16:9)**.
- Frame rate: **20** fijo. Es preferible a 30 inestable; con 1.8.2/1.9.0 la velocidad del juego se mantiene.
- Resolución interna **50%** y Salida **720**: más por temperatura que por fps.
- Upscaler **Bilinear**, Antialias **Off**.
- Cinemáticas a 60: **Off**.

**Teléfono**
- Modo rendimiento / Game Turbo de MIUI.
- Juega sin cargar: en carga el SoC no baja de ~50 °C y se estrangula.

Si quieres editar el ini a mano (`botwrecompandroid.ini`), las claves equivalentes son:

```ini
native_code = 1
gpu_heap = 1
inplace_reads = 1
turnip_lrz = 1
driver_env = LARECOMP_RENDER_BIG=1
aspect = original
frame_rate = 20
resolution = 50%
output = 720
upscaler = bilinear
antialias = off
cutscenes_60 = 0
```

## Paso 2: cambios en el proyecto (parche incluido)

El parche `0001-Android-render-thread-off-little-cores-on-7-series-S.patch` va sobre la rama `main` de mamcjr
(commit `82534ac`). Hace dos cosas.

**1. El hilo de render sale de los núcleos pequeños automáticamente** (`runtime/platform/android.c`)
- Lee `cpu_capacity` de cada núcleo.
- Si hay núcleos con menos de la mitad de capacidad, fija el hilo a todos los grandes. En el 778G son los 4 A78, no
  solo el de 2,4 GHz.
- En el 8 Elite no cambia nada, porque sus núcleos medios rinden ~80% de los prime.
- Lo probé con una simulación de 778G, 8 Elite y SD695.

**2. Distancia de dibujado experimental**
- Archivos: `draw_distance` en el ini, o `LARECOMP_FAR=<metros>` en Variables del driver
  (`runtime/hle/widescreen.c`, `settings.c/.h`).
- Acerca el plano lejano de la cámara principal: el juego lo tiene en 25000. Así su propia culling descarta lo que hay
  más allá y quedan menos draws para la GPU y el hilo de render.
- Solo funciona en **1.8.2 y 1.9.0** (donde existe el hook de la proyección).
- Prueba valores como `12500`, `8000` y `5000`. El horizonte se corta a esa distancia; la niebla lo disimula en parte.
- **No está probado en un teléfono.** El cielo o la iluminación podrían verse mal. Si pasa, pon `0`.

Para compilarlo necesitas el Android SDK/NDK (ver el README del proyecto):

```bash
cd BOTWRecompAndroid
git am 0001-*.patch
cd android/botwrecompandroid && ./gradlew assembleDebug
```

## Sobre los archivos del juego

- **No modifiques `exefs/main`.** Los parches del proyecto (`tools/patches.py`) y el modo Nativo comprueban el
  build ID y las instrucciones exactas de cada versión. Un `main` modificado hace fallar el build o carga código erróneo.
- Lo que más pesa aquí (distancia de dibujado, resolución dinámica, ritmo de cuadros) vive en el código, no en el
  `romfs`. Por eso está en el parche y no en tus archivos.
- Mods de `romfs`: el port los soporta (Ajustes > Mods). Las mods que quitan hierba, partículas o nubes pueden aliviar
  algo, pero no hay ninguna medida en este port, así que pruébalas de una en una con el contador de FPS.
- Tu volcado es tuyo: no lo subas a ningún sitio ni lo adjuntes en issues (`CONTRIBUTING.md`).

## Qué esperar

Siendo realistas, con la CPU del 778G el objetivo es **20 fps estables**, quizá 25–30 en interiores y santuarios. Los
60 fps no son alcanzables en este hardware.

Para medir, activa Mostrar FPS y guarda el log (Ajustes > Archivos > Guardar log). Busca estas líneas:
- `[android] render thread on the ... cores`
- `[vk] Turnip LRZ on`
- `[drawdist]` (si activas la distancia de dibujado)

## Versión Lite (parche 0002)

Hay dos parches: `0001-…` (núcleos grandes y distancia de dibujado) y `0002-…` (versión Lite). Se aplican en orden
sobre el commit `82534ac` de mamcjr.

Se compila con `-Plite` y genera una app **aparte**, que convive con la normal:
- Nombre "BOTW Lite", paquete `com.botwrecomp.android.lite`.
- Ajustes y configuración del juego propios: hay que volver a elegir los archivos del juego.

Valores por defecto de la Lite (todos se pueden cambiar en el menú o en el ini):

| Ajuste | Lite | Normal |
| --- | --- | --- |
| Aspecto | 16:9 original | panorámico |
| Distancia de dibujado | 8000 | 25000 (la del juego) |
| Memoria del juego en GPU / Lecturas en el sitio / LRZ | Activado | Automático (apagado fuera de Adreno 8xx) |
| Código del juego | Nativo | Recompilado |
| Frame rate | 20 | 30 |
| Resolución de la escena | 50% | 1x |
| Salida | 720 líneas | Auto |
| Upscaler | Bilinear | SGSR 1 |
| Hilo de render | siempre en núcleos grandes | solo con poca RAM |
| Suelo de reloj de CPU (uclamp.min) | 400 | apagado |

Variables para desactivar dos de estos ajustes (en "Variables del driver"):
- `LARECOMP_UCLAMP_MIN=0` quita el suelo de reloj.
- `LARECOMP_RENDER_BIG=0` deja libre el hilo de render.

Compilación (necesita Android SDK + NDK 30 y las dependencias de `android/deps/`, según el README):

```bash
git clone https://github.com/mamcjr/BOTWRecompAndroid.git && cd BOTWRecompAndroid
git checkout 82534ac
git am /ruta/0001-*.patch /ruta/0002-*.patch /ruta/0003-*.patch /ruta/0004-*.patch /ruta/0005-*.patch /ruta/0006-*.patch /ruta/0007-*.patch
cd android/botwrecompandroid && ./gradlew assembleDebug -Plite
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Pasos al instalarla:
1. Instala Turnip desde la propia app (Ajustes > Archivos > Driver de Vulkan). Sin Turnip, las rutas rápidas no
   funcionan.
2. Elige "Nativo" y tus archivos 1.9.0.

## Parche 0003: los hilos del juego en los núcleos grandes

El juego ejecuta su "núcleo 0" de uno en uno: el hilo principal y sus hilos de trabajo se turnan (`runtime/kernel.c`,
`core0_acquire`). Si cualquiera de ellos cae en un A55, todo el cuadro espera, porque un A55 es unas 2–3 veces más
lento que un A78.

El parche fija **todos** los hilos del juego a los mismos núcleos grandes. Como todos quedan a la misma velocidad, se
evita la carrera que tumbaba el juego con `LARECOMP_PIN`, que solo aceleraba el hilo principal.

- En la Lite está activado por defecto; se apaga con `LARECOMP_GAME_BIG=0`.
- En la app normal se activa con `LARECOMP_GAME_BIG=1`.
- En el log debe aparecer `[android] game threads on the big cores: on`.
- Cada 30 s el log escribe `[kernel] core 0, last 30 s: ... held N% of the time`. Si ese valor está cerca del 100%, el
  límite es el propio hilo del juego.

## Parche 0004: perfil de CPU del juego en el propio teléfono (1.9.0)

Sirve para saber qué funciones del juego gastan la CPU y decidir qué parchear en la 1.9.0. No hace falta PC ni adb.

1. En "Variables del driver", añade (separado por un espacio de lo que ya haya):
   `LARECOMP_SAMPLE=1200:3600`
   A 20 fps mide desde el minuto 1 hasta el minuto 3 de juego.
2. Carga tu partida y juega en el **mundo abierto** (no en menús) durante unos 3–4 minutos. Mejor en una zona que te
   vaya lenta, como un pueblo o un bosque.
3. Ajustes > Archivos > **Guardar log** y mándame el archivo. Las líneas que me interesan son
   `[sample] t80: … top: f_… %` y las de los demás hilos.
4. El informe completo queda en `build/samples.txt`, dentro de la carpeta de datos de la app para la 1.9.0. Si puedes
   sacarlo, mejor.

- Para que los nombres de las funciones coincidan, haz la medición en modo **Recompilado**. En Nativo da direcciones
  `main+…` y también sirve.
- Contiene solo direcciones y contadores, nada del juego.
- Quita la variable después: cada hilo deja de recibir muestras al terminar la medición, pero así no queda armada.

## Parche 0005: menos actores, hierba y árboles lejanos (solo 1.9.0)

Basado en los hallazgos de SavageWindRecomp (Revivedx, MIT), que apunta justo a la 1.9.0. Su preset "Potato" usa 0,6
de las distancias del juego, y en un PC limitado a 4 núcleos midió Rito 101 → 120 fps y Kakariko 148 → 159 fps.

**Qué cambia**
- **Actores colocados en el mapa** (árboles, rocas, edificios, enemigos): el juego crea menos alrededor de la cámara y
  aplica sus propias reglas. Hay menos actores que actualizar, así que el hilo del juego gasta menos CPU, y menos draws
  para el hilo de render.
- Los **modelos lejanos** (castillo, montañas, edificios "_Far") usan un factor más suave (0,8), para que el horizonte
  mantenga sus formas.
- **Hierba**: las briznas se generan más cerca, y las dos capas se desvanecen antes.
- **Árboles lejanos del terreno**: alcance multiplicado por 0,8. Solo en modo Recompilado.

**Cómo ajustarlo**
- `actor_distance` en el ini, o `LARECOMP_ACTOR_DIST=<factor>` en Variables del driver.
- La Lite viene en **0,6**. Rango 0,4–1; 1 deja los valores originales del juego, bit a bit.
- Si los enemigos o árboles aparecen demasiado cerca, sube a 0,7–0,8.
- En el log debe aparecer `[world] actors at 0.60 of the game's distances, far models 0.80`.

**Estado**
- No está probado en un teléfono.
- Las 6 direcciones y las instrucciones esperadas salen de la tabla de SavageWind para la 1.9.0. El port las comprueba
  antes de parchear, así que si no coinciden el build se detiene en vez de parchear mal.

## Parche 0006: sin vegetación que aparezca de golpe

- **Relevo de los árboles corregido.** Los árboles cercanos son actores completos (caros), y los lejanos los dibuja el
  terreno en versión simplificada (barata). El juego hace un fundido entre ambos donde terminan los actores. Ahora ese
  fundido se acerca con el mismo factor que los actores, así que el relevo sigue cuadrando y los árboles no aparecen de
  golpe: simplemente pasan antes a la versión barata (un LOD).
- **Hierba con su propio ajuste** (`grass_distance`, `LARECOMP_GRASS_DIST`). Por defecto, también en la Lite, queda
  como en el juego original: no termina más cerca.
- **Actores de la Lite a 0,7.** Los arbustos y rocas pequeñas (que no tienen versión lejana) desaparecen a partir de
  ~105 m en vez de 150 m. Si aun así ves algo aparecer, sube `actor_distance` a 0,8.

## Parche 0007: física más barata a 20 fps (solo 1.9.0)

Antes de cada paso de física (Havok), el juego pide `round(delta × 6)` micropasos del solver: 6 a 30 fps, pero **9 a
20 fps**. A 20 fps eso supone un 50% más de cálculo de física por cuadro. El dato y la dirección (`0x700156f930`) salen
de SavageWindRecomp (MIT).

- La Lite lo limita a **6**, el mismo número que la consola a 30 fps.
- Nunca baja de 6: con menos, los contactos ceden. En las pruebas de SavageWind, con 4 micropasos el salto de Link con
  un cuco en brazos baja a 0,42 m, frente a unos 0,55 m con 6.
- Ajuste: `solver_cap` en el ini, o `LARECOMP_SOLVER_CAP=0` para dejar el valor original del juego.
- En el log: `[physics] solver microsteps 9.0 a step -> 6`.
- Posible efecto: pilas de objetos y bisagras (puertas con Magnesis) algo menos rígidas que a 20 fps sin el parche,
  aunque con los mismos micropasos por paso que a 30 fps.
