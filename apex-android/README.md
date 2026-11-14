# APEX — app nativa Android (v0.1)

App de lectura OBD-II para adaptadores ELM327 **Bluetooth clásico**.
Versión mínima: conecta, inicializa el adaptador y lee en vivo
**RPM, velocidad y temperatura de refrigerante**.

## Cómo obtener el APK sin instalar Android Studio

1. Crea un repositorio **nuevo** en GitHub (ej. `apex-android`), **Public**.
2. Sube **todo el contenido de esta carpeta** manteniendo la estructura
   (incluida la carpeta `.github`).
3. GitHub compila solo: ve a la pestaña **Actions** y espera a que el
   trabajo "Compilar APK de APEX" termine (bolita verde).
4. Entra al trabajo terminado → sección **Artifacts** → descarga
   **`apex-debug-apk`**. Dentro está `app-debug.apk`.
5. Pásalo al celular e instálalo (hay que permitir "instalar apps de
   origen desconocido" en Android).

## Para probar con el carro

1. Empareja primero el ELM327 en los **ajustes Bluetooth** del teléfono.
2. Enchufa el ELM327 al puerto OBD del carro y ponlo en contacto.
3. Abre APEX, concede el permiso de Bluetooth, toca tu adaptador en la
   lista, y observa el registro y los datos.

## Estructura

```
app/src/main/java/com/apex/app/
├── core/            Result y constantes
├── data/bluetooth/  transporte SPP clásico (abstraído por interfaz)
├── data/obd/        comando polimórfico, PIDs e inicializador
└── ui/              ViewModel + pantalla Compose
```
