package vg.rg.security;

import org.junit.jupiter.api.Test;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.TelegramInitDataRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SecureAuthorizationContractArchitectureTest {

    private static final Path ROOT = repositoryRoot();
    private static final Path LOGIC_MAIN = ROOT.resolve("rg-logic/src/main/java");

    @Test
    void businessCode_dependsOnFacadeContractNotAdapterImplementations() throws IOException {
        var businessSource = textUnder(LOGIC_MAIN.resolve("vg/rg/service"));

        assertThat(businessSource).doesNotContain(
                "security.dev", "security.identity", "vg.identity", "IdentitySecureAuthorizationFacade",
                "DevSecureAuthorizationFacade");
    }

    @Test
    void logicModule_containsNoIdentityTransportOrFrontendTypes() throws IOException {
        var productionSource = textUnder(LOGIC_MAIN);
        var build = Files.readString(ROOT.resolve("rg-logic/build.gradle"));

        assertThat(productionSource).doesNotContain(
                "vg.identity.rest", "org.springframework.web.bind.annotation",
                "org.springframework.web.service.annotation", "io.swagger", "com.vaadin");
        assertThat(build).doesNotContain("identity-rest-client", "vaadin");
    }

    @Test
    void contractModels_haveNoRuntimeVersionField() {
        assertThat(AuthenticatedUserPrincipal.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("contractVersion");
        assertThat(TelegramInitDataRequest.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("contractVersion");
    }

    @Test
    void productionLogging_doesNotPassSensitiveValues() throws IOException {
        var source = textUnder(LOGIC_MAIN);
        var sensitiveLog = Pattern.compile(
                "log\\.(?:trace|debug|info|warn|error)\\([^;]*(?:initData|\\.sub\\(|\\.name\\(|\\.permissions\\(|principal)",
                Pattern.DOTALL);

        assertThat(sensitiveLog.matcher(source).find()).isFalse();
    }

    @Test
    void identityDependencies_useOneExactPublishedVersionProperty() throws IOException {
        var properties = Files.readString(ROOT.resolve("gradle.properties"));
        var logicBuild = Files.readString(ROOT.resolve("rg-logic/build.gradle"));
        var frontendBuild = Files.readString(ROOT.resolve("rg-frontend-vaadin/build.gradle"));

        assertThat(properties).containsPattern("(?m)^vg_identity_version=\\d+\\.\\d+\\.\\d+$");
        assertThat(properties).doesNotContain("vg_identity_version=+", "vg_identity_version=latest",
                "vg_identity_version=0.0.2-SNAPSHOT");
        assertThat(logicBuild).contains("identity-api:${vg_identity_version}");
        assertThat(frontendBuild).contains("identity-rest-client:${vg_identity_version}");
    }

    private static String textUnder(Path root) throws IOException {
        var result = new StringBuilder();
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            result.append(Files.readString(path)).append('\n');
                        } catch (IOException exception) {
                            throw new IllegalStateException("Cannot inspect authored source", exception);
                        }
                    });
        }
        return result.toString();
    }

    private static Path repositoryRoot() {
        var candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Cannot locate repository root");
        }
        return candidate;
    }
}
