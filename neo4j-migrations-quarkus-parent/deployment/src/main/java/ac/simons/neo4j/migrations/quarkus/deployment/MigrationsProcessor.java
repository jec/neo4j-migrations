/*
 * Copyright 2020-2022 the original author or authors.
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
package ac.simons.neo4j.migrations.quarkus.deployment;

import ac.simons.neo4j.migrations.core.Defaults;
import ac.simons.neo4j.migrations.core.JavaBasedMigration;
import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.core.MigrationsConfig;
import ac.simons.neo4j.migrations.core.internal.Location;
import ac.simons.neo4j.migrations.quarkus.runtime.MigrationsBuildTimeProperties;
import ac.simons.neo4j.migrations.quarkus.runtime.MigrationsProperties;
import ac.simons.neo4j.migrations.quarkus.runtime.MigrationsRecorder;
import ac.simons.neo4j.migrations.quarkus.runtime.ResourceWrapper;
import ac.simons.neo4j.migrations.quarkus.runtime.StaticClasspathResourceScanner;
import ac.simons.neo4j.migrations.quarkus.runtime.StaticJavaBasedMigrationDiscoverer;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.neo4j.deployment.Neo4jDriverBuildItem;
import io.quarkus.runtime.util.ClassPathUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

/**
 * This processor produces two additional items:
 * A synthetic bean of type {@link Migrations} and an additional bean of type {@link ServiceStartBuildItem}, the latter
 * indicating that all migrations have been applied (in case they are actually enabled).
 *
 * @author Michael J. Simons
 * @since 1.2.2
 */
// tag::createFeature[]
public class MigrationsProcessor {

	static final String FEATURE_NAME = "neo4j-migrations";

	@BuildStep
	@SuppressWarnings("unused")
	FeatureBuildItem createFeature() {

		return new FeatureBuildItem(FEATURE_NAME);
	}
	// end::createFeature[]

	static Set<Class<? extends JavaBasedMigration>> findClassBasedMigrations(Collection<String> packagesToScan, IndexView indexView) {

		if (packagesToScan.isEmpty()) {
			return Set.of();
		}

		var classesFoundAndLoaded = new HashSet<Class<? extends JavaBasedMigration>>();
		indexView
			.getAllKnownImplementors(DotName.createSimple(JavaBasedMigration.class.getName()))
			.forEach(cf -> {
				if (!packagesToScan.contains(cf.name().packagePrefix())) {
					return;
				}
				try {
					classesFoundAndLoaded.add(Thread.currentThread().getContextClassLoader()
						.loadClass(cf.name().toString()).asSubclass(JavaBasedMigration.class));
				} catch (ClassNotFoundException e) {
					// We silently ignore this (same behaviour as the Core-API does)
				}
			});
		return classesFoundAndLoaded;
	}

	// tag::createDiscoverer[]
	@BuildStep
	@SuppressWarnings("unused")
	DiscovererBuildItem createDiscoverer(
		CombinedIndexBuildItem combinedIndexBuildItem,
		MigrationsBuildTimeProperties buildTimeProperties
	) {

		var packagesToScan = buildTimeProperties.packagesToScan
			.orElseGet(List::of);
		var index = combinedIndexBuildItem.getIndex();
		var classesFoundDuringBuild = findClassBasedMigrations(
			packagesToScan, index); // <.>
		return new DiscovererBuildItem(StaticJavaBasedMigrationDiscoverer
			.of(classesFoundDuringBuild));
	}
	// end::createDiscoverer[]

	// tag::registerMigrationsForReflections[]
	@BuildStep
	@SuppressWarnings("unused")
	ReflectiveClassBuildItem registerMigrationsForReflections(DiscovererBuildItem discovererBuildItem) {

		var classes = discovererBuildItem.getDiscoverer()
			.getMigrationClasses().toArray(new Class<?>[0]);
		return new ReflectiveClassBuildItem(true, true, true, classes);
	}
	// end::registerMigrationsForReflections[]

