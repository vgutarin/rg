package vg.rg.frontend.vaadin;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css")
@ColorScheme(ColorScheme.Value.LIGHT_DARK)
@SpringBootApplication(scanBasePackages = {"vg.rg", "vg.unique.id"})
//@EnableJpaAuditing
public class FrontendApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(FrontendApplication.class, args);
    }

}
