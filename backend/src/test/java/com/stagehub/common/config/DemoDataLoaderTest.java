package com.stagehub.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataLoaderTest {

    @Test
    void actifUniquementEnDevOuDemo_jamaisEnProdSeule() {
        Profile profils = DemoDataLoader.class.getAnnotation(Profile.class);

        assertThat(profils.value()).containsExactlyInAnyOrder("dev", "demo");
    }
}
