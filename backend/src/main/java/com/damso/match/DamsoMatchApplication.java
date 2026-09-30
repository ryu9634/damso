package com.damso.match;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 좋아요 만료처럼 시간에 맞춰 도는 일이 있어 스케줄러를 켠다. */
@EnableScheduling
@SpringBootApplication
public class DamsoMatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(DamsoMatchApplication.class, args);
    }
}
