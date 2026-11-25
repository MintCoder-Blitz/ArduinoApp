#include "Arduino_LED_Matrix.h"
#include <WiFiS3.h>
#include "arduino_secrets.h"
#include <ArduinoMqttClient.h>
#include <ArduinoHttpClient.h>
#include <ArduinoJson.h>
#include <EEPROM.h>
#include <NTPClient.h>
#include <RTC.h>
#include <WiFiUdp.h>
#include <string.h>

ArduinoLEDMatrix matrix;

const int PIN_SENSOR = A0;
const int PIN_BOMBA = 7;
const int RELE_ON = LOW;
const int RELE_OFF = HIGH;

const char MQTT_BROKER[] = "10.46.111.101";
const int MQTT_PORT = 1883;

String MQTT_STATUS_TOPIC;
String MQTT_COMMAND_TOPIC;

const int UMBRAL_SECO = 800;
const int SENSOR_SECO = 800;
const int SENSOR_HUMEDO = 300;
const unsigned long TIEMPO_RIEGO = 3000;
const unsigned long ESPERA_ENTRE_RIEGOS = 1800000UL;
const unsigned long INTERVALO_MQTT = 10000UL;
const unsigned long INTERVALO_BLUETOOTH = 10000UL;
const unsigned long INTERVALO_WIFI = 15000UL;
const unsigned long INTERVALO_FIREBASE = 30000UL;
const unsigned long INTERVALO_ESTADO_FIREBASE = 60000UL;
const unsigned long INTERVALO_NTP = 21600000UL;
const unsigned long INTERVALO_AUTH = 3000000UL;
const uint32_t EEPROM_MAGIC = 0x52494547;
const uint8_t MAX_ALARMAS = 8;

struct AlarmaGuardada {
  char id[24];
  uint8_t hora;
  uint8_t minuto;
  uint8_t habilitada;
  uint8_t dias;
  uint8_t duracionSegundos;
  uint32_t ultimaFechaEjecutada;
};

struct HorariosGuardados {
  uint32_t magic;
  uint8_t cantidad;
  AlarmaGuardada alarmas[MAX_ALARMAS];
};

WiFiClient wifiClient;
MqttClient mqttClient(wifiClient);
WiFiUDP ntpUDP;
NTPClient ntpClient(ntpUDP, "pool.ntp.org", -10800, 60000);
HorariosGuardados horarios;

unsigned long ultimoRiego = 0;
unsigned long ultimaPublicacion = 0;
unsigned long ultimaPublicacionBluetooth = 0;
unsigned long ultimoIntentoWiFi = 0;
unsigned long ultimaConsultaFirebase = 0;
unsigned long ultimaPublicacionFirebase = 0;
unsigned long ultimaSincronizacionNtp = 0;
unsigned long ultimaAutenticacion = 0;
unsigned long ultimoIntentoAutenticacion = 0;
String mensajeBluetooth = "";
String firebaseIdToken = "";
int estadoMostrado = -1;
bool ultimoSeRego = false;
bool relojValido = false;
String ultimoMotivo = "Sin accion";

void regar(int duracionSegundos = TIEMPO_RIEGO / 1000);

String firebaseDevicePath() {
  return String("/devices/") + FIREBASE_OWNER_UID;
}

byte GOTA[8][12] = {
  {0,0,0,0,0,1,1,0,0,0,0,0},
  {0,0,0,0,0,1,1,0,0,0,0,0},
  {0,0,0,0,1,1,1,1,0,0,0,0},
  {0,0,0,1,1,1,1,1,1,0,0,0},
  {0,0,0,1,1,1,1,1,1,0,0,0},
  {0,0,0,1,1,1,1,1,1,0,0,0},
  {0,0,0,1,1,1,1,1,1,0,0,0},
  {0,0,0,0,1,1,1,1,0,0,0,0}
};

byte EQUIS[8][12] = {
  {0,0,1,0,0,0,0,0,0,1,0,0},
  {0,0,0,1,0,0,0,0,1,0,0,0},
  {0,0,0,0,1,0,0,1,0,0,0,0},
  {0,0,0,0,0,1,1,0,0,0,0,0},
  {0,0,0,0,0,1,1,0,0,0,0,0},
  {0,0,0,0,1,0,0,1,0,0,0,0},
  {0,0,0,1,0,0,0,0,1,0,0,0},
  {0,0,1,0,0,0,0,0,0,1,0,0}
};

