/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.cql.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.data.cql.core.session.DefaultReactiveSessionFactory;
import org.springframework.data.cql.core.session.ReactiveResultSet;
import org.springframework.data.cql.core.session.ReactiveSession;
import org.springframework.data.cql.core.session.ReactiveSessionFactory;
import org.springframework.data.cql.support.ReactiveCassandraAccessor;
import org.springframework.util.Assert;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.policies.RetryPolicy;
import com.datastax.driver.core.querybuilder.QueryBuilder;

/**
 * <b>This is the central class in the CQL core package for reactive Cassandra data access.</b> It simplifies the use of
 * CQL and helps to avoid common errors. It executes core CQL workflow, leaving application code to provide CQL and
 * extract results. This class executes CQL queries or updates, initiating iteration over {@link ReactiveResultSet}s and
 * catching {@link DriverException} exceptions and translating them to the generic, more informative exception hierarchy
 * defined in the {@code org.springframework.dao} package.
 * <p>
 * Code using this class need only implement callback interfaces, giving them a clearly defined contract. The
 * {@link PreparedStatementCreator} callback interface creates a prepared statement given a Connection, providing CQL
 * and any necessary parameters. The {@link ResultSetExtractor} interface extracts values from a
 * {@link ReactiveResultSet}. See also {@link PreparedStatementBinder} and {@link RowMapper} for two popular alternative
 * callback interfaces.
 * <p>
 * Can be used within a service implementation via direct instantiation with a {@link ReactiveSessionFactory} reference,
 * or get prepared in an application context and given to services as bean reference. Note: The
 * {@link ReactiveSessionFactory} should always be configured as a bean in the application context, in the first case
 * given to the service directly, in the second case to the prepared template.
 * <p>
 * Because this class is parameterizable by the callback interfaces and the
 * {@link org.springframework.dao.support.PersistenceExceptionTranslator} interface, there should be no need to subclass
 * it.
 * <p>
 * All CQL operations performed by this class are logged at debug level, using
 * "org.springframework.data.cql.core.ReactiveCqlTemplate" as log category.
 * <p>
 * <b>NOTE: An instance of this class is thread-safe once configured.</b>
 *
 * @author Mark Paluch
 * @since 2.0
 * @see PreparedStatementCreator
 * @see PreparedStatementBinder
 * @see PreparedStatementCallback
 * @see ResultSetExtractor
 * @see RowCallbackHandler
 * @see RowMapper
 * @see org.springframework.dao.support.PersistenceExceptionTranslator
 */
public class ReactiveCqlTemplate extends ReactiveCassandraAccessor implements ReactiveCqlOperations {

	/**
	 * Placeholder for default values.
	 */
	private static final Statement DEFAULTS = QueryBuilder.select().from("DEFAULT");

	/**
	 * If this variable is set to a non-negative value, it will be used for setting the {@code fetchSize} property on
	 * statements used for query processing.
	 */
	private int fetchSize = -1;

	/**
	 * If this variable is set to a value, it will be used for setting the {@code retryPolicy} property on statements used
	 * for query processing.
	 */
	private RetryPolicy retryPolicy;

	/**
	 * If this variable is set to a value, it will be used for setting the {@code consistencyLevel} property on statements
	 * used for query processing.
	 */
	private com.datastax.driver.core.ConsistencyLevel consistencyLevel;

	/**
	 * Construct a new {@link ReactiveCqlTemplate Note: The {@link ReactiveSessionFactory} has to be set before using the
	 * instance.
	 *
	 * @see #setSessionFactory
	 */
	public ReactiveCqlTemplate() {}

	/**
	 * Construct a new {@link ReactiveCqlTemplate}, given a {@link ReactiveSession}.
	 *
	 * @param reactiveSession the {@link ReactiveSession}, must not be {@literal null}.
	 */
	public ReactiveCqlTemplate(ReactiveSession reactiveSession) {

		Assert.notNull(reactiveSession, "ReactiveSession must not be null");

		setSessionFactory(new DefaultReactiveSessionFactory(reactiveSession));
		afterPropertiesSet();
	}

	/**
	 * Construct a new {@link ReactiveCqlTemplate}, given a {@link ReactiveSessionFactory} to obtain
	 * {@link ReactiveSession}s from.
	 *
	 * @param reactiveSessionFactory the {@link ReactiveSessionFactory} to obtain {@link ReactiveSession}s from, must not
	 *          be {@literal null}.
	 */
	public ReactiveCqlTemplate(ReactiveSessionFactory reactiveSessionFactory) {
		setSessionFactory(reactiveSessionFactory);
		afterPropertiesSet();
	}

