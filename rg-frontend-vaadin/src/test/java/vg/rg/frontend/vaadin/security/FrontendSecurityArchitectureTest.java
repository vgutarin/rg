package vg.rg.frontend.vaadin.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendSecurityArchitectureTest {

    private static final Path ROOT = repositoryRoot();
    private static final Path FRONTEND = ROOT.resolve("rg-frontend-vaadin/src/main");

    @Test
    void sourceCode_initDataInspection_confinesInitDataToRedemptionBoundary() throws IOException {
        try (var files = Files.walk(FRONTEND)) {
            var containingInitData = files
                    .filter(Files::isRegularFile)
                    .filter(FrontendSecurityArchitectureTest::isAuthoredTextFile)
                    .filter(path -> !path.toString().contains("/generated/"))
                    .filter(path -> read(path).contains("initData"))
                    .map(ROOT::relativize)
                    .map(Path::toString)
                    .toList();

            assertThat(containingInitData).containsExactly(
                    "rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/telegram/TelegramAuthView.java");
        }
    }

    @Test
    void sourceCode_clientStateInspection_containsNoBrowserPersistence() throws IOException {
        assertThat(textUnder(FRONTEND)).doesNotContain(
                "localStorage", "sessionStorage", "document.cookie");
    }

    @Test
    void sourceCode_permissionViewInspection_containsNoRoleSemantics() throws IOException {
        assertThat(textUnder(FRONTEND.resolve("java"))).doesNotContain(
                "RolesAllowed", "@RolesAllowed", "ROLE_", "hasRole(", "hasAnyRole(");
    }

    @Test
    void productionUi_dependsOnSharedContractsNotFacadeAdaptersOrIdentityTypes() throws IOException {
        assertThat(textUnder(FRONTEND.resolve("java"))).doesNotContain(
                "vg.rg.security.dev", "vg.rg.security.identity", "vg.identity",
                "IdentitySecureAuthorizationFacade", "DevSecureAuthorizationFacade");
    }

    @Test
    void productionUi_containsNoIdentityRestOrOpenApiTypes() throws IOException {
        assertThat(textUnder(FRONTEND.resolve("java"))).doesNotContain(
                "vg.identity.rest", "org.springframework.web.service.annotation",
                "io.swagger", "org.openapitools");
    }

    @Test
    void applicationProperties_credentialInspection_containsOnlySafePlaceholders() {
        var properties = read(FRONTEND.resolve("resources/application.properties"));
        var activeCredentialLines = properties.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .filter(line -> line.matches(".*(bot-token|api-key)=.*"))
                .toList();

        assertThat(activeCredentialLines).hasSize(2)
                .allSatisfy(line -> assertThat(line).contains("${").endsWith(":}"));
    }

    private static String textUnder(Path... roots) throws IOException {
        var result = new StringBuilder();
        for (var root : roots) {
            if (Files.isRegularFile(root)) {
                result.append(read(root)).append('\n');
                continue;
            }
            try (var files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(FrontendSecurityArchitectureTest::isAuthoredTextFile)
                        .filter(path -> !path.toString().contains("/generated/"))
                        .forEach(path -> result.append(read(path)).append('\n'));
            }
        }
        return result.toString();
    }

    private static boolean isAuthoredTextFile(Path path) {
        var name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return List.of(".java", ".js", ".css", ".properties", ".yaml", ".yml")
                .stream().anyMatch(name::endsWith);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + ROOT.relativize(path), exception);
        }
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
