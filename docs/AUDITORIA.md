# Auditoría del binario

Lo que se publica es el binario, no el código fuente. Que el código no declare
permiso de red no sirve de nada si una dependencia lo añade en su propio
manifiesto y el resultado los fusiona sin decir nada.

```
python tools/audit.py
```

Lee el paquete que se instala: el manifiesto ya fusionado, el dex ya
minificado y las bibliotecas nativas.

## Resultado

Sobre `app-arm64-v8a-release-unsigned.apk`, 2667 KB.

**Permisos declarados en el binario: dos.**

| Permiso | Para qué | Tipo |
|---|---|---|
| `android.permission.CAMERA` | Leer códigos en vivo | Peligroso: lo concede la persona |
| `android.permission.VIBRATE` | El toque que confirma una lectura | Normal: no da acceso a ningún dato |

**Bibliotecas nativas: tres.**

| Biblioteca | Peso | De dónde viene |
|---|---|---|
| `libzxingcpp_android.so` | 1596 KB | El decodificador, con los veinte formatos |
| `libimage_processing_util_jni.so` | 28 KB | CameraX |
| `libsurface_util_jni.so` | 5 KB | CameraX |

## Comprobaciones

| Comprobación | Estado |
|---|---|
| Sin permiso de red | ✅ |
| Sin permisos de almacenamiento | ✅ |
| Sin permiso de ubicación | ✅ |
| Sin identificador de publicidad | ✅ |
| Respaldo del sistema desactivado | ✅ |
| Sin SDK de analítica ni de publicidad | ✅ |

La última comprobación busca en el dex las huellas de los doce SDK de rastreo,
publicidad y facturación más frecuentes —Firebase, Google Mobile Ads,
Crashlytics, Facebook, AppsFlyer, Adjust, Sentry, Amplitude, Mixpanel,
OneSignal, Analytics y la facturación de Play—. Ninguna aparece.

## Por qué el permiso de red importa más que una promesa

El proyecto no promete no usar la red: **no declara el permiso**. La diferencia
no es retórica. Sin `android.permission.INTERNET`, el sistema operativo impide
abrir un socket, sea cual sea la biblioteca enlazada y sea cual sea el código
que la llame. No hay que confiar en la aplicación; basta con mirar esta tabla,
que sale del archivo que se instala.

## Lo que esta auditoría no comprueba

Que no haya rastreo **dentro** de las bibliotecas que sí están, más allá de
buscar los nombres de paquete conocidos. Para eso está la otra mitad del
argumento: las cuatro dependencias del proyecto son Apache-2.0, su código está
publicado y ninguna necesita red para hacer lo que hace. La ausencia del
permiso las ata a todas por igual.
