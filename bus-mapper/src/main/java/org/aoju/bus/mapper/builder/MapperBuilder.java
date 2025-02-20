/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2023 aoju.org mybatis.io and other contributors.           *
 *                                                                               *
 * Permission is hereby granted, free of charge, to any person obtaining a copy  *
 * of this software and associated documentation files (the "Software"), to deal *
 * in the Software without restriction, including without limitation the rights  *
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell     *
 * copies of the Software, and to permit persons to whom the Software is         *
 * furnished to do so, subject to the following conditions:                      *
 *                                                                               *
 * The above copyright notice and this permission notice shall be included in    *
 * all copies or substantial portions of the Software.                           *
 *                                                                               *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR    *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,      *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE   *
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER        *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, *
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN     *
 * THE SOFTWARE.                                                                 *
 *                                                                               *
 ********************************************************************************/
package org.aoju.bus.mapper.builder;

import org.aoju.bus.core.exception.InternalException;
import org.aoju.bus.core.lang.Symbol;
import org.aoju.bus.core.toolkit.StringKit;
import org.aoju.bus.logger.Logger;
import org.aoju.bus.mapper.annotation.RegisterMapper;
import org.aoju.bus.mapper.builder.resolve.EntityResolve;
import org.aoju.bus.mapper.entity.Config;
import org.aoju.bus.mapper.provider.EmptyProvider;
import org.aoju.bus.mapper.reflect.Reflector;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.builder.annotation.ProviderSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理主要逻辑，最关键的一个类
 *
 * @author Kimi Liu
 * @since Java 17+
 */
public class MapperBuilder {

    /**
     * 注册的接口
     */
    private List<Class<?>> registerClass = new ArrayList<>();
    /**
     * 注册的通用Mapper接口
     */
    private Map<Class<?>, MapperTemplate> registerMapper = new ConcurrentHashMap<>();
    /**
     * 通用Mapper配置
     */
    private Config config = new Config();

    /**
     * 默认构造方法
     */
    public MapperBuilder() {

    }

    /**
     * 带配置的构造方法
     *
     * @param properties 属性
     */
    public MapperBuilder(Properties properties) {
        this();
        setProperties(properties);
    }

    /**
     * 通过通用Mapper接口获取对应的MapperTemplate
     *
     * @param mapperClass mapper类
     * @return the object
     * @throws Exception 异常
     */
    private MapperTemplate fromMapperClass(Class<?> mapperClass) {
        Method[] methods = mapperClass.getDeclaredMethods();
        Class<?> templateClass = null;
        Class<?> tempClass = null;
        Set<String> methodSet = new HashSet<>();
        for (Method method : methods) {
            if (method.isAnnotationPresent(SelectProvider.class)) {
                SelectProvider provider = method.getAnnotation(SelectProvider.class);
                tempClass = provider.type();
                methodSet.add(method.getName());
            } else if (method.isAnnotationPresent(InsertProvider.class)) {
                InsertProvider provider = method.getAnnotation(InsertProvider.class);
                tempClass = provider.type();
                methodSet.add(method.getName());
            } else if (method.isAnnotationPresent(DeleteProvider.class)) {
                DeleteProvider provider = method.getAnnotation(DeleteProvider.class);
                tempClass = provider.type();
                methodSet.add(method.getName());
            } else if (method.isAnnotationPresent(UpdateProvider.class)) {
                UpdateProvider provider = method.getAnnotation(UpdateProvider.class);
                tempClass = provider.type();
                methodSet.add(method.getName());
            }
            if (templateClass == null) {
                templateClass = tempClass;
            } else if (templateClass != tempClass) {
                Logger.error("一个通用Mapper中只允许存在一个MapperTemplate子类!");
                throw new InternalException("一个通用Mapper中只允许存在一个MapperTemplate子类!");
            }
        }
        if (templateClass == null || !MapperTemplate.class.isAssignableFrom(templateClass)) {
            templateClass = EmptyProvider.class;
        }
        MapperTemplate mapperTemplate;
        try {
            mapperTemplate = (MapperTemplate) templateClass.getConstructor(Class.class, MapperBuilder.class).newInstance(mapperClass, this);
        } catch (Exception e) {
            Logger.error("实例化MapperTemplate对象失败:" + e, e);
            throw new InternalException("实例化MapperTemplate对象失败:" + e.getMessage());
        }
        //注册方法
        for (String methodName : methodSet) {
            try {
                mapperTemplate.addMethodMap(methodName, templateClass.getMethod(methodName, MappedStatement.class));
            } catch (NoSuchMethodException e) {
                Logger.error(templateClass.getName() + "中缺少" + methodName + "方法!", e);
                throw new InternalException(templateClass.getName() + "中缺少" + methodName + "方法!");
            }
        }
        return mapperTemplate;
    }

