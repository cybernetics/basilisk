/*
 * Copyright 2008-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kordamp.basilisk.runtime.core.mvc;

import basilisk.core.ApplicationClassLoader;
import basilisk.core.ApplicationEvent;
import basilisk.core.BasiliskApplication;
import basilisk.core.artifact.ArtifactManager;
import basilisk.core.artifact.BasiliskArtifact;
import basilisk.core.artifact.BasiliskClass;
import basilisk.core.artifact.BasiliskController;
import basilisk.core.artifact.BasiliskMvcArtifact;
import basilisk.core.artifact.BasiliskView;
import basilisk.core.mvc.MVCGroup;
import basilisk.core.mvc.MVCGroupConfiguration;
import basilisk.exceptions.BasiliskException;
import basilisk.exceptions.FieldException;
import basilisk.exceptions.MVCGroupInstantiationException;
import basilisk.exceptions.NewInstanceException;
import basilisk.inject.Contextual;
import basilisk.util.CollectionUtils;
import com.googlecode.openbeans.PropertyDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static basilisk.core.BasiliskExceptionHandler.sanitize;
import static basilisk.util.AnnotationUtils.annotationsOfMethodParameter;
import static basilisk.util.AnnotationUtils.findAnnotation;
import static basilisk.util.AnnotationUtils.nameFor;
import static basilisk.util.BasiliskClassUtils.getAllDeclaredFields;
import static basilisk.util.BasiliskClassUtils.getPropertyDescriptors;
import static basilisk.util.BasiliskClassUtils.setFieldValue;
import static basilisk.util.BasiliskClassUtils.setPropertiesOrFieldsNoException;
import static basilisk.util.BasiliskClassUtils.setPropertyOrFieldValueNoException;
import static basilisk.util.BasiliskNameUtils.capitalize;
import static basilisk.util.BasiliskNameUtils.isBlank;
import static basilisk.util.ConfigUtils.getConfigValueAsBoolean;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * Base implementation of the {@code MVCGroupManager} interface.
 *
 * @author Andres Almiray
 */
