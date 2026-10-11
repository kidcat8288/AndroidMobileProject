package com.moneymate.app.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParserTest {
    @Test fun normalReceiptSuggestsMerchantAndTotal() {
        val draft = parseReceipt("CORNER MARKET\n123 Main Street\nApples 4.00\nTOTAL $4.00")
        assertEquals("CORNER MARKET", draft.merchant)
        assertEquals(4.0, draft.total!!, 0.001)
    }

    @Test fun excludesSubtotalTaxTenderAndChange() {
        val draft = parseReceipt("Cafe\nSUBTOTAL 20.00\nTOTAL TAX 2.00\nTOTAL 22.00\nCASH 50.00\nCHANGE 28.00")
        assertEquals(22.0, draft.total!!, 0.001)
    }

    @Test fun noTotalDoesNotPickLargestAmount() {
        assertNull(parseReceipt("Cafe\nSUB TOTAL 10.00\nTAX 1.00\nCASH 20.00\nCHANGE 9.00").total)
    }

    @Test fun supportsCommaSeparatedDollarAmounts() {
        assertEquals(1234.56, parseReceipt("Shop\nGrand Total: USD $1,234.56").total!!, 0.001)
    }

    @Test fun rejectsAmountOnFollowingLine() {
        listOf("TOTAL", "Grand Total:", "Total Due", "Amount Due").forEach { label ->
            assertNull(parseReceipt("Bakery\n$label\n$17.25").total)
        }
    }

    @Test fun requiresTotalInsteadOfOtherPaymentLabels() {
        assertNull(parseReceipt("Bakery\nAmount Due $17.25\nBalance Due $17.25").total)
    }

    @Test fun totalIsCaseInsensitiveAndAcceptsSameRowSpacing() {
        assertEquals(17.25, parseReceipt("Bakery\ntOtAl:\t  $17.25").total!!, 0.001)
    }

    @Test fun ignoresOtherPaymentLabelsWhenTotalIsPresent() {
        assertEquals(10.0, parseReceipt("Shop\nTOTAL 10.00\nAMOUNT DUE 12.00").total!!, 0.001)
    }

    @Test fun skipsSplitTotalButAcceptsAnotherCompleteTotalRow() {
        assertEquals(15.0, parseReceipt("Shop\nTOTAL\n12.00\nGRAND TOTAL 15.00").total!!, 0.001)
    }

    @Test fun supportsWholeDollarTotal() {
        assertEquals(12.0, parseReceipt("Bakery\nTOTAL $12").total!!, 0.001)
    }

    @Test fun missingMerchantStaysBlank() {
        assertEquals("", parseReceipt("RECEIPT\n123 Main Street\n10/03/2026\nTOTAL 10.00").merchant)
    }

    @Test fun conflictingTotalsStayBlank() {
        assertNull(parseReceipt("Shop\nTOTAL 10.00\nGRAND TOTAL 12.00").total)
    }

    @Test fun repeatedSameTotalIsNotAmbiguous() {
        assertEquals(10.0, parseReceipt("Shop\nTOTAL 10.00\nGrand Total $10.00").total!!, 0.001)
    }

    @Test fun blankTextProducesEmptyDraft() {
        val draft = parseReceipt("")
        assertEquals("", draft.merchant)
        assertEquals("", draft.recognizedText)
        assertNull(draft.total)
    }

    @Test fun retainsRecognizedTextForReview() {
        val text = "My Store\nTotal 1.50\nThank you!"
        assertEquals(text, parseReceipt(text).recognizedText)
    }

    @Test fun rejectsMalformedAndNonPositiveTotals() {
        listOf("0.00", "-10.00", "12,34.56", "10.0", "Infinity", "NaN", "10.00 12.00").forEach {
            assertNull("Unexpected total for $it", parseReceipt("Shop\nTOTAL $it").total)
        }
    }

    @Test fun rejectsTotalItemsCountAndFollowingTax() {
        assertNull(parseReceipt("Shop\nTOTAL ITEMS 7\nTOTAL\nTAX 1.00").total)
    }

    @Test fun skipsReceiptHeaderAndItemPricesWhenSuggestingMerchant() {
        assertEquals("7-Eleven", parseReceipt("WELCOME\n7-Eleven\nCoffee 3.00\nTOTAL 3.00").merchant)
    }
}
