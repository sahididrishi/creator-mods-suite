package dev.riftal.creator.features.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParseException;
import dev.riftal.creator.features.rules.shop.ShopOffer;
import dev.riftal.creator.features.rules.shop.ShopOffers;
import net.minecraft.util.GsonHelper;
import org.junit.jupiter.api.Test;

/** The one piece of the hearts shop that must never be wrong: what a player can afford. */
class ShopOffersTest {

    @Test
    void aPurchaseMayNeverLeaveLessThanOneHeart() {
        // 20 max health, 3 hearts = 6 health -> 14 left, fine.
        assertTrue(ShopOffers.canAfford(20.0D, 3));
        // 8 max health, 3 hearts = 6 health -> 2 left, exactly one heart, still allowed.
        assertTrue(ShopOffers.canAfford(8.0D, 3));
        // 7 max health, 3 hearts -> 1 left, refused.
        assertFalse(ShopOffers.canAfford(7.0D, 3));
        // One heart left and anything to pay: refused.
        assertFalse(ShopOffers.canAfford(2.0D, 1));
    }

    @Test
    void freeAndNegativePricesAreRefused() {
        assertFalse(ShopOffers.canAfford(20.0D, 0));
        assertFalse(ShopOffers.canAfford(20.0D, -5));
    }

    @Test
    void offersParseFromJson() {
        ShopOffer offer = ShopOffer.fromJson(
                GsonHelper.parse("{\"item\":\"minecraft:netherite_sword\",\"count\":1,\"hearts\":3}"));

        assertEquals("minecraft", offer.itemId().getNamespace());
        assertEquals("netherite_sword", offer.itemId().getPath());
        assertEquals(1, offer.count());
        assertEquals(3, offer.hearts());
        assertTrue(offer.isValid());
    }

    @Test
    void countDefaultsToOne() {
        ShopOffer offer = ShopOffer.fromJson(
                GsonHelper.parse("{\"item\":\"minecraft:diamond\",\"hearts\":1}"));

        assertEquals(1, offer.count());
    }

    @Test
    void offersWithNoPriceOrSillyCountsAreInvalid() {
        assertFalse(ShopOffer.fromJson(
                GsonHelper.parse("{\"item\":\"minecraft:diamond\",\"hearts\":0}")).isValid());
        assertFalse(ShopOffer.fromJson(
                GsonHelper.parse("{\"item\":\"minecraft:diamond\",\"hearts\":1,\"count\":0}")).isValid());
        assertFalse(ShopOffer.fromJson(
                GsonHelper.parse("{\"item\":\"minecraft:diamond\",\"hearts\":1,\"count\":999}")).isValid());
    }

    @Test
    void missingPriceIsRejected() {
        assertThrows(JsonParseException.class,
                () -> ShopOffer.fromJson(GsonHelper.parse("{\"item\":\"minecraft:diamond\"}")));
    }

    @Test
    void theBuiltInCatalogueFitsTheWindowAndIsPriced() {
        assertTrue(ShopOffers.defaults().size() <= ShopOffers.SLOTS);
        assertFalse(ShopOffers.defaults().isEmpty());
        for (ShopOffer offer : ShopOffers.defaults()) {
            assertTrue(offer.isValid(), offer.itemId() + " is not a valid offer");
        }
    }
}
