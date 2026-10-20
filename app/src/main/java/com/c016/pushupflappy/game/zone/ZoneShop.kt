package com.c016.pushupflappy.game.zone

enum class ShopItem(val label: String, val basePrice: Int, val maxLevel: Int) {
    SPEED("スピードアップ", 500, 3),
    WIDE("ワイドショット", 1500, 1),
    LASER("レーザー", 3000, 1),
    HEAVY_BOMB("ヘビーボム", 2000, 1),
    EXTRA_LIFE("1UP", 4000, Int.MAX_VALUE),
}

/**
 * What the player owns from the shop and what things cost. Speed and extra lives get pricier with
 * each purchase; WIDE and LASER replace each other.
 */
class ZoneShop {
    private val levels = IntArray(ShopItem.entries.size)

    fun level(item: ShopItem) = levels[item.ordinal]

    fun isSoldOut(item: ShopItem) = level(item) >= item.maxLevel

    fun price(item: ShopItem): Int = when (item) {
        ShopItem.SPEED -> item.basePrice shl level(item)
        ShopItem.EXTRA_LIFE -> item.basePrice * (1 + level(item))
        else -> item.basePrice
    }

    /** Buys [item] with [coins]; returns the coins left, or null if it cannot be bought. */
    fun buy(item: ShopItem, coins: Int): Int? {
        if (isSoldOut(item)) return null
        val cost = price(item)
        if (cost > coins) return null
        levels[item.ordinal]++
        when (item) {
            ShopItem.WIDE -> levels[ShopItem.LASER.ordinal] = 0
            ShopItem.LASER -> levels[ShopItem.WIDE.ordinal] = 0
            else -> Unit
        }
        return coins - cost
    }

    /** Losing a life loses the weapons, but speed and the record of 1UP purchases stay. */
    fun loseWeapons() {
        for (item in listOf(ShopItem.WIDE, ShopItem.LASER, ShopItem.HEAVY_BOMB)) levels[item.ordinal] = 0
    }

    fun reset() = levels.fill(0)
}
