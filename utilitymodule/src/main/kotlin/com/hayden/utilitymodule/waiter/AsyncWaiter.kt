package com.hayden.utilitymodule.waiter

import lombok.extern.slf4j.Slf4j
import org.bouncycastle.asn1.cmp.PKIStatus.waiting
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

@Slf4j
class AsyncWaiter<T>(val waitFor: () -> T?,
                     val matcher: (T?) -> Boolean,
                     val maxWait: Duration = Duration.ofSeconds(10),
                     val intermittency: Duration = Duration.ofMillis(30)) {

    companion object Builder {

        val log = LoggerFactory.getLogger(AsyncWaiter::class.java)

        fun <T> doCallWaiter(waitFor: () -> T?, matcher: (T?) -> Boolean): T? {
            return AsyncWaiter(waitFor, matcher).doWait()
        }

        fun <T> doCallWaiter(waitFor: () -> T?, matcher: (T?) -> Boolean, wait: Duration, between: Duration): T? {
            return AsyncWaiter(waitFor, matcher, wait, between).doWait()
        }

    }


    fun doWait(): T? {

        val start = Instant.now()
        val end = start.plusNanos(maxWait.toNanos())

        var waitingFor : T? = null

        while (Instant.now().toEpochMilli() - end.toEpochMilli() < 0) {

            waitingFor = waitFor()
            if (this.matcher(waitingFor))
                return waitingFor

            log.info("Waiting for $waiting for $intermittency milliseconds")

            Thread.sleep(intermittency.toMillis())

        }

        return waitingFor

    }

}