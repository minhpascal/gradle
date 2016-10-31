/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.process.internal.daemon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.daemon.DaemonForkOptions;
import org.gradle.process.daemon.WorkerDaemonBuilder;
import org.gradle.process.internal.DefaultJavaForkOptions;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Set;

public abstract class AbstractWorkerDaemonBuilder<T> implements WorkerDaemonBuilder<T> {
    private final WorkerDaemonFactory workerDaemonFactory;
    private final FileResolver fileResolver;
    private final JavaForkOptions javaForkOptions;
    private final Set<File> classpath = Sets.newLinkedHashSet();
    private final Set<String> sharedPackages = Sets.newLinkedHashSet();
    private Class<? extends T> implementationClass;
    private Object[] params;

    public AbstractWorkerDaemonBuilder(WorkerDaemonFactory workerDaemonFactory, FileResolver fileResolver) {
        this.workerDaemonFactory = workerDaemonFactory;
        this.fileResolver = fileResolver;
        this.javaForkOptions = new DefaultJavaForkOptions(fileResolver);
    }

    @Override
    public WorkerDaemonBuilder<T> classpath(Iterable<File> files) {
        GUtil.addToCollection(classpath, files);
        return this;
    }

    @Override
    public WorkerDaemonBuilder<T> sharedPackages(Iterable<String> packages) {
        GUtil.addToCollection(sharedPackages, packages);
        return this;
    }

    @Override
    public WorkerDaemonBuilder<T> forkOptions(Action<JavaForkOptions> forkOptionsAction) {
        forkOptionsAction.execute(javaForkOptions);
        return this;
    }

    @Override
    public JavaForkOptions getForkOptions() {
        return javaForkOptions;
    }

    @Override
    public WorkerDaemonBuilder<T> implementationClass(Class<? extends T> implementationClass) {
        this.implementationClass = implementationClass;
        return this;
    }

    @Override
    public WorkerDaemonBuilder<T> params(Object... params) {
        this.params = params;
        return this;
    }

    protected Set<File> getClasspath() {
        return classpath;
    }

    protected Set<String> getSharedPackages() {
        return sharedPackages;
    }

    protected Class<? extends T> getImplementationClass() {
        return implementationClass;
    }

    protected Object[] getParams() {
        return params;
    }

    protected WorkerDaemonFactory getWorkerDaemonFactory() {
        return workerDaemonFactory;
    }

    static DaemonForkOptions toDaemonOptions(Class<?> actionClass, Iterable<Class<?>> paramClasses, JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages) {
        ImmutableList.Builder<File> classpathBuilder = ImmutableList.builder();
        ImmutableList.Builder<String> sharedPackagesBuilder = ImmutableList.builder();

        if (classpath != null) {
            classpathBuilder.addAll(classpath);
        }

        classpathBuilder.add(ClasspathUtil.getClasspathForClass(Action.class));
        classpathBuilder.add(ClasspathUtil.getClasspathForClass(actionClass));

        if (sharedPackages != null) {
            sharedPackagesBuilder.addAll(sharedPackages);
        }

        if (actionClass.getPackage() != null) {
            sharedPackagesBuilder.add(actionClass.getPackage().getName());
        }

        sharedPackagesBuilder.add("org.gradle.api");

        for (Class<?> paramClass : paramClasses) {
            if (paramClass.getClassLoader() != null) {
                classpathBuilder.add(ClasspathUtil.getClasspathForClass(paramClass));
            }
            if (paramClass.getPackage() != null) {
                sharedPackagesBuilder.add(paramClass.getPackage().getName());
            }
        }

        Iterable<File> daemonClasspath = classpathBuilder.build();
        Iterable<String> daemonSharedPackages = sharedPackagesBuilder.build();

        return new DaemonForkOptions(forkOptions.getMinHeapSize(), forkOptions.getMaxHeapSize(), forkOptions.getAllJvmArgs(), daemonClasspath, daemonSharedPackages);
    }
}
