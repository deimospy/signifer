# Rendimiento y tasa de detección

El rendimiento es el objetivo declarado del proyecto, así que se mide en lugar
de afirmarse. Todas las cifras de este documento salen de ejecutar las órdenes
que aparecen aquí; ninguna está estimada.

## Por qué existe este documento

**No hay una medición pública de la tasa de detección de zxing-cpp.** Los
estudios publicados miden la implementación Java original de ZXing, que es otra
base de código, en procesadores de sobremesa. Antes de decir que una aplicación
lee bien hay que poder demostrarlo, y para eso hace falta un banco que
cualquiera pueda regenerar.

## El banco de imágenes

Diez códigos, en seis formatos, con quince degradaciones cada uno: **150
imágenes**. Se regeneran byte a byte —la semilla del ruido es fija— con:

```
gradlew :app:testDebugUnitTest --tests "*GenerateBenchmark*" -Dsignifer.benchmark=true
```

Las degradaciones no son adorno. Son lo que le pasa a un código de verdad: una
etiqueta se arruga, un cartel se lee de lado, la luz entra por una ventana y
media imagen queda quemada.

| Condición | Qué simula |
|---|---|
| `limpio` | El caso ideal, para tener suelo |
| `pequeno`, `muy-pequeno` | El código ocupa poco en el encuadre |
| `desenfoque-leve`, `desenfoque-fuerte` | La cámara no llegó a enfocar |
| `girado-5`, `girado-15`, `girado-45` | El teléfono no está recto |
| `perspectiva` | No se mira de frente |
| `contraste-bajo` | Impresión gastada o pantalla con poco brillo |
| `ruido` | Sensor barato con poca luz |
| `sombra` | Luz lateral que quema medio código |
| `invertido` | Claro sobre oscuro |
| `danado` | Una esquina rota o tapada |
| `borde-recortado` | Zona tranquila mordida al imprimir |

## Cómo se mide

```
gradlew :app:connectedDebugAndroidTest
adb logcat -d -s SigniferBenchmark:I
```

La prueba corre en el dispositivo, porque el decodificador es nativo. Cuenta
como acierto **leer lo que el código decía**, no leer algo: un lector que
devuelve un número equivocado es peor que uno que no lee. El tiempo se toma con
reloj propio alrededor de la llamada, no con el contador de la biblioteca.

## Resultados

Medido el 7 de septiembre de 2026 sobre `sdk_gphone64_x86_64`, API 36.

| Perfil | Aciertos | Mediana | Percentil 95 | Máximo |
|---|---|---|---|---|
| En vivo (el de la cámara) | **122 de 150 — 81,3 %** | 1,5 ms | 6,5 ms | 21,4 ms |
| Imagen fija (`tryHarder`) | **122 de 150 — 81,3 %** | 3,5 ms | 21,0 ms | 34,5 ms |

Por condición, con el perfil en vivo:

| Condición | Aciertos | Mediana |
|---|---|---|
| limpio | 10/10 | 1,60 ms |
| desenfoque-leve | 10/10 | 1,45 ms |
| desenfoque-fuerte | 10/10 | 1,14 ms |
| girado-5 | 10/10 | 1,28 ms |
| perspectiva | 10/10 | 1,38 ms |
| contraste-bajo | 10/10 | 1,52 ms |
| ruido | 10/10 | 3,29 ms |
| danado | 10/10 | 1,48 ms |
| borde-recortado | 10/10 | 0,71 ms |
| pequeno | 8/10 | 0,40 ms |
| girado-15 | 7/10 | 1,85 ms |
| sombra | 6/10 | 2,12 ms |
| girado-45 | 5/10 | 3,87 ms |
| invertido | 5/10 | 2,76 ms |
| muy-pequeno | 1/10 | 0,36 ms |

## Lo que dicen estas cifras

**`tryHarder` no compensa.** Cuesta más del doble de tiempo —la mediana pasa de
1,5 a 3,5 ms y el percentil 95 se triplica— y no acierta **ni una sola imagen
más**. Esto responde con un dato a una de las decisiones que el proyecto dejó
abiertas: se queda apagado de fábrica.

**Lo que falla no es el ruido, es la resolución.** El desenfoque, el grano, el
contraste bajo y hasta una esquina rota se leen enteros. Lo que no se lee es un
código que ocupa pocos píxeles: en `muy-pequeno` cada módulo queda por debajo
del píxel y no hay algoritmo que lo recupere. De ahí que la resolución de
análisis de la cámara sea 1280×720 y no menos.

**Los códigos invertidos y los girados a 45° son de los lineales.** Los
matriciales los leen; las barras, no. Es una limitación conocida de los
formatos lineales, no de esta aplicación.

**Un fotograma dura 33 ms a 30 por segundo.** Con el percentil 95 en 6,5 ms
queda margen de sobra: la lectura no es lo que marca el ritmo de la vista
previa.

## Arranque en frío

```
adb shell am start -W -n org.sarambi.signifer/.MainActivity
```

Paquete de publicación, cinco arranques seguidos tras `force-stop`:

| Arranque | Tiempo |
|---|---|
| 1.º (primera vez tras instalar) | 1524 ms |
| 2.º al 5.º | 1022, 1042, 1063, 1055 ms |

Mediana **1055 ms** hasta el primer fotograma dibujado, incluida la vista
previa de la cámara.

## Honestidad sobre estas cifras

**El dispositivo de referencia debería ser el más lento disponible, y esto es
un emulador.** Un `sdk_gphone64_x86_64` corre sobre el procesador del equipo de
desarrollo: los tiempos absolutos de un teléfono de gama baja serán varias
veces mayores. Lo que **sí** se traslada es la comparación entre perfiles —
`tryHarder` cuesta el doble en cualquier procesador— y la tasa de detección,
que no depende de la velocidad.

Repetir la medición en un teléfono real es una orden y un `adb pull`. Cuando se
haga, esta tabla se sustituye por la de ese aparato.
