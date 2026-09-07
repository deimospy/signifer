# R8 no ve el puente nativo: zxing-cpp instancia estas clases desde C++.
-keep class zxingcpp.** { *; }

# Las vistas que se inflan desde XML las conserva aapt2, que genera las reglas leyendo los propios
# layouts.

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