    /**
     * 注册通用Mapper接口
     *
     * @param mapperClass mapper
     */
    public void registerMapper(Class<?> mapperClass) {
        if (!registerMapper.containsKey(mapperClass)) {
            registerClass.add(mapperClass);
            registerMapper.put(mapperClass, fromMapperClass(mapperClass));
        }
        //自动注册继承的接口
        Class<?>[] interfaces = mapperClass.getInterfaces();
        if (interfaces != null && interfaces.length > 0) {
            for (Class<?> anInterface : interfaces) {
                registerMapper(anInterface);
            }
        }
    }

    /**
     * 注册通用Mapper接口
     *
     * @param mapperClass mapper类
     */
    public void registerMapper(String mapperClass) {
        try {
            registerMapper(Class.forName(mapperClass));
        } catch (ClassNotFoundException e) {
            Logger.error("注册通用Mapper[" + mapperClass + "]失败，找不到该通用Mapper!", e);
            throw new InternalException("注册通用Mapper[" + mapperClass + "]失败，找不到该通用Mapper!");
        }
    }

    /**
     * 判断当前的接口方法是否需要进行拦截
     *
     * @param msId 方法信息
     * @return the object
     */
    public MapperTemplate isMapperMethod(String msId) {
        MapperTemplate mapperTemplate = getMapperTemplateByMsId(msId);
        if (mapperTemplate == null) {
            // 通过 @RegisterMapper 注解自动注册的功能
            try {
                Class<?> mapperClass = Reflector.getMapperClass(msId);
                if (mapperClass.isInterface() && hasRegisterMapper(mapperClass)) {
                    mapperTemplate = getMapperTemplateByMsId(msId);
                }
            } catch (Exception e) {
                Logger.warn("特殊情况: " + e);
            }
        }
        return mapperTemplate;
    }

