package com.antiam.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogControllerTest {

    @Test
    void exposesPublicCatalog() {
        Map<String, Object> catalog = new CatalogController().catalog();

        assertThat((String[]) catalog.get("modules")).contains("organization-directory", "application-sso");
        assertThat((Object[]) catalog.get("applicationProtocols")).isNotEmpty();
    }
}