byte REGANDO[8][12] = {
  {1,0,1,0,1,0,1,0,1,0,1,0},
  {0,1,0,1,0,1,0,1,0,1,0,1},
  {1,0,1,0,1,0,1,0,1,0,1,0},
  {0,1,0,1,0,1,0,1,0,1,0,1},
  {1,0,1,0,1,0,1,0,1,0,1,0},
  {0,1,0,1,0,1,0,1,0,1,0,1},
  {1,0,1,0,1,0,1,0,1,0,1,0},
  {0,1,0,1,0,1,0,1,0,1,0,1}
};

void setup() {
  MQTT_STATUS_TOPIC = String("riego/") + FIREBASE_OWNER_UID + "/estado";
  MQTT_COMMAND_TOPIC = String("riego/") + FIREBASE_OWNER_UID + "/comando";
  digitalWrite(PIN_BOMBA, RELE_OFF);
  pinMode(PIN_BOMBA, OUTPUT);
  Serial.begin(115200);
  Serial1.begin(9600);
  analogReadResolution(10);
  matrix.begin();
  RTC.begin();
  ntpClient.begin();
  cargarHorariosEEPROM();
  iniciarWiFi();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED &&
      millis() - ultimoIntentoWiFi >= INTERVALO_WIFI) {
    iniciarWiFi();
  }

  if (!mqttClient.connected() && WiFi.status() == WL_CONNECTED) {
    conectarMQTT();
  } else if (mqttClient.connected()) {
    mqttClient.poll();
  }

  leerComandosMQTT();
  leerComandosBluetooth();
  actualizarMatriz();
  procesarRiegosProgramados();

  if (millis() - ultimaPublicacion >= INTERVALO_MQTT &&
      mqttClient.connected()) {
    publicarEstado();
  }

  procesarFirebase();

  if (millis() - ultimaPublicacionBluetooth >= INTERVALO_BLUETOOTH) {
    publicarEstadoBluetooth();
  }

  riegoAutomatico();
}

