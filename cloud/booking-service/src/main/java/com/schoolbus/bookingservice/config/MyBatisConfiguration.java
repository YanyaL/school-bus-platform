package com.schoolbus.bookingservice.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(DataSource.class)
@MapperScan({
        "com.schoolbus.bookingservice.infrastructure.persistence",
        "com.schoolbus.bookingservice.infrastructure.outbox",
        "com.schoolbus.bookingservice.support.payment.infrastructure.outbox"
})
public class MyBatisConfiguration {
}
