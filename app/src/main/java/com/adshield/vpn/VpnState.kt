package com.adshield.vpn

import com.adshield.domain.model.ProtectionState

/** Estado real de la protección, publicado por el servicio VPN. */
data class VpnState(
    val protection: ProtectionState = ProtectionState.DISABLED,
    val error: String? = null,
    val since: Long? = null
)
