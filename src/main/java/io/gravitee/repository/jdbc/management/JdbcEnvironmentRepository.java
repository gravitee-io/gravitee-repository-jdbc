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
package io.gravitee.repository.jdbc.management;

import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.jdbc.orm.JdbcObjectMapper;
import io.gravitee.repository.management.api.EnvironmentRepository;
import io.gravitee.repository.management.model.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.*;

/**
 *
 * @author njt
 */
@Repository
public class JdbcEnvironmentRepository extends JdbcAbstractCrudRepository<Environment, String> implements EnvironmentRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcEnvironmentRepository.class);

    private static final JdbcObjectMapper ORM = JdbcObjectMapper.builder(Environment.class, "environments", "id")
            .addColumn("id", Types.NVARCHAR, String.class)
            .addColumn("name", Types.NVARCHAR, String.class)
            .addColumn("description", Types.NVARCHAR, String.class)
            .addColumn("organization_id", Types.NVARCHAR, String.class)
            .build();

    @Override
    protected JdbcObjectMapper getOrm() {
        return ORM;
    }

    @Override
    protected String getId(Environment item) {
        return item.getId();
    }

    @Override
    public Optional<Environment> findById(String id) throws TechnicalException {
        Optional<Environment> findById = super.findById(id);
        if (findById.isPresent()) {
            final Environment environment = findById.get();
            addDomainRestrictions(environment);
            addHrids(environment);
        }
        return findById;
    }

    @Override
    public Set<Environment> findAll() throws TechnicalException {
        Set<Environment> findAll = super.findAll();
        for(Environment env: findAll) {
            this.addDomainRestrictions(env);
            this.addHrids(env);
        }
        return findAll;
    }

    @Override
    public Environment create(Environment item) throws TechnicalException {
        super.create(item);
        storeDomainRestrictions(item, false);
        storeHrids(item, false);
        return findById(item.getId()).orElse(null);
    }

    @Override
    public Environment update(Environment item) throws TechnicalException {
        super.update(item);
        storeDomainRestrictions(item, true);
        storeHrids(item, true);
        return findById(item.getId()).orElse(null);
    }

    @Override
    public void delete(String id) throws TechnicalException {
        jdbcTemplate.update("delete from environment_domain_restrictions where environment_id = ?", id);
        jdbcTemplate.update("delete from environment_hrids where environment_id = ?", id);
        super.delete(id);
    }

    private void addDomainRestrictions(Environment parent) {
        List<String> domainRestrictions = jdbcTemplate.queryForList("select domain_restriction from environment_domain_restrictions where environment_id = ?", String.class, parent.getId());
        parent.setDomainRestrictions(domainRestrictions);
    }

    private void addHrids(Environment environment) {
        List<String> hrids = jdbcTemplate.queryForList("select hrid from environment_hrids where environment_id = ? order by pos", String.class, environment.getId());
        environment.setHrids(hrids);
    }

    private void storeDomainRestrictions(Environment environment, boolean deleteFirst) {
        if (deleteFirst) {
            jdbcTemplate.update("delete from environment_domain_restrictions where environment_id = ?", environment.getId());
        }
        List<String> filteredDomainRestrictions = ORM.filterStrings(environment.getDomainRestrictions());
        if (!filteredDomainRestrictions.isEmpty()) {
            jdbcTemplate.batchUpdate("insert into environment_domain_restrictions (environment_id, domain_restriction) values ( ?, ? )"
                    , ORM.getBatchStringSetter(environment.getId(), filteredDomainRestrictions));
        }
    }

    private void storeHrids(Environment environment, boolean deleteFirst) {
        if (deleteFirst) {
            jdbcTemplate.update("delete from environment_hrids where environment_id = ?", environment.getId());
        }
        List<String> hrids = ORM.filterStrings(environment.getHrids());
        if (!hrids.isEmpty()) {
            final List<Object[]> params = new ArrayList<>(hrids.size());

            for (int i = 0; i < hrids.size(); i++) {
                params.add(new Object[]{environment.getId(), hrids.get(i), i});
            }

            jdbcTemplate.batchUpdate("insert into environment_hrids (environment_id, hrid, pos) values ( ?, ?, ? )", params);
        }
    }

    @Override
    public Set<Environment> findByOrganization(String organizationId) throws TechnicalException {
        LOGGER.debug("JdbcEnvironmentRepository.findByOrganization({})", organizationId);
        try {
            List<Environment> environments = jdbcTemplate.query("select * from environments where organization_id = ?"
                    , ORM.getRowMapper()
                    , organizationId
            );
            for(Environment env: environments) {
                this.addDomainRestrictions(env);
                this.addHrids(env);
            }
            return new HashSet<>(environments);
        } catch (final Exception ex) {
            LOGGER.error("Failed to find environments by organization:", ex);
            throw new TechnicalException("Failed to find environments by organization", ex);
        }
    }

}