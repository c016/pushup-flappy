package com.c016.pushupflappy.game.zone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneShopTest {

    @Test
    fun speedGetsPricierAndSellsOut() {
        val shop = ZoneShop()
        assertEquals(9500, shop.buy(ShopItem.SPEED, 10000))
        assertEquals(1000, shop.price(ShopItem.SPEED))
        shop.buy(ShopItem.SPEED, 10000)
        shop.buy(ShopItem.SPEED, 10000)
        assertTrue(shop.isSoldOut(ShopItem.SPEED))
        assertNull(shop.buy(ShopItem.SPEED, 10000))
    }

    @Test
    fun notEnoughCoins() {
        val shop = ZoneShop()
        assertNull(shop.buy(ShopItem.LASER, 2999))
        assertEquals(0, shop.level(ShopItem.LASER))
    }

    @Test
    fun wideAndLaserReplaceEachOther() {
        val shop = ZoneShop()
        shop.buy(ShopItem.WIDE, 5000)
        shop.buy(ShopItem.LASER, 5000)
        assertEquals(0, shop.level(ShopItem.WIDE))
        assertEquals(1, shop.level(ShopItem.LASER))
    }

    @Test
    fun losingALifeKeepsSpeedButNotWeapons() {
        val shop = ZoneShop()
        shop.buy(ShopItem.SPEED, 5000)
        shop.buy(ShopItem.HEAVY_BOMB, 5000)
        shop.buy(ShopItem.EXTRA_LIFE, 5000)
        shop.loseWeapons()
        assertEquals(1, shop.level(ShopItem.SPEED))
        assertEquals(0, shop.level(ShopItem.HEAVY_BOMB))
        assertEquals(8000, shop.price(ShopItem.EXTRA_LIFE))
    }
}
