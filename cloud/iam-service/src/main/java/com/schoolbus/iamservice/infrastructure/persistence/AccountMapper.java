package com.schoolbus.iamservice.infrastructure.persistence;

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

    int insertRoleIfAbsent(
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

    AccountDataObject selectByUserId(
        @Param("userId")
        Long userId
    );

    List<String> selectRoleCodesByAccountId(
        @Param("accountId")
        Long accountId
    );
}