	/**
	 * Set the consistency level for this {@link ReactiveCqlTemplate}. Consistency level defines the number of nodes
	 * involved into query processing. Relaxed consistency level settings use fewer nodes but eventual consistency is more
	 * likely to occur while a higher consistency level involves more nodes to obtain results with a higher consistency
	 * guarantee.
	 *
	 * @see Statement#setConsistencyLevel(ConsistencyLevel)
	 * @see RetryPolicy
	 */
	public void setConsistencyLevel(ConsistencyLevel consistencyLevel) {
		this.consistencyLevel = consistencyLevel;
	}

	/**
	 * @return the {@link ConsistencyLevel} specified for this {@link ReactiveCqlTemplate}.
	 */
	public ConsistencyLevel getConsistencyLevel() {
		return consistencyLevel;
	}

	/**
	 * Set the fetch size for this {@link ReactiveCqlTemplate}. This is important for processing large result sets:
	 * Setting this higher than the default value will increase processing speed at the cost of memory consumption;
	 * setting this lower can avoid transferring row data that will never be read by the application. Default is -1,
	 * indicating to use the CQL driver's default configuration (i.e. to not pass a specific fetch size setting on to the
	 * driver).
	 *
	 * @see Statement#setFetchSize(int)
	 */
	public void setFetchSize(int fetchSize) {
		this.fetchSize = fetchSize;
	}

	/**
	 * @return the fetch size specified for this {@link ReactiveCqlTemplate}.
	 */
	public int getFetchSize() {
		return this.fetchSize;
	}

	/**
	 * Set the retry policy for this {@link ReactiveCqlTemplate}. This is important for defining behavior when a request
	 * fails.
	 *
	 * @see Statement#setRetryPolicy(RetryPolicy)
	 * @see RetryPolicy
	 */
	public void setRetryPolicy(RetryPolicy retryPolicy) {
		this.retryPolicy = retryPolicy;
	}

	/**
	 * @return the {@link RetryPolicy} specified for this {@link ReactiveCqlTemplate}.
	 */
	public RetryPolicy getRetryPolicy() {
		return retryPolicy;
	}

	// -------------------------------------------------------------------------
	// Methods dealing with a plain org.springframework.data.cql.core.ReactiveSession
	// -------------------------------------------------------------------------

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#execute(org.springframework.data.cql.core.ReactiveSessionCallback)
	 */
	@Override
	public <T> Flux<T> execute(ReactiveSessionCallback<T> action) throws DataAccessException {

		Assert.notNull(action, "Callback object must not be null");

		return createFlux(action).onErrorMap(translateException("ReactiveSessionCallback", getCql(action)));
	}

