package com.schoolbus.cdcsync;

import com.schoolbus.cdcsync.config.CacheProjectionProperties;
import com.schoolbus.cdcsync.config.CanalConnectionProperties;
import com.schoolbus.cdcsync.config.CdcMessagingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        CanalConnectionProperties.class,
        CdcMessagingProperties.class,
        CacheProjectionProperties.class
})
public class CdcCacheSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(CdcCacheSyncApplication.class, args);
    }
}
