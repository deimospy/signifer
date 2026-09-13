# Decisiones

Las que el proyecto dejó abiertas, y con qué se han cerrado. Las que se
cerraron midiendo llevan el enlace a la medición.

## 1. Identificador de aplicación

**`org.sarambi.signifer`.** Adoptado.

## 2. La tasa de detección de zxing-cpp

**78,2 % en vivo y 83,1 % desde una imagen** sobre un banco de 225 imágenes
difíciles de los trece formatos que la aplicación escribe, contando como
acierto leer exactamente lo que el código decía.

No existía una cifra pública de esta biblioteca —los estudios publicados miden
la implementación Java original— así que se ha medido, y el banco se regenera
byte a byte para que cualquiera pueda repetirlo. Ver
[RENDIMIENTO.md](RENDIMIENTO.md).

**La conclusión que se saca de mirarla por condición:** lo que hace fallar la
lectura no es el ruido ni el desenfoque, que se leen enteros, sino que el
código ocupe pocos píxeles. Por eso el análisis corre a 1280×720 y no a menos,
que era lo que la intuición pedía para ahorrar milisegundos.

**No se sustituye la biblioteca.** La opción estaba prevista —por eso el
decodificador vive tras una interfaz propia— y la medición no la justifica.

**Sí hay un segundo intento, solo para imágenes fijas.** Al ampliar el banco a
todos los formatos apareció que la opción de invertidos de la biblioteca no
alcanza a los códigos de barras: ninguno claro sobre oscuro se leía. Un segundo
intento con la imagen invertida, solo cuando el primero no encontró nada, los
lee todos. En la cámara no se hace: duplicaría el coste de cada fotograma
vacío, que son casi todos.

## 3. Qué formatos vienen activos de fábrica

**Los veinte.** Quien lee la etiqueta de un paquete no sabe qué formato lleva,
y fallar una lectura es peor que tardar unos milisegundos más. Con el percentil
95 por debajo de 12 ms y un fotograma de 33 ms, el margen sobra.

Se pueden apagar, y hay tres ajustes preparados —los veinte, solo matriciales,
solo QR— para quien use la aplicación como lector de QR y quiera cada
milisegundo.

## 4. Si `tryHarder` compensa

**No, en la cámara.** Sobre las 225 imágenes acierta una sola más —un UPC-E con
ruido— y la mediana pasa de 1,25 a 4,75 ms.

Queda apagado de fábrica y disponible en los ajustes. La lectura desde una
imagen fija sí lo activa: ahí no hay presupuesto de fotograma y la persona ya
está esperando.

## 5. Idiomas

**Cerrada en tres: español, inglés y portugués.** Las 217 cadenas traducibles
están al 100 % en los tres, y la sincronía no se vigila a mano: el análisis
estático falla la compilación si falta una traducción. El paquete solo incluye
esos tres, así que ninguna dependencia cuela los suyos.

No hay más idiomas previstos. Añadir uno es añadir un archivo y mantenerlo al
día para siempre; se hará cuando haya una razón concreta y alguien que hable
el idioma, no por tener más banderas en la ficha.

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

**Cualquier escritura entra en un código matricial, con dos límites que se
avisan.** QR y Aztec escriben emojis, chino, árabe, devanagari y cualquier otra
escritura, y el lector de la aplicación los lee tal cual. Data Matrix y PDF417
también, salvo los emojis: sus codificadores en ZXing procesan el texto de a
una unidad UTF-16 y no saben escribir un carácter fuera del plano básico. Y el
Data Matrix compacto de ZXing, el único que sabe declarar UTF-8, deja datos de
relleno que el lector toma por texto cuando una escritura no latina va con
saltos de línea. Por eso los textos latinos van por el codificador clásico, y
lo que sale del compacto se relee antes de mostrarlo: si no dice exactamente lo
escrito, la pantalla lo avisa y sugiere QR. Mejor ningún código que uno que
diga otra cosa.

**Los campos de texto tienen límite.** 4000 caracteres en la creación —el doble
de lo que cabe en el QR legible más grande— y 200 en la búsqueda. En Android 16
el propio sistema ya cortó a 5000 caracteres un texto pegado de 700 000; en las
versiones anteriores no se comprobó y no se da por hecho. Sin límite, un campo
con cientos de miles de caracteres se guarda al salir de la aplicación en un
envío al sistema que no admite más de un megabyte.
