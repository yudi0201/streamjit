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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The ServiceProvider annotation marks a class as a provider of some service
 * type. If the ServiceProviderProcessor annotation processor is enabled,
 * providers marked with this annotation are automatically registered in the
 * corresponding META-INF/services file for their service types.
 * @see java.util.ServiceLoader
 * @author Jeffrey Bosboom
 * @since 8/14/2013
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ServiceProvider {
	/**
	 * The service class or interface being provided.
	 * <p/>
	 * Design note: we could provide a default value here, say void.class, and
	 * infer the actual type in the processor if the annotated type only
	 * implements or inherits from one type. However, consider "public FooImpl
	 * extends AbstractFoo" where AbstractFoo implements a service interface
	 * Foo: inference would register FooImpl for AbstractFoo, not Foo, which is
	 * probably not what the developer intended. Thus we require explicit
	 * specification. (This also helps readers figure out which service is being
	 * provided.)
	 */
	public Class<?> value();

	/**
	 * The priority value of this provider, which determines the position at
	 * which it is listed in the META-INF/services file (and thus the order in
	 * which ServiceLoader loads it). Lower numbers are higher priority. The
	 * default priority is 0.
	 * <p/>
	 * (TODO: priority interaction with providers manually registered in
	 * META-INF/services rather than with annotations?)
	 */
	public int priority() default 0;
}
