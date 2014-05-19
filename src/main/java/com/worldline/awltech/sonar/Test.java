/**
 * Sonar Data Migrator, A tool to migrate sonar project data between to separate sonar instances.
 *
 * Copyright (C) 2013 Worldline or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see <http://www.gnu.org/licenses/>
 */
package com.worldline.awltech.sonar;

public class Test {
	
	/* * @param name classpath resource name [may not be null] 
	 * @param loader classloader through which to load the resource [null is equivalent to the application loader] 
	 * @return resource converted to java.util.Properties [may be null if the 
	 * * resource was not found and THROW_ON_LOAD_FAILURE is false] * 
	 * @throws IllegalArgumentException if the resource was not found and 
	 * * THROW_ON_LOAD_FAILURE is true */ 
/*	public static Properties loadProperties (String name, ClassLoader loader) 
	{ 
		if (name == null) 
			throw new IllegalArgumentException ("null input: name"); 
	if (name.startsWith ("/")) name = name.substring (1); 
	if (name.endsWith (SUFFIX)) name = name.substring (0, name.length () - SUFFIX.length ()); 
	Properties result = null; 
	InputStream in = null; 
	try { if (loader == null) loader = ClassLoader.getSystemClassLoader (); 
	if (LOAD_AS_RESOURCE_BUNDLE) 
	{ name = name.replace ('/', '.'); 
	// Throws MissingResourceException on lookup failures: 
	final ResourceBundle rb = ResourceBundle.getBundle (name, Locale.getDefault (), loader); 
	result = new Properties (); 
	for (Enumeration keys = rb.getKeys (); keys.hasMoreElements ();) 
	{ 
		final String key = (String) keys.nextElement (); 
		final String value = rb.getString (key); 
		result.put (key, value); } } 
	else { name = name.replace ('.', '/'); 
	if (! name.endsWith (SUFFIX)) name = name.concat (SUFFIX); 
	// Returns null on lookup failures: 
	in = loader.getResourceAsStream (name); 
	if (in != null) 
	{ result = new Properties (); 
	result.load (in); 
	// Can throw IOException 
	} } } 
	catch (Exception e) { result = null; } 
	finally { 
		if (in != null) 
			try { in.close (); } 
		catch (Throwable ignore) {} } 
	if (THROW_ON_LOAD_FAILURE && (result == null)) 
	{ 
		throw new IllegalArgumentException ("could not load [" + name + "]"+ " as " + (LOAD_AS_RESOURCE_BUNDLE ? "a resource bundle" : "a classloader resource")); } return result; } *//** * A convenience overload of {@link #loadProperties(String, ClassLoader)} * that uses the current thread's context classloader. *//* 
	public static Properties loadProperties (final String name) { 
			return loadProperties (name, Thread.currentThread ().getContextClassLoader ()); } 
		private static final boolean THROW_ON_LOAD_FAILURE = true; 
		private static final boolean LOAD_AS_RESOURCE_BUNDLE = false; 
		private static final String SUFFIX = ".properties"; } // End of class
	}
	*/

}
