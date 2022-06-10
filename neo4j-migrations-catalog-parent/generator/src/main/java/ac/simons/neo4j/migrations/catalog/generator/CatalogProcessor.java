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
package ac.simons.neo4j.migrations.catalog.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * @author Michael J. Simons
 * @soundtrack Pink Floyd - Pulse
 * @since TBA
 */
@SupportedAnnotationTypes({
	FullyQualifiedNames.OGM_INDEX
})
@SupportedOptions({ CatalogProcessor.OUTPUT_DIR_OPTION })
public final class CatalogProcessor extends AbstractProcessor {

	static final String OUTPUT_DIR_OPTION = "ac.simons.neo4j.migrations.catalog.generator.output_dir";

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	String getOutputDir() {

		String subDir = processingEnv.getOptions().getOrDefault(OUTPUT_DIR_OPTION, "neo4j/migrations/");
		if (!subDir.endsWith("/")) {
			subDir += "/";
		}
		return subDir;
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

		if (roundEnv.processingOver()) {
			try {
				/*
				String subDir = processingEnv.getOptions().getOrDefault(NATIVE_IMAGE_SUBDIR_OPTION, "");
				if (!(subDir.isEmpty() || subDir.endsWith("/"))) {
					subDir += "/";
				}
				String reflectionConfigPath = String.format("META-INF/native-image/%sreflection-config.json", subDir);

				 */
				FileObject fileObject = processingEnv.getFiler()
					.createResource(StandardLocation.SOURCE_OUTPUT, "", getOutputDir() + "i_was_there.txt");
				try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(fileObject.openOutputStream()))) {
					out.write("Hello");
				}
			} catch (IOException e) {
				processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
			}
		} else if (!annotations.isEmpty()) {
			/*
			roundEnv.getElementsAnnotatedWith(RegisterForReflection.class)
				.stream()
				.filter(e -> e.getKind().isClass() && registersElements(e.getAnnotation(RegisterForReflection.class)))
				.map(TypeElement.class::cast)
				.map(e -> {
					RegisterForReflection registerForReflection = e.getAnnotation(RegisterForReflection.class);
					Entry entry = new Entry(e.getQualifiedName().toString());
					entry.setAllDeclaredMethods(registerForReflection.allDeclaredMethods());
					entry.setAllDeclaredConstructors(registerForReflection.allDeclaredConstructors());
					return entry;
				})
				.forEach(entries::add);

			 */
			// do the magic
		}

		return true;
	}
}
