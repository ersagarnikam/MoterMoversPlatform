package uk.co.motormovers.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MotorMoversApplication {

    public static void main(String[] args) {
        SpringApplication.run(MotorMoversApplication.class, args);
    }
}
