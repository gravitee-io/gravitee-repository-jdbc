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
import io.gravitee.repository.jdbc.orm.JdbcColumn;
import io.gravitee.repository.jdbc.orm.JdbcObjectMapper;
import io.gravitee.repository.management.api.PageRepository;
import io.gravitee.repository.management.model.Page;
import io.gravitee.repository.management.model.PageConfiguration;
import io.gravitee.repository.management.model.PageSource;
import io.gravitee.repository.management.model.PageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static io.gravitee.repository.jdbc.common.AbstractJdbcRepositoryConfiguration.escapeReservedWord;
import static io.gravitee.repository.jdbc.orm.JdbcColumn.getDBName;
import static java.lang.String.format;

/**
 *
 * @author njt
 */
@Repository
public class JdbcPageRepository extends JdbcAbstractCrudRepository<Page, String> implements PageRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcPageRepository.class);

    private static final String ESCAPED_ORDER_COLUMN_NAME = escapeReservedWord("order");

    private static final JdbcObjectMapper ORM = JdbcObjectMapper.builder(Page.class, "pages", "id")
            .addColumn("id", Types.NVARCHAR, String.class)
            .addColumn("type", Types.NVARCHAR, PageType.class)
            .addColumn("name", Types.NVARCHAR, String.class)
            .addColumn("content", Types.NVARCHAR, String.class)
            .addColumn("last_contributor", Types.NVARCHAR, String.class)
            .addColumn("order", Types.INTEGER, int.class)
            .addColumn("api", Types.NVARCHAR, String.class)
            .addColumn("published", Types.BOOLEAN, boolean.class)
            .addColumn("homepage", Types.BOOLEAN, boolean.class)
            .addColumn("created_at", Types.TIMESTAMP, Date.class)
            .addColumn("updated_at", Types.TIMESTAMP, Date.class)
            .build();

    private class Rm implements RowMapper<Page> {
        @Override
        public Page mapRow(ResultSet rs, int i) throws SQLException {
            Page page = new Page();
            ORM.setFromResultSet(page, rs);
            String sourceType = rs.getString("source_type");
            String sourceConfiguration = rs.getString("source_configuration");
            if ((sourceType != null) || (sourceConfiguration != null)) {
                PageSource pageSource = new PageSource();
                pageSource.setType(sourceType);
                pageSource.setConfiguration(sourceConfiguration);
                page.setSource(pageSource);
            }
            PageConfiguration pageConfiguration = new PageConfiguration();
            pageConfiguration.setTryIt(rs.getBoolean("configuration_try_it"));
            pageConfiguration.setTryItURL(rs.getString("configuration_try_it_url"));
            page.setConfiguration(pageConfiguration);
            addExcludedGroups(page);
            return page;
        }
    }

    private final Rm mapper = new Rm();

    private static class Psc implements PreparedStatementCreator {

        private final String sql;
        private final Page page;
        private final Object[] ids;

        public Psc(String sql, Page page, Object... ids) {
            this.sql = sql;
            this.page = page;
            this.ids = ids;
        }

        @Override
        public PreparedStatement createPreparedStatement(Connection cnctn) throws SQLException {
            LOGGER.debug("SQL: {}", sql);
            LOGGER.debug("page: {}", page);
            PreparedStatement stmt = cnctn.prepareStatement(sql);
            int idx = ORM.setStatementValues(stmt, page, 1);
            stmt.setString(idx++, page.getSource() == null ? null : page.getSource().getType());
            stmt.setString(idx++, page.getSource() == null ? null : page.getSource().getConfiguration());
            stmt.setBoolean(idx++, page.getConfiguration() == null ? false : page.getConfiguration().isTryIt());
            stmt.setString(idx++, page.getConfiguration() == null ? null : page.getConfiguration().getTryItURL());

            for (Object id : ids) {
                stmt.setObject(idx++, id);
            }
            return stmt;
        }
    }

    private static String buildInsertStatement() {
        final StringBuilder builder = new StringBuilder("insert into pages (");
        boolean first = true;
        for (JdbcColumn column : (List<JdbcColumn>) ORM.getColumns()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(escapeReservedWord(getDBName(column.name)));
        }
        builder.append(", source_type");
        builder.append(", source_configuration");
        builder.append(", configuration_try_it");
        builder.append(", configuration_try_it_url");
        builder.append(" ) values ( ");
        first = true;
        for (int i = 0; i < ORM.getColumns().size(); i++) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append("?");
        }
        builder.append(", ?");
        builder.append(", ?");
        builder.append(", ?");
        builder.append(", ?");
        builder.append(" )");
        return builder.toString();
    }

    private static final String INSERT_SQL = buildInsertStatement();

    private static String buildUpdateStatement() {
        StringBuilder builder = new StringBuilder();
        builder.append("update pages set ");
        boolean first = true;
        for (JdbcColumn column : (List<JdbcColumn>) ORM.getColumns()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(escapeReservedWord(getDBName(column.name)));
            builder.append(" = ?");
        }
        builder.append(", source_type = ?");
        builder.append(", source_configuration = ?");
        builder.append(", configuration_try_it = ?");
        builder.append(", configuration_try_it_url = ?");

        builder.append(" where id = ?");
        return builder.toString();
    }

    private static final String UPDATE_SQL = buildUpdateStatement();

    @Override
    protected JdbcObjectMapper getOrm() {
        return ORM;
    }

    @Override
    protected String getId(Page item) {
        return item.getId();
    }

    @Override
    protected RowMapper<Page> getRowMapper() {
        return mapper;
    }

    @Override
    protected PreparedStatementCreator buildUpdatePreparedStatementCreator(Page page) {
        return new Psc(UPDATE_SQL, page, page.getId());
    }

    @Override
    protected PreparedStatementCreator buildInsertPreparedStatementCreator(Page page) {
        return new Psc(INSERT_SQL, page);
    }

    private void addExcludedGroups(Page parent) {
        List<String> excludedGroups = getExcludedGroups(parent.getId());
        parent.setExcludedGroups(excludedGroups);
    }

    private List<String> getExcludedGroups(String pageId) {
        return jdbcTemplate.query("select excluded_group from page_excluded_groups where page_id = ?"
                , (ResultSet rs, int rowNum) -> rs.getString(1)
                , pageId);
    }

    private void storeExcludedGroups(Page page, boolean deleteFirst) {
        if (deleteFirst) {
            jdbcTemplate.update("delete from page_excluded_groups where page_id = ?", page.getId());
        }
        if ((page.getExcludedGroups() != null) && !page.getExcludedGroups().isEmpty()) {
            List<String> excludedGroups = page.getExcludedGroups();
            jdbcTemplate.batchUpdate("insert into page_excluded_groups ( page_id, excluded_group ) values ( ?, ? )"
                    , new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            ps.setString(1, page.getId());
                            ps.setString(2, excludedGroups.get(i));
                        }

                        @Override
                        public int getBatchSize() {
                            return excludedGroups.size();
                        }
                    });
        }
    }


    @Override
    public Page create(Page item) throws TechnicalException {
        LOGGER.debug("JdbcPageRepository.create({})", item);
        try {
            jdbcTemplate.update(buildInsertPreparedStatementCreator(item));
            storeExcludedGroups(item, false);
            return findById(item.getId()).orElse(null);
        } catch (final Exception ex) {
            LOGGER.error("Failed to create page", ex);
            throw new TechnicalException("Failed to create page", ex);
        }
    }

    @Override
    public Page update(final Page page) throws TechnicalException {
        LOGGER.debug("JdbcPageRepository.update({})", page);
        if (page == null) {
            throw new IllegalStateException("Failed to update null");
        }
        try {
            jdbcTemplate.update(buildUpdatePreparedStatementCreator(page));
            storeExcludedGroups(page, true);
            return findById(page.getId()).orElseThrow(() ->
                    new IllegalStateException(format("No page found with id [%s]", page.getId())));
        } catch (final IllegalStateException ex) {
            throw ex;
        } catch (final Exception ex) {
            LOGGER.error("Failed to update page", ex);
            throw new TechnicalException("Failed to update page", ex);
        }
    }

    @Override
    public Collection<Page> findApiPageByApiIdAndHomepage(String apiId, boolean homePage) throws TechnicalException {
        LOGGER.debug("JdbcPageRepository.findApiPageByApiIdAndHomepage({}, {})", apiId, homePage);

        try {
            List<Page> items = jdbcTemplate.query("select * from pages where api = ? and homepage = ? order by " + ESCAPED_ORDER_COLUMN_NAME
                    , getRowMapper()
                    , apiId, homePage
            );
            for (Page page : items) {
                addExcludedGroups(page);
            }
            return items;
        } catch (final Exception ex) {
            LOGGER.error("Failed to find page by api and homepage:", ex);
            throw new TechnicalException("Failed to find page by api and homepage", ex);
        }
    }

    @Override
    public Collection<Page> findApiPageByApiId(String apiId) throws TechnicalException {
        LOGGER.debug("JdbcPageRepository.findApiPageByApiId({})", apiId);
        try {
            List<Page> items = jdbcTemplate.query("select * from pages where api = ? order by " + ESCAPED_ORDER_COLUMN_NAME
                    , getRowMapper()
                    , apiId
            );
            for (Page page : items) {
                addExcludedGroups(page);
            }
            return items;
        } catch (final Exception ex) {
            final String message = "Failed to find page by api";
            LOGGER.error(message, ex);
            throw new TechnicalException(message, ex);
        }

    }

    @Override
    public Integer findMaxApiPageOrderByApiId(String apiId) throws TechnicalException {
        LOGGER.debug("JdbcPageRepository.findMaxApiPageOrderByApiId({})", apiId);
        try {
            Integer result = jdbcTemplate.queryForObject("select max(" + ESCAPED_ORDER_COLUMN_NAME + ") from pages where api = ? "
                    , Integer.class
                    , apiId
            );
            return result == null ? 0 : result;
        } catch (final Exception ex) {
            LOGGER.error("Failed to find max page order by api:", ex);
            throw new TechnicalException("Failed to find max page order by api", ex);
        }
    }

    @Override
    public Collection<Page> findPortalPageByHomepage(boolean homePage) throws TechnicalException {
        LOGGER.debug("JdbcPageRepository.findPortalPageByHomepage({})", homePage);
        try {
            List<Page> items = jdbcTemplate.query("select * from pages where api is null and homepage = ? order by " + ESCAPED_ORDER_COLUMN_NAME
                    , getRowMapper()
                    , homePage
            );
            for (Page page : items) {
                addExcludedGroups(page);
            }
            return items;
        } catch (final Exception ex) {
            final String message = "Failed to find page by homepage";
            LOGGER.error(message, ex);
            throw new TechnicalException(message, ex);
        }
    }

    @Override
    public Collection<Page> findPortalPages() throws TechnicalException {
        LOGGER.debug("JdbcPageRepository.findPortalPages()");
        try {
            List<Page> items = jdbcTemplate.query("select * from pages where api is null order by " + ESCAPED_ORDER_COLUMN_NAME
                    , getRowMapper()
            );
            for (Page page : items) {
                addExcludedGroups(page);
            }
            return items;
        } catch (final Exception ex) {
            final String message = "Failed to find portal pages";
            LOGGER.error(message, ex);
            throw new TechnicalException(message, ex);
        }
    }

    @Override
    public Integer findMaxPortalPageOrder() throws TechnicalException {
        LOGGER.debug("JdbcPageRepository.findMaxPortalPageOrder()");
        try {
            return jdbcTemplate.queryForObject("select max(" + ESCAPED_ORDER_COLUMN_NAME + ") from pages where api is null "
                    , Integer.class
            );
        } catch (final Exception ex) {
            final String message = "Failed to find max portal page order";
            LOGGER.error(message, ex);
            throw new TechnicalException(message, ex);
        }
    }
}