# Mediciones

El techo del proyecto es **3 MB por dispositivo**. Se mide en cada cambio, no
al final.

```
python tools/measure.py --build
```

Y para saber qué cambio movió la aguja, en lugar de recordarlo mal:

```
python tools/measure_history.py
```

Compila cada commit del repositorio en un árbol de trabajo aparte y publica la
tabla de abajo. Las cifras de este documento salen de ahí; ninguna está
estimada.

## Peso del paquete

Paquete de publicación sin firmar, con R8, recursos reducidos y división por
arquitectura. Cifras en KB.

| Estado | arm64-v8a | armeabi-v7a | x86 | x86_64 |
|---|---|---|---|---|
| Estructura, identidad visual y tema Material 3 | 2127 | 2049 | 2181 | 2160 |
| Análisis de destinos y nueve tipos de contenido | 2127 | 2049 | 2181 | 2160 |
| Lectura con CameraX y pantalla de resultado | 2432 | 2354 | 2486 | 2465 |
| Generación de los trece formatos | 2559 | 2481 | 2613 | 2592 |
| Historial sobre el SQLite del sistema | 2584 | 2507 | 2639 | 2617 |
| Integración con el sistema y análisis en verde | 2593 | 2515 | 2648 | 2626 |
| Logotipo en el código, ficha y lenguaje claro | 2614 | 2536 | 2668 | 2646 |
| Direcciones como las resuelve el navegador y pruebas cruzadas | 2620 | 2542 | 2674 | 2653 |
| Zonas horarias, agendas antiguas e invertidos en imágenes | 2629 | 2551 | 2683 | 2662 |
| Escrituras de todo el mundo y límites de entrada | 2637 | 2559 | 2691 | 2670 |
| Panel único de ajustes | 2639 | 2561 | 2693 | 2672 |
| El estandarte como marca | 2643 | 2565 | 2697 | 2675 |
| Paleta de la marca y hojas con cierre visible | 2646 | 2568 | 2700 | 2679 |
| Formas de píldora e iconos Lucide | 2656 | 2578 | 2711 | 2689 |
| Licencias completas y comentarios esenciales | 2661 | 2583 | 2715 | 2694 |
| Descripción nueva y enlace a GitHub | 2662 | 2584 | 2716 | 2695 |
| Etiquetas finas, zoom y enfoque al tocar | 2667 | 2589 | 2721 | 2700 |

## Lo que descarga un teléfono desde la tienda

El paquete que se sube a Google Play es un App Bundle, y la tienda entrega a
cada teléfono solo su arquitectura, su densidad de pantalla y su idioma. Eso es
menos que el APK dividido, que lleva todas las densidades y los tres idiomas.

Medido con `bundletool get-size total` sobre `app-release.aab`:

| Arquitectura | Descarga |
|---|---|
| armeabi-v7a | 2023 – 2053 KB |
| **arm64-v8a** | **2100 – 2132 KB** |
| x86_64 | 2132 – 2164 KB |
| x86 | 2153 – 2186 KB |

```
gradlew :app:bundleRelease -Psignifer.abiSplits=false
```

La propiedad hace falta porque el App Bundle divide por arquitectura por su
cuenta y no puede convivir con los splits de APK, que siguen sirviendo para
distribuir fuera de la tienda.

## Composición de arm64-v8a

Sin comprimir, en KB.

| Estado | nativo | dex | arsc | recursos |
|---|---|---|---|---|
| Estructura, identidad visual y tema Material 3 | 1629 | 1269 | 500 | 496 |
| Análisis de destinos y nueve tipos de contenido | 1629 | 1269 | 500 | 496 |
| Lectura con CameraX y pantalla de resultado | 1629 | 1807 | 534 | 539 |
| Generación de los trece formatos | 1629 | 2031 | 540 | 549 |
| Historial sobre el SQLite del sistema | 1629 | 2063 | 544 | 560 |
| Integración con el sistema y análisis en verde | 1629 | 2067 | 546 | 569 |

El componente nativo es `libzxingcpp_android.so`, que trae los veinte
decodificadores compilados. Activarlos no cuesta espacio; cuesta tiempo por
fotograma.

## Lo que enseñan las cifras

**El dominio no pesó nada.** Analizar destinos y los nueve tipos de contenido
añadió más de mil líneas y el paquete no se movió ni un kilobyte: R8 borra lo
que ninguna pantalla usa todavía. El coste aparece cuando la interfaz lo llama,
repartido entre las dos filas siguientes.

**Lo que pesa es el marco, no la aplicación.** Del paquete final de arm64, el
decodificador nativo son 1629 KB y todo lo demás son 964 KB, de los cuales la
mayor parte es Material y CameraX. El código propio compilado no llega a los
400 KB.

**El logotipo en el centro del código costó 18 KB**, contando también la ficha
de la aplicación, los iconos nuevos y los textos en lenguaje claro. Es barato
porque no trajo nada: el selector de imágenes ya estaba para leer desde la
galería, y el decodificador ya estaba para la cámara. Poner un dibujo encima y
releer el resultado es aritmética sobre piezas que ya viajaban en el binario.

**La integración con el sistema costó 9 KB.** El azulejo del panel, los atajos
del icono, la recepción de imágenes compartidas y la respuesta al intent
heredado de ZXing, todo junto. Es lo que ningún competidor libre tiene reunido,
y es lo más barato del proyecto.

## Decisiones que ya movieron la aguja

| Cambio | Efecto |
|---|---|
| Quitar el `-keep` de toda subclase de `View` | −365 KB |
| Excluir `kotlin/**` y los avisos de licencia del empaquetado | −30 KB |

Una regla de conservación que abarque `android.view.View` arrastra Material y
AppCompat enteros. Las vistas que se inflan desde XML ya las conserva aapt2,
que genera las reglas leyendo los propios layouts.

## Referencia externa

Binary Eye pesa 8,3 MB en F-Droid con las cuatro arquitecturas en un solo
archivo, del orden de 2 a 3 MB por dispositivo. Signifer queda por debajo con
más funciones.
