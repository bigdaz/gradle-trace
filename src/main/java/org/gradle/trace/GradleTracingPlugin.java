package org.gradle.trace;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.*;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskState;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.UncheckedException;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GradleTracingPlugin implements Plugin<Project> {
    private final BuildRequestMetaData buildRequestMetaData;
    public static final String BUILD_TASK_GRAPH = "build task graph";
    final List<TraceEvent> events = new ArrayList<>();

    @Inject
    public GradleTracingPlugin(BuildRequestMetaData buildRequestMetaData) {
        this.buildRequestMetaData = buildRequestMetaData;
    }

    private void started(String name, String category) {
        events.add(TraceEvent.started(name, category));
    }

    private boolean finished(String name, String category) {
        return events.add(TraceEvent.finished(name, category));
    }

    @Override
    public void apply(Project project) {
        project.getGradle().getTaskGraph().addTaskExecutionListener(new TaskExecutionListener() {
            @Override
            public void beforeExecute(Task task) {
                started(task.getPath(), "TASK");
            }

            @Override
            public void afterExecute(Task task, TaskState taskState) {
                finished(task.getPath(), "TASK");
            }
        });

        project.getGradle().getTaskGraph().whenReady(new Action<TaskExecutionGraph>() {
            @Override
            public void execute(TaskExecutionGraph taskExecutionGraph) {
                finished(BUILD_TASK_GRAPH, "PHASE");
            }
        });

        project.getGradle().addListener(new DependencyResolutionListener() {
            @Override
            public void beforeResolve(ResolvableDependencies resolvableDependencies) {
                started(resolvableDependencies.getPath(), "RESOLVE");
            }

            @Override
            public void afterResolve(ResolvableDependencies resolvableDependencies) {
                finished(resolvableDependencies.getPath(), "RESOLVE");
            }
        });

        project.getGradle().addListener(new ProjectEvaluationListener() {
            @Override
            public void beforeEvaluate(Project project) {
                started(project.getPath(), "EVALUATE");
            }

            @Override
            public void afterEvaluate(Project project, ProjectState projectState) {
                finished(project.getPath(), "EVALUATE");
            }
        });

        project.getGradle().addBuildListener(new JsonAdapter(project.getBuildDir()));
    }

    private PrintWriter getPrintWriter(File jsonFile) {
        try {
            return new PrintWriter(new FileWriter(jsonFile), true);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private class JsonAdapter extends BuildAdapter {
        public static final String BUILD_DURATION = "build duration";
        private final File buildDir;

        public JsonAdapter(File buildDir) {
            this.buildDir = buildDir;
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            started(BUILD_TASK_GRAPH, "PHASE");
        }

        @Override
        public void buildFinished(BuildResult result) {
            File traceFile = getTraceFile(buildDir);
            PrintWriter writer = getPrintWriter(traceFile);
            writer.println("{\n" +
                    "  \"traceEvents\": [\n");


            events.add(TraceEvent.started(BUILD_DURATION, "PHASE", toNanoTime(buildRequestMetaData.getBuildTimeClock().getStartTime())));
            finished(BUILD_DURATION, "PHASE");

            Iterator<TraceEvent> itr = events.iterator();
            while (itr.hasNext()) {
                writer.print(itr.next().toString());
                writer.println(itr.hasNext() ? "," : "");
            }

            writer.println("],\n" +
                    "  \"displayTimeUnit\": \"ns\",\n" +
                    "  \"systemTraceEvents\": \"SystemTraceData\",\n" +
                    "  \"otherData\": {\n" +
                    "    \"version\": \"My Application v1.0\"\n" +
                    "  }\n" +
                    "}\n");
        }
    }

    private File getTraceFile(File buildDir) {
        File jsonDir = new File(buildDir, "trace");
        jsonDir.mkdirs();
        return new File(jsonDir, "task-trace.json");
    }

    private long toNanoTime(long timeInMillis) {
        long elapsedMillis = System.currentTimeMillis() - timeInMillis;
        long elapsedNanos = TimeUnit.MILLISECONDS.toNanos(elapsedMillis);
        return System.nanoTime() - elapsedNanos;
    }
}
