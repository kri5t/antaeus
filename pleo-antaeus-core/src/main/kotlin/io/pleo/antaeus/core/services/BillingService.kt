package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.lang.Runnable

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal,
    private val timeout: Long = 1000
) : Runnable {
    override fun run() {
        try {
            payAllInvoices()
        } catch(e: Exception){
            /*
             * Right now I just eat all the exceptions to keep the scheduled job running
             *
             * We should build an exception sink and shoot messages to some sort of logging
             * to be able to debug when an unhandled exception is bubbled out here.
             */
            logger.error(e) { "Eating all the exceptions to keep the cron job running" }
        }
    }

    private val logger = KotlinLogging.logger {}

    /*
     * Pay all invoices that are pending in the system
     *
     * We run the payments in parallel because we are contacting an external provider
     * and might need delays on payments when waiting for the request to happen.
     *
     * If we had a huge number of items being payed we might thing about throttling how many
     * invoice payments we are doing at the same time
     */
   fun payAllInvoices() {
        dal.fetchInvoices()
                .filter { invoice -> invoice.status == InvoiceStatus.PENDING }
                .forEachParallel { invoice -> payInvoice(invoice) }
        logger.info { "Done charging all the pending invoices" }
   }

    /*
     * Reset the invoices
     *
     * This is only used as a part of testing the code.
     */
   fun resetInvoices() {
        dal.fetchInvoices()
            .forEach { invoice -> dal.updateInvoice(invoice.id, InvoiceStatus.PENDING) }
   }

    /*
     * Pay a specific invoice
     *
     * attempt - represents how many times this method has been called recursively
     */
    private suspend fun payInvoice(invoice: Invoice, attempt: Int = 0){
        try {
            val paid = withContext(Dispatchers.Default) {
                paymentProvider.charge(invoice)
            }
            if (paid) {
                logger.info { "Payment went through on invoice id: ${invoice.id}" }
                dal.updateInvoice(invoice.id, InvoiceStatus.PAID)
            }
            else {
                logger.info { "Payment was declined by payment provider: ${invoice.id}" }
                dal.updateInvoice(invoice.id, InvoiceStatus.ERROR)
            }
        } catch(e: Exception){
            handleException(e, invoice, attempt)
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
    private suspend fun handleException(e: Exception, invoice: Invoice, attempt: Int) {
        when(e) {
            is CustomerNotFoundException -> {
                logger.error(e) { "Payment provider was not able to identify customer with id ${invoice.customerId} " +
                        "on invoice id ${invoice.id}" }
                dal.updateInvoice(invoice.id, InvoiceStatus.ERROR)
            }
            is CurrencyMismatchException -> {
                logger.error(e) { "There was a mismatch in current on invoice id ${invoice.id}" }
                dal.updateInvoice(invoice.id, InvoiceStatus.ERROR)
            }
            is NetworkException -> {
                //If it is a network exception we will retry 5 times before giving up
                if(attempt < 5){
                    delay((timeout * attempt))
                    payInvoice(invoice, attempt + 1)
                } else {
                    logger.error(e) { "Failed to pay invoice id: ${invoice.id}. After $attempt retries" }
                    dal.updateInvoice(invoice.id, InvoiceStatus.ERROR)
                }
            }
            else -> throw e
        }
    }

    /*
     * Helper method to run the collection of invoices in parallel
     */
    private fun <A>Collection<A>.forEachParallel(f: suspend (A) -> Unit): Unit = runBlocking {
        map { async { f(it) } }.forEach { it.await() }
    }
}