    /**
     * 根据 msId 获取 MapperTemplate
     *
     * @param msId 方法信息
     * @return the object
     */
    public MapperTemplate getMapperTemplateByMsId(String msId) {
        for (Map.Entry<Class<?>, MapperTemplate> entry : registerMapper.entrySet()) {
            if (entry.getValue().supportMethod(msId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 判断接口是否包含通用接口，
     *
     * @param mapperInterface 接口信息
     * @return the boolean
     */
    public boolean isExtendCommonMapper(Class<?> mapperInterface) {
        for (Class<?> mapperClass : registerClass) {
            if (mapperClass.isAssignableFrom(mapperInterface)) {
                return true;
            }
        }
        // 通过 @RegisterMapper 注解自动注册的功能
        return hasRegisterMapper(mapperInterface);
    }

    /**
     * 增加通过 @RegisterMapper 注解自动注册的功能
     *
     * @param mapperInterface 接口信息
     * @return the boolean
     */
    private boolean hasRegisterMapper(Class<?> mapperInterface) {
        // 如果一个都没匹配上，很可能是还没有注册 mappers，此时通过 @RegisterMapper 注解进行判断
        Class<?>[] interfaces = mapperInterface.getInterfaces();
        boolean hasRegisterMapper = false;
        if (interfaces != null && interfaces.length > 0) {
            for (Class<?> anInterface : interfaces) {
                // 自动注册标记了 @RegisterMapper 的接口
                if (anInterface.isAnnotationPresent(RegisterMapper.class)) {
                    hasRegisterMapper = true;
                    // 如果已经注册过，就避免在反复调用下面会迭代的方法
                    if (!registerMapper.containsKey(anInterface)) {
                        registerMapper(anInterface);
                    }
                }
                // 如果父接口的父接口存在注解，也可以注册
                else if (hasRegisterMapper(anInterface)) {
                    hasRegisterMapper = true;
                }
            }
        }
        return hasRegisterMapper;
    }

    /**
     * 配置完成后，执行下面的操作
     * 处理configuration中全部的MappedStatement
     *
     * @param configuration 配置
     */
    public void processConfiguration(Configuration configuration) {
        processConfiguration(configuration, null);
    }

    /**
     * 配置指定的接口
     *
     * @param configuration   配置
     * @param mapperInterface 接口
     */
    public void processConfiguration(Configuration configuration, Class<?> mapperInterface) {
        String prefix;
        if (mapperInterface != null) {
            prefix = mapperInterface.getName();
        } else {
            prefix = "";
        }
        for (Object object : new ArrayList<Object>(configuration.getMappedStatements())) {
            if (object instanceof MappedStatement) {
                MappedStatement ms = (MappedStatement) object;
                if (ms.getId().startsWith(prefix)) {
                    processMappedStatement(ms);
                }
            }
        }
    }

    /**
     * 处理 MappedStatement
     *
     * @param ms MappedStatement
     */
    public void processMappedStatement(MappedStatement ms) {
        MapperTemplate mapperTemplate = isMapperMethod(ms.getId());
        if (mapperTemplate != null && ms.getSqlSource() instanceof ProviderSqlSource) {
            setSqlSource(ms, mapperTemplate);
        }
    }

    /**
     * 获取通用Mapper配置
     *
     * @return the object
     */
    public Config getConfig() {
        return config;
    }

    /**
     * 设置通用Mapper配置
     *
     * @param config 配置
     */
    public void setConfig(Config config) {
        this.config = config;
        if (config.getResolveClass() != null) {
            try {
                EntityBuilder.setResolve(config.getResolveClass().getConstructor().newInstance());
            } catch (Exception e) {
                Logger.error("创建 " + config.getResolveClass().getName()
                        + " 实例失败，请保证该类有默认的构造方法!", e);
                throw new InternalException("创建 " + config.getResolveClass().getName()
                        + " 实例失败，请保证该类有默认的构造方法!", e);
            }
        }
        if (config.getMappers() != null && config.getMappers().size() > 0) {
            for (Class mapperClass : config.getMappers()) {
                registerMapper(mapperClass);
            }
        }
    }

    /**
     * 配置属性
     *
     * @param properties 属性
     */
    public void setProperties(Properties properties) {
        config.setProperties(properties);
        //注册解析器
        if (properties != null) {
            String resolveClass = properties.getProperty("resolveClass");
            if (StringKit.isNotEmpty(resolveClass)) {
                try {
                    EntityBuilder.setResolve((EntityResolve) Class.forName(resolveClass).getConstructor().newInstance());
                } catch (Exception e) {
                    Logger.error("创建 " + resolveClass + " 实例失败!", e);
                    throw new InternalException("创建 " + resolveClass + " 实例失败!", e);
                }
            }
        }
        //注册通用接口
        if (properties != null) {
            String mapper = properties.getProperty("mappers");
            if (StringKit.isNotEmpty(mapper)) {
                String[] mappers = mapper.split(Symbol.COMMA);
                for (String mapperClass : mappers) {
                    if (mapperClass.length() > 0) {
                        registerMapper(mapperClass);
                    }
                }
            }
        }
    }

    /**
     * 重新设置SqlSource
     * 执行该方法前必须使用isMapperMethod判断，否则msIdCache会空
     *
     * @param ms             MappedStatement
     * @param mapperTemplate 模板信息
     */
    public void setSqlSource(MappedStatement ms, MapperTemplate mapperTemplate) {
        try {
            if (mapperTemplate != null) {
                mapperTemplate.setSqlSource(ms);
            }
        } catch (Exception e) {
            throw new InternalException(e);
        }
    }

}
