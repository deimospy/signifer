# Decisiones

Las que el proyecto dejó abiertas, y con qué se han cerrado. Las que se
cerraron midiendo llevan el enlace a la medición.

## 1. Identificador de aplicación

**`org.sarambi.signifer`.** Adoptado.

## 2. La tasa de detección de zxing-cpp

**81,3 %** sobre un banco de 150 imágenes difíciles: 122 aciertos, contando
como acierto leer exactamente lo que el código decía.

No existía una cifra pública de esta biblioteca —los estudios publicados miden
la implementación Java original— así que se ha medido, y el banco se regenera
byte a byte para que cualquiera pueda repetirlo. Ver
[RENDIMIENTO.md](RENDIMIENTO.md).

**La conclusión que se saca de mirarla por condición:** lo que hace fallar la
lectura no es el ruido ni el desenfoque, que se leen enteros, sino que el
código ocupe pocos píxeles. Por eso el análisis corre a 1280×720 y no a menos,
que era lo que la intuición pedía para ahorrar milisegundos.

**No se sustituye la biblioteca ni se añade una segunda pasada.** La opción
estaba prevista —por eso el decodificador vive tras una interfaz propia— y la
medición no la justifica: los fallos están en imágenes que ninguna segunda
pasada recupera.

## 3. Qué formatos vienen activos de fábrica

**Los veinte.** Quien lee la etiqueta de un paquete no sabe qué formato lleva,
y fallar una lectura es peor que tardar unos milisegundos más. Con el percentil
95 en 6,5 ms y un fotograma de 33 ms, el margen sobra.

Se pueden apagar, y hay tres ajustes preparados —los veinte, solo matriciales,
solo QR— para quien use la aplicación como lector de QR y quiera cada
milisegundo.

## 4. Si `tryHarder` compensa

**No.** Cuesta más del doble de tiempo —la mediana pasa de 1,5 a 3,5 ms y el
percentil 95 se triplica, de 6,5 a 21 ms— y no acierta **ni una sola imagen
más** de las 150.

Queda apagado de fábrica y disponible en los ajustes. La generación desde una
imagen fija sí lo activa: ahí no hay presupuesto de fotograma y la persona ya
está esperando.

## 5. Idiomas

Tres de salida: **español, inglés y portugués**, con las 158 cadenas
sincronizadas y comprobadas por el análisis estático, que falla la compilación
si falta una traducción.

**El guaraní sigue pendiente** y sigue siendo buen candidato: es cooficial en
Paraguay y no lo trae ningún lector de códigos. No se ha hecho porque traducir
sin hablar el idioma produce texto que nadie usa; hace falta alguien que lo
hable.

## Decisiones que no estaban abiertas y aun así hubo que tomar

**El permiso `VIBRATE` se declara.** No estaba previsto. Es un permiso normal
—el sistema lo concede sin preguntar y no da acceso a ningún dato— y sin él la
aplicación se cerraba al confirmar una lectura. Aparece en la
[auditoría del binario](AUDITORIA.md) junto con el de cámara, que son los dos
únicos.

**El identificador de depuración no lleva sufijo.** Los atajos del icono
declaran el paquete de destino en un recurso XML, que no admite marcadores de
compilación; con sufijo, los atajos de una compilación de desarrollo no abrían
nada.

**El contraste mínimo para exportar es 3 a 1.** La norma ISO/IEC 15415 habla de
reflectancia, que no se puede medir desde una pantalla. Se usa la relación de
luminancia relativa de sRGB, la misma de las pautas de accesibilidad, con 3 a 1
como suelo y 4,5 a 1 como cómodo. Un código invertido —módulos más claros que
el fondo— sí se deja exportar, avisando: tiene contraste de sobra y es una
elección legítima.