	// -------------------------------------------------------------------------
	// Methods dealing with static CQL
	// -------------------------------------------------------------------------

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#execute(java.lang.String)
	 */
	@Override
	public Mono<Boolean> execute(String cql) throws DataAccessException {

		Assert.hasText(cql, "CQL must not be empty");

		return queryForResultSet(cql).map(ReactiveResultSet::wasApplied);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#query(java.lang.String, org.springframework.data.cql.core.ReactiveResultSetExtractor)
	 */
	@Override
	public <T> Flux<T> query(String cql, ReactiveResultSetExtractor<T> resultSetExtractor) throws DataAccessException {

		Assert.hasText(cql, "CQL must not be empty");
		Assert.notNull(resultSetExtractor, "ReactiveResultSetExtractor must not be null");

		return createFlux(new SimpleStatement(cql), (session, stmt) -> {

			if (logger.isDebugEnabled()) {
				logger.debug("Executing CQL Statement [{}]", cql);
			}

			return session.execute(stmt).flatMapMany(resultSetExtractor::extractData);
		}).onErrorMap(translateException("Query", cql));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#query(java.lang.String, org.springframework.data.cql.core.RowMapper)
	 */
	@Override
	public <T> Flux<T> query(String cql, RowMapper<T> rowMapper) throws DataAccessException {
		return query(cql, new ReactiveRowMapperResultSetExtractor<>(rowMapper));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForObject(java.lang.String, org.springframework.data.cql.core.RowMapper)
	 */
	@Override
	public <T> Mono<T> queryForObject(String cql, RowMapper<T> rowMapper) throws DataAccessException {
		return query(cql, rowMapper).buffer(2).flatMap(list -> Mono.just(DataAccessUtils.requiredSingleResult(list)))
				.next();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForObject(java.lang.String, java.lang.Class)
	 */
	@Override
	public <T> Mono<T> queryForObject(String cql, Class<T> requiredType) throws DataAccessException {
		return queryForObject(cql, getSingleColumnRowMapper(requiredType));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForMap(java.lang.String)
	 */
	@Override
	public Mono<Map<String, Object>> queryForMap(String cql) throws DataAccessException {
		return queryForObject(cql, getColumnMapRowMapper());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForFlux(java.lang.String, java.lang.Class)
	 */
	@Override
	public <T> Flux<T> queryForFlux(String cql, Class<T> elementType) throws DataAccessException {
		return query(cql, getSingleColumnRowMapper(elementType));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForFlux(java.lang.String)
	 */
	@Override
	public Flux<Map<String, Object>> queryForFlux(String cql) throws DataAccessException {
		return query(cql, getColumnMapRowMapper());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForResultSet(java.lang.String)
	 */
	@Override
	public Mono<ReactiveResultSet> queryForResultSet(String cql) throws DataAccessException {

		Assert.hasText(cql, "CQL must not be empty");

		return createMono(new SimpleStatement(cql), (session, statement) -> {

			if (logger.isDebugEnabled()) {
				logger.debug("Executing CQL [{}]", cql);

			}
			return session.execute(statement);
		}).onErrorMap(translateException("QueryForResultSet", cql));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForRows(java.lang.String)
	 */
	@Override
	public Flux<Row> queryForRows(String cql) throws DataAccessException {
		return queryForResultSet(cql).flatMapMany(ReactiveResultSet::rows)
				.onErrorMap(translateException("QueryForRows", cql));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#execute(org.reactivestreams.Publisher)
	 */
	@Override
	public Flux<Boolean> execute(Publisher<String> statementPublisher) throws DataAccessException {

		Assert.notNull(statementPublisher, "CQL Publisher must not be null");

		return Flux.from(statementPublisher).flatMap(this::execute);
	}

	// -------------------------------------------------------------------------
	// Methods dealing with com.datastax.driver.core.Statement
	// -------------------------------------------------------------------------

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#execute(com.datastax.driver.core.Statement)
	 */
	@Override
	public Mono<Boolean> execute(Statement statement) throws DataAccessException {

		Assert.notNull(statement, "CQL Statement must not be null");

		return queryForResultSet(statement).map(ReactiveResultSet::wasApplied);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#query(com.datastax.driver.core.Statement, org.springframework.data.cql.core.ReactiveResultSetExtractor)
	 */
	@Override
	public <T> Flux<T> query(Statement statement, ReactiveResultSetExtractor<T> rse) throws DataAccessException {

		Assert.notNull(statement, "CQL Statement must not be null");
		Assert.notNull(rse, "ReactiveResultSetExtractor must not be null");

		return createFlux(statement, (session, stmt) -> {

			if (logger.isDebugEnabled()) {
				logger.debug("Executing CQL Statement [{}]", statement);
			}

			return session.execute(stmt).flatMapMany(rse::extractData);
		}).onErrorMap(translateException("Query", statement.toString()));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#query(com.datastax.driver.core.Statement, org.springframework.data.cql.core.RowMapper)
	 */
	@Override
	public <T> Flux<T> query(Statement statement, RowMapper<T> rowMapper) throws DataAccessException {
		return query(statement, new ReactiveRowMapperResultSetExtractor<>(rowMapper));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForObject(com.datastax.driver.core.Statement, org.springframework.data.cql.core.RowMapper)
	 */
	@Override
	public <T> Mono<T> queryForObject(Statement statement, RowMapper<T> rowMapper) throws DataAccessException {
		return query(statement, rowMapper).buffer(2).flatMap(list -> Mono.just(DataAccessUtils.requiredSingleResult(list)))
				.next();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForObject(com.datastax.driver.core.Statement, java.lang.Class)
	 */
	@Override
	public <T> Mono<T> queryForObject(Statement statement, Class<T> requiredType) throws DataAccessException {
		return queryForObject(statement, getSingleColumnRowMapper(requiredType));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForMap(com.datastax.driver.core.Statement)
	 */
	@Override
	public Mono<Map<String, Object>> queryForMap(Statement statement) throws DataAccessException {
		return queryForObject(statement, getColumnMapRowMapper());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForFlux(com.datastax.driver.core.Statement, java.lang.Class)
	 */
	@Override
	public <T> Flux<T> queryForFlux(Statement statement, Class<T> elementType) throws DataAccessException {
		return query(statement, getSingleColumnRowMapper(elementType));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForFlux(com.datastax.driver.core.Statement)
	 */
	@Override
	public Flux<Map<String, Object>> queryForFlux(Statement statement) throws DataAccessException {
		return query(statement, getColumnMapRowMapper());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForResultSet(com.datastax.driver.core.Statement)
	 */
	@Override
	public Mono<ReactiveResultSet> queryForResultSet(Statement statement) throws DataAccessException {

		Assert.notNull(statement, "CQL Statement must not be null");

		return createMono(statement, (session, executedStatement) -> {

			if (logger.isDebugEnabled()) {
				logger.debug("Executing CQL [{}]", executedStatement);

			}

			return session.execute(executedStatement);
		}).onErrorMap(translateException("QueryForResultSet", statement.toString()));
	}

	@Override
	public Flux<Row> queryForRows(Statement statement) throws DataAccessException {
		return queryForResultSet(statement).flatMapMany(ReactiveResultSet::rows)
				.onErrorMap(translateException("QueryForRows", statement.toString()));
	}

	// -------------------------------------------------------------------------
	// Methods dealing with prepared statements
	// -------------------------------------------------------------------------

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#execute(org.springframework.data.cql.core.ReactivePreparedStatementCreator, org.springframework.data.cql.core.ReactivePreparedStatementCallback)
	 */
	@Override
	public <T> Flux<T> execute(ReactivePreparedStatementCreator psc, ReactivePreparedStatementCallback<T> action)
			throws DataAccessException {

		Assert.notNull(psc, "ReactivePreparedStatementCreator must not be null");
		Assert.notNull(action, "ReactivePreparedStatementCallback object must not be null");

		return createFlux(session -> {

			logger.debug("Preparing statement [{}] using {}", getCql(psc), psc);

			return psc.createPreparedStatement(session).doOnNext(this::applyStatementSettings)
					.flatMapMany(ps -> action.doInPreparedStatement(session, ps));
		}).onErrorMap(translateException("ReactivePreparedStatementCallback", getCql(psc)));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#execute(java.lang.String, org.springframework.data.cql.core.ReactivePreparedStatementCallback)
	 */
	@Override
	public <T> Flux<T> execute(String cql, ReactivePreparedStatementCallback<T> action) throws DataAccessException {
		return execute(new SimpleReactivePreparedStatementCreator(cql), action);
	}

	/**
	 * Query using a prepared statement, reading the {@link ReactiveResultSet} with a {@link ReactiveResultSetExtractor}.
	 *
	 * @param psc object that can create a {@link PreparedStatement} given a {@link ReactiveSession}
	 * @param preparedStatementBinder object that knows how to set values on the prepared statement. If this is
	 *          {@literal null}, the CQL will be assumed to contain no bind parameters.
	 * @param rse object that will extract results
	 * @return an arbitrary result object, as returned by the {@link ReactiveResultSetExtractor}
	 * @throws DataAccessException if there is any problem
	 */
	public <T> Flux<T> query(ReactivePreparedStatementCreator psc, PreparedStatementBinder preparedStatementBinder,
			ReactiveResultSetExtractor<T> rse) throws DataAccessException {

		Assert.notNull(psc, "ReactivePreparedStatementCreator must not be null");
		Assert.notNull(rse, "ReactiveResultSetExtractor object must not be null");

		return execute(psc, (session, ps) -> Mono.just(ps).flatMapMany(pps -> {

			if (logger.isDebugEnabled()) {
				logger.debug("Executing Prepared CQL Statement [{}]", ps.getQueryString());
			}

			BoundStatement boundStatement = (preparedStatementBinder != null ? preparedStatementBinder.bindValues(ps)
					: ps.bind());

			applyStatementSettings(boundStatement);

			return session.execute(boundStatement);
		}).flatMap(rse::extractData)).onErrorMap(translateException("Query", getCql(psc)));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#query(org.springframework.data.cql.core.ReactivePreparedStatementCreator, org.springframework.data.cql.core.ReactiveResultSetExtractor)
	 */
	@Override
	public <T> Flux<T> query(ReactivePreparedStatementCreator psc, ReactiveResultSetExtractor<T> rse)
			throws DataAccessException {

		return query(psc, null, rse);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#query(java.lang.String, org.springframework.data.cql.core.PreparedStatementBinder, org.springframework.data.cql.core.ReactiveResultSetExtractor)
	 */
	@Override
	public <T> Flux<T> query(String cql, PreparedStatementBinder psb, ReactiveResultSetExtractor<T> rse)
			throws DataAccessException {

		return query(new SimpleReactivePreparedStatementCreator(cql), psb, rse);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#query(java.lang.String, org.springframework.data.cql.core.ReactiveResultSetExtractor, java.lang.Object[])
	 */
	@Override
	public <T> Flux<T> query(String cql, ReactiveResultSetExtractor<T> rse, Object... args) throws DataAccessException {
		return query(new SimpleReactivePreparedStatementCreator(cql), newArgPreparedStatementBinder(args), rse);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#query(org.springframework.data.cql.core.ReactivePreparedStatementCreator, org.springframework.data.cql.core.RowMapper)
	 */
	@Override
	public <T> Flux<T> query(ReactivePreparedStatementCreator psc, RowMapper<T> rowMapper) throws DataAccessException {
		return query(psc, null, new ReactiveRowMapperResultSetExtractor<>(rowMapper));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#query(java.lang.String, org.springframework.data.cql.core.PreparedStatementBinder, org.springframework.data.cql.core.RowMapper)
	 */
	@Override
	public <T> Flux<T> query(String cql, PreparedStatementBinder psb, RowMapper<T> rowMapper) throws DataAccessException {
		return query(cql, psb, new ReactiveRowMapperResultSetExtractor<>(rowMapper));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#query(org.springframework.data.cql.core.ReactivePreparedStatementCreator, org.springframework.data.cql.core.PreparedStatementBinder, org.springframework.data.cql.core.RowMapper)
	 */
	@Override
	public <T> Flux<T> query(ReactivePreparedStatementCreator psc, PreparedStatementBinder psb, RowMapper<T> rowMapper)
			throws DataAccessException {

		return query(psc, psb, new ReactiveRowMapperResultSetExtractor<>(rowMapper));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#query(java.lang.String, org.springframework.data.cql.core.RowMapper, java.lang.Object[])
	 */
	@Override
	public <T> Flux<T> query(String cql, RowMapper<T> rowMapper, Object... args) throws DataAccessException {
		return query(cql, newArgPreparedStatementBinder(args), rowMapper);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForObject(java.lang.String, org.springframework.data.cql.core.RowMapper, java.lang.Object[])
	 */
	@Override
	public <T> Mono<T> queryForObject(String cql, RowMapper<T> rowMapper, Object... args) throws DataAccessException {
		return query(cql, rowMapper, args).buffer(2).flatMap(list -> Mono.just(DataAccessUtils.requiredSingleResult(list)))
				.next();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForObject(java.lang.String, java.lang.Class, java.lang.Object[])
	 */
	@Override
	public <T> Mono<T> queryForObject(String cql, Class<T> requiredType, Object... args) throws DataAccessException {
		return queryForObject(cql, getSingleColumnRowMapper(requiredType), args);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForMap(java.lang.String, java.lang.Object[])
	 */
	@Override
	public Mono<Map<String, Object>> queryForMap(String cql, Object... args) throws DataAccessException {
		return queryForObject(cql, getColumnMapRowMapper(), args);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForFlux(java.lang.String, java.lang.Class, java.lang.Object[])
	 */
	@Override
	public <T> Flux<T> queryForFlux(String cql, Class<T> elementType, Object... args) throws DataAccessException {
		return query(cql, getSingleColumnRowMapper(elementType), args);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForFlux(java.lang.String, java.lang.Object[])
	 */
	@Override
	public Flux<Map<String, Object>> queryForFlux(String cql, Object... args) throws DataAccessException {
		return query(cql, getColumnMapRowMapper(), args);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForResultSet(java.lang.String, java.lang.Object[])
	 */
	@Override
	public Mono<ReactiveResultSet> queryForResultSet(String cql, Object... args) throws DataAccessException {

		Assert.hasText(cql, "CQL must not be empty");

		return query(new SimpleReactivePreparedStatementCreator(cql), newArgPreparedStatementBinder(args), Mono::just)
				.next();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#queryForRows(java.lang.String, java.lang.Object[])
	 */
	@Override
	public Flux<Row> queryForRows(String cql, Object... args) throws DataAccessException {
		return queryForResultSet(cql, args).flatMapMany(ReactiveResultSet::rows)
				.onErrorMap(translateException("QueryForRows", cql));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#execute(org.springframework.data.cql.core.ReactivePreparedStatementCreator)
	 */
	@Override
	public Mono<Boolean> execute(ReactivePreparedStatementCreator psc) throws DataAccessException {
		return query(psc, resultSet -> Mono.just(resultSet.wasApplied())).last();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#execute(java.lang.String, org.springframework.data.cql.core.PreparedStatementBinder)
	 */
	@Override
	public Mono<Boolean> execute(String cql, PreparedStatementBinder psb) throws DataAccessException {
		return query(new SimpleReactivePreparedStatementCreator(cql), psb, resultSet -> Mono.just(resultSet.wasApplied()))
				.next();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#execute(java.lang.String, java.lang.Object[])
	 */
	@Override
	public Mono<Boolean> execute(String cql, Object... args) throws DataAccessException {
		return execute(cql, newArgPreparedStatementBinder(args));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cql.core.ReactiveCqlOperations#execute(java.lang.String, org.reactivestreams.Publisher)
	 */
	@Override
	public Flux<Boolean> execute(String cql, Publisher<Object[]> args) throws DataAccessException {

		Assert.notNull(args, "Args Publisher must not be null");

		SimpleReactivePreparedStatementCreator psc = new SimpleReactivePreparedStatementCreator(cql);

		return execute(psc, (session, ps) -> Flux.from(args).flatMap(objects -> {

			if (logger.isDebugEnabled()) {
				logger.debug("Executing Prepared CQL Statement [{}]", cql);
			}

			BoundStatement boundStatement = newArgPreparedStatementBinder(objects).bindValues(ps);

			applyStatementSettings(boundStatement);

			return session.execute(boundStatement);

		}).map(ReactiveResultSet::wasApplied));
	}

	// -------------------------------------------------------------------------
	// Implementation hooks and helper methods
	// -------------------------------------------------------------------------

	/**
	 * Create a reusable {@link Flux} given a {@link ReactiveStatementCallback} without exception translation.
	 *
	 * @param callback must not be {@literal null}.
	 * @return a reusable {@link Flux} wrapping the {@link ReactiveStatementCallback}.
	 */
	protected <T> Flux<T> createFlux(Statement statement, ReactiveStatementCallback<T> callback) {

		Assert.notNull(callback, "ReactiveStatementCallback must not be null");

		applyStatementSettings(statement);

		ReactiveSession session = getSession();

		return Flux.defer(() -> callback.doInStatement(session, statement));
	}

	/**
	 * Create a reusable {@link Mono} given a {@link ReactiveStatementCallback} without exception translation.
	 *
	 * @param callback must not be {@literal null}.
	 * @return a reusable {@link Mono} wrapping the {@link ReactiveStatementCallback }.
	 */
	protected <T> Mono<T> createMono(Statement statement, ReactiveStatementCallback<T> callback) {

		Assert.notNull(callback, "ReactiveStatementCallback must not be null");

		applyStatementSettings(statement);

		ReactiveSession session = getSession();

		return Mono.defer(() -> Mono.from(callback.doInStatement(session, statement)));
	}

	/**
	 * Create a reusable {@link Flux} given a {@link ReactiveSessionCallback} without exception translation.
	 *
	 * @param callback must not be {@literal null}.
	 * @return a reusable {@link Flux} wrapping the {@link ReactiveSessionCallback}.
	 */
	protected <T> Flux<T> createFlux(ReactiveSessionCallback<T> callback) {

		Assert.notNull(callback, "ReactiveStatementCallback must not be null");

		ReactiveSession session = getSession();

		return Flux.defer(() -> callback.doInSession(session));
	}

	/**
	 * Exception translation {@link Function} intended for {@link Mono#otherwise(Function)} usage.
	 *
	 * @param task readable text describing the task being attempted
	 * @param cql CQL query or update that caused the problem (may be {@code null})
	 * @return the exception translation {@link Function}
	 * @see CqlProvider
	 */
	protected Function<Throwable, Throwable> translateException(String task, String cql) {
		return throwable -> throwable instanceof DriverException ? translate(task, cql, (DriverException) throwable)
				: throwable;
	}

	/**
	 * Create a new RowMapper for reading columns as key-value pairs.
	 *
	 * @return the RowMapper to use
	 * @see ColumnMapRowMapper
	 */
	protected RowMapper<Map<String, Object>> getColumnMapRowMapper() {
		return new ColumnMapRowMapper();
	}

	/**
	 * Create a new RowMapper for reading result objects from a single column.
	 *
	 * @param requiredType the type that each result object is expected to match
	 * @return the RowMapper to use
	 * @see SingleColumnRowMapper
	 */
	protected <T> RowMapper<T> getSingleColumnRowMapper(Class<T> requiredType) {
		return SingleColumnRowMapper.newInstance(requiredType);
	}

	/**
	 * Prepare the given CQL Statement (or {@link com.datastax.driver.core.PreparedStatement}), applying statement
	 * settings such as fetch size, retry policy, and consistency level.
	 *
	 * @param stmt the CQL Statement to prepare
	 * @see #setFetchSize(int)
	 * @see #setRetryPolicy(RetryPolicy)
	 * @see #setConsistencyLevel(ConsistencyLevel)
	 */
	protected void applyStatementSettings(Statement stmt) {

		ConsistencyLevel consistencyLevel = getConsistencyLevel();

		if (consistencyLevel != null && stmt.getConsistencyLevel() == DEFAULTS.getConsistencyLevel()) {
			stmt.setConsistencyLevel(consistencyLevel);
		}

		int fetchSize = getFetchSize();

		if (fetchSize != -1 && stmt.getFetchSize() == DEFAULTS.getFetchSize()) {
			stmt.setFetchSize(fetchSize);
		}

		RetryPolicy retryPolicy = getRetryPolicy();

		if (retryPolicy != null && stmt.getRetryPolicy() == DEFAULTS.getRetryPolicy()) {
			stmt.setRetryPolicy(retryPolicy);
		}
	}

	/**
	 * Prepare the given CQL Statement (or {@link com.datastax.driver.core.PreparedStatement}), applying statement
	 * settings such as retry policy and consistency level.
	 *
	 * @param stmt the CQL Statement to prepare
	 * @see #setRetryPolicy(RetryPolicy)
	 * @see #setConsistencyLevel(ConsistencyLevel)
	 */
	protected void applyStatementSettings(PreparedStatement stmt) {

		ConsistencyLevel consistencyLevel = getConsistencyLevel();

		if (consistencyLevel != null) {
			stmt.setConsistencyLevel(consistencyLevel);
		}

		RetryPolicy retryPolicy = getRetryPolicy();

		if (retryPolicy != null) {
			stmt.setRetryPolicy(retryPolicy);
		}
	}

	/**
	 * Create a new arg-based PreparedStatementSetter using the args passed in.
	 * <p>
	 * By default, we'll create an {@link ArgumentPreparedStatementBinder}. This method allows for the creation to be
	 * overridden by subclasses.
	 *
	 * @param args object array with arguments
	 * @return the new {@link PreparedStatementBinder} to use
	 */
	protected PreparedStatementBinder newArgPreparedStatementBinder(Object[] args) {
		return new ArgumentPreparedStatementBinder(args);
	}

	private ReactiveSession getSession() {
		return getSessionFactory().getSession();
	}

	/**
	 * Determine CQL from potential provider object.
	 *
	 * @param cqlProvider object that's potentially a {@link CqlProvider}
	 * @return the CQL string, or {@code null}
	 * @see CqlProvider
	 */
	private static String getCql(Object cqlProvider) {

		return Optional.ofNullable(cqlProvider) //
				.filter(o -> o instanceof CqlProvider) //
				.map(o -> (CqlProvider) o) //
				.map(CqlProvider::getCql) //
				.orElse(null);
	}

	private class SimpleReactivePreparedStatementCreator implements ReactivePreparedStatementCreator, CqlProvider {

		private final String cql;

		SimpleReactivePreparedStatementCreator(String cql) {

			Assert.notNull(cql, "CQL must not be null");

			this.cql = cql;
		}

		@Override
		public Mono<PreparedStatement> createPreparedStatement(ReactiveSession session) throws DriverException {
			return session.prepare(cql);
		}

		@Override
		public String getCql() {
			return cql;
		}
	}
}
