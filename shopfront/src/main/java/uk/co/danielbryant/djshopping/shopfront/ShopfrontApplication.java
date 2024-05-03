package uk.co.danielbryant.djshopping.shopfront;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;


@SpringBootApplication
@EnableHystrix
public class ShopfrontApplication {

    @Value("${server.port}")
    private int serverPort;

    @Value("${productCatalogueUri}")
    private String productCatalogueUri;

    @Value("${stockManagerUri}")
    private String stockManagerUri;

    public static void main(String[] args) {
        SpringApplication.run(ShopfrontApplication.class, args);
    }

    @Bean(name = "stdRestTemplate")
    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationStartup() {
        System.out.println("Application started with the following properties:");
        System.out.println("Server Port: " + serverPort);
        System.out.println("Product Catalogue URI: " + productCatalogueUri);
        System.out.println("Stock Manager URI: " + stockManagerUri);
    }
}
