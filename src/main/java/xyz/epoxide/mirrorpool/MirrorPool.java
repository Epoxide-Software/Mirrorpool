/*
 * This class is distributed as part of  
 * You can find the source here: https://github.com/Epoxide-Software/Mirrorpool
 * Copyright (C) 2016  Tyler Hancock
 * 
 * This library is free software; you can redistribute it and/or modify it under 
 * the terms of version 2.1 of the GNU Lesser Public License as published by the 
 * Free Software Foundation. 
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY, without even the implied warrant of MERCANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See version 2.1 of the GNU Lesser General Public
 * License for more details. 
 * 
 * You should have received a copy of the HNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
package xyz.epoxide.mirrorpool;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sections of this class are inspired by the DynamicEnumTest posted by badtrash on
 * niceideas.ch http://niceideas.ch/roller2/badtrash/resource/DynamicEnumTest.java
 */
public final class MirrorPool {
    
    /**
     * The current version of the library. Follows a Major-Release-Build structure. The major
     * number points to the current iteration of the project. The release number points to the
     * current release of the project. The build number refers to the current build of the
     * project and is handled by the build server.
     */
    public static final String VERSION = "1.0.0";
    
    /**
     * Logger for Mirrorpool. Allows for greater compatibility support with other logger APIs.
     * This should only ever be used internally!
     */
    private static Logger LOGGER = Logger.getLogger("Mirrorpool");
    
    /**
     * Access to Sun's reflection factory. Used to invoke constructor accessors.
     */
    private static Object reflectionFactory = null;
    
    /**
     * Access to sun.reflect.ReflectionFactory#newConstructorAccessor
     */
    private static Method newConstructorAccessor = null;
    
    /**
     * Access to sun.reflect.ConstructorAccessor#newInstance
     */
    private static Method newInstance = null;
    
    /**
     * Access to sun.reflect.ReflectionFactory#newFieldAccessor
     */
    private static Method newFieldAccessor = null;
    
    /**
     * Access to sun.reflect.FieldAccessor#set
     */
    private static Method fieldAccessorSet = null;
    
    /**
     * Sets the value of a constant field to the passed value.
     * 
     * @param field The Field to set the value of.
     * @param target Instance of the object to set the field on. Can be null if the field is
     *        static.
     * @param value The value to set the field to.
     */
    public static void setConstantField (Field field, Object target, Object value) throws Exception {
        
        field.setAccessible(true);
        
        final Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        
        final Object fieldAccessor = newFieldAccessor.invoke(reflectionFactory, field, false);
        fieldAccessorSet.invoke(fieldAccessor, target, value);
    }
    
    /**
     * Nullifies the value of a constant field.
     * 
     * @param clazz The class which contains the target field.
     * @param fieldName The name of the target field.
     */
    public static void nullifyConstantField (Class<?> clazz, String fieldName) throws Exception {
        
        for (final Field field : Class.class.getDeclaredFields())
            
            if (field.getName().contains(fieldName)) {
                
                field.setAccessible(true);
                setConstantField(field, clazz, null);
                break;
            }
    }
    
    /**
     * Gets access to the constructor of an enum class.
     * 
     * @param enumClass The target enum class.
     * @param additionalParameterTypes Array of classes which represent the constructor
     *        arguments of the enum, beyond the name and ordinal.
     * @return Access to the enum constructor.
     */
    private static Object getEnumConstructor (Class<?> enumClass, Class<?>[] additionalParameterTypes) throws Exception {
        
        final Class<?>[] parameterTypes = new Class[additionalParameterTypes.length + 2];
        parameterTypes[0] = String.class;
        parameterTypes[1] = int.class;
        System.arraycopy(additionalParameterTypes, 0, parameterTypes, 2, additionalParameterTypes.length);
        
        return newConstructorAccessor.invoke(reflectionFactory, enumClass.getDeclaredConstructor(parameterTypes));
    }
    
    /**
     * Constructs a new constant enum field.
     * 
     * @param enumClass The enum class to target.
     * @param enumName The name of the enum to create. Should be all upper case and unique.
     * @param ordinal The position of the new enum field.
     * @param additionalParameterTypes Array of classes which represent the constructor
     *        arguments of the enum, beyond name and ordinal.
     * @param additionalValues Array of values to supply the constructor with, beyond name and
     *        ordinal.
     * @return An object which represents the enum that was constructed.
     */
    private static <T extends Enum<?>> T constructEnum (Class<T> enumClass, String enumName, int ordinal, Class<?>[] additionalParameterTypes, Object[] additionalValues) throws Exception {
        
        final Object[] params = new Object[additionalValues.length + 2];
        params[0] = enumName;
        params[1] = ordinal;
        System.arraycopy(additionalValues, 0, params, 2, additionalValues.length);
        
        return enumClass.cast(newInstance.invoke(getEnumConstructor(enumClass, additionalParameterTypes), new Object[] { params }));
    }
    
