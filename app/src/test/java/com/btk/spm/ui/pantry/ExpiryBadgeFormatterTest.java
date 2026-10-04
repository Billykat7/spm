package com.btk.spm.ui.pantry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.btk.spm.util.ExpiryStatus;
import com.google.android.material.R;

import org.junit.Test;

/**
 * The badge's colour roles, one fill and its matching "on" colour per status, and no badge for an
 * item with no date. The words come from plurals and are checked on a device, where resources exist.
 */
public class ExpiryBadgeFormatterTest {

    @Test
    public void expired_isTheErrorPair() {
        assertEquals(androidx.appcompat.R.attr.colorError, ExpiryBadgeFormatter.backgroundAttr(ExpiryStatus.EXPIRED));
        assertEquals(R.attr.colorOnError, ExpiryBadgeFormatter.textColorAttr(ExpiryStatus.EXPIRED));
    }

    @Test
    public void expiringSoon_isTheTertiaryContainerPair() {
        assertEquals(R.attr.colorTertiaryContainer, ExpiryBadgeFormatter.backgroundAttr(ExpiryStatus.EXPIRING_SOON));
        assertEquals(R.attr.colorOnTertiaryContainer, ExpiryBadgeFormatter.textColorAttr(ExpiryStatus.EXPIRING_SOON));
    }

    @Test
    public void ok_isTheNeutralSurfaceVariantPair() {
        assertEquals(R.attr.colorSurfaceVariant, ExpiryBadgeFormatter.backgroundAttr(ExpiryStatus.OK));
        assertEquals(R.attr.colorOnSurfaceVariant, ExpiryBadgeFormatter.textColorAttr(ExpiryStatus.OK));
    }

    @Test
    public void everyBadgedStatus_hasItsOwnFill() {
        assertNotEquals(ExpiryBadgeFormatter.backgroundAttr(ExpiryStatus.EXPIRED),
                ExpiryBadgeFormatter.backgroundAttr(ExpiryStatus.EXPIRING_SOON));
        assertNotEquals(ExpiryBadgeFormatter.backgroundAttr(ExpiryStatus.EXPIRING_SOON),
                ExpiryBadgeFormatter.backgroundAttr(ExpiryStatus.OK));
    }

    @Test
    public void noDate_hasNoBadge() {
        // Resources are never read for NONE, so null stands in for them on the JVM
        assertNull(ExpiryBadgeFormatter.format(null, ExpiryStatus.NONE, 0));
        assertThrows(IllegalArgumentException.class, () -> ExpiryBadgeFormatter.backgroundAttr(ExpiryStatus.NONE));
    }
}
