package com.schoolbus.iamservice.application.account;

import com.schoolbus.iamservice.domain.account.StudentNumber;
import com.schoolbus.iamservice.api.BusinessException;
import com.schoolbus.iamservice.api.ErrorCode;

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
