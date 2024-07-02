package ch.martinelli.vj;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestVjConfiguration.class)
@SpringBootTest
class VjApplicationTests {

	@Test
	void contextLoads() {
	}

}