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

Quince códigos, uno por cada formato que la aplicación escribe y tres de QR con
cargas distintas, con quince degradaciones cada uno: **225 imágenes**. Se
regeneran byte a byte —la semilla del ruido es fija— con:

```
gradlew :app:testDebugUnitTest --tests "*GenerateBenchmark*" -Dsignifer.benchmark=true
```

Los códigos de producto (EAN, UPC) van completos, con su dígito de control,
porque es lo que devuelve el lector y se compara texto contra texto.

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
reloj propio alrededor de la llamada, no con el contador de la biblioteca. El
informe incluye el desglose por condición, por formato y la lista de imágenes
que fallaron.

## Resultados

Medido el 13 de septiembre de 2026 sobre `sdk_gphone64_x86_64`, API 36.

| Perfil | Aciertos | Mediana | Percentil 95 | Máximo |
|---|---|---|---|---|
| En vivo (el de la cámara) | **176 de 225 — 78,2 %** | 1,5 ms | 11,3 ms | 38,4 ms |
| Imagen fija (`tryHarder` y segundo intento invertido) | **187 de 225 — 83,1 %** | 7,3 ms | 33,0 ms | 130,9 ms |

Los aciertos son idénticos en cada ejecución; los tiempos no. En tres
ejecuciones seguidas el percentil 95 en vivo osciló entre 5,0 y 12,0 ms,
porque el emulador comparte procesador con el equipo que lo ejecuta.

Por condición:

| Condición | En vivo | Imagen fija |
|---|---|---|
| limpio | 15/15 | 15/15 |
| desenfoque-leve | 15/15 | 15/15 |
| desenfoque-fuerte | 15/15 | 15/15 |
| girado-5 | 15/15 | 15/15 |
| perspectiva | 15/15 | 15/15 |
| contraste-bajo | 15/15 | 15/15 |
| danado | 15/15 | 15/15 |
| borde-recortado | 15/15 | 15/15 |
| ruido | 14/15 | 15/15 |
| pequeno | 12/15 | 12/15 |
| girado-15 | 12/15 | 12/15 |
| invertido | 5/15 | **15/15** |
| sombra | 6/15 | 6/15 |
| girado-45 | 5/15 | 5/15 |
| muy-pequeno | 2/15 | 2/15 |

Por formato:

| Formato | En vivo | Imagen fija |
|---|---|---|
| QR Code (tres cargas) | 43/45 | 43/45 |
| Data Matrix | 14/15 | 14/15 |
| Aztec | 13/15 | 13/15 |
| ITF | 12/15 | 13/15 |
| PDF417 | 11/15 | 12/15 |
| EAN-13 | 11/15 | 12/15 |
| EAN-8 | 11/15 | 12/15 |
| UPC-A | 11/15 | 12/15 |
| Code 93 | 11/15 | 12/15 |
| UPC-E | 10/15 | 12/15 |
| Code 128 | 10/15 | 11/15 |
| Codabar | 10/15 | 11/15 |
| Code 39 | 9/15 | 10/15 |

## Lo que dicen estas cifras

**Los códigos matriciales aguantan casi todo; los lineales, no.** QR, Data
Matrix y Aztec solo fallan cuando el código ocupa unos pocos píxeles. Los de
barras —y PDF417, que también se lee por filas— fallan además girados a 45°,
con media imagen quemada y en claro sobre oscuro: una fila que cruza el código
en diagonal o con dos iluminaciones distintas no tiene un umbral que sirva.

**La opción de invertidos de la biblioteca solo alcanza a los matriciales.** Los
códigos de barras y PDF417 en claro sobre oscuro no se leían en ningún perfil:
0 de 10. Para imágenes fijas hay ahora un segundo intento con la imagen
invertida, que solo se hace cuando el primero no encontró nada; con él se leen
los 15. En vivo no se hace: la mayoría de fotogramas no contienen ningún código,
y el segundo intento duplicaría el coste de cada uno de ellos.

**`tryHarder` en todos los fotogramas no compensa en vivo.** En una ejecución
sin el segundo intento, la imagen fija acertó 177 frente a 176 —una sola imagen
más, un UPC-E con ruido— y su mediana fue 4,75 ms frente a 1,25 ms. El banco,
sin embargo, no tiene etiquetas finas; la sección siguiente sí.

