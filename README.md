# 🚖 ColetoMaps - Arica

> Aplicación móvil nativa para Android diseñada para optimizar el transporte público en Arica, Chile, facilitando la visualización de rutas de colectivos urbanos y la notificación en tiempo real de incidentes viales.

---

## 📌 Descripción del Proyecto

**ColetoMaps** nace para resolver la falta de trazado digital del servicio de colectivos en la ciudad de Arica. La plataforma permite a los usuarios consultar trayectos, tarifas por horario (diurno, tarde, noche) y recibir alertas automatizadas sobre incidentes viales que afecten su recorrido seleccionado.

Además, fomenta la participación comunitaria mediante un sistema de **reportes ciudadanos en tiempo real** (accidentes, cortes de calle, congestión, incendios), integrando geolocalización y bases de datos en la nube con validación comunitaria.

---

## ✨ Características Principales

* 🗺️ **Visualización de Rutas Locales:** Trazados interactivos en Google Maps para las líneas urbanas principales (Líneas 3, 4, 5, 7 y Línea U).
* 💵 **Información de Tarifas:** Consulta clara de valores según la jornada (Diurna, Tarde, Noche).
* 🚨 **Alertas Automáticas en Ruta:** Sistema inteligente que detecta si existen reportes activos sobre las calles que componen la ruta seleccionada y notifica al usuario mediante alertas visuales.
* 📍 **Reporte Ciudadano en Tiempo Real:** Creación de incidentes con ubicación geográfica, tipo de evento, hora y calle afectada.
* 👥 **Gestión Personalizada ("Mis Reportes"):** Historial de reportes creados por el usuario con filtrado dinámico de estado activo e integración con Firebase.
* 🎨 **Interfaz Personalizada:** Marcadores dinámicos según el tipo de incidente y diseño adaptado a la identidad gráfica de la app.

---

## 🛠️ Tecnologías Utilizadas

* **Lenguaje:** Kotlin
* **Plataforma:** Android Nativo (SDK 24+)
* **Arquitectura:** Navigation Component / Multi-Fragment UI
* **Mapas & Ubicación:** Google Maps SDK for Android & Fused Location Provider Services
* **Backend & Base de Datos:** Firebase Firestore (Base de datos NoSQL en tiempo real)
* **Autenticación:** Firebase Authentication

---

## 📂 Estructura del Proyecto

```text
com.example.coletomaps/
│
├── ui/
│   ├── MapFragment/         # Vista principal interactiva de Google Maps
│   ├── data/               # Modelos de datos y gestión de Firebase (FirebaseManager)
│   ├── gallery/            # Gestión de galería e imágenes
│   ├── home/               # Pantalla principal / Dashboard
│   ├── login/              # Flujo de autenticación e inicio de sesión
│   ├── reportar/           # Formulario y envío de incidentes viales
│   ├── slideshow/          # Logica pantalla de Mis Reportes
│   └── MainActivity.kt     # Contenedor principal de navegación
│
├── assets/                 # Recursos estáticos
└── res/                    # Layouts, drawables, íconos y temas visuales
