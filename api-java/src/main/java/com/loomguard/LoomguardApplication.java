package com.loomguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * loomguard — a high-throughput fraud detection API and real-time feature store.
 *
 * <p>Virtual threads (Project Loom) are enabled in {@code application.yml} via
 * {@code spring.threads.virtual.enabled=true}, so every request is served on a
 * virtual thread. Blocking I/O in a handler parks the virtual thread instead of
 * pinning an OS thread, which is what lets a single instance carry tens of
 * thousands of concurrent in-flight requests.
 */
@SpringBootApplication
@EnableScheduling
public class LoomguardApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoomguardApplication.class, args);
    }
}
