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
import org.gradle.internal.progress.BuildOperationInternal;
import org.gradle.internal.progress.InternalBuildListener;
import org.gradle.internal.progress.OperationResult;
import org.gradle.internal.progress.OperationStartEvent;

import javax.inject.Inject;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GradleTracingPlugin implements Plugin<Project> {
    public static final String BUILD_OPERATION = "BUILD_OPERATION";
    private final BuildRequestMetaData buildRequestMetaData;
    public static final String BUILD_TASK_GRAPH = "build task graph";
    final List<TraceEvent> events = new ArrayList<>();

    @Inject
    public GradleTracingPlugin(BuildRequestMetaData buildRequestMetaData) {
        this.buildRequestMetaData = buildRequestMetaData;
    }

    private void start(String name, String category) {
        events.add(TraceEvent.started(name, category));
    }

    private boolean finish(String name, String category) {
        return events.add(TraceEvent.finished(name, category));
    }

    @Override
    public void apply(Project project) {
        project.getGradle().addListener(new TaskExecutionListener() {
            @Override
            public void beforeExecute(Task task) {
                start(task.getPath(), "TASK");
            }

            @Override
            public void afterExecute(Task task, TaskState taskState) {
                finish(task.getPath(), "TASK");
            }
        });

        project.getGradle().addListener(new DependencyResolutionListener() {
            @Override
            public void beforeResolve(ResolvableDependencies resolvableDependencies) {
                start(resolvableDependencies.getPath(), "RESOLVE");
            }

            @Override
            public void afterResolve(ResolvableDependencies resolvableDependencies) {
                finish(resolvableDependencies.getPath(), "RESOLVE");
            }
        });

        project.getGradle().addListener(new ProjectEvaluationListener() {
            @Override
            public void beforeEvaluate(Project project) {
                start(project.getPath(), "EVALUATE");
            }

            @Override
            public void afterEvaluate(Project project, ProjectState projectState) {
                finish(project.getPath(), "EVALUATE");
            }
        });

        project.getGradle().addListener(new InternalBuildListener() {
            @Override
            public void started(BuildOperationInternal buildOperationInternal, OperationStartEvent operationStartEvent) {
                start(buildOperationInternal.getDisplayName(), BUILD_OPERATION);
            }

            @Override
            public void finished(BuildOperationInternal buildOperationInternal, OperationResult operationResult) {
                finish(buildOperationInternal.getDisplayName(), BUILD_OPERATION);
            }
        });

        project.getGradle().getTaskGraph().whenReady(new Action<TaskExecutionGraph>() {
            @Override
            public void execute(TaskExecutionGraph taskExecutionGraph) {
                finish(BUILD_TASK_GRAPH, "PHASE");
            }
        });

        project.getGradle().addListener(new JsonAdapter(project.getBuildDir()));
    }

    private class JsonAdapter extends BuildAdapter {
        public static final String BUILD_DURATION = "build duration";
        private final File buildDir;

        public JsonAdapter(File buildDir) {
            this.buildDir = buildDir;
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            start(BUILD_TASK_GRAPH, "PHASE");
        }

        @Override
        public void buildFinished(BuildResult result) {
            events.add(TraceEvent.started(BUILD_DURATION, "PHASE", toNanoTime(buildRequestMetaData.getBuildTimeClock().getStartTime())));
            finish(BUILD_DURATION, "PHASE");

            File traceFile = getTraceFile(buildDir);

            appendResourceToFile("/trace-header.html", traceFile, false);
            writeEvents(traceFile);
            appendResourceToFile("/trace-footer.html", traceFile, true);
        }

        private void appendResourceToFile(String resourcePath, File traceFile, boolean append) {
            try(OutputStream out = new FileOutputStream(traceFile, append);
                InputStream in = getClass().getResourceAsStream(resourcePath)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    private void writeEvents(File traceFile) {
        PrintWriter writer = getPrintWriter(traceFile);
        writer.println("{\n" +
                "  \"traceEvents\": [\n");

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

    private File getTraceFile(File buildDir) {
        File jsonDir = new File(buildDir, "trace");
        jsonDir.mkdirs();
        return new File(jsonDir, "task-trace.html");
    }

    private PrintWriter getPrintWriter(File jsonFile) {
        try {
            return new PrintWriter(new FileWriter(jsonFile, true), true);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private long toNanoTime(long timeInMillis) {
        long elapsedMillis = System.currentTimeMillis() - timeInMillis;
        long elapsedNanos = TimeUnit.MILLISECONDS.toNanos(elapsedMillis);
        return System.nanoTime() - elapsedNanos;
    }
}
