package com.alecu.balancetycoon

/**
 * Punto de enganche de la monetización, igual que el objeto Monetization de HeadBallerz.
 *
 * En la versión 1.0 se implementará con Google AdMob (anuncios recompensados) y el SDK de
 * consentimiento UMP, obligatorio en la UE por el RGPD. La compra Premium irá con Google Play
 * Billing y llamará a la misma recompensa de forma permanente.
 */
interface AdsProvider {
    fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit)
}

/** Prototipo: concede la recompensa al momento, sin mostrar ningún anuncio. */
class SimulatedAdsProvider : AdsProvider {
    override fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit) = onReward()
}
