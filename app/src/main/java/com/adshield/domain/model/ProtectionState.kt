package com.adshield.domain.model

/** Estado de la protección. Lo publica el servicio VPN a través de VpnManager. */
enum class ProtectionState {
    DISABLED,
    STARTING,
    ACTIVE,
    ERROR
}
