/**
 * © Copyright The University of Queensland 2010-2014.
 * © Copyright QIMR Berghofer Medical Research Institute 2014-2016.
 *
 * This code is released under the terms outlined in the included LICENSE file.
 */
package org.qcmg.common.util;

import static org.qcmg.common.util.FileUtils.FILE_SEPARATOR;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LoadReferencedClasses {
	
	
	public static final <T> void loadClasses(final Class<T> clazz) throws URISyntaxException, IOException, ClassNotFoundException {
		
		final File thisJarFile = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
		if (null != thisJarFile && FileUtils.isFileTypeValid(thisJarFile, "jar")) {
		
			// create JarFile and extract info from manifest
			final JarFile jf = new JarFile(thisJarFile);
			
			// load entries from this far file first
			loadClassesInJar(jf, clazz, false);
			
			// now onto dependent jars
			try {
				final Attributes att = jf.getManifest().getMainAttributes();
				String classpath = att.getValue("Class-Path");
				
				if (null != classpath) {
					String [] jarArray = classpath.split("\\s");
					for (String jar : jarArray) {
						try {
							JarFile internalJarFile = new JarFile(thisJarFile.getParent() + FILE_SEPARATOR + jar);
							loadClassesInJar(internalJarFile, clazz, true);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				} else {
					System.err.println("couldn't locate Class-Path attribute in jar manifest file");
				}
			} finally {
				jf.close();
			}
		} 
	}
	
	static final <T> void loadClassesInJar(JarFile jarFile, Class<T> clazz, boolean closeFile) throws IOException, ClassNotFoundException {
		if (null != jarFile) {
						
			try {
				Enumeration<JarEntry> enums = jarFile.entries();
				while (enums.hasMoreElements()) {
					JarEntry je = enums.nextElement();
					if (FileUtils.isFileTypeValid(je.getName(), "class")) {
						String classToLoad = je.getName().replace(".class", "").replaceAll(FILE_SEPARATOR, ".");
						try {
							//classToLoad = classToLoad.replaceAll(FILE_SEPARATOR, ".");
							clazz.getClassLoader().loadClass(classToLoad);
						} catch (NoClassDefFoundError e) {	
							//XXX catching errors is not recommended, but is necessary in this instance
							// Ignoring - have seen instances where classes refer to other classes not included in the jar file
						}
					}
				}
			} finally {
				if (closeFile) jarFile.close();
			}
		}
	}

}
