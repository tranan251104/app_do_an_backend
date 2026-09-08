package vn.anpay.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication @EnableScheduling public class AnpayBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnpayBackendApplication.class, args);

    }

}
