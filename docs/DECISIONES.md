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

**En todos los fotogramas, no; en uno de cada dos y solo para lineales, sí.**
Sobre las 225 imágenes acierta una sola más —un UPC-E con ruido— y la mediana
pasa de 1,25 a 4,75 ms. Pero el banco no tiene etiquetas finas, y una etiqueta
de número de serie fuera del centro no se lee sin él. La cámara alterna un
fotograma rápido y uno completo que busca solo códigos de barras
([medición](RENDIMIENTO.md#etiquetas-finas)).

Aplicarlo a todos los fotogramas sigue disponible en los ajustes. La lectura
desde una imagen fija lo activa siempre: ahí no hay presupuesto de fotograma y
la persona ya está esperando.

## 5. Idiomas

**Cerrada en tres: español, inglés y portugués.** Las 219 cadenas traducibles
están al 100 % en los tres, y la sincronía no se vigila a mano: el análisis
estático falla la compilación si falta una traducción. El paquete solo incluye
esos tres, así que ninguna dependencia cuela los suyos.

No hay más idiomas previstos. Añadir uno es añadir un archivo y mantenerlo al
día para siempre; se hará cuando haya una razón concreta y alguien que hable
el idioma, no por tener más banderas en la ficha.

## Decisiones que no estaban abiertas y aun así hubo que tomar

**En la cámara solo se lee lo que está dentro del marco.** Con varios códigos a
la vista —una pila de discos, una caja con etiquetas— se leía el primero que la
biblioteca encontraba en cualquier parte de la imagen, no el que se apuntaba.
Ahora el fotograma se recorta al marco, con un margen para la zona tranquila
de un código que lo llena, y decodificar cuesta la mitad. El marco ocupa el
84 % del ancho en vertical para que quepan los códigos largos.

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

**La marca es un estandarte, y se genera desde su geometría.** El signifer
portaba el signum; aquí el paño lleva una S de módulos con dos patrones de
detección. No hay piezas dibujadas a mano: `tools/brand/banner.py` define la
figura sobre una grilla de 144 unidades y de ahí salen los SVG de `docs/marca`,
el icono adaptativo, los mapas de bits del lanzador y el icono de la tienda.
Los módulos de la S son huecos, no cuadrados pintados: sobre el tema claro el
estandarte es carmesí `#8C1D2C` y la S deja ver el blanco del fondo; sobre el
oscuro, el estandarte pasa a hueso.

**La paleta es la de la marca en todos los teléfonos.** Material 3 ofrece
tomar los colores del fondo de pantalla, y con eso la aplicación salía en
azules o verdes según el aparato, lejos del carmesí del estandarte. La paleta
es propia: carmesí para lo principal, bronce para lo secundario y superficies
hueso cálido, con cada par de texto y fondo por encima del contraste AA en el
tema claro y en el oscuro. Los colores de riesgo siguen aparte y siempre van
con icono y texto, porque el rojo de peligro y el carmesí de la marca se
parecen demasiado para confiar solo en el color.

**Todo lo que se pulsa es una píldora.** Botones, filtros, la búsqueda y los
botones de la cámara van con las esquinas del todo redondeadas; los bloques de
contenido, con esquinas amplias de 20 dp; los campos con etiqueta flotante, de
16 dp, porque con más la etiqueta caería sobre la curva del borde. Antes
convivían cinco radios distintos. La forma no cuesta rendimiento: la GPU dibuja
igual un rectángulo recto que uno redondeado.

**Los iconos son de Lucide.** Un juego de trazo coherente, con licencia ISC
compatible con Apache-2.0. `tools/brand/lucide_import.py` copia al proyecto
solo la geometría de los iconos usados y `tools/brand/generate.py` la convierte
en vectores de Android, reescribiendo los números que el lector de trazados de
algunas versiones de Android interpreta mal. La resistencia a daños no tiene
equivalente en Lucide y se dibuja con su mismo trazo. Cada acción del resultado
lleva su icono: red, contacto, correo, teléfono, mensaje, mapa y evento.

**Toda hoja inferior se puede cerrar a la vista.** Arrastrarla hacia abajo o
tocar fuera funciona, pero no se descubre solo. Cada hoja lleva el asa que el
sistema reconoce y un botón de cerrar fijo arriba, que no se va al desplazar.

**Los ajustes son un solo panel.** La lectura se ajustaba desde la cámara y el
historial desde un menú propio, y encontrar una opción era adivinar en cuál de
los dos estaba. Ahora el mismo botón abre el mismo panel en las dos pantallas,
con tres secciones: lectura, historial y acerca de. La lista de formatos va
plegada para que el resto no quede al fondo.

**Los campos de texto tienen límite.** 4000 caracteres en la creación —el doble
de lo que cabe en el QR legible más grande— y 200 en la búsqueda. En Android 16
el propio sistema ya cortó a 5000 caracteres un texto pegado de 700 000; en las
versiones anteriores no se comprobó y no se da por hecho. Sin límite, un campo
con cientos de miles de caracteres se guarda al salir de la aplicación en un
envío al sistema que no admite más de un megabyte.
