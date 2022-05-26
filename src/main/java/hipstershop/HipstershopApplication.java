package hipstershop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class HipstershopApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(HipstershopApplication.class, args);
    }
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(HipstershopApplication.class);
    }

    @Bean
    public GrpcCommandLineRunner schedulerRunner() {
        return new GrpcCommandLineRunner();
    }
}
