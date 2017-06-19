/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.cassandra.core.cql.keyspace;

import org.springframework.data.cassandra.core.cql.KeyspaceIdentifier;

public class AlterKeyspaceSpecification extends KeyspaceOptionsSpecification<AlterKeyspaceSpecification> {

	/**
	 * Entry point into the {@link AlterKeyspaceSpecification}'s fluent API to alter a keyspace. Convenient if imported
	 * statically.
	 */
	public static AlterKeyspaceSpecification alterKeyspace() {
		return new AlterKeyspaceSpecification();
	}

	/**
	 * Entry point into the {@link AlterKeyspaceSpecification}'s fluent API to alter a keyspace. Convenient if imported
	 * statically.
	 */
	public static AlterKeyspaceSpecification alterKeyspace(KeyspaceIdentifier name) {
		return new AlterKeyspaceSpecification(name);
	}

	public AlterKeyspaceSpecification() {}

	public AlterKeyspaceSpecification(String name) {
		name(name);
	}

	public AlterKeyspaceSpecification(KeyspaceIdentifier name) {
		name(name);
	}

}
