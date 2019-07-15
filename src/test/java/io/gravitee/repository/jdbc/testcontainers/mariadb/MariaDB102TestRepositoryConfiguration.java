/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.repository.jdbc.testcontainers.mariadb;

import io.gravitee.repository.jdbc.AbstractJdbcTestRepositoryConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.testcontainers.containers.MariaDBContainer;

import javax.inject.Inject;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@Conditional(MariaDB102Condition.class)
public class MariaDB102TestRepositoryConfiguration extends AbstractJdbcTestRepositoryConfiguration {

    @Inject
    private MariaDBContainer embeddedMariaDB;

    @Override
    protected String getJdbcUrl() {
        return getJdbcUrl(embeddedMariaDB);
    }

    @Bean(destroyMethod = "stop")
    public MariaDBContainer embeddedMariaDB() {
        System.out.println("Start Mariadb 10.2");
        MariaDBContainer container = new MariaDBContainer<>("mariadb:10.2");
        container.start();
        return container;
    }
}