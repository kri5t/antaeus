package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BillingServiceTest {
    private val invoice = Invoice(1, 1, Money(10.toBigDecimal(), Currency.EUR), InvoiceStatus.PENDING)

    private val dal = mockk<AntaeusDal> {
        every { fetchInvoices() } returns listOf(invoice)
    }

    private val paymentProvider = mockk<PaymentProvider> {
        every { charge(invoice) } returns true
    }

    private val sut = BillingService(dal = dal, paymentProvider = paymentProvider)

    @Test
    fun `will change invoice to paid`() {
        sut.payAllInvoices()
        Assertions.assertEquals(InvoiceStatus.PAID, invoice.status)
    }
}