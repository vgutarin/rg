package vg.template;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vg.test.containers.starters.Mysql8ContainerStarter;

@SpringBootTest
@ActiveProfiles({"test", "integration"})
@SpringBootApplication
public class BaseFuncTest implements Mysql8ContainerStarter {

}
