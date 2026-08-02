package com.schoolbus.iam.infrastructure.persistence.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AccountMapper {

    int countByStudentNumber(
        @Param("studentNumber")
        String studentNumber
    );

    int insertAccount(
        AccountDataObject account
    );

    int insertRole(
        @Param("accountId")
        Long accountId,
        @Param("roleCode")
        String roleCode,
        @Param("createdAt")
        LocalDateTime createdAt
    );

    AccountDataObject selectByStudentNumber(
        @Param("studentNumber")
        String studentNumber
    );

    List<String> selectRoleCodesByAccountId(
        @Param("accountId")
        Long accountId
    );
}
