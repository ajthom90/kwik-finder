package com.ajthom90.kwikfinder.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LocalCarWash
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.ui.graphics.vector.ImageVector
import com.ajthom90.kwikfinder.data.StoreFeature

/** Material icons for store features (closest match to iOS SF Symbols). */
fun StoreFeature.icon(): ImageVector = when (this) {
    StoreFeature.FamilyRestroom -> Icons.Filled.FamilyRestroom
    StoreFeature.EvCharging -> Icons.Filled.EvStation
    StoreFeature.Open24Hours -> Icons.Filled.Schedule
    StoreFeature.Diesel -> Icons.Filled.LocalGasStation
    StoreFeature.PremiumDiesel -> Icons.Filled.LocalGasStation
    StoreFeature.Def -> Icons.Filled.Opacity
    StoreFeature.E85 -> Icons.Outlined.Eco
    StoreFeature.Cng -> Icons.Filled.Air
    StoreFeature.NoEthanolGas -> Icons.Filled.Opacity
    StoreFeature.Unleaded88 -> Icons.Filled.LocalGasStation
    StoreFeature.Scale -> Icons.Outlined.Scale
    StoreFeature.Showers -> Icons.Filled.Shower
    StoreFeature.TruckParking -> Icons.Filled.LocalShipping
    StoreFeature.TransFlo -> Icons.Outlined.Description
    StoreFeature.FleetCards -> Icons.Filled.CreditCard
    StoreFeature.TruckFriendly -> Icons.Filled.LocalShipping
    StoreFeature.CarWash -> Icons.Filled.LocalCarWash
    StoreFeature.Atm -> Icons.Filled.AttachMoney
    StoreFeature.BitcoinATM -> Icons.Filled.CurrencyBitcoin
    StoreFeature.Wifi -> Icons.Filled.Wifi
    StoreFeature.Restaurant -> Icons.Filled.Restaurant
}
