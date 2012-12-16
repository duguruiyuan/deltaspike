/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.deltaspike.core.impl.jmx;

import org.apache.deltaspike.core.api.jmx.annotation.Description;
import org.apache.deltaspike.core.api.jmx.annotation.ManagedAttribute;
import org.apache.deltaspike.core.api.jmx.annotation.ManagedOperation;
import org.apache.deltaspike.core.api.jmx.annotation.NotificationInfo;
import org.apache.deltaspike.core.api.jmx.annotation.NotificationInfos;
import org.apache.deltaspike.core.api.provider.BeanManagerProvider;
import org.apache.deltaspike.core.api.provider.BeanProvider;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.ImmutableDescriptor;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DynamicMBeanWrapper implements DynamicMBean
{
    public static final Logger LOGGER = Logger.getLogger(DynamicMBeanWrapper.class.getName());

    private final MBeanInfo info;
    private final Map<String, Method> getters = new HashMap<String, Method>();
    private final Map<String, Method> setters = new HashMap<String, Method>();
    private final Map<String, Method> operations = new HashMap<String, Method>();
    private final ClassLoader classloader;
    private final Class<?> clazz;
    private final boolean normalScope;

    private final Annotation[] qualifiers;

    private Object instance = null;

    public DynamicMBeanWrapper(final Class<?> annotatedMBean, final boolean normalScope, final Annotation[] qualifiers)
    {
        this.clazz = annotatedMBean;
        this.classloader = Thread.currentThread().getContextClassLoader();
        this.normalScope = normalScope;
        this.qualifiers = qualifiers;

        final List<MBeanAttributeInfo> attributeInfos = new ArrayList<MBeanAttributeInfo>();
        final List<MBeanOperationInfo> operationInfos = new ArrayList<MBeanOperationInfo>();
        final List<MBeanNotificationInfo> notificationInfos = new ArrayList<MBeanNotificationInfo>();

        // class
        final String description = getDescription(annotatedMBean.getAnnotation(Description.class), "-");

        final NotificationInfo notification = annotatedMBean.getAnnotation(NotificationInfo.class);
        if (notification != null)
        {
            notificationInfos.add(getNotificationInfo(notification));
        }

        final NotificationInfos notifications = annotatedMBean.getAnnotation(NotificationInfos.class);
        if (notifications != null && notifications.value() != null)
        {
            for (NotificationInfo n : notifications.value())
            {
                notificationInfos.add(getNotificationInfo(n));
            }
        }


        // methods
        for (Method m : annotatedMBean.getMethods())
        {
            final int modifiers = m.getModifiers();
            if (m.getDeclaringClass().equals(Object.class)
                    || !Modifier.isPublic(modifiers)
                    || Modifier.isAbstract(modifiers))
            {
                continue;
            }

            if (m.getAnnotation(ManagedAttribute.class) != null)
            {
                final String methodName = m.getName();

                String attrName = methodName;
                if (isAccessor(m))
                {
                    attrName = attrName.substring(3);
                    if (attrName.length() > 1)
                    {
                        attrName = Character.toLowerCase(attrName.charAt(0)) + attrName.substring(1);
                    }
                    else
                    {
                        attrName = attrName.toLowerCase();
                    }
                }
                else
                {
                    LOGGER.warning("ignoring attribute " + m.getName() + " for " + annotatedMBean.getName());
                    continue;
                }

                if (methodName.startsWith("get"))
                {
                    getters.put(attrName, m);
                }
                else if (methodName.startsWith("set"))
                {
                    setters.put(attrName, m);
                }
            }
            else if (m.getAnnotation(ManagedOperation.class) != null)
            {
                operations.put(m.getName(), m);

                String operationDescr = "";
                final Description descr = m.getAnnotation(Description.class);
                if (descr != null)
                {
                    operationDescr = getDescription(descr, "-");
                }

                operationInfos.add(new MBeanOperationInfo(operationDescr, m));
            }
        }

        for (Map.Entry<String, Method> e : getters.entrySet())
        {
            final String key = e.getKey();
            final Method mtd = e.getValue();

            String attrDescr = "";
            final Description descr = mtd.getAnnotation(Description.class);
            if (descr != null)
            {
                attrDescr = getDescription(descr, "-");
            }

            try
            {
                attributeInfos.add(new MBeanAttributeInfo(key, attrDescr, mtd, setters.get(key)));
            }
            catch (IntrospectionException ex)
            {
                LOGGER.log(Level.WARNING, "can't manage " + key + " for " + mtd.getName(), ex);
            }
        }

        info = new MBeanInfo(annotatedMBean.getName(),
                description,
                attributeInfos.toArray(new MBeanAttributeInfo[attributeInfos.size()]),
                null, // default constructor is mandatory
                operationInfos.toArray(new MBeanOperationInfo[operationInfos.size()]),
                notificationInfos.toArray(new MBeanNotificationInfo[notificationInfos.size()]));
    }

    private static boolean isAccessor(final Method m)
    {
        final String name = m.getName();
        return ((name.startsWith("get") && m.getParameterTypes().length == 0)
                || (name.startsWith("set") && m.getParameterTypes().length == 1))
                && name.length() > 3;
    }

    private MBeanNotificationInfo getNotificationInfo(final NotificationInfo n)
    {
        return new MBeanNotificationInfo(n.types(),
                n.notificationClass().getName(), getDescription(n.description(), "-"),
                new ImmutableDescriptor(n.descriptorFields()));
    }

    private String getDescription(final Description d, final String defaultValue)
    {
        if (d != null)
        {
            if (d.bundleBaseName() != null && d.key() != null)
            {
                try
                {
                    return ResourceBundle.getBundle(d.bundleBaseName()).getString(d.key());
                }
                catch (RuntimeException re)
                {
                    return d.value();
                }
            }
            else
            {
                return d.value();
            }
        }
        return defaultValue;
    }

    @Override
    public MBeanInfo getMBeanInfo()
    {
        return info;
    }

    @Override
    public Object getAttribute(final String attribute)
        throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        if (getters.containsKey(attribute))
        {
            final ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(classloader);
            try
            {
                return getters.get(attribute).invoke(instance());
            }
            catch (IllegalArgumentException e)
            {
                LOGGER.log(Level.SEVERE, "can't get " + attribute + " value", e);
            }
            catch (IllegalAccessException e)
            {
                LOGGER.log(Level.SEVERE, "can't get " + attribute + " value", e);
            }
            catch (InvocationTargetException e)
            {
                LOGGER.log(Level.SEVERE, "can't get " + attribute + " value", e);
            }
            finally
            {
                Thread.currentThread().setContextClassLoader(oldCl);
            }
        }
        throw new AttributeNotFoundException();
    }

    @Override
    public void setAttribute(final Attribute attribute)
        throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException
    {
        if (setters.containsKey(attribute.getName()))
        {
            final ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(classloader);
            try
            {
                setters.get(attribute.getName()).invoke(instance(), attribute.getValue());
            }
            catch (IllegalArgumentException e)
            {
                LOGGER.log(Level.SEVERE, "can't set " + attribute + " value", e);
            }
            catch (IllegalAccessException e)
            {
                LOGGER.log(Level.SEVERE, "can't set " + attribute + " value", e);
            }
            catch (InvocationTargetException e)
            {
                LOGGER.log(Level.SEVERE, "can't set " + attribute + " value", e);
            }
            finally
            {
                Thread.currentThread().setContextClassLoader(oldCl);
            }
        }
        else
        {
            throw new AttributeNotFoundException();
        }
    }

    @Override
    public AttributeList getAttributes(final String[] attributes)
    {
        final AttributeList list = new AttributeList();
        for (String n : attributes)
        {
            try
            {
                list.add(new Attribute(n, getAttribute(n)));
            }
            catch (Exception ignore)
            {
                // no-op
            }
        }
        return list;
    }

    @Override
    public AttributeList setAttributes(final AttributeList attributes)
    {
        final AttributeList list = new AttributeList();
        for (Object o : attributes)
        {
            final Attribute attr = (Attribute) o;
            try
            {
                setAttribute(attr);
                list.add(attr);
            }
            catch (Exception ignore)
            {
                // no-op
            }
        }
        return list;
    }

    @Override
    public Object invoke(final String actionName, final Object[] params, final String[] signature)
        throws MBeanException, ReflectionException
    {
        if (operations.containsKey(actionName))
        {
            final ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(classloader);
            try
            {
                return operations.get(actionName).invoke(instance(), params);
            }
            catch (IllegalArgumentException e)
            {
                LOGGER.log(Level.SEVERE, actionName + "can't be invoked", e);
            }
            catch (IllegalAccessException e)
            {
                LOGGER.log(Level.SEVERE, actionName + "can't be invoked", e);
            }
            catch (InvocationTargetException e)
            {
                LOGGER.log(Level.SEVERE, actionName + "can't be invoked", e);
            }
            finally
            {
                Thread.currentThread().setContextClassLoader(oldCl);
            }
        }
        throw new MBeanException(new IllegalArgumentException(), actionName + " doesn't exist");
    }

    private synchronized Object instance()
    {
        final ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classloader);
        try
        {
            if (instance != null)
            {
                return instance;
            }

            if (normalScope)
            {
                instance = BeanProvider.getContextualReference(clazz, qualifiers);
            }
            else
            {
                final BeanManager bm = BeanManagerProvider.getInstance().getBeanManager();
                final Set<Bean<?>> beans = bm.getBeans(clazz, qualifiers);
                if (beans == null || beans.isEmpty())
                {
                    throw new IllegalStateException("Could not find beans for Type=" + clazz
                            + " and qualifiers:" + Arrays.toString(qualifiers));
                }

                final Bean<?> resolvedBean = bm.resolve(beans);
                final CreationalContext<?> creationalContext = bm.createCreationalContext(resolvedBean);
                instance = bm.getReference(resolvedBean, clazz, creationalContext);
                creationalContext.release();
            }
            return instance;
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(oldCl);
        }
    }
}