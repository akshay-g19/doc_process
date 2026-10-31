package com.java.doc_process;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.cloud.aws.sqs.enabled=false")
@ActiveProfiles("floci")
class DocProcessApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertThat(applicationContext).isNotNull();
		assertThat(applicationContext.getEnvironment().getProperty("aws.sqs.queue-url"))
				.isEqualTo("http://localhost:4566/000000000000/doc-process-queue");
	}

}
