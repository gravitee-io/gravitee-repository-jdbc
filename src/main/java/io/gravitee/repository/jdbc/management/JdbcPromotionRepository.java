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

import io.gravitee.common.data.domain.Page;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.jdbc.orm.JdbcObjectMapper;
import io.gravitee.repository.management.api.PromotionRepository;
import io.gravitee.repository.management.api.search.Order;
import io.gravitee.repository.management.api.search.Pageable;
import io.gravitee.repository.management.api.search.PromotionCriteria;
import io.gravitee.repository.management.api.search.Sortable;
import io.gravitee.repository.management.model.Promotion;
import io.gravitee.repository.management.model.PromotionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.Date;
import java.util.List;

import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.isEmpty;

@Repository
public class JdbcPromotionRepository extends JdbcAbstractCrudRepository<Promotion, String> implements PromotionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcPromotionRepository.class);

    JdbcPromotionRepository(@Value("${management.jdbc.prefix:}") String tablePrefix) {
        super(tablePrefix, "promotions");
    }

    @Override
    protected JdbcObjectMapper<Promotion> buildOrm() {
        return JdbcObjectMapper.builder(Promotion.class, this.tableName, "id")
            .addColumn("id", Types.NVARCHAR, String.class)
            .addColumn("api_definition", Types.NCLOB, String.class)
            .addColumn("status", Types.NVARCHAR, PromotionStatus.class)
            .addColumn("target_environment_id", Types.NVARCHAR, String.class)
            .addColumn("target_installation_id", Types.NVARCHAR, String.class)
            .addColumn("source_environment_id", Types.NVARCHAR, String.class)
            .addColumn("source_installation_id", Types.NVARCHAR, String.class)
            .addColumn("created_at", Types.TIMESTAMP, Date.class)
            .addColumn("updated_at", Types.TIMESTAMP, Date.class)
            .build();
    }

    @Override
    protected String getId(Promotion item) {
        return item.getId();
    }

    @Override
    public Page<Promotion> search(PromotionCriteria criteria, Sortable sortable, Pageable pageable) throws TechnicalException {

        LOGGER.debug("JdbcPromotionRepository.search() - {}", getOrm().getTableName());

        try {
            List<Promotion> result;
            final StringBuilder query = new StringBuilder(getOrm().getSelectAllSql());

            if (criteria == null) {
                applySortable(sortable, query);
                result = jdbcTemplate.query(query.toString(), getRowMapper());
            } else {
                query.append(" where 1=1 ");

                if (!isEmpty(criteria.getTargetEnvironmentIds())) {
                    query.append(" and target_environment_id in ( ")
                         .append(getOrm().buildInClause(criteria.getTargetEnvironmentIds()))
                         .append(" ) ");
                }

                if (!isEmpty(criteria.getStatus())) {
                    query.append(" and status = ?");
                }

                applySortable(sortable, query);

                result = jdbcTemplate.query(query.toString(),
                        (PreparedStatement ps) -> {
                            int idx = 1;
                            if (!isEmpty(criteria.getTargetEnvironmentIds())) {
                                idx = getOrm().setArguments(ps, criteria.getTargetEnvironmentIds(), idx);
                            }

                            if (!isEmpty(criteria.getStatus())) {
                                ps.setString(idx, criteria.getStatus().name());
                            }
                        },
                        getOrm().getRowMapper()
                );
            }

            return getResultAsPage(pageable, result);

        } catch (Exception e) {
            LOGGER.error("Failed to search {} items:", getOrm().getTableName(), e);
            throw new TechnicalException("Failed to search " + getOrm().getTableName() + " items", e);
        }
    }

    void applySortable(Sortable sortable, StringBuilder query) {
        if (sortable != null && sortable.field() != null && sortable.field().length() > 0) {
            query.append(" order by ");
            if ("created_at".equals(sortable.field())) {
                query.append(sortable.field());
            } else {
                query.append(" lower(");
                query.append(sortable.field());
                query.append(") ");
            }

            query.append(sortable.order().equals(Order.ASC) ? " asc " : " desc ");
        }
    }
}