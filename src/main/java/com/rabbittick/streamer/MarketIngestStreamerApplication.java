package com.rabbittick.streamer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarketIngestStreamerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketIngestStreamerApplication.class, args);
	}

}