public class DefaultMVCGroupManager extends AbstractMVCGroupManager {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultMVCGroupManager.class);
    private static final String CONFIG_KEY_COMPONENT = "component";
    private static final String CONFIG_KEY_EVENTS_LIFECYCLE = "events.lifecycle";
    private static final String CONFIG_KEY_EVENTS_LISTENER = "events.listener";
    private static final String KEY_PARENT_GROUP = "parentGroup";

    private final ApplicationClassLoader applicationClassLoader;

    @Inject
    public DefaultMVCGroupManager(@Nonnull BasiliskApplication application, @Nonnull ApplicationClassLoader applicationClassLoader) {
        super(application);
        this.applicationClassLoader = requireNonNull(applicationClassLoader, "Argument 'applicationClassLoader' must not be null");
    }

    protected void doInitialize(@Nonnull Map<String, MVCGroupConfiguration> configurations) {
        requireNonNull(configurations, "Argument 'configurations' must not be null");
        for (MVCGroupConfiguration configuration : configurations.values()) {
            addConfiguration(configuration);
        }
    }

    @Nonnull
    protected MVCGroup createMVCGroup(@Nonnull MVCGroupConfiguration configuration, @Nullable String mvcId, @Nonnull Map<String, Object> args) {
        requireNonNull(configuration, ERROR_CONFIGURATION_NULL);
        requireNonNull(args, ERROR_ARGS_NULL);

        mvcId = resolveMvcId(configuration, mvcId);
        checkIdIsUnique(mvcId, configuration);

        LOG.debug("Building MVC group '{}' with name '{}'", configuration.getMvcType(), mvcId);
        Map<String, Object> argsCopy = copyAndConfigureArguments(args, configuration, mvcId);

        // figure out what the classes are
        Map<String, ClassHolder> classMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> memberEntry : configuration.getMembers().entrySet()) {
            String memberType = memberEntry.getKey();
            String memberClassName = memberEntry.getValue();
            selectClassesPerMember(memberType, memberClassName, classMap);
        }

        Map<String, Object> instances = new LinkedHashMap<>();
        instances.putAll(instantiateMembers(classMap, argsCopy));

        MVCGroup group = newMVCGroup(configuration, mvcId, instances, (MVCGroup) args.get(KEY_PARENT_GROUP));
        adjustMvcArguments(group, argsCopy);

        boolean fireEvents = isConfigFlagEnabled(configuration, CONFIG_KEY_EVENTS_LIFECYCLE);
        if (fireEvents) {
            getApplication().getEventRouter().publishEvent(ApplicationEvent.INITIALIZE_MVC_GROUP.getName(), asList(configuration, group));
        }

        // special case -- controllers are added as application listeners
        if (isConfigFlagEnabled(group.getConfiguration(), CONFIG_KEY_EVENTS_LISTENER)) {
            BasiliskController controller = group.getController();
            if (controller != null) {
                getApplication().getEventRouter().addEventListener(controller);
            }
        }

        // mutually set each other to the available fields and inject args
        fillReferencedProperties(group, argsCopy);

        doAddGroup(group);

        initializeMembers(group, argsCopy);

        if (fireEvents) {
            getApplication().getEventRouter().publishEvent(ApplicationEvent.CREATE_MVC_GROUP.getName(), asList(group));
        }

        return group;
    }

    protected void adjustMvcArguments(@Nonnull MVCGroup group, @Nonnull Map<String, Object> args) {
        // must set it again because mvcId might have been initialized internally
        args.put("mvcId", group.getMvcId());
        args.put("mvcGroup", group);
        args.put("application", getApplication());
    }

    @Nonnull
    @SuppressWarnings("ConstantConditions")
    protected String resolveMvcId(@Nonnull MVCGroupConfiguration configuration, @Nullable String mvcId) {
        boolean component = getConfigValueAsBoolean(configuration.getConfig(), CONFIG_KEY_COMPONENT, false);

        if (isBlank(mvcId)) {
            if (component) {
                mvcId = configuration.getMvcType() + "-" + System.nanoTime();
            } else {
                mvcId = configuration.getMvcType();
            }
        }
        return mvcId;
    }

    @SuppressWarnings("unchecked")
    protected void selectClassesPerMember(@Nonnull String memberType, @Nonnull String memberClassName, @Nonnull Map<String, ClassHolder> classMap) {
        BasiliskClass basiliskClass = getApplication().getArtifactManager().findBasiliskClass(memberClassName);
        ClassHolder classHolder = new ClassHolder();
        if (basiliskClass != null) {
            classHolder.artifactClass = (Class<? extends BasiliskArtifact>) basiliskClass.getClazz();
        } else {
            classHolder.regularClass = loadClass(memberClassName);
        }
        classMap.put(memberType, classHolder);
    }

    @Nonnull
    protected Map<String, Object> copyAndConfigureArguments(@Nonnull Map<String, Object> args, @Nonnull MVCGroupConfiguration configuration, @Nonnull String mvcId) {
        Map<String, Object> argsCopy = CollectionUtils.<String, Object>map()
            .e("application", getApplication())
            .e("mvcType", configuration.getMvcType())
            .e("mvcId", mvcId)
            .e("configuration", configuration);

        if (args.containsKey(KEY_PARENT_GROUP)) {
            if (args.get(KEY_PARENT_GROUP) instanceof MVCGroup) {
                MVCGroup parentGroup = (MVCGroup) args.get(KEY_PARENT_GROUP);
                for (Map.Entry<String, Object> e : parentGroup.getMembers().entrySet()) {
                    args.put("parent" + capitalize(e.getKey()), e.getValue());
                }
            }
        }

        argsCopy.putAll(args);
        return argsCopy;
    }

    protected void checkIdIsUnique(@Nonnull String mvcId, @Nonnull MVCGroupConfiguration configuration) {
        if (findGroup(mvcId) != null) {
            String action = getApplication().getConfiguration().getAsString("basilisk.mvcid.collision", "exception");
            if ("warning".equalsIgnoreCase(action)) {
                LOG.warn("A previous instance of MVC group '{}' with name '{}' exists. Destroying the old instance first.", configuration.getMvcType(), mvcId);
                destroyMVCGroup(mvcId);
            } else {
                throw new MVCGroupInstantiationException("Can not instantiate MVC group '" + configuration.getMvcType() + "' with name '" + mvcId + "' because a previous instance with that name exists and was not disposed off properly.", configuration.getMvcType(), mvcId);
            }
        }
    }

    @Nonnull
    protected Map<String, Object> instantiateMembers(@Nonnull Map<String, ClassHolder> classMap, @Nonnull Map<String, Object> args) {
        // instantiate the parts
        Map<String, Object> instanceMap = new LinkedHashMap<>();
        for (Map.Entry<String, ClassHolder> classEntry : classMap.entrySet()) {
            String memberType = classEntry.getKey();
            if (args.containsKey(memberType)) {
                // use provided value, even if null
                instanceMap.put(memberType, args.get(memberType));
            } else {
                // otherwise create a new value
                ClassHolder classHolder = classEntry.getValue();
                if (classHolder.artifactClass != null) {
                    Class<? extends BasiliskArtifact> memberClass = classHolder.artifactClass;
                    ArtifactManager artifactManager = getApplication().getArtifactManager();
                    BasiliskClass basiliskClass = artifactManager.findBasiliskClass(memberClass);
                    BasiliskArtifact instance = artifactManager.newInstance(basiliskClass);
                    instanceMap.put(memberType, instance);
                    args.put(memberType, instance);
                } else {
                    Class<?> memberClass = classHolder.regularClass;
                    try {
                        Object instance = memberClass.newInstance();
                        getApplication().getInjector().injectMembers(instance);
                        instanceMap.put(memberType, instance);
                        args.put(memberType, instance);
                    } catch (InstantiationException | IllegalAccessException e) {
                        LOG.error("Can't create member {} with {}", memberType, memberClass);
                        throw new NewInstanceException(memberClass, e);
                    }
                }
            }
        }
        return instanceMap;
    }

    protected void initializeMembers(@Nonnull MVCGroup group, @Nonnull Map<String, Object> args) {
        LOG.debug("Initializing each MVC member of group '{}'", group.getMvcId());
        for (Map.Entry<String, Object> memberEntry : group.getMembers().entrySet()) {
            String memberType = memberEntry.getKey();
            Object member = memberEntry.getValue();
            if (member instanceof BasiliskArtifact) {
                initializeArtifactMember(group, memberType, (BasiliskArtifact) member, args);
            } else {
                initializeNonArtifactMember(group, memberType, member, args);
            }
        }
    }

    protected void initializeArtifactMember(@Nonnull MVCGroup group, @Nonnull String type, final @Nonnull BasiliskArtifact member, final @Nonnull Map<String, Object> args) {
        if (member instanceof BasiliskView) {
            getApplication().getUIThreadManager().runInsideUISync(new Runnable() {
                @Override
                public void run() {
                    try {
                        BasiliskView view = (BasiliskView) member;
                        view.initUI();
                        view.mvcGroupInit(args);
                    } catch (RuntimeException e) {
                        throw (RuntimeException) sanitize(e);
                    }
                }
            });
        } else if (member instanceof BasiliskMvcArtifact) {
            ((BasiliskMvcArtifact) member).mvcGroupInit(args);
        }
    }

    protected void initializeNonArtifactMember(@Nonnull MVCGroup group, @Nonnull String type, @Nonnull Object member, @Nonnull Map<String, Object> args) {
        // empty
    }

    protected void fillReferencedProperties(@Nonnull MVCGroup group, @Nonnull Map<String, Object> args) {
        for (Map.Entry<String, Object> memberEntry : group.getMembers().entrySet()) {
            String memberType = memberEntry.getKey();
            Object member = memberEntry.getValue();
            if (member instanceof BasiliskArtifact) {
                fillArtifactMemberProperties(memberType, (BasiliskArtifact) member, args);
            } else {
                fillNonArtifactMemberProperties(memberType, member, args);
            }
            fillContextualMemberProperties(group, memberType, member);
        }
    }

    private void fillArtifactMemberProperties(@Nonnull String type, @Nonnull BasiliskArtifact member, @Nonnull Map<String, Object> args) {
        // set the args and instances
        setPropertiesOrFieldsNoException(member, args);
    }

    protected void fillNonArtifactMemberProperties(@Nonnull String type, @Nonnull Object member, @Nonnull Map<String, Object> args) {
        // empty
    }

    protected void fillContextualMemberProperties(@Nonnull MVCGroup group, @Nonnull String type, @Nonnull Object member) {
        for (PropertyDescriptor descriptor : getPropertyDescriptors(member.getClass())) {
            Method method = descriptor.getWriteMethod();
            if (method != null && method.getAnnotation(Contextual.class) != null) {
                String key = nameFor(method);
                Object arg = group.getContext().get(key);

                Nonnull nonNull = findAnnotation(annotationsOfMethodParameter(method, 0), Nonnull.class);
                if (arg == null && nonNull != null) {
                    throw new IllegalStateException("Could not find an instance of type " +
                        method.getParameterTypes()[0].getName() + " under key '" + key +
                        "' in the context of MVCGroup[" + group.getMvcType() + ":" + group.getMvcId() +
                        "] to be injected on property '" + descriptor.getName() +
                        "' in " + type + " (" + member.getClass().getName() + "). Property does not accept null values.");
                }

                try {
                    method.invoke(member, arg);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new MVCGroupInstantiationException(group.getMvcType(), group.getMvcId(), e);
                }
            }
        }

        for (Field field : getAllDeclaredFields(member.getClass())) {
            if (field.getAnnotation(Contextual.class) != null) {
                String key = nameFor(field);
                Object arg = group.getContext().get(key);
                if (arg == null && field.getAnnotation(Nonnull.class) != null) {
                    throw new IllegalStateException("Could not find an instance of type " +
                        field.getType().getName() + " under key '" + key +
                        "' in the context of MVCGroup[" + group.getMvcType() + ":" + group.getMvcId() +
                        "] to be injected on field '" + field.getName() +
                        "' in " + type + " (" + member.getClass().getName() + "). Field does not accept null values.");
                }

                try {
                    setFieldValue(member, field.getName(), arg);
                } catch (FieldException e) {
                    throw new MVCGroupInstantiationException(group.getMvcType(), group.getMvcId(), e);
                }
            }
        }
    }

    protected void doAddGroup(@Nonnull MVCGroup group) {
        addGroup(group);
    }

    public void destroyMVCGroup(@Nonnull String mvcId) {
        MVCGroup group = findGroup(mvcId);
        LOG.debug("Group '{}' points to {}", mvcId, group);

        if (group == null) return;

        LOG.debug("Destroying MVC group identified by '{}'", mvcId);

        if (isConfigFlagEnabled(group.getConfiguration(), CONFIG_KEY_EVENTS_LISTENER)) {
            BasiliskController controller = group.getController();
            if (controller != null) {
                getApplication().getEventRouter().removeEventListener(controller);
            }
        }

        destroyMembers(group);

        doRemoveGroup(group);
        group.destroy();

        if (isConfigFlagEnabled(group.getConfiguration(), CONFIG_KEY_EVENTS_LIFECYCLE)) {
            getApplication().getEventRouter().publishEvent(ApplicationEvent.DESTROY_MVC_GROUP.getName(), asList(group));
        }
    }

    protected void destroyMembers(@Nonnull MVCGroup group) {
        for (Map.Entry<String, Object> memberEntry : group.getMembers().entrySet()) {
            if (memberEntry.getValue() instanceof BasiliskArtifact) {
                destroyArtifactMember(memberEntry.getKey(), (BasiliskArtifact) memberEntry.getValue());
            } else {
                destroyNonArtifactMember(memberEntry.getKey(), memberEntry.getValue());
            }
        }
    }

    protected void destroyArtifactMember(@Nonnull String type, @Nonnull BasiliskArtifact member) {
        if (member instanceof BasiliskMvcArtifact) {
            final BasiliskMvcArtifact artifact = (BasiliskMvcArtifact) member;

            if (artifact instanceof BasiliskView) {
                getApplication().getUIThreadManager().runInsideUISync(() -> {
                    try {
                        artifact.mvcGroupDestroy();
                    } catch (RuntimeException e) {
                        throw (RuntimeException) sanitize(e);
                    }
                });
            } else {
                artifact.mvcGroupDestroy();
            }

            // clear all parent* references
            for (String parentMemberName : new String[]{"parentModel", "parentView", "parentController", "parentGroup"}) {
                setPropertyOrFieldValueNoException(member, parentMemberName, null);
            }
        }

        destroyContextualMemberProperties(type, member);
    }

    protected void destroyContextualMemberProperties(@Nonnull String type, @Nonnull BasiliskArtifact member) {
        for (Field field : getAllDeclaredFields(member.getClass())) {
            if (field.getAnnotation(Contextual.class) != null) {
                try {
                    setFieldValue(member, field.getName(), null);
                } catch (FieldException e) {
                    throw new IllegalStateException("Could not nullify field " +
                        field.getName() + "' in " + type + " (" + member.getClass().getName() + ")", e);
                }
            }
        }
    }

    protected void destroyNonArtifactMember(@Nonnull String type, @Nonnull Object member) {
        // empty
    }

    protected void doRemoveGroup(@Nonnull MVCGroup group) {
        removeGroup(group);
    }

    protected boolean isConfigFlagEnabled(@Nonnull MVCGroupConfiguration configuration, @Nonnull String key) {
        return getConfigValueAsBoolean(configuration.getConfig(), key, true);
    }

    @Nullable
    protected Class<?> loadClass(@Nonnull String className) {
        try {
            return applicationClassLoader.get().loadClass(className);
        } catch (ClassNotFoundException e) {
            // #39 do not ignore this CNFE
            throw new BasiliskException(e.toString(), e);
        }
    }

    protected static final class ClassHolder {
        protected Class<?> regularClass;
        protected Class<? extends BasiliskArtifact> artifactClass;
    }
}
