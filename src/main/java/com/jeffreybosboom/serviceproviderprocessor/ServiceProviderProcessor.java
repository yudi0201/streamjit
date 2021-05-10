/*
 * Copyright 2013 Jeffrey Bosboom
 *
 * This file is part of ServiceProviderProcessor.
 *
 * ServiceProviderProcessor is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * ServiceProviderProcessor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * ServiceProviderProcessor. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jeffreybosboom.serviceproviderprocessor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * An annotation processor that generates META-INF/services files for providers
 * annotated with ServiceProvider.
 * @see ServiceProvider
 * @see java.util.ServiceLoader
 * @author Jeffrey Bosboom
 * @since 8/14/2013
 */
@SupportedAnnotationTypes("com.jeffreybosboom.serviceproviderprocessor.ServiceProvider")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public final class ServiceProviderProcessor extends AbstractProcessor {
	private final List<ServiceRecord> records = new ArrayList<>();
	private final Set<String> servicesAlreadyParsed = new HashSet<>();

	public ServiceProviderProcessor() {}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (roundEnv.processingOver()) {
			writeOutput();
			return false;
		}
		if (annotations.isEmpty())
			return false;

		Elements elementUtils = processingEnv.getElementUtils();
		Types typeUtils = processingEnv.getTypeUtils();
		Messager messager = processingEnv.getMessager();
		for (Element provider : roundEnv.getElementsAnnotatedWith(ServiceProvider.class)) {
			ServiceProvider annotation = provider.getAnnotation(ServiceProvider.class);
			TypeElement serviceType = getValue(annotation);
			if (serviceType == null) {
				messager.printMessage(Diagnostic.Kind.ERROR, "service type not a class or interface", provider);
				continue;
			}
			if (!typeUtils.isAssignable(provider.asType(), serviceType.asType())) {
				messager.printMessage(Diagnostic.Kind.ERROR, "provider does not implement or extend service type", provider);
				continue;
			}
			//TODO: check if provider is accessible and has a no-arg ctor

			String serviceTypeStr = elementUtils.getBinaryName(serviceType).toString();
			int priority = annotation.priority();
			parseExistingService(serviceTypeStr);
			addRecord(new ServiceRecord(serviceTypeStr, elementUtils.getBinaryName((TypeElement)provider).toString(), priority, provider));
		}

		return true;
	}

	private void addRecord(ServiceRecord newRecord) {
		//Remove any existing records for this service/provider combo.
		for (Iterator<ServiceRecord> it = records.iterator(); it.hasNext();) {
			ServiceRecord r = it.next();
			if (r.service.equals(newRecord.service) && r.provider.equals(newRecord.provider))
				it.remove();
		}
		records.add(newRecord);
	}

	private void parseExistingService(String serviceTypeStr) {
		if (!servicesAlreadyParsed.add(serviceTypeStr))
			return;
		Filer filer = processingEnv.getFiler();
		try {
			FileObject resource = filer.getResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/"+serviceTypeStr);
			BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openInputStream(), StandardCharsets.UTF_8));
			String line = null;
			while ((line = reader.readLine()) != null) {
				//TODO: parse more gracefully
				String[] split = line.split("#");
				String provider = split[0].trim();
				int priority = Integer.parseInt(split[1].trim());
				Element e = processingEnv.getElementUtils().getTypeElement(provider);
				addRecord(new ServiceRecord(serviceTypeStr, provider, priority, e));
			}
			reader.close();
		} catch (FileNotFoundException ex) {
			//File doesn't exist yet.
		} catch (IOException ex) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "error reading existing service configuration file for "+serviceTypeStr);
		}
	}

	private void writeOutput() {
		Filer filer = processingEnv.getFiler();

		Map<String, List<ServiceRecord>> serviceMap = new HashMap<>();
		for (ServiceRecord r : records) {
			List<ServiceRecord> service = serviceMap.get(r.service);
			if (service == null) {
				service = new ArrayList<>();
				serviceMap.put(r.service, service);
			}
			service.add(r);
		}

		for (final String serviceType : serviceMap.keySet()) {
			List<ServiceRecord> providers = serviceMap.get(serviceType);
			Collections.sort(providers);

			List<Element> originatingElements = new ArrayList<>();
			for (ServiceRecord r : providers)
				if (r.originatingElement != null)
					originatingElements.add(r.originatingElement);

			try {
				FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/"+serviceType, originatingElements.toArray(new Element[0]));
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resource.openOutputStream(), StandardCharsets.UTF_8));
				for (ServiceRecord provider : providers)
					writer.write(String.format("%s # %d\n", provider.provider, provider.priority));
				writer.close();
			} catch (IOException ex) {
				processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "error writing service configuration file for "+serviceType);
			}
		}
	}

	private TypeElement getValue(ServiceProvider annotation) {
		try {
			annotation.value();
			throw new AssertionError("Expected MirroredTypeException");
		} catch (MirroredTypeException ex) {
			TypeMirror mirror = ex.getTypeMirror();
			if (mirror.getKind() == TypeKind.DECLARED)
				return (TypeElement)((DeclaredType)mirror).asElement();
			return null;
		}
	}

	private static final class ServiceRecord implements Comparable<ServiceRecord> {
		private final String service, provider;
		private final int priority;
		private final Element originatingElement;
		private ServiceRecord(String service, String provider, int priority, Element originatingElement) {
			this.service = service;
			this.provider = provider;
			this.priority = priority;
			this.originatingElement = originatingElement;
		}
		@Override
		public int compareTo(ServiceRecord other) {
			return Integer.compare(priority, other.priority);
		}
	}
}
