package org.paohaijiao.jstark.context.service;
import org.paohaijiao.jstark.anno.Autowired;
import org.paohaijiao.jstark.context.BeanContainer;
import org.paohaijiao.jstark.context.BeanPostProcessor;
import org.paohaijiao.jstark.context.MethodInterceptor;
import org.paohaijiao.jstark.context.bean.BeanDefinition;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class SimpleBeanContainer implements BeanContainer {

    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>();
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>();
    private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) {
        if (beanDefinitionMap.containsKey(beanName)) {
            throw new IllegalStateException("Bean name '" + beanName + "' exists");
        }
        beanDefinitionMap.put(beanName, beanDefinition);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(String beanName, Class<T> requiredType) {
        Object bean = doGetBean(beanName);
        if (requiredType != null && !requiredType.isInstance(bean)) {
            throw new IllegalArgumentException("Bean '" + beanName + "' is not   " + requiredType.getName() + " type");
        }
        return (T) bean;
    }

    protected Object doGetBean(String beanName) {
        Object sharedInstance = singletonObjects.get(beanName);
        if (sharedInstance != null) {
            return sharedInstance;
        }
        BeanDefinition bd = beanDefinitionMap.get(beanName);
        if (bd == null) {
            throw new IllegalArgumentException("can not find the  '" + beanName + "' Bean define");
        }
        Object bean = createBean(beanName, bd);
        if (bd.isSingleton()) {
            addSingleton(beanName, bean);
        }
        return bean;
    }

    protected Object createBean(String beanName, BeanDefinition bd) {
        try {
            if (bd.getBeanClass().isInterface() || bd.getBeanClass().isPrimitive() || bd.getBeanClass().isArray()) {
                throw new IllegalArgumentException("can not instance due to class is interface or Primitive or array " + bd.getBeanClass().getName());
            }
            //attemp get no argues method parameter
            Constructor<?>[] constructors = bd.getBeanClass().getDeclaredConstructors();
            //no arguement param is best option
            Constructor<?> constructorToUse = null;
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == 0) {
                    constructorToUse = constructor;
                    break;
                }
            }
            // if no argue param constructor then choose first constructors
            if (constructorToUse == null && constructors.length > 0) {
                constructorToUse = constructors[0];
                throw new UnsupportedOperationException("not support constructor");
            }
            if (constructorToUse == null) {
                throw new IllegalStateException("no available constructor");
            }
            constructorToUse.setAccessible(true);
            Object bean = constructorToUse.newInstance();
            return bean;
        } catch (Exception e) {
            throw new RuntimeException("create Bean '" + beanName + "' fail", e);
        }
    }

    protected void populateBean(String beanName, BeanDefinition bd, Object bean) {
        for (Field field : bd.getBeanClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                try {
                    field.setAccessible(true);
                    Object dependency = getBean(field.getName(), field.getType());
                    field.set(bean, dependency);
                } catch (Exception e) {
                    throw new RuntimeException("inject Dependency '" + field.getName() + "' into Bean '" + beanName + "' fail", e);
                }
            }
        }
    }

    protected void initializeBean(String beanName, BeanDefinition bd, Object bean) {
        if (bd.getInitMethodName() != null) {
            try {
                Method initMethod = bd.getBeanClass().getMethod(bd.getInitMethodName());
                initMethod.invoke(bean);
            } catch (Exception e) {
                throw new RuntimeException("invoke the method '" + bd.getInitMethodName() + "' fail", e);
            }
        }
    }

    protected Object resolveBeforeInstantiation(String beanName, BeanDefinition bd) {
        for (BeanPostProcessor bp : beanPostProcessors) {
            Object result = bp.postProcessBeforeInstantiation(bd.getBeanClass(), beanName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    protected Object applyBeanPostProcessorsBeforeInitialization(Object bean, String beanName) {
        Object result = bean;
        for (BeanPostProcessor processor : beanPostProcessors) {
            Object current = processor.postProcessBeforeInitialization(result, beanName);
            if (current == null) {
                return result;
            }
            result = current;
        }
        return result;
    }

    protected Object applyBeanPostProcessorsAfterInitialization(Object bean, String beanName) {
        Object result = bean;
        for (BeanPostProcessor processor : beanPostProcessors) {
            Object current = processor.postProcessAfterInitialization(result, beanName);
            if (current == null) {
                return result;
            }
            result = current;
        }
        return result;
    }

    protected void addSingleton(String beanName, Object singletonObject) {
        singletonObjects.put(beanName, singletonObject);
    }

    @Override
    public void addBeanPostProcessor(BeanPostProcessor processor) {
        this.beanPostProcessors.add(processor);
    }

    @Override
    public void registerInterceptor(String beanName, MethodInterceptor interceptor) {
        throw new UnsupportedOperationException("simple container not support registerInterceptor");
    }
}
