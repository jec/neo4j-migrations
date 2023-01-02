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
package ac.simons.neo4j.migrations.quarkus.runtime;

import java.util.Objects;

/**
 * A wrapper around a classpath resource written in a way so that Quarkus can serialize it to and load it from bytecode.
 *
 * @author Michael J. Simons
 * @since 1.3.0
 */
public final class ResourceWrapper {

	/**
	 * The url representing the resource's location. A {@link String} is used to avoid having to deal with unsupported `jar:` urls in native image.
	 * We don't use {@link java.net.URI} as that type isn't serializable in Quarkus generated byte code.
	 */
	private String url;

	/**
	 * The path of this classpath resource relative to the package root.
	 */
	private String path;

	/**
	 * @return The url.
	 * @see #url
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @param url a new url
	 */
	public void setUrl(String url) {

		Objects.requireNonNull(url);
		this.url = url;
	}

	/**
	 * @return the absolute path (might be inside the classpath or outside)
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @param path a new path
	 */
	public void setPath(String path) {

		Objects.requireNonNull(path);
		this.path = path;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ResourceWrapper that = (ResourceWrapper) o;
		return url.equals(that.url) && path.equals(that.path);
	}

	@Override
	public int hashCode() {
		return Objects.hash(url, path);
	}
}
