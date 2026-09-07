# Mediciones

El techo del proyecto es **3 MB por dispositivo**. Se mide en cada cambio, no
al final.

```
python tools/measure.py --build
```

## Peso del paquete

Paquete de publicación sin firmar, con R8, recursos reducidos y división por
arquitectura. Cifras en KB.

| Estado | arm64-v8a | armeabi-v7a | x86 | x86_64 |
|---|---|---|---|---|
| Cimientos: tema, identidad, actividad única | 2127 | 2049 | 2181 | 2160 |
| Dominio: análisis de destinos y nueve tipos | 2127 | 2049 | 2181 | 2160 |
| Lectura y pantalla de resultado | 2437 | 2359 | 2491 | 2469 |
| Generación de los trece formatos | 2529 | 2451 | 2583 | 2561 |
| Historial | 2585 | 2507 | 2639 | 2617 |

## Composición de arm64-v8a

Sin comprimir, en KB.

| Estado | nativo | dex | arsc | recursos |
|---|---|---|---|---|
| Cimientos | 1629 | 1269 | 500 | 496 |
| Historial | 1629 | 2063 | 544 | 560 |

El componente nativo es `libzxingcpp_android.so`, que trae los veinte
decodificadores compilados. Activarlos no cuesta espacio; cuesta tiempo por
fotograma.

## Decisiones que ya movieron la aguja

| Cambio | Efecto |
|---|---|
| Quitar el `-keep` de toda subclase de `View` | −365 KB |
| Excluir `kotlin/**` y los avisos de licencia del empaquetado | −30 KB |

Una regla de conservación que abarque `android.view.View` arrastra Material y
AppCompat enteros. Las vistas que se inflan desde XML ya las conserva aapt2,
que genera las reglas leyendo los propios layouts.
