package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KotlinLogging

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal
) {
    private val logger = KotlinLogging.logger {}

    /*
     * Pay all invoices that are pending in the system
     */
   fun payAllInvoices() {
        dal.fetchInvoices()
            .filter { invoice -> invoice.status == InvoiceStatus.PENDING }
            .forEach { invoice -> payInvoice(invoice) }
   }

    private fun payInvoice(invoice: Invoice){
        GlobalScope.launch  {
            try {
                val paid = withContext(Dispatchers.Default) { paymentProvider.charge(invoice) }
                if (paid)
                    dal.updateInvoice(invoice.id, InvoiceStatus.PAID)
            } catch(e: Exception){
                handleException(e, invoice)
            }
        }
    }

    /*
     * Handles exceptions of a known type:
     * - CustomerNotFoundException
     * - CurrencyMismatchException
     * - NetworkException
     *
     * If the code throws exception we don't know how to handle it, and will rethrow it
     */
    private fun handleException(e: Exception, invoice: Invoice) {
        when(e) {
            is CustomerNotFoundException -> {
                logger.error { "Payment provider was not able to identify customer with id ${invoice.customerId} " +
                        "on invoice id ${invoice.id}" }
                dal.updateInvoice(invoice.id, InvoiceStatus.ERROR)
            }
            is CurrencyMismatchException -> {
                logger.error { "There was a mismatch in current on invoice id ${invoice.id}" }
                dal.updateInvoice(invoice.id, InvoiceStatus.ERROR)
            }
            is NetworkException -> {
                //TODO handle error
            }
            else -> throw e
        }
    }
}