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

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal
) {
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
            } catch(e: CustomerNotFoundException){
                //TODO handle error
            } catch(e: CurrencyMismatchException){
                //TODO handle error
            } catch(e: NetworkException){
                //TODO handle error
            }
        }
    }
}