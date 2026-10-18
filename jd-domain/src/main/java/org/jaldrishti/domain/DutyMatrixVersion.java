package org.jaldrishti.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Version record for the duty matrix implementing four-eyes review.
 * Only an APPROVED version can be used for dispatches.
 */
public record DutyMatrixVersion(
        int version,
        MatrixStatus status,
        String createdBy,
        String approvedBy,
        Instant approvedAt
) {
    public DutyMatrixVersion {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
    }

    public boolean isApproved() {
        return status == MatrixStatus.APPROVED;
    }
}
