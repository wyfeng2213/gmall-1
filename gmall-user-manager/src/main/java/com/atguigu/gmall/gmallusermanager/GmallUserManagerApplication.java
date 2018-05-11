package com.atguigu.gmall.gmallusermanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import tk.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@ComponentScan("com.atguigu.gmall")
@MapperScan(basePackages = "com.atguigu.gmall.gmallusermanager.mapper")
public class GmallUserManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GmallUserManagerApplication.class, args);
	}
}