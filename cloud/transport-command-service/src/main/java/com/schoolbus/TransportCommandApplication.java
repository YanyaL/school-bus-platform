package com.schoolbus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class TransportCommandApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransportCommandApplication.class, args);
    }
}
