package com.adshield.presentation.navigation

enum class AppDestination(
    val route: String,
    val title: String,
    val summary: String = "",
    val showInMenu: Boolean = true
) {
    SPLASH("splash", "AD Shield", showInMenu = false),
    DASHBOARD("dashboard", "Inicio", showInMenu = false),
    STATISTICS("statistics", "Estadísticas", "Bloqueos y solicitudes por periodo"),
    APPLICATIONS("applications", "Aplicaciones", "Qué apps pasan por la protección"),
    ACTIVITY("activity", "Actividad", "Dominios permitidos y bloqueados recientemente"),
    BLACKLIST("blacklist", "Lista negra", "Dominios que quieres bloquear siempre"),
    WHITELIST("whitelist", "Lista blanca", "Dominios que nunca se bloquean"),
    PROTECTION("protection", "Protección", "Modo de filtrado y listas de bloqueo"),
    SETTINGS("settings", "Configuración", "Inicio automático, actualizaciones y tema"),
    DIAGNOSTICS("diagnostics", "Diagnóstico", "Comprobar que todo funciona"),
    ABOUT("about", "Acerca de", "Versión, funcionamiento y limitaciones");

    companion object {
        val menuItems: List<AppDestination> = entries.filter { it.showInMenu }
    }
}
