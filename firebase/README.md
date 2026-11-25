# Configuración de Firebase

## Proyecto y reglas

1. Crea un proyecto Firebase y una Realtime Database.
2. En Authentication, habilita el proveedor de correo y contraseña.
3. Publica las reglas de [`database.rules.json`](./database.rules.json) en Realtime Database.
4. Copia [`local.properties.example`](./local.properties.example) a `local.properties` en la raíz del proyecto y completa los datos de la app Android.

Al registrar una cuenta desde la app se crea un perfil con rol `usuario`. Cada cuenta administra los horarios de su propia placa en `devices/<UID>/alarms`; el estado se consulta en `devices/<UID>/status`. La pantalla permite añadir hasta ocho horarios, activarlos/desactivarlos y eliminarlos.

## Placa Arduino UNO R4 WiFi

1. Crea una cuenta Firebase exclusiva para la placa. En `users/<UID_DE_LA_PLACA>`, configura `role: "device"` y `ownerUid: "<UID_DEL_USUARIO>"`.
2. Copia [`../firmware/arduino_secrets.h.example`](../firmware/arduino_secrets.h.example) como `arduino_secrets.h` junto al sketch y completa Wi-Fi, API key, host de Realtime Database, credenciales de la placa y `FIREBASE_OWNER_UID` con el UID del usuario al que pertenece.
3. En `FIREBASE_OWNER_UID` usa el mismo UID de la cuenta que inicia sesión en la app; así la app y la placa apuntan a la misma ruta y las reglas impiden acceder a horarios ajenos. Los tópicos MQTT también quedan separados por UID: `riego/<UID>/estado` y `riego/<UID>/comando`.
4. Instala las bibliotecas ArduinoMqttClient, ArduinoHttpClient, ArduinoJson y NTPClient. `WiFiS3`, `RTC` y `EEPROM` vienen con el core `arduino:renesas_uno`.

La placa sincroniza la hora por NTP en UTC−3, descarga los horarios y conserva una copia en EEPROM para ejecutarlos sin conexión. Tras un reinicio sin acceso a Internet, necesita volver a sincronizar el reloj antes de ejecutar horarios.

Si ya existían horarios en `devices/uno-r4/alarms`, muévelos a `devices/<UID_DEL_USUARIO>/alarms` en Realtime Database. No publiques `local.properties` ni `arduino_secrets.h`: contienen claves y credenciales. Ambos archivos están excluidos del control de versiones.