void iniciarWiFi() {
  ultimoIntentoWiFi = millis();
  Serial.println("Iniciando conexion Wi-Fi");
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

void conectarMQTT() {
  mqttClient.setId("uno-r4-riego");
  Serial.print("Conectando MQTT...");

  if (mqttClient.connect(MQTT_BROKER, MQTT_PORT)) {
    Serial.println(" conectado");
    mqttClient.subscribe(MQTT_COMMAND_TOPIC.c_str());
  } else {
    Serial.print(" error ");
    Serial.println(mqttClient.connectError());
  }

  delay(1000);
}

bool estaSeca() {
  return analogRead(PIN_SENSOR) > UMBRAL_SECO;
}

int porcentajeHumedad() {
  int lectura = analogRead(PIN_SENSOR);
  int porcentaje = map(
    lectura,
    SENSOR_SECO,
    SENSOR_HUMEDO,
    0,
    100
  );
  return constrain(porcentaje, 0, 100);
}

void publicarEstado() {
  int lectura = analogRead(PIN_SENSOR);
  int porcentaje = porcentajeHumedad();

  mqttClient.beginMessage(MQTT_STATUS_TOPIC.c_str());
  mqttClient.print("{\"humedad_porcentaje\":");
  mqttClient.print(porcentaje);
  mqttClient.print(",\"humedad_raw\":");
  mqttClient.print(lectura);
  mqttClient.print(",\"umbral_seco\":");
  mqttClient.print(UMBRAL_SECO);
  mqttClient.print(",\"necesita_riego\":");
  mqttClient.print(estaSeca() ? "true" : "false");
  mqttClient.print(",\"se_rego\":");
  mqttClient.print(ultimoSeRego ? "true" : "false");
  mqttClient.print(",\"motivo\":\"");
  mqttClient.print(ultimoMotivo);
  mqttClient.print("\"}");
  mqttClient.endMessage();

  Serial.print("Estado publicado: humedad=");
  Serial.print(porcentaje);
  Serial.print("%, raw=");
  Serial.println(lectura);

  ultimaPublicacion = millis();
}

void leerComandosMQTT() {
  while (mqttClient.available()) {
    String comando;

    while (mqttClient.available()) {
      comando += (char)mqttClient.read();
    }

    comando.trim();
    comando.toLowerCase();

    if (comando == "si" || comando == "sí") {
      ultimoMotivo = "Riego manual confirmado";
      ultimoSeRego = true;
      regar();
    } else if (comando == "no") {
      ultimoMotivo = "Riego rechazado manualmente";
      ultimoSeRego = false;
      publicarEstado();
    } else if (comando == "estado" || comando == "valor") {
      publicarEstado();
    }
  }
}

void leerComandosBluetooth() {
  while (Serial1.available()) {
    char c = Serial1.read();

    if (c == '\n') {
      mensajeBluetooth.trim();
      mensajeBluetooth.toLowerCase();

      if (mensajeBluetooth == "si" || mensajeBluetooth == "sí") {
        ultimoMotivo = "Riego manual confirmado por Bluetooth";
        ultimoSeRego = true;
        Serial1.println("Regando...");
        regar();
        Serial1.println("Listo");
      } else if (mensajeBluetooth == "no") {
        ultimoMotivo = "Riego rechazado por Bluetooth";
        ultimoSeRego = false;
        Serial1.println("Ok, no riego");
      } else if (mensajeBluetooth == "estado" ||
                 mensajeBluetooth == "valor") {
        publicarEstadoBluetooth();
      }

      mensajeBluetooth = "";
    } else if (c != '\r') {
      mensajeBluetooth += c;
    }
  }
}

void publicarEstadoBluetooth() {
  int porcentaje = porcentajeHumedad();
  bool seca = estaSeca();

  Serial1.print("Humedad: ");
  Serial1.print(porcentaje);
  Serial1.print("%; estado: ");
  Serial1.print(seca ? "SECA" : "HUMEDA");
  Serial1.print("; se_rego: ");
  Serial1.print(ultimoSeRego ? "si" : "no");
  Serial1.print("; motivo: ");
  Serial1.println(ultimoMotivo);

  ultimoSeRego = false;
  ultimaPublicacionBluetooth = millis();
}

void cargarHorariosEEPROM() {
  EEPROM.get(0, horarios);
  if (horarios.magic != EEPROM_MAGIC || horarios.cantidad > MAX_ALARMAS) {
    memset(&horarios, 0, sizeof(horarios));
    horarios.magic = EEPROM_MAGIC;
    EEPROM.put(0, horarios);
  }
}

void guardarHorariosEEPROM() {
  EEPROM.put(0, horarios);
}

bool firebaseRequest(
  const char *host,
  const String &method,
  const String &path,
  const String &payload,
  String &response
) {
  WiFiSSLClient secureClient;
  HttpClient http(secureClient, host, 443);
  http.setHttpResponseTimeout(8000);

  if (method == "GET") {
    http.get(path);
  } else if (method == "POST") {
    http.post(path, "application/json", payload);
  } else if (method == "PUT") {
    http.put(path, "application/json", payload);
  } else {
    return false;
  }

  int statusCode = http.responseStatusCode();
  response = http.responseBody();
  http.stop();
  return statusCode >= 200 && statusCode < 300;
}

bool autenticarFirebase() {
  if (strlen(FIREBASE_API_KEY) == 0 ||
      strlen(FIREBASE_DATABASE_HOST) == 0 ||
      strlen(FIREBASE_DEVICE_EMAIL) == 0 ||
      strlen(FIREBASE_DEVICE_PASSWORD) == 0 ||
      strlen(FIREBASE_OWNER_UID) == 0) {
    Serial.println("Configura Firebase en arduino_secrets.h");
    return false;
  }

  StaticJsonDocument<256> request;
  request["email"] = FIREBASE_DEVICE_EMAIL;
  request["password"] = FIREBASE_DEVICE_PASSWORD;
  request["returnSecureToken"] = true;
  String payload;
  serializeJson(request, payload);

  String response;
  String path = String("/v1/accounts:signInWithPassword?key=") + FIREBASE_API_KEY;
  if (!firebaseRequest(
        "identitytoolkit.googleapis.com",
        "POST",
        path,
        payload,
        response
      )) {
    Serial.println("No se pudo autenticar el dispositivo con Firebase");
    firebaseIdToken = "";
    ultimaAutenticacion = 0;
    return false;
  }

  StaticJsonDocument<1024> result;
  DeserializationError error = deserializeJson(result, response);
  if (error || !result["idToken"].is<const char *>()) {
    Serial.println("Respuesta de autenticacion Firebase invalida");
    firebaseIdToken = "";
    ultimaAutenticacion = 0;
    return false;
  }

  firebaseIdToken = result["idToken"].as<String>();
  ultimaAutenticacion = millis();
  Serial.println("Dispositivo autenticado con Firebase");
  return true;
}

void sincronizarReloj() {
  if (WiFi.status() != WL_CONNECTED) return;
  if (!ntpClient.update() && !ntpClient.forceUpdate()) {
    Serial.println("No se pudo sincronizar el reloj por NTP");
    return;
  }

  RTCTime horaLocal(ntpClient.getEpochTime());
  if (RTC.setTime(horaLocal)) {
    relojValido = true;
    ultimaSincronizacionNtp = millis();
    Serial.print("Reloj sincronizado: ");
    Serial.println(horaLocal.toString());
  }
}

bool sincronizarHorariosFirebase() {
  String response;
  String path = firebaseDevicePath() + "/alarms.json?auth=" + firebaseIdToken;
  if (!firebaseRequest(
        FIREBASE_DATABASE_HOST,
        "GET",
        path,
        "",
        response
      )) {
    Serial.println("No se pudieron leer los horarios de Firebase");
    return false;
  }

  HorariosGuardados nuevos = {};
  nuevos.magic = EEPROM_MAGIC;
  if (response != "null") {
    StaticJsonDocument<2048> document;
    DeserializationError error = deserializeJson(document, response);
    if (error || !document.is<JsonObject>()) {
      Serial.println("Formato de horarios Firebase invalido");
      return false;
    }

    for (JsonPair pair : document.as<JsonObject>()) {
      if (nuevos.cantidad >= MAX_ALARMAS) break;
      JsonObject alarm = pair.value().as<JsonObject>();
      int hora = alarm["hour"] | -1;
      int minuto = alarm["minute"] | -1;
      int dias = alarm["daysMask"] | 127;
      int duracion = alarm["durationSeconds"] | 3;
      if (hora < 0 || hora > 23 || minuto < 0 || minuto > 59 ||
          dias < 1 || dias > 127 || duracion < 1 || duracion > 30) {
        continue;
      }

      AlarmaGuardada &destino = nuevos.alarmas[nuevos.cantidad];
      const char *id = pair.key().c_str();
      strncpy(destino.id, id, sizeof(destino.id) - 1);
      destino.hora = hora;
      destino.minuto = minuto;
      destino.habilitada = alarm["enabled"] | false;
      destino.dias = dias;
      destino.duracionSegundos = duracion;

      for (uint8_t i = 0; i < horarios.cantidad; i++) {
        const AlarmaGuardada &anterior = horarios.alarmas[i];
        if (strcmp(anterior.id, destino.id) == 0 &&
            anterior.hora == destino.hora &&
            anterior.minuto == destino.minuto &&
            anterior.habilitada == destino.habilitada &&
            anterior.dias == destino.dias &&
            anterior.duracionSegundos == destino.duracionSegundos) {
          destino.ultimaFechaEjecutada = anterior.ultimaFechaEjecutada;
          break;
        }
      }
      nuevos.cantidad++;
    }
  }

  if (memcmp(&nuevos, &horarios, sizeof(horarios)) != 0) {
    horarios = nuevos;
    guardarHorariosEEPROM();
    Serial.print("Horarios guardados localmente: ");
    Serial.println(horarios.cantidad);
  }
  return true;
}

void publicarEstadoFirebase() {
  if (firebaseIdToken.length() == 0 || WiFi.status() != WL_CONNECTED) return;

  RTCTime hora;
  bool horaDisponible = RTC.getTime(hora) && hora.getYear() >= 2024;
  StaticJsonDocument<384> document;
  document["humedad_porcentaje"] = porcentajeHumedad();
  document["humedad_raw"] = analogRead(PIN_SENSOR);
  document["umbral_seco"] = UMBRAL_SECO;
  document["necesita_riego"] = estaSeca();
  document["se_rego"] = ultimoSeRego;
  document["motivo"] = ultimoMotivo;
  document["reloj_sincronizado"] = horaDisponible;
  if (horaDisponible) {
    document["hora_local"] = hora.toString();
  }

  String payload;
  serializeJson(document, payload);
  String response;
  String path = firebaseDevicePath() + "/status.json?auth=" + firebaseIdToken;
  if (!firebaseRequest(
        FIREBASE_DATABASE_HOST,
        "PUT",
        path,
        payload,
        response
      )) {
    Serial.println("No se pudo publicar el estado en Firebase");
  } else {
    ultimaPublicacionFirebase = millis();
    ultimoSeRego = false;
  }
}

void procesarFirebase() {
  if (WiFi.status() != WL_CONNECTED) return;

  bool tokenVacio = firebaseIdToken.length() == 0;
  bool debeAutenticar = tokenVacio
    ? (ultimaAutenticacion == 0 &&
       (ultimoIntentoAutenticacion == 0 ||
        millis() - ultimoIntentoAutenticacion >= 60000UL))
    : millis() - ultimaAutenticacion >= INTERVALO_AUTH;
  if (debeAutenticar) {
    ultimoIntentoAutenticacion = millis();
    if (!autenticarFirebase()) return;
  }

  if (!relojValido || millis() - ultimaSincronizacionNtp >= INTERVALO_NTP) {
    sincronizarReloj();
  }

  if (millis() - ultimaConsultaFirebase >= INTERVALO_FIREBASE) {
    ultimaConsultaFirebase = millis();
    sincronizarHorariosFirebase();
  }

  if (millis() - ultimaPublicacionFirebase >= INTERVALO_ESTADO_FIREBASE) {
    publicarEstadoFirebase();
  }
}

uint32_t fechaActual(const RTCTime &hora) {
  return hora.getYear() * 10000UL +
    (static_cast<int>(hora.getMonth()) + 1) * 100UL +
    hora.getDayOfMonth();
}

void procesarRiegosProgramados() {
  RTCTime hora;
  if (!RTC.getTime(hora) || hora.getYear() < 2024) {
    relojValido = false;
    return;
  }
  relojValido = true;

  int diaSemana = static_cast<int>(hora.getDayOfWeek());
  uint8_t mascaraDia = 1U << diaSemana;
  uint32_t fecha = fechaActual(hora);
  int duracion = 0;

  for (uint8_t i = 0; i < horarios.cantidad; i++) {
    AlarmaGuardada &alarma = horarios.alarmas[i];
    if (alarma.habilitada &&
        (alarma.dias & mascaraDia) != 0 &&
        alarma.hora == hora.getHour() &&
        alarma.minuto == hora.getMinutes() &&
        alarma.ultimaFechaEjecutada != fecha) {
      alarma.ultimaFechaEjecutada = fecha;
      duracion = max(duracion, alarma.duracionSegundos);
    }
  }

  if (duracion > 0) {
    guardarHorariosEEPROM();
    ultimoMotivo = "Horario programado";
    ultimoSeRego = true;
    regar(duracion);
  }
}

bool puedeRegarAutomaticamente() {
  return ultimoRiego == 0 ||
    millis() - ultimoRiego >= ESPERA_ENTRE_RIEGOS;
}

void riegoAutomatico() {
  if (!mqttClient.connected() &&
      estaSeca() &&
      puedeRegarAutomaticamente()) {
    ultimoMotivo = "Riego automatico";
    ultimoSeRego = true;
    regar();
  }
}

void regar(int duracionSegundos) {
  matrix.renderBitmap(REGANDO, 8, 12);
  digitalWrite(PIN_BOMBA, RELE_ON);
  delay(static_cast<unsigned long>(duracionSegundos) * 1000UL);
  digitalWrite(PIN_BOMBA, RELE_OFF);
  ultimoRiego = millis();
  estadoMostrado = -1;
  publicarSiConectado();
}

void publicarSiConectado() {
  if (mqttClient.connected()) {
    publicarEstado();
  }
  if (firebaseIdToken.length() > 0) {
    publicarEstadoFirebase();
  }
}

void actualizarMatriz() {
  int estado = estaSeca() ? 1 : 0;
  if (estado != estadoMostrado) {
    if (estado) {
      matrix.renderBitmap(EQUIS, 8, 12);
    } else {
      matrix.renderBitmap(GOTA, 8, 12);
    }
    estadoMostrado = estado;
  }
}