    /**
     * Creates a new enum and adds it to the field definitions of an enum class through
     * reflection. The newly created field will not be accessible through source, but can be
     * referenced through the enum object returned by this field.
     * 
     * @param enumClass The class of the target enum.
     * @param enumName The name of the enum to create. Should be all upper case and unique.
     * @param additionalParameterTypes Array of classes which represent the constructor
     *        argument of the enum, beyond name and ordinal.
     * @param paramValues Array of values to supply the constructor with, beyond name and
     *        ordinal.
     * @return Reference to the newly created enum field.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Enum<?>> T addEnum (Class<T> enumClass, String enumName, final Class<?>[] additionalParameterTypes, Object[] paramValues) {
        
        if (!Enum.class.isAssignableFrom(enumClass)) {
            
            LOGGER.log(Level.WARNING, "Class " + enumClass.getName() + " is not a valid enum!");
            return null;
        }
        
        Field valuesField = null;
        final Field[] fields = enumClass.getDeclaredFields();
        
        for (final Field field : fields) {
            
            final String name = field.getName();
            
            if (name.equals("$VALUES") || name.equals("ENUM$VALUES")) {
                
                valuesField = field;
                break;
            }
        }
        
        if (valuesField == null) {
            
            LOGGER.log(Level.WARNING, "Could not find $VALUES or ENUM$VALUES field in " + enumClass.getName() + ". New enum can not be constructed!");
            return null;
        }
        
        valuesField.setAccessible(true);
        
        try {
            
            final T[] previousValues = (T[]) valuesField.get(enumClass);
            final List<T> values = new ArrayList<T>(Arrays.asList(previousValues));
            final T newValue = constructEnum(enumClass, enumName, values.size(), additionalParameterTypes, paramValues);
            
            values.add(newValue);
            setConstantField(valuesField, null, values.toArray((T[]) Array.newInstance(enumClass, 0)));
            nullifyConstantField(enumClass, "enumConstantDirectory");
            nullifyConstantField(enumClass, "enumConstants");
            
            return newValue;
        }
        
        catch (final Exception e) {
            
            LOGGER.log(Level.WARNING, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Retrieves a list of all the classes that are within a package. This is done by looking
     * at the actual files.
     * 
     * @param packageName The name of the package to search through. You can use . or /.
     * @return A list of classes which are found within the specified package.
     */
    public static List<Class<?>> getClasses (String packageName) throws ClassNotFoundException {
        
        final String packagePath = packageName.replace('.', '/');
        final URL packageURL = Thread.currentThread().getContextClassLoader().getResource(packagePath);
        
        if (packageURL == null) {
            
            LOGGER.log(Level.WARNING, "Could not load from " + packagePath + ". Does " + packageName + " actually exist?");
            return null;
        }
        
        final File packageDirectory = new File(packageURL.getFile());
        final List<Class<?>> classList = new ArrayList<Class<?>>();
        
        for (final File file : packageDirectory.listFiles())
            classList.addAll(getClasses(file, packageName));
        
        return classList;
    }
    
    /**
     * Recursively gets all class files within a director.
     * 
     * @param file The base directory to search through. If this is instead a class file, it
     *        will be returned as the only class in the list.
     * @param packageName The name of the package for the file being looked at.
     * @return A list of all classes that were found.
     */
    private static List<Class<?>> getClasses (File file, String packageName) throws ClassNotFoundException {
        
        final List<Class<?>> classList = new ArrayList<Class<?>>();
        final String fileName = packageName + '.' + file.getName();
        
        if (file.isDirectory())
            for (final File subFile : file.listFiles())
                classList.addAll(getClasses(subFile, fileName));
            
        else if (fileName.endsWith(".class"))
            classList.add(Class.forName(fileName.substring(0, fileName.length() - ".class".length())));
        
        return classList;
    }
    
    static {
        
        try {
            
            reflectionFactory = ((Method) Class.forName("sun.reflect.ReflectionFactory").getDeclaredMethod("getReflectionFactory")).invoke(null);
            newConstructorAccessor = Class.forName("sun.reflect.ReflectionFactory").getDeclaredMethod("newConstructorAccessor", Constructor.class);
            newInstance = Class.forName("sun.reflect.ConstructorAccessor").getDeclaredMethod("newInstance", Object[].class);
            newFieldAccessor = Class.forName("sun.reflect.ReflectionFactory").getDeclaredMethod("newFieldAccessor", Field.class, boolean.class);
            fieldAccessorSet = Class.forName("sun.reflect.FieldAccessor").getDeclaredMethod("set", Object.class, Object.class);
        }
        
        catch (final Exception e) {
            
            LOGGER.log(Level.WARNING, "Mirrorpool could not be initialized, it will not work as expected!", e);
        }
    }
}