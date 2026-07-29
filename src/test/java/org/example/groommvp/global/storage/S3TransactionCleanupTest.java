package org.example.groommvp.global.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3TransactionCleanupTest {

    @Mock
    private S3imageStorage s3imageStorage;

    private S3TransactionCleanup cleanup;

    @BeforeEach
    void setUp() {
        cleanup = new S3TransactionCleanup(s3imageStorage);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteAfterCommitDeletesOnlyAfterCommit() {
        cleanup.deleteAfterCommit("old-image.jpg");
        TransactionSynchronization synchronization = registeredSynchronization();

        verify(s3imageStorage, never()).deleteQuietly("old-image.jpg");

        synchronization.afterCommit();

        verify(s3imageStorage).deleteQuietly("old-image.jpg");
    }

    @Test
    void deleteAfterRollbackDeletesOnlyAfterRollback() {
        cleanup.deleteAfterRollback("new-image.jpg");
        TransactionSynchronization synchronization = registeredSynchronization();

        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(s3imageStorage).deleteQuietly("new-image.jpg");
    }

    @Test
    void deleteAfterRollbackKeepsObjectAfterCommit() {
        cleanup.deleteAfterRollback("new-image.jpg");
        TransactionSynchronization synchronization = registeredSynchronization();

        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(s3imageStorage, never()).deleteQuietly("new-image.jpg");
    }

    @Test
    void registrationRequiresActiveTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();

        assertThatThrownBy(() -> cleanup.deleteAfterCommit("image.jpg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("활성화된 DB 트랜잭션");
    }

    private TransactionSynchronization registeredSynchronization() {
        return TransactionSynchronizationManager.getSynchronizations().getFirst();
    }
}
