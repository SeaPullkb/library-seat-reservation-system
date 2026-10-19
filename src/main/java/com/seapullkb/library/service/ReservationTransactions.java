package com.seapullkb.library.service;

import java.sql.SQLException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Retry the whole rolled-back operation, never a statement inside a failed transaction. */
@Component
public class ReservationTransactions {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationTransactions.class);
    private final TransactionTemplate transaction;

    public ReservationTransactions(PlatformTransactionManager manager) {
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(Supplier<T> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.execute(status -> operation.get());
            } catch (PessimisticLockingFailureException ex) {
                // InnoDB's foreign-key checks may take additional parent-row locks
                // when an indexed status changes, beyond the explicit lock order.
                if (attempt >= 3 || !isMysqlDeadlock(ex)) throw ex;
                LOG.info("MySQL deadlock: reservation transaction rolled back; retry {}/2", attempt);
                try { Thread.sleep(20L * attempt); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw ex; }
            }
        }
    }

    private boolean isMysqlDeadlock(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1213) return true;
        }
        return false;
    }
}
