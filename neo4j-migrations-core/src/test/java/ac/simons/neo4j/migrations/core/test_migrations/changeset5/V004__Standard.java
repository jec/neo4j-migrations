/*
 * Copyright 2020-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ac.simons.neo4j.migrations.core.test_migrations.changeset5;

import org.neo4j.driver.Session;

import ac.simons.neo4j.migrations.core.JavaBasedMigration;
import ac.simons.neo4j.migrations.core.MigrationContext;

/**
 * @author Michael J. Simons
 */
public class V004__Standard implements JavaBasedMigration {

	@Override
	public void apply(MigrationContext context) {

		try (Session session = context.getSession()) {
			session.run("CREATE (n:V004__Standard)").consume();
		}
	}
}

