package vg.template.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vg.template.BaseIntegrationTest;
import vg.template.model.TemplateModel;
import vg.template.rest.v1.TemplateServiceApiRestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

public class TemplateRestClientIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private TemplateServiceApiRestClient restClient;

    @Autowired
    private Clock clock;

    private String name;
    private String description;

    private Instant creationTime;

    @BeforeEach
    void setUp() {
        name = nextString();
        description = nextString();
        creationTime = clock.instant();
    }

    @Test
    void create() {
        var savedModel = restClient.create(buildModel());

        assertThat(
                savedModel.getName()
        ).isEqualTo(
                name
        );

        assertThat(
                savedModel.getDescription()
        ).isEqualTo(
                description
        );

        assertThat(
                savedModel.getCreatedAt()
        ).isCloseTo(
            creationTime,
            within(2, ChronoUnit.SECONDS)
        );

        assertThat(
                savedModel.getUniqueId()
        ).isNotNull();


        assertThat(
                savedModel.getVersion()
        ).isEqualTo(
                0
        );

        assertThat(restClient.getAll()).contains(savedModel);
    }

    @Test
    void update() {
        var savedModel = restClient.create(buildModel());

        var savedModelId = savedModel.getUniqueId();
        var newName = nextString();
        var newDescription = nextString();
        var newCreationTime = creationTime.plusSeconds(10 + nextLong());

        savedModel.setName(newName);
        savedModel.setDescription(newDescription);
        savedModel.setCreatedAt(newCreationTime);

        var updatedModel = restClient.update(savedModel);

        assertThat(
                updatedModel.getName()
        ).isEqualTo(
                newName
        );

        assertThat(
                updatedModel.getDescription()
        ).isEqualTo(
                newDescription
        );

        assertThat(
                updatedModel.getCreatedAt()
        ).isEqualTo(
                newCreationTime
        );

        assertThat(
                updatedModel.getUniqueId()
        ).isEqualTo(savedModelId);

        assertThat(
                updatedModel.getVersion()
        ).isEqualTo(
                1
        );
    }

    private TemplateModel buildModel() {
        return TemplateModel.builder()
                .uniqueId(null)
                .name(name)
                .description(description)
                .createdAt(creationTime)
                .build();
    }
}
