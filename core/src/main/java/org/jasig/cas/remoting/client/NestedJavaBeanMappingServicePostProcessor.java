/*
 * Copyright 2005 The JA-SIG Collaborative. All rights reserved. See license
 * distributed with this file and available online at
 * http://www.uportal.org/license.html
 */
package org.jasig.cas.remoting.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.rpc.Service;
import javax.xml.rpc.encoding.TypeMapping;
import javax.xml.rpc.encoding.TypeMappingRegistry;

import org.apache.axis.encoding.ser.BeanDeserializerFactory;
import org.apache.axis.encoding.ser.BeanSerializerFactory;
import org.springframework.remoting.jaxrpc.JaxRpcServicePostProcessor;

/**
 * Axis-specific JaxRpcServicePostProcessor that attempts to detect the
 * JavaBeans it needs to register by inspecting the service interface's return
 * types and parameter types for each method. Nested version attempts to find
 * beans nested within JavaBeans and register them also. It is currently
 * designed to ignore any java.* or javax.* class. It also assumes that the
 * types are JavaBeans. It does not actually check. A more sophisticated version
 * would be able to check if a class was a valid JavaBean and only register
 * valid JavaBeans. Also allows you to specify a list of JavaBeans to register
 * manually. This needs to be here incase one of the parameters to a method is
 * an interface and you need to register the implementing class.
 * 
 * @author Scott Battaglia
 * @author Dmitriy Kopylenko
 * @version $Revision$ $Date$
 * @since 3.0
 */
public final class NestedJavaBeanMappingServicePostProcessor implements
    JaxRpcServicePostProcessor {

    /** The namespace for the service. */
    private String namespace;

    /** The start of the package name for Java packages. */
    private static final String PACKAGE_NAME_JAVA = "java.";

    /** The start of the package name for Java Extension packages. */
    private static final String PACKAGE_NAME_JAVAX = "javax.";

    /** The list of beans. */
    private List javaBeans;

    /** The service interface. */
    private Class serviceInterface;

    /**
     * @param mapping The registry to add the beans to.
     * @param registeredBeans The list of beans registered so far.
     * @param clazz The class to loop through looking for beans to add.
     */
    protected void registerBeans(final TypeMapping mapping,
        final List registeredBeans, final Class clazz) {
        if (registeredBeans.contains(clazz)
            || clazz.getName().startsWith(PACKAGE_NAME_JAVA)
            || clazz.getName().startsWith(PACKAGE_NAME_JAVAX)) {
            return;
        }

        if (!clazz.equals(this.serviceInterface)) {
            registeredBeans.add(clazz);
            addJavaBeanToMap(mapping, clazz);
        }

        final Method[] methods = clazz.getDeclaredMethods(); // TODO
        // getDeclaredMethods or
        // getMethods ??

        for (int i = 0; i < methods.length; i++) {
            final Method method = methods[i];

            final Class returnType = method.getReturnType();
            final Class[] params = method.getParameterTypes();

            registerBeans(mapping, registeredBeans, returnType);

            for (int j = 0; j < params.length; j++) {
                registerBeans(mapping, registeredBeans, params[j]);
            }
        }
    }

    public void postProcessJaxRpcService(final Service service) {
        final TypeMappingRegistry registry = service.getTypeMappingRegistry();
        final TypeMapping mapping = registry.createTypeMapping();

        registerBeans(mapping, new ArrayList(), this.serviceInterface);

        if (this.javaBeans != null) {
            for (Iterator iter = this.javaBeans.iterator(); iter.hasNext();) {
                final String bean = (String) iter.next();
                try {
                    final Class clazz = Class.forName(bean);
                    this.addJavaBeanToMap(mapping, clazz);
                } catch (Exception e) {
                    throw new IllegalArgumentException("bean of class " + bean
                        + "not found.");
                }
            }
        }

        registry.register("http://schemas.xmlsoap.org/soap/encoding/", mapping);
    }

    /**
     * @param mapping The TypeMapping to register the bean to.
     * @param clazz The JavaBean class to register.
     */
    protected void addJavaBeanToMap(final TypeMapping mapping, final Class clazz) {
        String name = clazz.getName().substring(
            clazz.getName().lastIndexOf(".") + 1);
        QName qName = new QName(this.namespace, name);
        mapping.register(clazz, qName, new BeanSerializerFactory(clazz, qName),
            new BeanDeserializerFactory(clazz, qName));
    }

    /**
     * @param namespace The namespace.
     */
    public void setNamespace(final String namespace) {
        this.namespace = namespace;
    }

    /**
     * @param javaBeans The additional JavaBeans to add to the registry.
     */
    public void setJavaBeans(final List javaBeans) {
        this.javaBeans = javaBeans;
    }

    /**
     * @param serviceInterface
     */
    public void setServiceInterface(final Class serviceInterface) {
        this.serviceInterface = serviceInterface;
    }
}
