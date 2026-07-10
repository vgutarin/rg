package vg.template;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vg.test.containers.starters.Mysql8ContainerStarter;

@SpringBootTest(classes = TemplateApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles({"test"})
public abstract class BaseIntegrationTest implements Mysql8ContainerStarter {

}