	static Set<ResourceWrapper> findResourceBasedMigrations(Collection<String> locationsToScan) throws IOException {

		if (locationsToScan.isEmpty()) {
			return Set.of();
		}

		var resourcesFound = new HashSet<ResourceWrapper>();
		var expectedCypherSuffix = "." + Defaults.CYPHER_SCRIPT_EXTENSION;
		Predicate<Path> isCypherFile = path -> Files.isRegularFile(path) && path.getFileName().toString()
			.toLowerCase(Locale.ROOT)
			.endsWith(expectedCypherSuffix);

		// This piece is deliberately not using the streams due to the heckmeck with catching IOExceptions
		// and to avoid allocations of several sets.
		for (var value : locationsToScan) {
			var location = Location.of(value);
			if (location.getType() != Location.LocationType.CLASSPATH) {
				continue;
			}
			var name = location.getName();
			if (name.startsWith("/")) {
				name = name.substring(1);
			}
			var rootPath = Path.of(name);
			ClassPathUtils.consumeAsPaths(name, rootResource -> {
				try (var paths = Files.walk(rootResource)) {
					paths
						.filter(isCypherFile)
						// Resolving the string and not the path object is done on purpose, as otherwise
						// a provider mismatch can occur.
						.map(it -> rootPath.resolve(rootResource.relativize(it).normalize().toString()))
						.map(r -> {
							var resource = new ResourceWrapper();
							resource.setUrl(r.toUri().toString());
							resource.setPath(r.toString().replace('\\', '/'));
							return resource;
						})
						.forEach(resourcesFound::add);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}

		return resourcesFound;
	}

	@BuildStep
	@SuppressWarnings("unused")
	ClasspathResourceScannerBuildItem createScanner(MigrationsBuildTimeProperties buildTimeProperties) throws IOException {

		var resourcesFoundDuringBuild = findResourceBasedMigrations(buildTimeProperties.locationsToScan);
		return new ClasspathResourceScannerBuildItem(StaticClasspathResourceScanner.of(resourcesFoundDuringBuild));
	}

	// tag::addCypherResources[]
	@BuildStep
	@SuppressWarnings("unused")
	NativeImageResourceBuildItem addCypherResources(
		ClasspathResourceScannerBuildItem classpathResourceScannerBuildItem
	) {

		var resources = classpathResourceScannerBuildItem.getScanner()
			.getResources();
		return new NativeImageResourceBuildItem(resources.stream()
			.map(ResourceWrapper::getPath)
			.collect(Collectors.toList()));
	}
	// end::addCypherResources[]

	// tag::createMigrations[]
	@BuildStep
	@Record(ExecutionTime.RUNTIME_INIT) // <.>
	@SuppressWarnings("unused")
	MigrationsBuildItem createMigrations(
		MigrationsBuildTimeProperties buildTimeProperties,
		MigrationsProperties runtimeProperties,
		DiscovererBuildItem discovererBuildItem,
		ClasspathResourceScannerBuildItem classpathResourceScannerBuildItem,
		MigrationsRecorder migrationsRecorder,
		Neo4jDriverBuildItem driverBuildItem, // <.>
		BuildProducer<SyntheticBeanBuildItem> syntheticBeans
	) {
		var configRv = migrationsRecorder
			.recordConfig(
				buildTimeProperties, runtimeProperties,
				discovererBuildItem.getDiscoverer(),
				classpathResourceScannerBuildItem.getScanner()
			);
		var configBean = SyntheticBeanBuildItem
			.configure(MigrationsConfig.class)
			.runtimeValue(configRv).setRuntimeInit()
			.done();
		syntheticBeans.produce(configBean);

		var migrationsRv = migrationsRecorder
			.recordMigrations(configRv, driverBuildItem.getValue()); // <.>
		var migrationsBean = SyntheticBeanBuildItem
			.configure(Migrations.class)
			.runtimeValue(migrationsRv)
			.setRuntimeInit()
			.done();
		syntheticBeans.produce(migrationsBean);

		return new MigrationsBuildItem(migrationsRv);
	}
	// end::createMigrations[]

	// tag::applyMigrations[]
	@BuildStep
	@Record(ExecutionTime.RUNTIME_INIT)
	@SuppressWarnings("unused")
	ServiceStartBuildItem applyMigrations(
		MigrationsProperties migrationsProperties,
		MigrationsRecorder migrationsRecorder,
		MigrationsBuildItem migrationsBuildItem
	) {

		migrationsRecorder.applyMigrations(migrationsBuildItem.getValue(),
			migrationsRecorder.isEnabled(migrationsProperties));
		return new ServiceStartBuildItem(FEATURE_NAME);
	}
	// end::applyMigrations[]

	// tag::createFeature[]
}
// end::createFeature[]