**Lo que falla no es el ruido, es la resolución.** El desenfoque, el grano, el
contraste bajo y hasta una esquina rota se leen enteros. Lo que no se lee es un
código que ocupa pocos píxeles: en `muy-pequeno` cada módulo queda por debajo
del píxel y no hay algoritmo que lo recupere. De ahí que la resolución de
análisis de la cámara sea 1280×720 y no menos.

**Un fotograma dura 33 ms a 30 por segundo.** Con el percentil 95 en vivo por
debajo de 12 ms queda margen de sobra: la lectura no es lo que marca el ritmo
de la vista previa.

## Etiquetas finas

Un número de serie de disco duro es un código de barras largo y de pocos
milímetros de alto. El modo rápido de la biblioteca revisa unas pocas filas
separadas entre sí, y una etiqueta así cabe entera entre dos de ellas: se lee
si cae en el centro de la imagen y no se lee apenas se aparta.

```
adb logcat -c
gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.sarambi.signifer.decode.ThinBarcodeTest
adb logcat -d -s SigniferThin:I
```

La prueba dibuja 72 etiquetas en un fotograma de cámara: Code 128 y Code 39,
tres largos, cuatro grosores, en el centro y a un tercio del borde.

| Fotograma | Etiquetas leídas |
|---|---|
| Rápido | 48 de 72 — ninguna fina fuera del centro |
| Completo, solo lineales | 64 de 72 |
| Los dos alternados | 64 de 72 — las 8 que faltan son el Code 39 más corto, por debajo de dos píxeles por módulo |

Por eso la cámara alterna: un fotograma rápido y uno que revisa todas las filas
buscando solo códigos lineales. Los matriciales ya se leían igual en el modo
rápido; con ellos, el fotograma completo sin código costaba 25,5 ms en una
medición aparte.

Coste por fotograma, medido en la misma prueba:

| Imágenes | Rápido (mediana) | Completo (mediana) |
|---|---|---|
| Banco de 225, con código | 1,0 ms | 1,5 ms |
| 40 sin código, con texto y recuadros | 7,5 ms | 15,0 ms |

La cámara casi siempre mira algo sin código, así que esa es la fila que cuenta:
alternando, el fotograma medio pasa de 7,5 a unos 11 ms, lejos de los 33 ms de
un fotograma.

Lo que la alternancia no arregla es la resolución: un código largo que cruza
el visor de lado a lado, con el teléfono en vertical, dispone solo del lado
corto del sensor, 720 píxeles. Para eso está el zoom: pellizcar acerca, un
doble toque alterna entre 1× y 2× y un toque enfoca en ese punto.

## Arranque en frío

```
adb shell am start -W -n org.sarambi.signifer/.MainActivity
```

Paquete de publicación firmado, cinco arranques seguidos tras `force-stop`:

| Arranque | Tiempo |
|---|---|
| 1.º (tras instalar, con el emulador recién arrancado) | 2655 ms |
| 2.º al 6.º | 1044, 920, 901, 993, 909 ms |

Mediana **920 ms** hasta el primer fotograma dibujado, incluida la vista previa
de la cámara.

## Tiempo hasta el primer código

La aplicación mide, en cada sesión de cámara, cuánto pasa desde que se pide la
cámara hasta la primera lectura, y el tiempo de decodificación de cada
fotograma. No escribe nada salvo que se active a mano:

```
adb shell setprop log.tag.SigniferMetrics DEBUG
adb logcat -s SigniferMetrics:D
```

Cada vez que la cámara se detiene deja una línea. Esta es real, de una sesión
en el emulador en la que no se leyó ningún código —de ahí el guion—:

```
primer codigo=-  fotogramas n=264  p50=0.25 ms  p95=0.25 ms  max=0.468 ms
```

Esta cifra no se publica todavía. El emulador tiene una cámara virtual con una
habitación en 3D donde se puede colgar una imagen, pero apuntarla hacia ella
exige mover la cámara con el teclado de su ventana, y medir la latencia de una
cámara simulada no dice nada de la de un teléfono.

## Honestidad sobre estas cifras

**El dispositivo de referencia debería ser el más lento disponible, y esto es
un emulador.** Un `sdk_gphone64_x86_64` corre sobre el procesador del equipo de
desarrollo: los tiempos absolutos de un teléfono de gama baja serán varias
veces mayores. Lo que **sí** se traslada es la comparación entre perfiles —
`tryHarder` cuesta varias veces más en cualquier procesador— y la tasa de
detección, que no depende de la velocidad.

Repetir la medición en un teléfono real es una orden y un `adb logcat`. Cuando
se haga, estas tablas se sustituyen por las de ese aparato.
