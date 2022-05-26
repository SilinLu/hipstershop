package hipstershop;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.CommandLineRunner;

import java.io.IOException;


public class GrpcCommandLineRunner implements CommandLineRunner {
    private static final Logger logger = LogManager.getLogger(GrpcCommandLineRunner.class);

    @Override
    public void run(String... args) throws Exception {
        try {
            CurrencyService currencyService=CurrencyService.getInstance();
            currencyService.startService();
        }catch (Exception e){
            logger.info(e+" cause CurrencyService Shutdown.");
        }


    }
}
