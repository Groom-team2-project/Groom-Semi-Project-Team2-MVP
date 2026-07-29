package org.example.groommvp.global.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED;

@Component
@RequiredArgsConstructor
public class S3TransactionCleanup {

    private final S3imageStorage s3imageStorage;

    public void deleteAfterCommit(String objectKey) {
        requireActiveTransaction();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        s3imageStorage.deleteQuietly(objectKey);
                    }
                }
        );
    }

    public void deleteAfterRollback(String objectKey) {
        requireActiveTransaction();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            s3imageStorage.deleteQuietly(objectKey);
                        }
                    }
                }
        );
    }

    private void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "S3 정리 작업은 활성화된 DB 트랜잭션 안에서 등록해야 합니다."
            );
        }
    }
}
