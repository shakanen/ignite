package org.apache.ignite.cache.store.cassandra.persistence;

class ClassAccessHelper {

	private ClassAccessHelper() {}

	/**
	 * Returns description that can be used to load a class.
	 *
	 * @param pojoCls class for which the description is needed
	 * @return description for class loading
	 */
	public static String getClassDescription(final Class<?> pojoCls) {
		return pojoCls.getName();
	}

	/**
	 * Tries to load the class matching the given description
	 *
	 * @param pojoClassDescription description of class to load
	 * @return the loaded class
	 * @throws ClassNotFoundException if class can't be loaded with given description
	 */
	public static Class<?> tryLoadClass(final String pojoClassDescription) throws ClassNotFoundException {
		return ClassAccessHelper.class.getClassLoader().loadClass(pojoClassDescription);
	}
}
