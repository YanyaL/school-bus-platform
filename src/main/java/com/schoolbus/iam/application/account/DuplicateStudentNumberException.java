package com.schoolbus.iam.application.account;

import com.schoolbus.iam.domain.account.StudentNumber;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class DuplicateStudentNumberException
        extends BusinessException {

    public DuplicateStudentNumberException(
            StudentNumber studentNumber
    ) {
        super(
                ErrorCode.DUPLICATE_STUDENT_NUMBER,
                "student number already exists: "
                        + studentNumber.value()
        );
    }
}
