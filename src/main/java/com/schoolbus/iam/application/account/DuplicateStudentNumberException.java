package com.schoolbus.iam.application.account;

import com.schoolbus.iam.domain.account.StudentNumber;

public final class DuplicateStudentNumberException
        extends RuntimeException {

    public DuplicateStudentNumberException(
            StudentNumber studentNumber
    ) {
        super(
                "student number already exists: "
                        + studentNumber.value()
        );
    }
}
