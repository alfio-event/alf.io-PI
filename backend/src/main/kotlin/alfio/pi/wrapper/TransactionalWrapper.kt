/*
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */

package alfio.pi.wrapper

import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.AbstractTransactionStatus
import org.springframework.transaction.support.DefaultTransactionDefinition
private val logger = LoggerFactory.getLogger("TransactionalWrapper")

fun <T> doInTransaction(): (PlatformTransactionManager, () -> T, (Exception) -> T) -> T = { transactionManager, operation, exceptionHandler ->
    val transactionDefinition = DefaultTransactionDefinition()
    val transactionStatus = tryOrDefault<TransactionStatus>().invoke({transactionManager.getTransaction(transactionDefinition)}, {
        logger.error("unable to begin a new transaction", it)
        failedTransactionStatus
    })
    if(transactionStatus === failedTransactionStatus) {
        exceptionHandler(CannotBeginTransaction())
    } else {
        tryOrDefault<T>().invoke({
            val result = operation.invoke()
            transactionManager.commit(transactionStatus)
            result
        }, { e ->
            tryOrDefault<T>().invoke({
                transactionManager.rollback(transactionStatus)
                exceptionHandler.invoke(e)
            }, {
                val exc = Exception("Cannot rollback transaction, previous Exception: $e", it)
                exceptionHandler.invoke(exc)
            })
        })
    }
}

fun <T> tryOrDefault(): (() -> T, (Exception) -> T) -> T = { operation, exceptionHandler ->
    try {
        operation.invoke()
    } catch (e: Exception) {
        exceptionHandler.invoke(e)
    }
}

private val failedTransactionStatus = object : AbstractTransactionStatus() {
    override fun isNewTransaction(): Boolean = false
}

class CannotBeginTransaction : RuntimeException("cannot begin a new transaction")