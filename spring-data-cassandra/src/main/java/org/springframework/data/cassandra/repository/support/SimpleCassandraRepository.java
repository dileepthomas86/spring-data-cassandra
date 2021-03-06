/*
 * Copyright 2013-2017 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.cassandra.repository.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.query.CassandraEntityInformation;
import org.springframework.util.Assert;

import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;

/**
 * Repository base implementation for Cassandra.
 *
 * @author Alex Shvid
 * @author Matthew T. Adams
 * @author Mark Paluch
 */
public class SimpleCassandraRepository<T, ID> implements CassandraRepository<T, ID> {

	private final CassandraEntityInformation<T, ID> entityInformation;

	private final CassandraOperations operations;

	/**
	 * Create a new {@link SimpleCassandraRepository} for the given {@link CassandraEntityInformation} and
	 * {@link CassandraTemplate}.
	 *
	 * @param metadata must not be {@literal null}.
	 * @param operations must not be {@literal null}.
	 */
	public SimpleCassandraRepository(CassandraEntityInformation<T, ID> metadata, CassandraOperations operations) {

		Assert.notNull(metadata, "CassandraEntityInformation must not be null");
		Assert.notNull(operations, "CassandraOperations must not be null");

		this.entityInformation = metadata;
		this.operations = operations;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#save(S)
	 */
	@Override
	public <S extends T> S save(S entity) {

		Assert.notNull(entity, "Entity must not be null");

		Insert insert = createFullInsert(entity);

		operations.getCqlOperations().execute(insert);

		return entity;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#saveAll(java.lang.Iterable)
	 */
	@Override
	public <S extends T> List<S> saveAll(Iterable<S> entities) {

		Assert.notNull(entities, "The given Iterable of entities must not be null");

		List<S> result = new ArrayList<S>();

		for (S entity : entities) {

			result.add(entity);
			operations.getCqlOperations().execute(createFullInsert(entity));
		}

		return result;
	}

	private <S extends T> Insert createFullInsert(S entity) {

		CassandraConverter converter = operations.getConverter();
		CassandraPersistentEntity<?> persistentEntity = converter.getMappingContext()
				.getRequiredPersistentEntity(entity.getClass());
		Map<String, Object> toInsert = new LinkedHashMap<>();

		converter.write(entity, toInsert, persistentEntity);

		Insert insert = QueryBuilder.insertInto(persistentEntity.getTableName().toCql());

		for (Entry<String, Object> entry : toInsert.entrySet()) {
			insert.value(entry.getKey(), entry.getValue());
		}

		return insert;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.repository.TypedIdCassandraRepository#insert(java.lang.Object)
	 */
	@Override
	public <S extends T> S insert(S entity) {

		Assert.notNull(entity, "Entity must not be null");

		return operations.insert(entity);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.repository.TypedIdCassandraRepository#insert(java.lang.Iterable)
	 */
	@Override
	public <S extends T> List<S> insert(Iterable<S> entities) {

		Assert.notNull(entities, "The given Iterable of entities must not be null");

		List<S> result = new ArrayList<>();

		for (S entity : entities) {

			S saved = operations.insert(entity);

			if (saved != null) {
				result.add(saved);
			}
		}

		return result;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#findById(java.lang.Object)
	 */
	@Override
	public Optional<T> findById(ID id) {

		Assert.notNull(id, "The given id must not be null");

		return Optional.ofNullable(operations.selectOneById(id, entityInformation.getJavaType()));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#existsById(java.lang.Object)
	 */
	@Override
	public boolean existsById(ID id) {

		Assert.notNull(id, "The given id must not be null");

		return operations.exists(id, entityInformation.getJavaType());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#count()
	 */
	@Override
	public long count() {
		return operations.count(entityInformation.getJavaType());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#findAll()
	 */
	@Override
	public List<T> findAll() {

		Select select = QueryBuilder.select().all().from(entityInformation.getTableName().toCql());

		return operations.select(select, entityInformation.getJavaType());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#findAll(java.lang.Iterable)
	 */
	@Override
	public List<T> findAllById(Iterable<ID> ids) {

		Assert.notNull(ids, "The given Iterable of id's must not be null");

		return operations.selectBySimpleIds(ids, entityInformation.getJavaType());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#deleteById(java.lang.Object)
	 */
	@Override
	public void deleteById(ID id) {

		Assert.notNull(id, "The given id must not be null");

		operations.deleteById(id, entityInformation.getJavaType());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#delete(java.lang.Object)
	 */
	@Override
	public void delete(T entity) {

		Assert.notNull(entity, "The given entity must not be null");

		deleteById(entityInformation.getId(entity)
				.orElseThrow(() -> new IllegalArgumentException(String.format("Cannot obtain Id from [%s]", entity))));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#deleteAll(java.lang.Iterable)
	 */
	@Override
	public void deleteAll(Iterable<? extends T> entities) {

		Assert.notNull(entities, "The given Iterable of entities must not be null");

		entities.forEach(operations::delete);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#deleteAll()
	 */
	@Override
	public void deleteAll() {
		operations.truncate(entityInformation.getJavaType());
	}
}
