package io.pleo.antaeus.app

import mu.KotlinLogging
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.*

/*
 * Object to control setting up the scheduler
 */
class MonthlyScheduler {
    private val logger = KotlinLogging.logger {}
    /*
     * Scheduling is done here for ease of running the project
     * Normally scheduling would be run from a service dedicated to it - instead of taking up resources on our API
     */
    fun schedule(runnable: Runnable) {
        logger.info { "Setting up scheduling" }

        val task = object : TimerTask() {
            override fun run() {
                if(todayIsFirstDayOfMonth())
                    runnable.run()
                else
                    logger.info { "Today is not the first. We are not billing our clients today." }
            }
        }
        //Run at beginning of next day and then consecutive day
        //Timer().schedule(task, 1000, 30000)
        Timer().schedule(task, getMillisecondsUntilStartOfTomorrow(), 24*60*60*1000)
    }

    /*
     * Checks if today is the first of the month. If it is we run the billing.
     */
    private fun todayIsFirstDayOfMonth(): Boolean {
        val now = ZonedDateTime.now()
        val dateNow = now.toLocalDate()
        return dateNow == dateNow.with(TemporalAdjusters.firstDayOfMonth())
    }

    /*
     * Get time until midnight tomorrow.
     *
     * Assuming this is a service that is always running, we do not want to bill our customers again the same day
     * so we wait till tomorrow and check again every 24 hours
     */
    private fun getMillisecondsUntilStartOfTomorrow(): Long{
        val now = ZonedDateTime.now()
        val tomorrow = now.toLocalDate().plusDays(1)
        val tomorrowStart = tomorrow.atStartOfDay(ZoneId.systemDefault())
        val millisToTomorrow = Duration.between( now , tomorrowStart ).toMillis()
        logger.info { "Schedule check will run in ${(millisToTomorrow/1000).toShort()} seconds" }
        return millisToTomorrow
    }
}