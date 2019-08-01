package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class BillingServiceTest {
    private val pendingInvoice = Invoice(1, 1, Money(10.toBigDecimal(), Currency.EUR), InvoiceStatus.PENDING)
    private val paidInvoice = Invoice(2, 2, Money(12.toBigDecimal(), Currency.DKK), InvoiceStatus.PAID)

    private val dal = mockk<AntaeusDal> {
        every { fetchInvoices() } returns listOf(pendingInvoice)
        every { updateInvoice(any(), any()) } returns Unit
    }

    private val paymentProvider = mockk<PaymentProvider> {
        every { charge(any()) } returns true
    }

    private val sut = BillingService(dal = dal, paymentProvider = paymentProvider, timeout = 0)

    @Test
    fun `will call dal to set status to PAID`() {
        runBlocking { sut.payAllInvoices() }
        verify(exactly = 1) {
            paymentProvider.charge(pendingInvoice)
        }
        verify(exactly = 1) {
            dal.updateInvoice(pendingInvoice.id, InvoiceStatus.PAID)
        }
    }

    @Test
    fun `will NOT call dal to set status to PAID, when invoice is already paid`() {
        runBlocking { sut.payAllInvoices() }
        verify(exactly = 0) {
            paymentProvider.charge(paidInvoice)
        }
        verify(exactly = 0) {
            dal.updateInvoice(paidInvoice.id, InvoiceStatus.PAID)
        }
    }

    @Test
    fun `will NOT call dal to set status to PAID, when PaymentProvider returns false`() {
        every { paymentProvider.charge(any()) } returns false

        runBlocking { sut.payAllInvoices() }
        verify(exactly = 1) {
            paymentProvider.charge(pendingInvoice)
        }
        verify(exactly = 0) {
            dal.updateInvoice(pendingInvoice.id, InvoiceStatus.PAID)
        }
    }

    @Test
    fun `will call dal to set status to ERROR, when PaymentProvider returns false`() {
        every { paymentProvider.charge(any()) } returns false

        runBlocking { sut.payAllInvoices() }
        verify(exactly = 1) {
            paymentProvider.charge(pendingInvoice)
        }
        verify(exactly = 1) {
            dal.updateInvoice(pendingInvoice.id, InvoiceStatus.ERROR)
        }
    }

    @Test
    fun `will NOT call dal to set status to PAID, when PaymentProvider throws CustomerNotFoundException`() {
        every { paymentProvider.charge(pendingInvoice) } throws CustomerNotFoundException(pendingInvoice.id)

        runBlocking { sut.payAllInvoices() }
        verify(exactly = 1) {
            paymentProvider.charge(pendingInvoice)
        }
        verify(exactly = 0) {
            dal.updateInvoice(pendingInvoice.id, InvoiceStatus.PAID)
        }
    }

    @Test
    fun `will call dal to set status to ERROR, when PaymentProvider throws CustomerNotFoundException`() {
        every { paymentProvider.charge(pendingInvoice) } throws CustomerNotFoundException(pendingInvoice.id)

        runBlocking { sut.payAllInvoices() }
        verify(exactly = 1) {
            paymentProvider.charge(pendingInvoice)
        }
        verify(exactly = 1) {
            dal.updateInvoice(pendingInvoice.id, InvoiceStatus.ERROR)
        }
    }

    @Test
    fun `will NOT call dal to set status to PAID, when PaymentProvider throws CurrencyMismatchException`() {
        every { paymentProvider.charge(pendingInvoice) } throws CurrencyMismatchException(pendingInvoice.id, pendingInvoice.customerId)

        runBlocking { sut.payAllInvoices() }
        verify(exactly = 1) {
            paymentProvider.charge(pendingInvoice)
        }
        verify(exactly = 0) {
            dal.updateInvoice(pendingInvoice.id, InvoiceStatus.PAID)
        }
    }

    @Test
    fun `will calls dal to set status to ERROR, when PaymentProvider throws CurrencyMismatchException`() {
        every { paymentProvider.charge(pendingInvoice) } throws CurrencyMismatchException(pendingInvoice.id, pendingInvoice.customerId)

        runBlocking { sut.payAllInvoices() }
        verify(exactly = 1) {
            paymentProvider.charge(pendingInvoice)
        }
        verify(exactly = 1) {
            dal.updateInvoice(pendingInvoice.id, InvoiceStatus.ERROR)
        }
    }

    @Test
    fun `will call dal to set status to ERROR, when PaymentProvider throws NetworkException`() {
        every { paymentProvider.charge(pendingInvoice) } throws NetworkException()

        runBlocking { sut.payAllInvoices() }
        //Initial + five retries
        verify(exactly = 6) {
            paymentProvider.charge(pendingInvoice)
        }
        verify(exactly = 1) {
            dal.updateInvoice(pendingInvoice.id, InvoiceStatus.ERROR)
        }
    }

    @Test
    fun `will NOT call dal to set status to PAID, when PaymentProvider throws NetworkException`() {
        every { paymentProvider.charge(pendingInvoice) } throws NetworkException()

        runBlocking { sut.payAllInvoices() }
        //Initial + five retries
        verify(exactly = 6) {
            paymentProvider.charge(pendingInvoice)
        }
        verify(exactly = 0) {
            dal.updateInvoice(pendingInvoice.id, InvoiceStatus.PAID)
        }
    }
}